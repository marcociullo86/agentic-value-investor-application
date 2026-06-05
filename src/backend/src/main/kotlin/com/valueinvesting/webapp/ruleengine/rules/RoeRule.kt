package com.valueinvesting.webapp.ruleengine.rules

import com.valueinvesting.webapp.ruleengine.RuleSignal
import com.valueinvesting.webapp.ruleengine.Signal
import com.valueinvesting.webapp.ruleengine.ValuationRule
import com.valueinvesting.webapp.service.FinancialDataset
import org.springframework.stereotype.Component

// Rule: 10-year average Return On Equity vs. value-investing thresholds.
//
// Thresholds (US-007 Business Rules, wiki/concepts/value-investing-rule-engine):
//   avg ROE > 15%   -> GREEN
//   10% ≤ avg ≤ 15% -> YELLOW
//   avg ROE < 10%   -> RED
//
// Edge cases:
//   - keyMetrics empty OR every roe == null  -> NOT_CALCULABLE
//   - < 5 years with non-null roe            -> INDETERMINATE (US-007 AC: NOT RED)
//
// FMP's `KeyMetricsDto.roe` is a fraction (0.18 == 18%), so thresholds are
// expressed as fractions (0.15, 0.10) and the human-readable form multiplies x100.
// [^src: management/kanban/EP-003-rule-engine-quantitativo/US-007-regola-redditivita/US-007.md §Business Rules]
// [^src: wiki/concepts/value-investing-rule-engine.md §Redditività]
@Component
class RoeRule : ValuationRule {

    override val ruleId: String = "ROE_10Y_AVG"

    override fun evaluate(dataset: FinancialDataset): RuleSignal {
        val sample = averageOf(dataset.keyMetrics) { it.roe }

        if (sample.average == null) {
            return RuleSignal.Roe10yAvg(
                signal = Signal.NOT_CALCULABLE,
                averagePercent = null,
                yearsAvailable = sample.effectiveYears,
                thresholdGreenPercent = GREEN_THRESHOLD * 100.0,
                thresholdYellowPercent = YELLOW_THRESHOLD * 100.0,
                observedValue = null,
                threshold = THRESHOLD_LABEL,
                rationale = "Nessun valore di ROE disponibile nei ${sample.totalYears} esercizi forniti.",
            )
        }

        if (sample.effectiveYears < MIN_YEARS) {
            return RuleSignal.Roe10yAvg(
                signal = Signal.INDETERMINATE,
                averagePercent = sample.average * 100.0,
                yearsAvailable = sample.effectiveYears,
                thresholdGreenPercent = GREEN_THRESHOLD * 100.0,
                thresholdYellowPercent = YELLOW_THRESHOLD * 100.0,
                observedValue = sample.average,
                threshold = THRESHOLD_LABEL,
                rationale = "Solo ${sample.effectiveYears} esercizi con ROE valido (minimo richiesto: $MIN_YEARS).",
            )
        }

        val signal = when {
            sample.average > GREEN_THRESHOLD -> Signal.GREEN
            sample.average >= YELLOW_THRESHOLD -> Signal.YELLOW
            else -> Signal.RED
        }
        val pct = "%.2f%%".format(sample.average * 100)
        return RuleSignal.Roe10yAvg(
            signal = signal,
            averagePercent = sample.average * 100.0,
            yearsAvailable = sample.effectiveYears,
            thresholdGreenPercent = GREEN_THRESHOLD * 100.0,
            thresholdYellowPercent = YELLOW_THRESHOLD * 100.0,
            observedValue = sample.average,
            threshold = THRESHOLD_LABEL,
            rationale = "Media ROE su ${sample.effectiveYears} esercizi: $pct.",
        )
    }

    private companion object {
        const val GREEN_THRESHOLD = 0.15
        const val YELLOW_THRESHOLD = 0.10
        const val MIN_YEARS = 5
        const val THRESHOLD_LABEL = "> 15% (GREEN), 10%-15% (YELLOW), < 10% (RED)"
    }
}
