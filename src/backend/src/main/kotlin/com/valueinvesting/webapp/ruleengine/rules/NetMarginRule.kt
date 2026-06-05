package com.valueinvesting.webapp.ruleengine.rules

import com.valueinvesting.webapp.fmp.dto.IncomeStatementDto
import com.valueinvesting.webapp.ruleengine.RuleSignal
import com.valueinvesting.webapp.ruleengine.Signal
import com.valueinvesting.webapp.ruleengine.ValuationRule
import com.valueinvesting.webapp.service.FinancialDataset
import org.springframework.stereotype.Component

// Rule: 10-year average Net Margin vs. pricing-power thresholds.
//
// Thresholds (US-008 Business Rules, TSK-013 §Scope tecnico):
//   avg NM > 10%   -> GREEN
//   avg NM ≤ 10%   -> RED
//
// Design note — BINARY classification (no YELLOW band):
//   TSK-013 explicitly states "Verde se Net Margin > 10% costante. Rosso se < 10%."
//   Unlike GrossMargin / ROE / ROIC the spec defines only two outcome bands.
//   We honour the spec verbatim (PATTERN §11 "Standards verbatim") and DO NOT
//   introduce a YELLOW band that is not in the requirement. The decision keeps
//   the rule strict and audit-friendly; should the PM later require a YELLOW
//   buffer (e.g. 8%-10%), it must travel through a new TSK or US revision.
//   YELLOW is therefore reserved here for the data-availability edge cases
//   modelled by INDETERMINATE — which is the existing closed-set convention.
//
// Source priority (per-year):
//   1. FMP-provided `netIncomeRatio` if non-null      (already a fraction)
//   2. Otherwise compute `netIncome / revenue` when both are non-null and
//      `revenue` is strictly positive.
//   3. Otherwise the year is excluded as "missing" (NEVER coerced to 0.0).
//
// Edge cases:
//   - dataset.income empty OR no usable year                -> NOT_CALCULABLE
//   - < 5 usable years                                       -> INDETERMINATE
//
// [^src: management/kanban/EP-003-rule-engine-quantitativo/US-008-regola-pricing-power/TSK-013.md §Scope tecnico]
// [^src: design_&_architecture/components/backend-components.md §Validazione null safety]
@Component
class NetMarginRule : ValuationRule {

    override val ruleId: String = "NET_MARGIN_10Y_AVG"

    override fun evaluate(dataset: FinancialDataset): RuleSignal {
        val sample = averageOfMetric(dataset.income) { extractNetMargin(it) }

        if (sample.average == null) {
            return RuleSignal.NetMargin10yAvg(
                signal = Signal.NOT_CALCULABLE,
                averagePercent = null,
                thresholdGreenPercent = GREEN_THRESHOLD * 100.0,
                observedValue = null,
                threshold = THRESHOLD_LABEL,
                rationale = "Nessun valore di Net Margin calcolabile nei ${sample.totalYears} esercizi forniti.",
            )
        }

        if (sample.effectiveYears < MIN_YEARS) {
            return RuleSignal.NetMargin10yAvg(
                signal = Signal.INDETERMINATE,
                averagePercent = sample.average * 100.0,
                thresholdGreenPercent = GREEN_THRESHOLD * 100.0,
                observedValue = sample.average,
                threshold = THRESHOLD_LABEL,
                rationale = "Solo ${sample.effectiveYears} esercizi con Net Margin valido (minimo richiesto: $MIN_YEARS).",
            )
        }

        // Binary classification per TSK-013: > 10% GREEN, otherwise RED.
        val signal = if (sample.average > GREEN_THRESHOLD) Signal.GREEN else Signal.RED
        val pct = "%.2f%%".format(sample.average * 100)
        return RuleSignal.NetMargin10yAvg(
            signal = signal,
            averagePercent = sample.average * 100.0,
            thresholdGreenPercent = GREEN_THRESHOLD * 100.0,
            observedValue = sample.average,
            threshold = THRESHOLD_LABEL,
            rationale = "Media Net Margin su ${sample.effectiveYears} esercizi: $pct.",
        )
    }

    private fun extractNetMargin(row: IncomeStatementDto): Double? {
        row.netIncomeRatio?.let { return it }
        val revenue = row.revenue
        val netIncome = row.netIncome
        if (revenue == null || netIncome == null) return null
        if (revenue <= 0.0) return null
        return netIncome / revenue
    }

    private companion object {
        const val GREEN_THRESHOLD = 0.10
        const val MIN_YEARS = 5
        const val THRESHOLD_LABEL = "> 10% (GREEN), ≤ 10% (RED)"
    }
}
