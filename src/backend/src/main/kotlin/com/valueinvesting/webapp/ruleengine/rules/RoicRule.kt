package com.valueinvesting.webapp.ruleengine.rules

import com.valueinvesting.webapp.ruleengine.RuleSignal
import com.valueinvesting.webapp.ruleengine.Signal
import com.valueinvesting.webapp.ruleengine.ValuationRule
import com.valueinvesting.webapp.service.FinancialDataset
import org.springframework.stereotype.Component

// Rule: 10-year average Return On Invested Capital vs. value-investing thresholds.
//
// Thresholds (US-007 + wiki/concepts/value-investing-rule-engine):
//   avg ROIC > 12%  -> GREEN
//   8% ≤ avg ≤ 12%  -> YELLOW
//   avg ROIC < 8%   -> RED
//
// Edge cases:
//   - keyMetrics empty OR every roic == null -> NOT_CALCULABLE
//   - < 5 years with non-null roic           -> INDETERMINATE (US-007 AC: NOT RED)
//
// FMP's `KeyMetricsDto.roic` is a fraction (0.13 == 13%).
// [^src: management/kanban/EP-003-rule-engine-quantitativo/US-007-regola-redditivita/US-007.md §Business Rules]
// [^src: wiki/concepts/value-investing-rule-engine.md §Redditività]
@Component
class RoicRule : ValuationRule {

    override val ruleId: String = "ROIC_10Y_AVG"

    override fun evaluate(dataset: FinancialDataset): RuleSignal {
        val sample = averageOf(dataset.keyMetrics) { it.roic }

        if (sample.average == null) {
            return RuleSignal.Roic10yAvg(
                signal = Signal.NOT_CALCULABLE,
                averagePercent = null,
                yearsAvailable = sample.effectiveYears,
                thresholdGreenPercent = GREEN_THRESHOLD * 100.0,
                thresholdYellowPercent = YELLOW_THRESHOLD * 100.0,
                observedValue = null,
                threshold = THRESHOLD_LABEL,
                rationale = "Nessun valore di ROIC disponibile nei ${sample.totalYears} esercizi forniti.",
            )
        }

        if (sample.effectiveYears < MIN_YEARS) {
            return RuleSignal.Roic10yAvg(
                signal = Signal.INDETERMINATE,
                averagePercent = sample.average * 100.0,
                yearsAvailable = sample.effectiveYears,
                thresholdGreenPercent = GREEN_THRESHOLD * 100.0,
                thresholdYellowPercent = YELLOW_THRESHOLD * 100.0,
                observedValue = sample.average,
                threshold = THRESHOLD_LABEL,
                rationale = "Solo ${sample.effectiveYears} esercizi con ROIC valido (minimo richiesto: $MIN_YEARS).",
            )
        }

        val signal = when {
            sample.average > GREEN_THRESHOLD -> Signal.GREEN
            sample.average >= YELLOW_THRESHOLD -> Signal.YELLOW
            else -> Signal.RED
        }
        val pct = "%.2f%%".format(sample.average * 100)
        return RuleSignal.Roic10yAvg(
            signal = signal,
            averagePercent = sample.average * 100.0,
            yearsAvailable = sample.effectiveYears,
            thresholdGreenPercent = GREEN_THRESHOLD * 100.0,
            thresholdYellowPercent = YELLOW_THRESHOLD * 100.0,
            observedValue = sample.average,
            threshold = THRESHOLD_LABEL,
            rationale = "Media ROIC su ${sample.effectiveYears} esercizi: $pct.",
        )
    }

    private companion object {
        const val GREEN_THRESHOLD = 0.12
        const val YELLOW_THRESHOLD = 0.08
        const val MIN_YEARS = 5
        const val THRESHOLD_LABEL = "> 12% (GREEN), 8%-12% (YELLOW), < 8% (RED)"
    }
}
