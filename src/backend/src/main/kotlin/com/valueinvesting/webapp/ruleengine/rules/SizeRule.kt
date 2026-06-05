package com.valueinvesting.webapp.ruleengine.rules

import com.valueinvesting.webapp.ruleengine.RuleSignal
import com.valueinvesting.webapp.ruleengine.Signal
import com.valueinvesting.webapp.ruleengine.ValuationRule
import com.valueinvesting.webapp.service.FinancialDataset
import org.springframework.stereotype.Component

// Rule: Graham "Adequate Size of the Enterprise" — single-year revenue check.
//
// Thresholds (US-032 Business Rules, TSK-073 §Cosa fare):
//   revenue >= $100M -> GREEN
//   revenue <  $100M -> RED   (data present, below threshold)
//   revenue == null OR dataset.income empty -> INDETERMINATE (NOT RED, per AC)
//
// Design note — single-year semantics:
//   Graham's defensive criterion uses the LATEST fiscal year (`SIZE_LATEST`)
//   rather than a multi-year average: the size threshold is a snapshot of the
//   current scale of operations, not a long-run trend. Latest-year selection
//   mirrors CapexIntensityRule.evaluateLatest() — pick the income row with the
//   max `date` (ISO lexicographic == chronological), falling back to
//   `calendarYear`. Empty list -> INDETERMINATE (we report no data, NOT RED).
//
// Design note — revenue == 0.0:
//   Treated as RED (data point exists and is below the $100M threshold).
//   FMP's "missing field" convention is JSON `null`, not 0 — so a literal 0.0
//   is interpreted as a genuine (defunct / shell company) zero revenue, not
//   a placeholder. This is consistent with PATTERN §7 r.13 "never coerce to 0".
//
// Threshold expressed as Long to keep the spec verbatim (TSK-073 Note
// implementative: `private const val SIZE_THRESHOLD_USD = 100_000_000L`),
// converted to Double at compare-time because IncomeStatementDto.revenue is
// `Double?`.
//
// [^src: management/kanban/EP-010-graham-defensive-completeness/US-032-regola-dimensioni-graham/TSK-073.md §Cosa fare]
// [^src: wiki/concepts/seven-criteria-defensive-stock-selection.md §Criterio 1 — Dimensioni Adeguate dell'Azienda]
// [^src: wiki/concepts/value-investing-rule-engine.md §Output del Rule Engine: il "Traffic Light"]
@Component
class SizeRule : ValuationRule {

    override val ruleId: String = "SIZE_LATEST"

    override fun evaluate(dataset: FinancialDataset): RuleSignal {
        if (dataset.income.isEmpty()) {
            return RuleSignal.Size(
                signal = Signal.INDETERMINATE,
                revenueLatest = null,
                thresholdUsd = SIZE_THRESHOLD_USD,
                observedValue = null,
                threshold = THRESHOLD_LABEL,
                rationale = "Income Statement non disponibile: dimensione aziendale indeterminata.",
            )
        }

        // TODO(US-032 utility carve-out): out-of-scope per TSK-073 (richiede
        // CompanyProfileDto.sector in FinancialDataset). Per le utility la soglia
        // alternativa sarebbe $50M ma il dataset attuale non veicola il settore.

        val latestIncome = dataset.income.maxByOrNull { it.date ?: it.calendarYear ?: "" }
            ?: return RuleSignal.Size(
                signal = Signal.INDETERMINATE,
                revenueLatest = null,
                thresholdUsd = SIZE_THRESHOLD_USD,
                observedValue = null,
                threshold = THRESHOLD_LABEL,
                rationale = "Income Statement non ordinabile: dimensione aziendale indeterminata.",
            )

        val yearLabel = latestIncome.date
            ?: latestIncome.calendarYear
            ?: "ultimo esercizio"

        val revenue = latestIncome.revenue
        if (revenue == null) {
            return RuleSignal.Size(
                signal = Signal.INDETERMINATE,
                revenueLatest = null,
                thresholdUsd = SIZE_THRESHOLD_USD,
                observedValue = null,
                threshold = THRESHOLD_LABEL,
                rationale = "Revenue mancante per $yearLabel: dimensione aziendale indeterminata.",
            )
        }

        val signal = if (revenue >= SIZE_THRESHOLD_USD.toDouble()) Signal.GREEN else Signal.RED
        val revenueMillions = "%.0fM".format(revenue / 1_000_000.0)
        return RuleSignal.Size(
            signal = signal,
            revenueLatest = revenue,
            thresholdUsd = SIZE_THRESHOLD_USD,
            observedValue = revenue,
            threshold = THRESHOLD_LABEL,
            rationale = "Revenue $yearLabel: \$$revenueMillions.",
        )
    }

    private companion object {
        const val SIZE_THRESHOLD_USD = 100_000_000L
        const val THRESHOLD_LABEL = "≥ \$100M (GREEN), < \$100M (RED)"
    }
}
