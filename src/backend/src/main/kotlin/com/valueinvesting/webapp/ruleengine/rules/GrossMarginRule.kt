package com.valueinvesting.webapp.ruleengine.rules

import com.valueinvesting.webapp.fmp.dto.IncomeStatementDto
import com.valueinvesting.webapp.ruleengine.RuleSignal
import com.valueinvesting.webapp.ruleengine.Signal
import com.valueinvesting.webapp.ruleengine.ValuationRule
import com.valueinvesting.webapp.service.FinancialDataset
import org.springframework.stereotype.Component

// Rule: 10-year average Gross Margin vs. pricing-power thresholds.
//
// Thresholds (US-008 Business Rules):
//   avg GM > 40%   -> GREEN
//   30% ≤ avg ≤ 40% -> YELLOW
//   avg GM < 30%   -> RED
//
// Source priority (per-year):
//   1. FMP-provided `grossProfitRatio` if non-null  (already a fraction, e.g. 0.42)
//   2. Otherwise compute `grossProfit / revenue` when both are non-null and
//      `revenue` is strictly positive (to avoid division-by-zero artefacts).
//   3. Otherwise the year is excluded as "missing" (NEVER coerced to 0.0).
//
// Edge cases:
//   - dataset.income empty OR no usable year                -> NOT_CALCULABLE
//   - < 5 usable years                                       -> INDETERMINATE
//                                                              (consistent with TSK-012)
//
// [^src: management/kanban/EP-003-rule-engine-quantitativo/US-008-regola-pricing-power/US-008.md §Business Rules]
// [^src: design_&_architecture/components/backend-components.md §Validazione null safety]
// [^src: wiki/concepts/value-investing-rule-engine.md §Pricing power]
// [^src: wiki/concepts/economic-moat.md §Margini come proxy moat]
@Component
class GrossMarginRule : ValuationRule {

    override val ruleId: String = "GROSS_MARGIN_10Y_AVG"

    override fun evaluate(dataset: FinancialDataset): RuleSignal {
        val sample = averageOfMetric(dataset.income) { extractGrossMargin(it) }

        if (sample.average == null) {
            return RuleSignal(
                ruleId = ruleId,
                signal = Signal.NOT_CALCULABLE,
                observedValue = null,
                threshold = THRESHOLD_LABEL,
                rationale = "Nessun valore di Gross Margin calcolabile nei ${sample.totalYears} esercizi forniti.",
            )
        }

        if (sample.effectiveYears < MIN_YEARS) {
            return RuleSignal(
                ruleId = ruleId,
                signal = Signal.INDETERMINATE,
                observedValue = sample.average,
                threshold = THRESHOLD_LABEL,
                rationale = "Solo ${sample.effectiveYears} esercizi con Gross Margin valido (minimo richiesto: $MIN_YEARS).",
            )
        }

        val signal = when {
            sample.average > GREEN_THRESHOLD -> Signal.GREEN
            sample.average >= YELLOW_THRESHOLD -> Signal.YELLOW
            else -> Signal.RED
        }
        val pct = "%.2f%%".format(sample.average * 100)
        return RuleSignal(
            ruleId = ruleId,
            signal = signal,
            observedValue = sample.average,
            threshold = THRESHOLD_LABEL,
            rationale = "Media Gross Margin su ${sample.effectiveYears} esercizi: $pct.",
        )
    }

    // FMP-provided ratio wins; fall back to a derived ratio only when revenue > 0.
    // Returning null marks the year as "missing" so it is excluded from the mean.
    private fun extractGrossMargin(row: IncomeStatementDto): Double? {
        row.grossProfitRatio?.let { return it }
        val revenue = row.revenue
        val grossProfit = row.grossProfit
        if (revenue == null || grossProfit == null) return null
        if (revenue <= 0.0) return null
        return grossProfit / revenue
    }

    private companion object {
        const val GREEN_THRESHOLD = 0.40
        const val YELLOW_THRESHOLD = 0.30
        const val MIN_YEARS = 5
        const val THRESHOLD_LABEL = "> 40% (GREEN), 30%-40% (YELLOW), < 30% (RED)"
    }
}
