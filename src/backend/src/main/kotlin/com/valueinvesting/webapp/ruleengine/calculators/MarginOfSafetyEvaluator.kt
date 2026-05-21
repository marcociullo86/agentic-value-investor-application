package com.valueinvesting.webapp.ruleengine.calculators

import com.valueinvesting.webapp.ruleengine.Signal
import org.springframework.stereotype.Component

@Component
class MarginOfSafetyEvaluator {

    data class MosEvaluation(
        val signal: Signal,
        val rationale: String,
    )

    fun evaluate(currentPrice: Double?, dcfResult: DcfResult): MosEvaluation {
        val dcf = dcfResult.intrinsicValue
        if (dcf == null || currentPrice == null || dcf <= 0.0 || currentPrice <= 0.0) {
            return MosEvaluation(
                signal = Signal.NOT_CALCULABLE,
                rationale = "Prezzo o DCF non disponibili — MoS non calcolabile.",
            )
        }
        val threshold = dcf * MOS_DISCOUNT_FACTOR
        return when {
            currentPrice < threshold -> MosEvaluation(
                signal = Signal.GREEN,
                rationale = "Prezzo $currentPrice < 70% DCF ($threshold) — margine di sicurezza.",
            )
            currentPrice < dcf -> MosEvaluation(
                signal = Signal.YELLOW,
                rationale = "Prezzo $currentPrice tra 70% e 100% del DCF ($dcf).",
            )
            else -> MosEvaluation(
                signal = Signal.RED,
                rationale = "Prezzo $currentPrice ≥ DCF ($dcf) — nessun margine.",
            )
        }
    }

    companion object {
        const val MOS_DISCOUNT_FACTOR = 0.70
    }
}
