import { apiGet, type ApiResult } from '@/lib/api/client';

/**
 * Analysis domain API (TSK-021 — US-014).
 *
 * Wrapper sopra `apiClient` (lib/api/client.ts, zero-touch da TSK-030).
 * Consumato da `lib/stores/useAnalysisStore.ts` e indirettamente dai
 * componenti `components/analysis/TrafficLightPanel.tsx`,
 * `RuleSignalCard.tsx`, `ValuationSummary.tsx`.
 *
 * Endpoint contract: design_&_architecture/api/openapi.yaml
 *   §/api/analysis/{ticker} — schemas `RuleEngineResult` / `RuleSignal`
 *   / `Signal` / `DcfMethod`.
 *
 * Naming CANONICAL:
 *  - Root object: `RuleEngineResult` (verbatim da OpenAPI line 536).
 *  - Timestamp snapshot dati: `dataSnapshotAt` (verbatim da OpenAPI; il
 *    TSK-021 spec menzionava `sourceSnapshotFetchedAt` ma la gerarchia
 *    delle fonti (PATTERN §1) pone OpenAPI sopra il TSK: OpenAPI vince).
 *  - `evaluatedAt` aggiuntivo per quando il Rule Engine ha effettuato la
 *    valutazione (≠ `dataSnapshotAt` che è il momento snapshot upstream FMP).
 *  - `Signal` enum allargato include `NOT_APPLICABLE` (usato da `mosSignal`)
 *    rispetto al subset gestito da `lib/utils/signal-color.ts` (TSK-030,
 *    che copre solo le 5 regole quantitative). Definiamo qui un alias
 *    `MosSignal` che è strutturalmente il `Signal` completo.
 *
 * NOTA tipi TS: il generatore `openapi-typescript` (script `npm run
 * generate:api`) emette `lib/api/generated/schema.ts` ma è gitignored.
 * Definiamo qui i tipi minimal del subset usato in US-014, structurally
 * compatibili (drop-in replace).
 */

/** Enum `Signal` allargato — verbatim OpenAPI §components/schemas/Signal. */
export type Signal =
  | 'GREEN'
  | 'YELLOW'
  | 'RED'
  | 'INDETERMINATE'
  | 'NOT_APPLICABLE'
  | 'NOT_CALCULABLE';

/** Alias semantico — `mosSignal` usa l'intero enum `Signal` (include `NOT_APPLICABLE`). */
export type MosSignal = Signal;

/** Enum `DcfMethod` — verbatim OpenAPI §components/schemas/DcfMethod. */
export type DcfMethod = 'GREENWALD' | 'FCF_FALLBACK' | 'NOT_APPLICABLE';

/** Enum `DcfMethodSource` — ADR-011 / US-020. */
export type DcfMethodSource = 'DEFAULT_POLICY' | 'USER_OVERRIDE';

export interface RuleSignal {
  readonly ruleId: string;
  readonly signal: Signal;
  /** Valore osservato; `null` quando la regola non è calcolabile. */
  readonly observedValue: number | null;
  /** Stringa descrittiva della soglia (es. "ROE ≥ 15%"). */
  readonly threshold: string;
  /** Razionale human-readable; opzionale nel contratto, default `''`. */
  readonly rationale?: string;
}

/**
 * EP-013 — Mr. Market Context Flags (US-056 RSI 14-day + US-057 SMA200).
 *
 * `ContextFlags` raggruppa 2 advisory flag tecnici complementari ai 13
 * `ruleSignals` fondamentali del Rule Engine. NON contribuiscono a
 * `mosSignal` né al verdetto — sono pure UI hint per timing/sentiment.
 *
 * Sorgenti contratto: OpenAPI §schemas/ContextFlags, MrMarketRsiFlag,
 * LongTermTrendFlag, MrMarketRsiSignal, LongTermTrendSignal.
 */
export type MrMarketRsiSignal =
  | 'OVERSOLD'
  | 'NEUTRAL'
  | 'OVERBOUGHT'
  | 'INDETERMINATE';

export type LongTermTrendSignal =
  | 'BELOW_TREND'
  | 'NEAR_TREND'
  | 'ABOVE_TREND'
  | 'INDETERMINATE';

export interface MrMarketRsiFlag {
  readonly flag: MrMarketRsiSignal;
  /** RSI latest record value (Wilder 14-day); `null` se serie vuota. */
  readonly rsiLatest: number | null;
  /** ISO-8601 — timestamp record FMP latest; `null` se INDETERMINATE. */
  readonly rsiTimestamp: string | null;
  /** Period length default 14 (FMP `periodLength` query param). */
  readonly periodLength: number;
  /** Timeframe default "1day". */
  readonly timeframe: string;
}

export interface LongTermTrendFlag {
  readonly flag: LongTermTrendSignal;
  /** SMA200 latest value; `null` se serie vuota o IPO < 200gg. */
  readonly sma200Latest: number | null;
  /** Prezzo corrente al momento valutazione; `null` se profile mancante. */
  readonly currentPrice: number | null;
  /**
   * Pct vs SMA200 calcolato BE come `(price - sma) / sma`. Es. `-0.20`
   * = prezzo 20% sotto la media; `+0.50` = 50% sopra. `null` se
   * INDETERMINATE. La UI moltiplica per 100 per la label utente.
   */
  readonly priceVsSmaPct: number | null;
  /** ISO-8601 — timestamp record FMP latest; `null` se INDETERMINATE. */
  readonly smaTimestamp: string | null;
  /** Period length default 200. */
  readonly periodLength: number;
  /** Timeframe default "1day". */
  readonly timeframe: string;
}

export interface ContextFlags {
  readonly mrMarketRsi: MrMarketRsiFlag | null;
  readonly longTermTrend: LongTermTrendFlag | null;
}

export interface RuleEngineResult {
  readonly ticker: string;
  /** ISO-8601 — momento valutazione Rule Engine. */
  readonly evaluatedAt: string;
  readonly signals: ReadonlyArray<RuleSignal>;
  /** Graham Number USD; `null` se EPS o BVPS non utilizzabili. */
  readonly grahamNumber: number | null;
  /** DCF intrinsic value USD; `null` se metodo non applicabile. */
  readonly dcfIntrinsicValue: number | null;
  /** Metodo DCF effettivamente usato (o `NOT_APPLICABLE`). */
  readonly dcfMethod: DcfMethod;
  /** Provenienza del metodo DCF (default-policy vs override utente). */
  readonly dcfMethodSource: DcfMethodSource;
  /** Semaforo Margin of Safety. */
  readonly mosSignal: MosSignal;
  /** Prezzo corrente al momento della valutazione; `null` se non disponibile. */
  readonly currentPriceAtEval: number | null;
  /** ISO-8601 — momento snapshot dati upstream (FMP). */
  readonly dataSnapshotAt: string;
  /** `true` se i dati sono oltre la soglia di freschezza (US-005/006). */
  readonly isStale?: boolean;
  /**
   * EP-013 — Advisory flag opzionali (RSI Mr. Market + SMA200 trend).
   * Backward-compat: `undefined`/`null` quando il BE non ha popolato
   * (es. response cache pre-EP-013 o evaluator failure-tolerant).
   */
  readonly contextFlags?: ContextFlags | null;
}

/**
 * GET /api/analysis/{ticker} — restituisce `RuleEngineResult`.
 *
 * Headers `X-Data-Snapshot-At` / `X-Data-Stale` letti dal wrapper
 * `apiGet` in `ApiResult.snapshotAt` / `ApiResult.isStale` (TSK-030).
 * Il body include già `dataSnapshotAt` + `isStale`, quindi tipicamente
 * NON serve leggere gli headers; manteniamo l'unwrap canonico a `T`.
 *
 * Per casi in cui il caller voglia accedere agli headers (es. cache
 * conditional), esponiamo anche `getAnalysisRaw` che ritorna l'intero
 * `ApiResult<RuleEngineResult>`.
 *
 * Errori network/4xx/5xx rilanciati al caller (store/componente).
 *  - 404: ticker inesistente.
 *  - 503: dati insufficienti per analisi e FMP non raggiungibile.
 */
export async function getAnalysis(ticker: string): Promise<RuleEngineResult> {
  const normalized = ticker.trim().toUpperCase();
  const result: ApiResult<RuleEngineResult> = await apiGet<RuleEngineResult>(
    `/api/analysis/${encodeURIComponent(normalized)}`,
  );
  return result.data;
}

/**
 * Variante che espone l'intero `ApiResult` (headers inclusi).
 * Usata quando il caller vuole correlare `X-Data-Stale` (header) con
 * `result.isStale` (body) — per ora non usata, esposta per estensione.
 */
export async function getAnalysisRaw(
  ticker: string,
): Promise<ApiResult<RuleEngineResult>> {
  const normalized = ticker.trim().toUpperCase();
  return apiGet<RuleEngineResult>(
    `/api/analysis/${encodeURIComponent(normalized)}`,
  );
}
