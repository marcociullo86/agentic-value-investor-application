package com.valueinvesting.webapp.api.model

import io.swagger.v3.oas.annotations.media.Schema

// DTO StopPlacement + PositionSizing — EP-024 US-100, TSK-330.
//
// Pure-function output, NO LLM, NO persistenza dell'equity (privacy/lente di
// scope: il BE non e' un portfolio tracker; calcola solo il singolo trade).
//
// [^src: management/kanban/EP-024-riepilogo-e-technical-analysis-tab/US-100-stop-placement-position-sizing-be/US-100.md]
// [^src: wiki/syntheses/ta-stop-placement-position-sizing.md] (Murphy §Page 82, Elder §50/§51/§54)
// [^src: wiki/concepts/elder-risk-management-2pct-6pct.md]

@Schema(
    name = "StopType",
    description = """
Tipo di ancoraggio dello stop suggerito (priorita' decrescente, Murphy + Elder):
- SUPPORT_BASED: stop sotto il support strutturale piu' vicino (buffer 0.5%, Murphy §Page 82).
- SMA200_BASED: stop sotto la SMA200 (buffer 0.5%, applicabile solo se price > SMA200).
- ATR_BASED: stop = currentPrice − 2×ATR14 (fallback Elder).
- NOT_CALCULABLE: nessuno dei 3 candidati e' applicabile (dati mancanti).
""",
)
enum class StopType {
    SUPPORT_BASED,
    SMA200_BASED,
    ATR_BASED,
    NOT_CALCULABLE,
}

@Schema(name = "StopSuggestion", description = "Stop ancorato a struttura (Murphy §Page 82, Elder §50). Layer ADVISORY.")
data class StopSuggestion(
    val type: StopType,
    /** USD. Null quando type = NOT_CALCULABLE. */
    val stopPrice: Double?,
    /** currentPrice - stopPrice. Null quando NOT_CALCULABLE. */
    val stopDistance: Double?,
    /** Distanza dello stop come % del prezzo corrente. */
    val stopDistancePct: Double?,
    /** Riferimento human-readable all'ancora ("support@$47.5 (SWING_LOW)" / "SMA200@$45.0" / "ATR14=$0.45"). */
    val anchorReference: String?,
    val rationale: String,
)

@Schema(
    name = "TwoPercentRule",
    description = "Position sizing 2% Rule Elder §50. `equity` non e' persistito server-side (US-100 §AC).",
)
data class TwoPercentRule(
    val equity: Double,
    /** equity × 0.02. */
    val maxRiskAllowed: Double,
    /** Da StopSuggestion. */
    val stopDistance: Double?,
    /** floor(maxRiskAllowed / stopDistance), 0 se stopDistance ≤ 0. */
    val sharesRecommended: Long,
    /** sharesRecommended × currentPrice. */
    val positionValueRecommended: Double,
    /** positionValueRecommended / equity. */
    val positionPctEquity: Double,
    /**
     * Warning machine-readable. Null = OK. POSITION_EXCEEDS_EQUITY = stop molto
     * stretto, la size teorica supera l'equity; abbiamo cappato shares a
     * floor(equity / currentPrice) e l'utente deve valutare se la posizione
     * "intera equity" e' coerente con il suo portafoglio.
     */
    val warning: PositionSizingWarning?,
)

enum class PositionSizingWarning {
    POSITION_EXCEEDS_EQUITY,
}

@Schema(
    name = "SixPercentRule",
    description = "Heat di portafoglio Elder §51 (Iron Triangle). Il BE NON traccia le altre posizioni.",
)
data class SixPercentRule(
    /** equity × 0.06. */
    val maxAggregateRiskPerMonth: Double,
    val disclaimer: String,
)

@Schema(name = "PositionSizing", description = "Sizing 2%/6% Rule (US-100, TSK-330).")
data class PositionSizing(
    val twoPercentRule: TwoPercentRule,
    val sixPercentRule: SixPercentRule,
)

@Schema(
    name = "RewardRiskLabel",
    description = """
Etichetta qualitativa del rapporto reward/risk vs DCF intrinsic value:
- EXCELLENT: ≥ 3:1 (Elder §54 raccomanda minimo 2:1).
- ACCEPTABLE: ≥ 2:1.
- MARGINAL:  ≥ 1:1 — valutare se aspettare prezzo migliore.
- UNFAVORABLE: < 1 — stop piu' lontano dell'upside fondamentale residuo.
- NOT_APPLICABLE: DCF assente, ≤ currentPrice, o stopDistance non calcolabile.
""",
)
enum class RewardRiskLabel {
    EXCELLENT,
    ACCEPTABLE,
    MARGINAL,
    UNFAVORABLE,
    NOT_APPLICABLE,
}

@Schema(name = "RewardRiskRatio", description = "Rapporto reward/risk vs DCF intrinsic value (US-100, TSK-330).")
data class RewardRiskRatio(
    /** dcfIntrinsicValue - currentPrice. Null quando NOT_APPLICABLE. */
    val upside: Double?,
    /** stopDistance. Null quando NOT_APPLICABLE. */
    val downside: Double?,
    /** upside / downside. Null quando NOT_APPLICABLE. */
    val value: Double?,
    val label: RewardRiskLabel,
    val rationale: String,
)
