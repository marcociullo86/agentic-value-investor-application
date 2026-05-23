package com.valueinvesting.webapp.ruleengine.calculators

import com.valueinvesting.webapp.ruleengine.Signal
import org.springframework.stereotype.Component

@Component
class MarginOfSafetyEvaluator {

    data class MosEvaluation(
        val signal: Signal,
        val rationale: String,
    )

    // US-052: `dcfResult.intrinsicValue` is the per-share fair value (USD/share),
    // directly comparable to `currentPrice` from `/stable/profile` (per-share).
    // Rationale messages reflect per-share semantics for operator transparency.
    fun evaluate(currentPrice: Double?, dcfResult: DcfResult): MosEvaluation {
        val fairValuePerShare = dcfResult.intrinsicValue
        if (fairValuePerShare == null || currentPrice == null || fairValuePerShare <= 0.0 || currentPrice <= 0.0) {
            return MosEvaluation(
                signal = Signal.NOT_CALCULABLE,
                rationale = "Prezzo o fair value/share non disponibili — MoS non calcolabile.",
            )
        }
        val threshold = fairValuePerShare * MOS_DISCOUNT_FACTOR
        val pricePerShare = "%.2f".format(currentPrice)
        val fairValue = "%.2f".format(fairValuePerShare)
        val thresholdFmt = "%.2f".format(threshold)
        return when {
            currentPrice < threshold -> MosEvaluation(
                signal = Signal.GREEN,
                rationale = "Prezzo $$pricePerShare/share < 70% Fair Value ($$thresholdFmt/share) — margine di sicurezza adeguato.",
            )
            currentPrice < fairValuePerShare -> MosEvaluation(
                signal = Signal.YELLOW,
                rationale = "Prezzo $$pricePerShare/share tra 70% e 100% del Fair Value ($$fairValue/share) — margine ridotto.",
            )
            else -> MosEvaluation(
                signal = Signal.RED,
                rationale = "Prezzo $$pricePerShare/share ≥ Fair Value ($$fairValue/share) — nessun margine di sicurezza.",
            )
        }
    }

    companion object {
        const val MOS_DISCOUNT_FACTOR = 0.70
    }
}
