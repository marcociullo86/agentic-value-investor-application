package com.valueinvesting.webapp.ruleengine.rules

import com.valueinvesting.webapp.fmp.dto.BalanceSheetDto
import com.valueinvesting.webapp.ruleengine.RuleSignal
import com.valueinvesting.webapp.ruleengine.Signal
import com.valueinvesting.webapp.ruleengine.ValuationRule
import com.valueinvesting.webapp.service.FinancialDataset
import org.springframework.stereotype.Component

// Rule: Current Ratio vs. value-investing financial-solidity thresholds.
//
// Thresholds (US-009 Business Rules):
//   ratio > 2.0          -> GREEN
//   1.5 <= ratio <= 2.0  -> YELLOW  (rationale notes "stabile-friendly")
//   ratio < 1.5          -> RED
//
// Design note — LATEST-YEAR (snapshot), NOT 10y average:
//   Current Ratio is a point-in-time liquidity snapshot (short-term assets vs.
//   short-term liabilities at fiscal year-end), not a multi-year quality trend
//   like ROE / ROIC / margins. The value-investing literature evaluates it on
//   the MOST RECENT balance sheet. TSK-014 text does not say "media decennale"
//   (contrary to TSK-012/013), and US-009 AC uses the singular ("il segnale e'
//   Verde / Giallo / Rosso"). We therefore pick the latest available year by
//   `date` (ISO-8601 lexicographic order, fallback `calendarYear`) instead of
//   averaging.
//
// Source (per balance sheet row):
//   totalCurrentAssets / totalCurrentLiabilities
//
// Edge cases (PATTERN convention + ADR-005 null safety):
//   - dataset.balance empty                                    -> NOT_CALCULABLE
//   - latest row missing currentAssets OR currentLiabilities   -> INDETERMINATE
//   - latest row currentLiabilities <= 0 (div-by-zero/neg)     -> INDETERMINATE
//   We NEVER coerce a missing financial value to 0.0 (PATTERN).
//
// [^src: management/kanban/EP-003-rule-engine-quantitativo/US-009-regola-solidita-finanziaria/US-009.md §Business Rules]
// [^src: management/kanban/EP-003-rule-engine-quantitativo/US-009-regola-solidita-finanziaria/TSK-014.md §Scope tecnico]
// [^src: wiki/concepts/value-investing-rule-engine.md §Solidita' Finanziaria]
// [^src: design_&_architecture/components/backend-components.md §Validazione null safety]
@Component
class CurrentRatioRule : ValuationRule {

    override val ruleId: String = "CURRENT_RATIO_LATEST"

    override fun evaluate(dataset: FinancialDataset): RuleSignal {
        if (dataset.balance.isEmpty()) {
            return RuleSignal(
                ruleId = ruleId,
                signal = Signal.NOT_CALCULABLE,
                observedValue = null,
                threshold = THRESHOLD_LABEL,
                rationale = "Nessun esercizio di Balance Sheet disponibile.",
            )
        }

        val latest = latestRow(dataset.balance)
        val assets = latest.totalCurrentAssets
        val liabilities = latest.totalCurrentLiabilities
        val yearLabel = latest.date ?: latest.calendarYear ?: "ultimo esercizio"

        if (assets == null || liabilities == null) {
            return RuleSignal(
                ruleId = ruleId,
                signal = Signal.INDETERMINATE,
                observedValue = null,
                threshold = THRESHOLD_LABEL,
                rationale = "Voci di Current Assets o Current Liabilities mancanti per $yearLabel.",
            )
        }
        if (liabilities <= 0.0) {
            return RuleSignal(
                ruleId = ruleId,
                signal = Signal.INDETERMINATE,
                observedValue = null,
                threshold = THRESHOLD_LABEL,
                rationale = "Current Liabilities non positive ($liabilities) per $yearLabel: ratio non definito.",
            )
        }

        val ratio = assets / liabilities
        val signal = when {
            ratio > GREEN_THRESHOLD -> Signal.GREEN
            ratio >= YELLOW_THRESHOLD -> Signal.YELLOW
            else -> Signal.RED
        }
        val rationale = when (signal) {
            Signal.YELLOW ->
                "Current Ratio %.2f per %s nella zona stabile-friendly (1.5-2.0)."
                    .format(ratio, yearLabel)
            else ->
                "Current Ratio %.2f per %s.".format(ratio, yearLabel)
        }
        return RuleSignal(
            ruleId = ruleId,
            signal = signal,
            observedValue = ratio,
            threshold = THRESHOLD_LABEL,
            rationale = rationale,
        )
    }

    // Choose the most recent balance sheet row. `date` is ISO-8601 (e.g. 2024-12-31)
    // so lexicographic max == chronological max. Fall back to `calendarYear` when
    // `date` is null; if both are null we degrade to first() preserving FMP order.
    private fun latestRow(rows: List<BalanceSheetDto>): BalanceSheetDto {
        return rows.maxByOrNull { row ->
            row.date ?: row.calendarYear ?: ""
        } ?: rows.first()
    }

    private companion object {
        const val GREEN_THRESHOLD = 2.0
        const val YELLOW_THRESHOLD = 1.5
        const val THRESHOLD_LABEL = "> 2.0 (GREEN), 1.5-2.0 (YELLOW), < 1.5 (RED)"
    }
}
