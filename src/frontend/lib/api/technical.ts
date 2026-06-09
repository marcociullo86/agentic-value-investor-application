import { apiGet, type ApiResult } from '@/lib/api/client';

/**
 * Technical Analysis domain API (TSK-333 — US-101, EP-024 Fase 1).
 *
 * Wrapper sopra `apiClient` (lib/api/client.ts), allineato pattern
 * `historical.ts` / `deep-analysis.ts`.
 *
 * Endpoint contract: design_&_architecture/api/openapi.yaml
 *   §/api/analysis/{ticker}/technical — schemas `TechnicalAnalysisResponse`
 *   (6 blocchi indicatori deterministici US-098 + 3 advisor opzionali
 *    US-099/US-100: entryTimingAdvisor, stopSuggestion, positionSizing,
 *    rewardRiskRatio).
 *
 * NOTA tipi TS: il generatore `openapi-typescript` (script
 * `npm run generate:api`) emette `lib/api/generated/schema.ts` (gitignored).
 * Definiamo qui i tipi minimal del subset usato in US-101, structurally
 * compatibili con le definizioni OpenAPI — drop-in replace quando il
 * generato sarà rigenerato in CI/local (la CI rigenera in stage fe-build).
 *
 * Layer ADVISORY di timing: questo endpoint NON sostituisce il verdetto VI
 * del Rule Engine (`/api/analysis/{ticker}` resta invariato). Il gate VI
 * primario è applicato dal Riepilogo US-103 — non qui.
 *
 * Equity per il position sizing: parametro query `equity` (default 50000).
 * Mai persistito server-side (US-100 §"Separazione di responsabilità").
 */

/* ------------------------------------------------------------------ */
/*  Enum tipati (verbatim OpenAPI §components/schemas)                  */
/* ------------------------------------------------------------------ */

export type TrendClassification =
  | 'UPTREND'
  | 'SIDEWAYS'
  | 'DOWNTREND'
  | 'INDETERMINATE';

export type SupportType =
  | 'SWING_LOW'
  | 'SWING_HIGH'
  | 'RETRACEMENT_33'
  | 'RETRACEMENT_50'
  | 'RETRACEMENT_66';

export type LevelConfidence = 'LOW' | 'MEDIUM' | 'HIGH';

export type EntryTimingVerdict =
  | 'ENTRY_FAVORABLE'
  | 'ENTRY_NEUTRAL'
  | 'ENTRY_UNFAVORABLE'
  | 'WAIT'
  | 'INDETERMINATE';

export type ReentryConditionCode =
  | 'RSI_BELOW_50'
  | 'PRICE_ABOVE_SMA200_WITH_VOLUME'
  | 'PULLBACK_TO_SUPPORT_50PCT';

export type StopType =
  | 'SUPPORT_BASED'
  | 'SMA200_BASED'
  | 'ATR_BASED'
  | 'NOT_CALCULABLE';

export type PositionSizingWarning = 'POSITION_EXCEEDS_EQUITY';

export type RewardRiskLabel =
  | 'EXCELLENT'
  | 'ACCEPTABLE'
  | 'MARGINAL'
  | 'UNFAVORABLE'
  | 'NOT_APPLICABLE';

/* ------------------------------------------------------------------ */
/*  Blocchi indicatori (US-098)                                         */
/* ------------------------------------------------------------------ */

export interface TaPriceLevel {
  readonly price: number;
  readonly type: SupportType;
  readonly confidence: LevelConfidence;
}

export interface TaTrendBlock {
  readonly sma50: number | null;
  readonly sma200: number | null;
  readonly classification: TrendClassification;
  /** Slope regressione lineare 20 sedute (frazione SMA / giorno). */
  readonly sma200SlopePerDay: number | null;
  /** True quando lo storico EOD è < 200 sedute. */
  readonly confidenceReduced: boolean;
}

export interface TaMomentumBlock {
  /** RSI 14 giorni, 0..100. */
  readonly rsi14: number | null;
  /** MACD daily (12,26,9). */
  readonly macdDaily: number | null;
  /** MACD weekly — Screen 1 Elder. */
  readonly macdWeekly: number | null;
  readonly confidenceReduced: boolean;
}

export interface TaVolatilityBlock {
  readonly atr14: number | null;
  readonly confidenceReduced: boolean;
}

export interface TaVolumeBlock {
  /** On-Balance Volume cumulativo latest. */
  readonly obv: number | null;
  /** Volume medio ultime 20 sedute. */
  readonly avgVolume20d: number | null;
  readonly confidenceReduced: boolean;
}

export interface TaLevelsBlock {
  readonly support: ReadonlyArray<TaPriceLevel>;
  readonly resistance: ReadonlyArray<TaPriceLevel>;
  readonly confidenceReduced: boolean;
}

export interface TaPriceContextBlock {
  readonly currentPrice: number | null;
  readonly high52w: number | null;
  readonly low52w: number | null;
  /** Magnitudo 0..1 (0.32 = -32% dal picco). */
  readonly drawdownFrom52wHigh: number | null;
  readonly confidenceReduced: boolean;
}

/* ------------------------------------------------------------------ */
/*  Entry-timing advisor (US-099)                                       */
/* ------------------------------------------------------------------ */

export interface ReentryCondition {
  readonly code: ReentryConditionCode;
  readonly description: string;
}

export interface EntryTimingRationale {
  /** Nota Screen 1 — trend di lungo (Elder). */
  readonly screen1: string;
  /** Nota Screen 2 — oscillatore: RSI + MACD daily. */
  readonly screen2: string;
  /** Nota Screen 3 — livello d'entry: support/resistance. */
  readonly screen3: string;
  /** Pagine wiki che giustificano il verdetto. */
  readonly wikiCitations: ReadonlyArray<string>;
}

export interface EntryTimingAdvisor {
  readonly verdict: EntryTimingVerdict;
  /** Presente SOLO quando verdict === 'WAIT'. */
  readonly reentryCondition: ReentryCondition | null;
  readonly rationale: EntryTimingRationale;
  /** Disclaimer machine-readable. Costante. */
  readonly viGate: string;
}

/* ------------------------------------------------------------------ */
/*  Stop placement + position sizing (US-100)                           */
/* ------------------------------------------------------------------ */

export interface StopSuggestion {
  readonly type: StopType;
  /** USD. Null se NOT_CALCULABLE. */
  readonly stopPrice: number | null;
  /** currentPrice - stopPrice. */
  readonly stopDistance: number | null;
  /** Distanza stop come % del prezzo (0.05 = 5%). */
  readonly stopDistancePct: number | null;
  /** Riferimento human-readable (es. "support@$47.5 (SWING_LOW)"). */
  readonly anchorReference: string | null;
  readonly rationale: string;
}

export interface TwoPercentRule {
  readonly equity: number;
  /** equity × 0.02. */
  readonly maxRiskAllowed: number;
  readonly stopDistance: number | null;
  /** floor(maxRiskAllowed / stopDistance); 0 se stopDistance non disponibile. */
  readonly sharesRecommended: number;
  readonly positionValueRecommended: number;
  readonly positionPctEquity: number;
  readonly warning: PositionSizingWarning | null;
}

export interface SixPercentRule {
  /** equity × 0.06. */
  readonly maxAggregateRiskPerMonth: number;
  readonly disclaimer: string;
}

export interface PositionSizing {
  readonly twoPercentRule: TwoPercentRule;
  readonly sixPercentRule: SixPercentRule;
}

export interface RewardRiskRatio {
  readonly upside: number | null;
  readonly downside: number | null;
  /** upside / downside. */
  readonly value: number | null;
  readonly label: RewardRiskLabel;
  readonly rationale: string;
}

/* ------------------------------------------------------------------ */
/*  Root payload                                                        */
/* ------------------------------------------------------------------ */

export interface TechnicalAnalysisResponse {
  readonly ticker: string;
  /** ISO-8601 — istante valutazione lato BE. */
  readonly evaluatedAt: string;
  readonly trend: TaTrendBlock;
  readonly momentum: TaMomentumBlock;
  readonly volatility: TaVolatilityBlock;
  readonly volume: TaVolumeBlock;
  readonly levels: TaLevelsBlock;
  readonly priceContext: TaPriceContextBlock;
  /** Optional in schema, sempre popolato in produzione. */
  readonly entryTimingAdvisor: EntryTimingAdvisor | null;
  readonly stopSuggestion: StopSuggestion | null;
  readonly positionSizing: PositionSizing | null;
  readonly rewardRiskRatio: RewardRiskRatio | null;
}

/* ------------------------------------------------------------------ */
/*  Fetcher                                                             */
/* ------------------------------------------------------------------ */

export interface GetTechnicalAnalysisOptions {
  /**
   * Capitale di riferimento per il calcolo 2%/6% Rule (US-100). Default
   * 50000 USD lato BE. NON viene mai persistito server-side: arriva via
   * query string e ritorna riflesso nel `positionSizing.twoPercentRule.equity`.
   */
  readonly equity?: number;
}

/**
 * GET /api/analysis/{ticker}/technical?equity={equity}
 *
 * Errori HTTP rilanciati al caller (hook/componente):
 *  - 404 → ticker inesistente
 *  - 503 → FMP indisponibile (ProblemDetail body)
 */
export async function getTechnicalAnalysis(
  ticker: string,
  options: GetTechnicalAnalysisOptions = {},
): Promise<TechnicalAnalysisResponse> {
  const normalized = ticker.trim().toUpperCase();
  const qs =
    options.equity !== undefined && Number.isFinite(options.equity)
      ? `?equity=${encodeURIComponent(options.equity.toString())}`
      : '';
  const result: ApiResult<TechnicalAnalysisResponse> =
    await apiGet<TechnicalAnalysisResponse>(
      `/api/analysis/${encodeURIComponent(normalized)}/technical${qs}`,
    );
  return result.data;
}
