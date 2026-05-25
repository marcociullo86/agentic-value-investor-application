package com.valueinvesting.webapp.service

import org.springframework.stereotype.Service

/**
 * Pure-logic position-size calculator ported from agent.py `_calcola_position_size`.
 *
 * MoS-proportional sizing: the recommended allocation scales linearly with the
 * margin-of-safety percentage within a clamped range specific to each verdict class.
 *
 * Formula: `positionPct = rangeLow + (mosPct / 100.0) * scaleFactor`, clamped to [rangeLow, rangeHigh].
 * WATCHLIST and BOCCIATO_* always yield 0%.
 *
 * [^src: wiki/concepts/margin-of-safety.md §Margin of Safety operativa]
 * [^src: wiki/syntheses/graham-investing-philosophy.md §Genealogia dei criteri difensivi]
 */
@Service
class PositionSizeCalculator {

    fun calculate(verdictClass: VerdictClass, marginOfSafetyPct: Double): PositionSizeResult {
        val spec = SIZING_SPECS[verdictClass]
            ?: return zeroResult(verdictClass, marginOfSafetyPct)

        val raw = spec.rangeLow + (marginOfSafetyPct / 100.0) * spec.scaleFactor
        val clamped = raw.coerceIn(spec.rangeLow, spec.rangeHigh)

        return PositionSizeResult(
            recommendedPct = clamped,
            range = spec.rangeLow to spec.rangeHigh,
            basisVerdict = verdictClass,
            marginOfSafetyPct = marginOfSafetyPct,
        )
    }

    private fun zeroResult(verdictClass: VerdictClass, marginOfSafetyPct: Double) =
        PositionSizeResult(
            recommendedPct = 0.0,
            range = 0.0 to 0.0,
            basisVerdict = verdictClass,
            marginOfSafetyPct = marginOfSafetyPct,
        )

    private data class SizingSpec(
        val rangeLow: Double,
        val rangeHigh: Double,
        val scaleFactor: Double,
    )

    private companion object {
        val SIZING_SPECS: Map<VerdictClass, SizingSpec> = mapOf(
            VerdictClass.APPROVATO_PANIC_BUY to SizingSpec(
                rangeLow = 4.0,
                rangeHigh = 6.0,
                scaleFactor = 4.0,
            ),
            VerdictClass.APPROVATO to SizingSpec(
                rangeLow = 2.0,
                rangeHigh = 4.0,
                scaleFactor = 4.0,
            ),
        )
    }
}
