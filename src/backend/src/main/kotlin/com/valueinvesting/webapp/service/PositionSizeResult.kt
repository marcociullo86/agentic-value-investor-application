package com.valueinvesting.webapp.service

data class PositionSizeResult(
    val recommendedPct: Double,
    val range: Pair<Double, Double>,
    val basisVerdict: VerdictClass,
    val marginOfSafetyPct: Double,
    val disclaimer: String = DISCLAIMER,
) {
    companion object {
        const val DISCLAIMER = "Indicazione tecnica, non consiglio finanziario"
    }
}
