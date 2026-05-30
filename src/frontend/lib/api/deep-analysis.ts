import { apiGet, apiPost, type ApiResult } from '@/lib/api/client';
import type { RuleSignal } from '@/lib/api/analysis';

/**
 * Deep Analysis domain API (TSK-122 — US-046).
 *
 * Types and fetch function for GET /api/analysis/{ticker}/deep.
 * Verbatim from OpenAPI §components/schemas/DeepAnalysisResponse
 * (design_&_architecture/api/openapi.yaml).
 *
 * Deterministic fields are always populated. LLM-dependent fields
 * (mungerReport, newsSentiment) are null when invoke_llm=false.
 */

export type VerdictClass =
  | 'APPROVATO_PANIC_BUY'
  | 'APPROVATO'
  | 'WATCHLIST'
  | 'BOCCIATO_NUMERICO'
  | 'BOCCIATO_QUALITATIVO'
  | 'BOCCIATO_VALUE_TRAP';

export type LivelloRischio =
  | 'RISCHIO_BASSO'
  | 'RISCHIO_MODERATO'
  | 'RISCHIO_ALTO'
  | 'RISCHIO_ESTREMO';

export type SentimentClass =
  | 'TEMPORARY_PANIC'
  | 'STRUCTURAL_DAMAGE'
  | 'NEUTRAL';

export type LlmStatus = 'INVOKED' | 'NOT_INVOKED' | 'CACHE_HIT';

export interface RoeBlock {
  readonly fiveYearAvg: number | null;
  readonly tenYearAvg: number | null;
  readonly fiveYearDataPoints: number;
  readonly tenYearDataPoints: number;
}

export interface PriceActionBlock {
  readonly priceNow: number | null;
  readonly max52w: number | null;
  readonly min52w: number | null;
  readonly drawdownPct: number | null;
  readonly trend3mPct: number | null;
  readonly ma50: number | null;
  readonly ma200: number | null;
  readonly panicDiscount: boolean;
  readonly deteriorationWarning: boolean;
  readonly seriesDays: number;
}

export interface VerdictBlock {
  readonly verdettoClasse: VerdictClass;
  readonly positionSizePct: number;
  readonly partialBasis: boolean;
  readonly motivazioneAggregata: string;
  readonly ruleCountGreen: number;
  readonly ruleCountYellow: number;
  readonly ruleCountRed: number;
  readonly livelloRischio: LivelloRischio;
  readonly newsSentimentDominante: SentimentClass;
}

export interface PositionSizeBlock {
  readonly recommendedPct: number;
  readonly rangeLow: number;
  readonly rangeHigh: number;
  readonly basisVerdict: VerdictClass;
  readonly marginOfSafetyPct: number;
  readonly disclaimer: string;
}

export interface InversionItem {
  readonly testo: string;
  readonly chunkIndex: number;
}

export interface MungerReportBlock {
  readonly livelloRischio: LivelloRischio;
  /** Sintesi narrativa LLM del livello di rischio (US-089); null per report legacy in cache. */
  readonly sintesi: string | null;
  readonly rischiPrincipali: readonly InversionItem[];
  readonly puntiDiForza: readonly InversionItem[];
  readonly segnaliRecenti10Q: readonly InversionItem[];
  readonly filingComboHash: string;
  readonly llmCallsCount: number;
}

/** Singola notizia analizzata nel Sentiment News (US-091). */
export interface NewsItem {
  readonly headline: string | null;
  readonly textExcerpt: string | null;
  readonly sentimentClass: SentimentClass;
  readonly motivazione: string | null;
  readonly url: string | null;
}

export interface NewsSentimentBlock {
  readonly total: number;
  readonly panicCount: number;
  readonly structuralCount: number;
  readonly neutralCount: number;
  readonly dominantClass: SentimentClass;
  /** Notizie analizzate (set curato): titolo + testo + classe + motivazione. */
  readonly items: readonly NewsItem[];
}

export interface FilingRef {
  readonly accessionNumber: string;
  readonly formType: string;
  readonly filingDate: string;
}

export interface DeepAnalysisResponse {
  readonly ticker: string;
  readonly generatedAt: string;
  readonly roe: RoeBlock;
  readonly priceAction: PriceActionBlock;
  readonly ruleEngineResults: readonly RuleSignal[];
  readonly verdict: VerdictBlock;
  readonly positionSize: PositionSizeBlock | null;
  readonly filingsUsed: readonly FilingRef[];
  readonly mungerReport: MungerReportBlock | null;
  readonly newsSentiment: NewsSentimentBlock | null;
  readonly llmStatus: LlmStatus;
  readonly llmCalls: number;
  readonly totalDurationMs: number;
  readonly llmCostEstimateUsd: number | null;
}

/**
 * GET /api/analysis/{ticker}/deep
 *
 * @param invokeLlm - when true, triggers Munger inversion + news sentiment
 *   LLM pipeline (may take 30-90s). Default false → deterministic-only in <2s.
 *
 * NOTE: This synchronous endpoint is preserved for backwards compatibility but
 * the page now uses the asynchronous run/latest flow below.
 */
export async function getDeepAnalysis(
  ticker: string,
  invokeLlm = false,
): Promise<DeepAnalysisResponse> {
  const normalized = ticker.trim().toUpperCase();
  const params = invokeLlm ? '?invoke_llm=true' : '';
  const result: ApiResult<DeepAnalysisResponse> =
    await apiGet<DeepAnalysisResponse>(
      `/api/analysis/${encodeURIComponent(normalized)}/deep${params}`,
    );
  return result.data;
}

/* ------------------------------------------------------------------ */
/*  Async run/latest API (TSK-async-deep)                              */
/* ------------------------------------------------------------------ */

/** Status of a single deep analysis run on the backend. */
export type DeepAnalysisRunStatus = 'RUNNING' | 'SUCCESS' | 'FAILED';

/**
 * Status surfaced by GET /latest. `NONE` is used when no run has ever been
 * executed for this ticker (empty state for the frontend).
 */
export type LatestStatus = 'RUNNING' | 'SUCCESS' | 'FAILED' | 'NONE';

/** Payload returned by POST /api/analysis/{ticker}/deep/runs (HTTP 202). */
export interface DeepAnalysisRunDto {
  readonly runId: string;
  readonly ticker: string;
  readonly status: DeepAnalysisRunStatus;
  readonly invokeLlm: boolean;
}

/** Structured error payload emitted by the backend when a run fails. */
export interface LatestDeepAnalysisError {
  readonly reason: string;
  readonly message: string | null;
}

/** Payload returned by GET /api/analysis/{ticker}/deep/latest (HTTP 200). */
export interface LatestDeepAnalysis {
  readonly ticker: string;
  readonly status: LatestStatus;
  readonly runId: string | null;
  readonly invokeLlm: boolean;
  readonly requestedAt: string | null;
  readonly completedAt: string | null;
  /** Valorizzato solo quando `status === 'SUCCESS'`. */
  readonly result: DeepAnalysisResponse | null;
  /** Valorizzato solo quando `status === 'FAILED'`. */
  readonly error: LatestDeepAnalysisError | null;
}

/**
 * POST /api/analysis/{ticker}/deep/runs?invoke_llm=true|false
 *
 * Avvia un nuovo run asincrono. La response 202 conferma l'accettazione del
 * job; lo stato successivo deve essere recuperato via {@link getLatestDeepAnalysis}.
 * Il backend deduplica i POST concorrenti: due click ravvicinati restituiscono
 * lo stesso `runId` con status `RUNNING`.
 */
export async function startDeepAnalysisRun(
  ticker: string,
  invokeLlm: boolean,
): Promise<DeepAnalysisRunDto> {
  const normalized = ticker.trim().toUpperCase();
  const qs = `?invoke_llm=${invokeLlm ? 'true' : 'false'}`;
  const result: ApiResult<DeepAnalysisRunDto> = await apiPost<DeepAnalysisRunDto>(
    `/api/analysis/${encodeURIComponent(normalized)}/deep/runs${qs}`,
  );
  return result.data;
}

/**
 * GET /api/analysis/{ticker}/deep/latest
 *
 * Restituisce l'ultimo run noto per il ticker (qualsiasi status). Quando non
 * esiste alcuna esecuzione passata, il backend restituisce `status === 'NONE'`
 * con campi `runId/result/error/requestedAt/completedAt` a null.
 */
export async function getLatestDeepAnalysis(
  ticker: string,
): Promise<LatestDeepAnalysis> {
  const normalized = ticker.trim().toUpperCase();
  const result: ApiResult<LatestDeepAnalysis> = await apiGet<LatestDeepAnalysis>(
    `/api/analysis/${encodeURIComponent(normalized)}/deep/latest`,
  );
  return result.data;
}

/* ------------------------------------------------------------------ */
/*  Filings ingest run/latest API                                      */
/* ------------------------------------------------------------------ */

/** Status di un singolo ingest run. */
export type IngestStatus = 'RUNNING' | 'SUCCESS' | 'FAILED' | 'NONE';

/** Riassunto deterministico restituito dal backend solo su SUCCESS. */
export interface IngestSummary {
  readonly filingsTotal: number;
  readonly chunksIndexed: number;
  readonly chunksSkipped: number;
  readonly indexedAt: string | null;
}

/** Errore strutturato emesso dall'ingest job in caso di FAILED. */
export interface IngestError {
  readonly reason: string;
  readonly message: string | null;
}

/** Payload restituito da POST /api/analysis/{ticker}/deep/ingest (HTTP 202). */
export interface IngestRunDto {
  readonly runId: string;
  readonly ticker: string;
  readonly status: 'RUNNING' | 'SUCCESS' | 'FAILED';
  readonly invokeLlm: false;
  readonly kind: 'INGEST';
}

/** Payload restituito da GET /api/analysis/{ticker}/deep/ingest/latest. */
export interface LatestIngest {
  readonly ticker: string;
  readonly status: IngestStatus;
  readonly runId: string | null;
  readonly requestedAt: string | null;
  readonly completedAt: string | null;
  /** Valorizzato solo quando `status === 'SUCCESS'`. */
  readonly summary: IngestSummary | null;
  /** Valorizzato solo quando `status === 'FAILED'`. */
  readonly error: IngestError | null;
}

/**
 * POST /api/analysis/{ticker}/deep/ingest
 *
 * Avvia un nuovo job di indicizzazione dei filing SEC. La response 202 conferma
 * l'accettazione del job; lo stato successivo va recuperato via
 * {@link getLatestIngest}. Il backend deduplica POST concorrenti.
 */
export async function startIngest(ticker: string): Promise<IngestRunDto> {
  const normalized = ticker.trim().toUpperCase();
  const result: ApiResult<IngestRunDto> = await apiPost<IngestRunDto>(
    `/api/analysis/${encodeURIComponent(normalized)}/deep/ingest`,
  );
  return result.data;
}

/**
 * GET /api/analysis/{ticker}/deep/ingest/latest
 *
 * Restituisce l'ultimo ingest noto per il ticker (qualsiasi status). Quando non
 * esiste alcun job, il backend restituisce `status === 'NONE'` con
 * `runId/summary/error/requestedAt/completedAt` a null.
 */
export async function getLatestIngest(ticker: string): Promise<LatestIngest> {
  const normalized = ticker.trim().toUpperCase();
  const result: ApiResult<LatestIngest> = await apiGet<LatestIngest>(
    `/api/analysis/${encodeURIComponent(normalized)}/deep/ingest/latest`,
  );
  return result.data;
}
