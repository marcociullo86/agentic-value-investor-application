import { apiGet, type ApiResult } from '@/lib/api/client';

/**
 * Backtest "Verifica storica" domain API (TSK-350 — US-106, EP-024 Fase 3).
 *
 * Wrapper sopra `apiClient` (lib/api/client.ts), allineato pattern
 * `summary.ts` / `technical.ts` / `deep-analysis.ts` / `historical.ts`.
 *
 * Endpoint contract: design_&_architecture/api/openapi.yaml
 *   §/api/analysis/{ticker}/backtest — schema `BacktestResponse`
 *   (EP-024 Fase 3 BE, US-105).
 *
 * NOTA tipi TS: il generatore `openapi-typescript` (script
 * `npm run generate:api`) emette `lib/api/generated/schema.ts` (gitignored).
 * Definiamo qui i tipi minimal del subset usato in US-106, structurally
 * compatibili con le definizioni OpenAPI — drop-in replace quando il
 * generato sarà rigenerato in CI/local (la CI rigenera in stage fe-build).
 *
 * Il backtest è una VERIFICA STORICA per-ticker, **on-demand** (mai al mount
 * della pagina): viene innescato solo al click esplicito del bottone
 * "BACKTEST". L'engine deterministico vive BE-side; il FE renderizza solo.
 *
 * `equity` è un metadato del DTO BE — NON entra nei calcoli del backtest
 * (US-105 §"Vincoli di scope", il backtest opera in % di rendimento) e NON
 * è MAI persistita server-side. Il FE la mantiene in localStorage e la
 * forwarda solo come query param informativo.
 */

/* ------------------------------------------------------------------ */
/*  Enum tipati (verbatim OpenAPI §components/schemas)                  */
/* ------------------------------------------------------------------ */

/**
 * Stato del backtest (US-105):
 *  - OK                   → backtest completato, `strategies` + `timingEdge` popolati.
 *  - INSUFFICIENT_HISTORY → storico FMP non copre la finestra (IPO recente / years
 *                           troppo lunghi). `insufficientHistoryReason` esplicito.
 *                           NESSUN risultato parziale — il FE mostra messaggio
 *                           dedicato senza chart/tabelle fuorvianti.
 */
export type BacktestStatus = 'OK' | 'INSUFFICIENT_HISTORY';

/**
 * Strategia simulata sulla stessa finestra di lookback (US-105):
 *  - EP024_ENTER_NOW → entry sui segnali EP-024 ENTER_NOW (gate VI + TA),
 *                      exit VI_TARGET / STOP_HIT / HORIZON.
 *  - VI_ONLY         → entra ad ogni `t` con gate VI positivo (ignora il
 *                      timing TA). Baseline per isolare l'edge del layer di
 *                      timing.
 *  - BUY_AND_HOLD    → trade unico — compra al primo EOD, vende all'ultimo.
 */
export type BacktestStrategy =
  | 'EP024_ENTER_NOW'
  | 'VI_ONLY'
  | 'BUY_AND_HOLD';

/**
 * Causale di uscita dal round-trip (priorità decrescente):
 *  - VI_TARGET → prezzo >= dcfIntrinsicValue a t (margin of safety chiusa).
 *  - STOP_HIT  → prezzo <= stopSuggestion.stopPrice a t (tesi rotta).
 *  - HORIZON   → raggiunto `horizonMonths` senza target né stop.
 *
 * Mappato in UI a colori marker timeline (verde / rosso / grigio neutro).
 */
export type BacktestExitReason = 'VI_TARGET' | 'STOP_HIT' | 'HORIZON';

/**
 * Etichetta del `timingEdgePct` (US-105):
 *  - POSITIVE_EDGE → > +2pp. Il layer di timing TA ha aggiunto soldi.
 *  - NEUTRAL       → entro ±2pp. Edge irrilevante.
 *  - NEGATIVE_EDGE → < -2pp. Il layer di timing TA ha distrutto soldi —
 *                    mostrato con pari risalto del positivo (lente di
 *                    valore [[value-investing-design-lens]], niente
 *                    cherry-picking).
 */
export type BacktestTimingEdgeLabel =
  | 'POSITIVE_EDGE'
  | 'NEUTRAL'
  | 'NEGATIVE_EDGE';

/* ------------------------------------------------------------------ */
/*  Sub-blocchi tipati                                                  */
/* ------------------------------------------------------------------ */

export interface BacktestWindow {
  /** Date ISO YYYY-MM-DD (BE field `format: date`). */
  readonly fromDate: string;
  readonly toDate: string;
  readonly years: number;
  readonly horizonMonths: number;
}

export interface BacktestExitBreakdown {
  readonly viTarget: number;
  readonly stopHit: number;
  readonly horizon: number;
}

export interface BacktestStrategyMetrics {
  readonly strategy: BacktestStrategy;
  readonly trades: number;
  /** Quota di trade con returnPct > 0. Null se trades = 0. */
  readonly winRate: number | null;
  /** Media aritmetica dei returnPct per round-trip. */
  readonly avgReturnPct: number | null;
  readonly medianReturnPct: number | null;
  /** Media degli holding days per round-trip. */
  readonly avgHoldingDays: number | null;
  /** Media del rapporto (returnPct / drawdown intra-trade). */
  readonly avgRealizedRewardRisk: number | null;
  /** Total return composto: prod(1 + returnPct) − 1. */
  readonly totalReturnPct: number | null;
  /** Drawdown massimo intra-trade osservato (magnitudo positiva). */
  readonly maxTradeDrawdownPct: number | null;
  /** Null per BUY_AND_HOLD (trade unico, nessun breakdown). */
  readonly exitBreakdown: BacktestExitBreakdown | null;
  /**
   * True quando la strategia non ha generato alcun trade nella finestra
   * (es. EP024 senza segnali ENTER_NOW). Driver dello stato `empty` lato
   * hook + messaggio dedicato in UI.
   */
  readonly noSignalsInPeriod: boolean;
}

export interface BacktestTimingEdge {
  /**
   * avgReturnPct(EP024) − avgReturnPct(VI_ONLY). Null se una delle due
   * strategie non ha trade (no comparison possibile → label NEUTRAL).
   */
  readonly timingEdgePct: number | null;
  readonly label: BacktestTimingEdgeLabel;
  /**
   * True se EP024_ENTER_NOW non ha generato segnali nella finestra
   * (degrada il confronto → label = NEUTRAL).
   */
  readonly noSignalsInPeriod: boolean;
}

export interface BacktestTrade {
  readonly strategy: BacktestStrategy;
  readonly entryDate: string;
  readonly entryPrice: number;
  readonly exitDate: string;
  readonly exitPrice: number;
  readonly exitReason: BacktestExitReason;
  /** (exitPrice − entryPrice) / entryPrice × 100. */
  readonly returnPct: number;
  /** Giorni di calendario tra entryDate e exitDate. */
  readonly holdingDays: number;
  /** Drawdown intra-trade massimo (magnitudo positiva, %). */
  readonly maxIntraTradeDrawdownPct: number;
}

export interface BacktestCaveats {
  /** Fondamentali FMP ristrutturati — `filingDate` toglie look-ahead grossolano ma non revisioni. */
  readonly lookAheadResidual: boolean;
  /** Single ticker → no survivorship bias ma risultato NON generalizzabile. */
  readonly singleTicker: boolean;
  /** Verifica storica del timing su QUESTO ticker, NON una equity curve di portafoglio. */
  readonly notPortfolioPerformance: boolean;
}

/* ------------------------------------------------------------------ */
/*  Root payload                                                        */
/* ------------------------------------------------------------------ */

/**
 * Payload del backtest per-ticker (US-105):
 *  - `status = OK`                   → `window`, `strategies` (3 elementi),
 *                                       `timingEdge`, `trades` popolati.
 *  - `status = INSUFFICIENT_HISTORY` → `insufficientHistoryReason` valorizzato;
 *                                       il resto a `null`. Il FE rende un
 *                                       messaggio esplicito senza chart
 *                                       parziali fuorvianti.
 *
 * `caveats` SEMPRE presente, anche per `status = OK` — il banner caveat è
 * sempre visibile in UI (lente di valore: nascondere i limiti = marketing).
 */
export interface BacktestResponse {
  readonly ticker: string;
  /** ISO-8601 — istante valutazione lato BE. */
  readonly evaluatedAt: string;
  readonly status: BacktestStatus;
  /** Presente SOLO quando status = INSUFFICIENT_HISTORY. */
  readonly insufficientHistoryReason: string | null;
  /** Null quando status = INSUFFICIENT_HISTORY. */
  readonly window: BacktestWindow | null;
  /** 3 elementi (EP024_ENTER_NOW, VI_ONLY, BUY_AND_HOLD) o null. */
  readonly strategies: ReadonlyArray<BacktestStrategyMetrics> | null;
  readonly timingEdge: BacktestTimingEdge | null;
  /** Trade individuali EP024_ENTER_NOW + VI_ONLY. Null se INSUFFICIENT_HISTORY. */
  readonly trades: ReadonlyArray<BacktestTrade> | null;
  readonly caveats: BacktestCaveats;
}

/* ------------------------------------------------------------------ */
/*  Fetcher                                                             */
/* ------------------------------------------------------------------ */

/**
 * Anni di lookback ammessi dal selettore FE.
 *  - 3 / 5 → enumerati semplici;
 *  - 'max' → mappato sul valore massimo OpenAPI (20).
 *
 * Il BE accetta integer 1..20. Tenere l'enum stringato lato FE evita di
 * leakare il valore di "max" (oggi 20, domani potrebbe cambiare) nei test
 * della UI selectors.
 */
export type BacktestYearsOption = 3 | 5 | 'max';
export const BACKTEST_YEARS_MAX = 20;

/**
 * Orizzonti di holding ammessi (US-105 §Endpoint, validati BE):
 * 1 / 3 / 6 / 12 mesi. Un valore diverso causa HTTP 400 (validazione
 * dichiarativa, niente clamping silenzioso).
 */
export type BacktestHorizonMonths = 1 | 3 | 6 | 12;
export const BACKTEST_HORIZON_OPTIONS: ReadonlyArray<BacktestHorizonMonths> = [
  1, 3, 6, 12,
];

export interface GetBacktestOptions {
  /**
   * Anni di lookback (default 5 anni lato BE). `'max'` viene tradotto
   * nel limite OpenAPI (20) lato FE.
   */
  readonly years?: BacktestYearsOption;
  /**
   * Orizzonte massimo di holding del round-trip in mesi (default 6 lato BE).
   */
  readonly horizonMonths?: BacktestHorizonMonths;
  /**
   * Capitale di riferimento (metadato del DTO BE — NON entra nei calcoli, NON
   * persistita server-side, NON parte della chiave di cache). Riuso semantica
   * US-100/TSK-335: il FE lo mantiene in localStorage per il rendering del
   * "valore in dollari" lato client.
   */
  readonly equity?: number;
}

function resolveYearsParam(value: BacktestYearsOption | undefined): number | null {
  if (value === undefined) return null;
  if (value === 'max') return BACKTEST_YEARS_MAX;
  return value;
}

/**
 * GET /api/analysis/{ticker}/backtest?years=…&horizonMonths=…&equity=…
 *
 * Errori HTTP rilanciati al caller (hook/componente):
 *  - 400 → parametri invalidi (years fuori [1..20], horizonMonths ∉ {1,3,6,12})
 *  - 404 → ticker inesistente
 *  - 503 → FMP indisponibile (ProblemDetail body)
 */
export async function getBacktest(
  ticker: string,
  options: GetBacktestOptions = {},
): Promise<BacktestResponse> {
  const normalized = ticker.trim().toUpperCase();
  const params = new URLSearchParams();
  const years = resolveYearsParam(options.years);
  if (years !== null) {
    params.set('years', years.toString());
  }
  if (options.horizonMonths !== undefined) {
    params.set('horizonMonths', options.horizonMonths.toString());
  }
  if (
    options.equity !== undefined &&
    Number.isFinite(options.equity) &&
    options.equity > 0
  ) {
    params.set('equity', options.equity.toString());
  }
  const qs = params.toString();
  const result: ApiResult<BacktestResponse> = await apiGet<BacktestResponse>(
    `/api/analysis/${encodeURIComponent(normalized)}/backtest${
      qs.length > 0 ? `?${qs}` : ''
    }`,
  );
  return result.data;
}
