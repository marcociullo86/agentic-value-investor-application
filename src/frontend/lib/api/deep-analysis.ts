import { apiGet, type ApiResult } from '@/lib/api/client';
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
  readonly rischiPrincipali: readonly InversionItem[];
  readonly puntiDiForza: readonly InversionItem[];
  readonly segnaliRecenti10Q: readonly InversionItem[];
  readonly filingComboHash: string;
  readonly llmCallsCount: number;
}

export interface NewsSentimentBlock {
  readonly total: number;
  readonly panicCount: number;
  readonly structuralCount: number;
  readonly neutralCount: number;
  readonly dominantClass: SentimentClass;
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
