package com.valueinvesting.webapp.api.model

import com.valueinvesting.webapp.ruleengine.RuleSignal
import com.valueinvesting.webapp.service.LivelloRischio
import com.valueinvesting.webapp.service.SentimentClass
import com.valueinvesting.webapp.service.VerdictClass
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

// Full response DTO for GET /api/analysis/{ticker}/deep (US-045).
//
// Deterministic fields (ROE, price action, rule engine, verdict) are always
// populated. LLM-dependent fields (mungerReport, newsSentiment) are nullable
// and controlled by the invoke_llm query parameter and llmStatus.
//
// [^src: design_&_architecture/decisions/ADR-020-roe-lookback-policy-deep-analysis.md §Specifica payload Deep Analysis]
// [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-045-endpoint-deep-analysis/US-045.md §Business Rules]
@Schema(name = "DeepAnalysisResponse")
data class DeepAnalysisResponse(
    val ticker: String,
    val generatedAt: Instant,
    val roe: RoeBlock,
    val priceAction: PriceActionBlock,
    val ruleEngineResults: List<RuleSignal>,
    val verdict: VerdictBlock,
    val positionSize: PositionSizeBlock?,
    val filingsUsed: List<FilingRef>,
    @Schema(description = "Munger inversion LLM report, null when invoke_llm=false", nullable = true)
    val mungerReport: MungerReportBlock?,
    @Schema(description = "News sentiment classification, null when invoke_llm=false", nullable = true)
    val newsSentiment: NewsSentimentBlock?,
    @Schema(description = "INVOKED | NOT_INVOKED | CACHE_HIT")
    val llmStatus: String,
    val llmCalls: Int,
    val totalDurationMs: Long,
)

@Schema(name = "RoeBlock", description = "Dual-lookback ROE metrics (ADR-020)")
data class RoeBlock(
    @Schema(description = "5-year average ROE (fraction), null if insufficient data", nullable = true)
    val fiveYearAvg: Double?,
    @Schema(description = "10-year average ROE (fraction), null if insufficient data", nullable = true)
    val tenYearAvg: Double?,
    @Schema(description = "Number of fiscal years actually used for fiveYearAvg (0..5)")
    val fiveYearDataPoints: Int,
    @Schema(description = "Number of fiscal years actually used for tenYearAvg (0..10)")
    val tenYearDataPoints: Int,
)

@Schema(name = "PriceActionBlock")
data class PriceActionBlock(
    val priceNow: Double?,
    val max52w: Double?,
    val min52w: Double?,
    val drawdownPct: Double?,
    val trend3mPct: Double?,
    val ma50: Double?,
    val ma200: Double?,
    val panicDiscount: Boolean,
    val deteriorationWarning: Boolean,
    val seriesDays: Int,
)

@Schema(name = "VerdictBlock")
data class VerdictBlock(
    val verdettoClasse: VerdictClass,
    val positionSizePct: Double,
    val partialBasis: Boolean,
    val motivazioneAggregata: String,
    val ruleCountGreen: Int,
    val ruleCountYellow: Int,
    val ruleCountRed: Int,
    val livelloRischio: LivelloRischio,
    val newsSentimentDominante: SentimentClass,
)

@Schema(name = "PositionSizeBlock")
data class PositionSizeBlock(
    val recommendedPct: Double,
    val rangeLow: Double,
    val rangeHigh: Double,
    val basisVerdict: VerdictClass,
    val marginOfSafetyPct: Double,
    val disclaimer: String,
)

@Schema(name = "MungerReportBlock")
data class MungerReportBlock(
    val livelloRischio: LivelloRischio,
    val rischiPrincipali: List<InversionItem>,
    val puntiDiForza: List<InversionItem>,
    val segnaliRecenti10Q: List<InversionItem>,
    val filingComboHash: String,
    val llmCallsCount: Int,
)

@Schema(name = "InversionItem")
data class InversionItem(
    val testo: String,
    val chunkIndex: Int,
)

@Schema(name = "NewsSentimentBlock")
data class NewsSentimentBlock(
    val total: Int,
    val panicCount: Int,
    val structuralCount: Int,
    val neutralCount: Int,
    val dominantClass: SentimentClass,
)

@Schema(name = "FilingRef")
data class FilingRef(
    val accessionNumber: String,
    val formType: String,
    val filingDate: String,
)
