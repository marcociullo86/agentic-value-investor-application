package com.valueinvesting.webapp.api.model

import com.valueinvesting.webapp.technicalanalysis.LevelConfidence
import com.valueinvesting.webapp.technicalanalysis.SupportType
import com.valueinvesting.webapp.technicalanalysis.TrendClassification
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

// Payload del nuovo endpoint GET /api/analysis/{ticker}/technical (EP-024, US-098).
//
// Composto da 6 blocchi tipati indicatori (TSK-326) + 3 blocchi advisor
// (TSK-328 entryTimingAdvisor, TSK-330 stopSuggestion + positionSizing +
// rewardRiskRatio). I 3 blocchi advisor sono opzionali a livello DTO ma
// SEMPRE popolati in produzione dal TechnicalAnalysisService: opzionali
// solo per backward-compat additiva (ADR-030 §4).
//
// Stile coerente con RuleEngineResultResponse (EP-013/EP-021): @Schema su
// data class, kdoc per ogni blocco, enum tipati invece di String.
//
// [^src: management/kanban/EP-024-riepilogo-e-technical-analysis-tab/US-098-pipeline-ta-payload-be/US-098.md §"Indicatori in scope"]
// [^src: wiki/syntheses/ta-entry-timing-stock-detail.md]
// [^src: wiki/syntheses/ta-stop-placement-position-sizing.md]
@Schema(name = "TechnicalAnalysisResponse", description = "Payload Technical Analysis tab — 6 blocchi indicatori + 3 advisor (timing/stop/sizing). Layer ADVISORY di timing: NON sostituisce il verdetto VI.")
data class TechnicalAnalysisResponse(
    val ticker: String,
    val evaluatedAt: Instant,
    val trend: TrendBlock,
    val momentum: MomentumBlock,
    val volatility: VolatilityBlock,
    val volume: VolumeBlock,
    val levels: LevelsBlock,
    val priceContext: PriceContextBlock,
    /** Verdetto Triple-Screen di timing (US-099, TSK-328). */
    val entryTimingAdvisor: EntryTimingAdvisor? = null,
    /** Suggested stop ancorato a struttura (US-100, TSK-330). */
    val stopSuggestion: StopSuggestion? = null,
    /** 2%/6% Rule sizing (US-100, TSK-330). Mai persistito server-side. */
    val positionSizing: PositionSizing? = null,
    /** Reward/Risk vs DCF intrinsic value (US-100, TSK-330). */
    val rewardRiskRatio: RewardRiskRatio? = null,
)

// ----------------------------------------------------------------------------
// Blocchi indicatori (TSK-326)
// ----------------------------------------------------------------------------

@Schema(name = "TaTrendBlock", description = "Trend primario daily — SMA50/SMA200 + classificazione deterministica.")
data class TrendBlock(
    val sma50: Double?,
    val sma200: Double?,
    val classification: TrendClassification,
    /** Slope SMA200 stimato via regressione lineare ultime 20 sedute (frazione di SMA / giorno). */
    val sma200SlopePerDay: Double?,
    /** True quando lo storico EOD e' inferiore alla finestra canonica (200 sedute). */
    val confidenceReduced: Boolean = false,
)

@Schema(name = "TaMomentumBlock", description = "Indicatori di momentum — RSI 14d, MACD daily e weekly (Triple Screen).")
data class MomentumBlock(
    val rsi14: Double?,
    /** MACD daily — `value` ultimo record FMP `/technical-indicators/macd?timeframe=1day`. */
    val macdDaily: Double?,
    /** MACD weekly — Screen 1 Elder Triple Screen. */
    val macdWeekly: Double?,
    val confidenceReduced: Boolean = false,
)

@Schema(name = "TaVolatilityBlock", description = "Volatilita' — ATR(14) per stop-placement (Murphy §Page 82, Elder).")
data class VolatilityBlock(
    val atr14: Double?,
    val confidenceReduced: Boolean = false,
)

@Schema(name = "TaVolumeBlock", description = "Volume e OBV (On-Balance Volume) — conferma volume per breakout/breakdown.")
data class VolumeBlock(
    /** OBV ultimo valore (cumulativo). */
    val obv: Double?,
    /** Volume medio sulle ultime 20 sedute. */
    val avgVolume20d: Double?,
    val confidenceReduced: Boolean = false,
)

@Schema(name = "TaLevelsBlock", description = "Livelli structural support/resistance — max 3 ciascuno, ordinati per distanza dal prezzo corrente.")
data class LevelsBlock(
    val support: List<PriceLevelDto>,
    val resistance: List<PriceLevelDto>,
    val confidenceReduced: Boolean = false,
)

@Schema(name = "TaPriceLevel", description = "Singolo livello strutturale — prezzo + tipo (swing o retracement) + confidence.")
data class PriceLevelDto(
    val price: Double,
    val type: SupportType,
    val confidence: LevelConfidence,
)

@Schema(name = "TaPriceContextBlock", description = "Contesto di prezzo — 52w range + drawdown da picco.")
data class PriceContextBlock(
    val currentPrice: Double?,
    val high52w: Double?,
    val low52w: Double?,
    /** Magnitudo positiva 0..1 (0.32 = -32% dal picco 52w). */
    val drawdownFrom52wHigh: Double?,
    val confidenceReduced: Boolean = false,
)
