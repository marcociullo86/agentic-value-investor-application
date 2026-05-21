package com.valueinvesting.webapp.ruleengine.rules

import com.valueinvesting.webapp.fmp.dto.BalanceSheetDto
import com.valueinvesting.webapp.fmp.dto.IncomeStatementDto
import com.valueinvesting.webapp.ruleengine.RuleSignal
import com.valueinvesting.webapp.ruleengine.Signal
import com.valueinvesting.webapp.ruleengine.ValuationRule
import com.valueinvesting.webapp.service.FinancialDataset
import org.springframework.stereotype.Component

// Rule: Long-Term Debt / Net Income (latest year) vs. solvency thresholds.
//
// Thresholds (US-009 Business Rules + TSK-014):
//   ratio < 4.0          -> GREEN
//   4.0 <= ratio <= 5.0  -> YELLOW (interpolation, see Design note below)
//   ratio > 5.0          -> RED
//
// Design note — INTERPOLATED YELLOW BAND [4, 5]:
//   The TSK-014 spec text gives only two extremes ("Verde < 4, Rosso > 5") and
//   does not classify the [4, 5] interval. Two options were considered:
//     (A) introduce a YELLOW band on [4, 5] (interpolation).
//     (B) keep a strict binary split at an arbitrary midpoint (e.g. <=4 GREEN,
//         >4 RED) which would CONTRADICT the literal "> 5 = RED" boundary.
//   We pick (A) because:
//     - US-009 already lists Verde and Rosso as TWO distinct thresholds (not a
//       single boundary); leaving [4, 5] unclassified produces a hole.
//     - The Signal enum reserves YELLOW exactly for "borderline / mixed" which
//       maps 1:1 to this interpolated band.
//     - Same approach used for CurrentRatio in the same US-009 ("1.5-2.0
//       stabile-friendly").
//   This differs from NetMarginRule (TSK-013) which was binary by VERBATIM spec
//   ("Verde > 10%. Rosso < 10%") — there the spec covered the full real line.
//   Here it does not.
//
// Critical edge case (US-009 AC verbatim):
//   netIncome null OR netIncome <= 0   -> INDETERMINATE   (NOT RED).
//   A non-positive utile means the ratio is undefined (negative denominator
//   would invert sign; zero denominator is divide-by-zero). Treating it as RED
//   would mis-classify loss-making years as "over-indebted" which is false
//   reasoning. INDETERMINATE preserves the semantic distinction.
//
// Source (per row, latest year by `date`):
//   balance.longTermDebt / income.netIncome
//   Year selection: max `date` (ISO-8601 lexicographic == chronological) with
//   fallback to `calendarYear`. We REQUIRE both rows to refer to the same year
//   when both lists carry a year label — otherwise we degrade to "latest of
//   each" but flag it in the rationale.
//
// Edge cases (PATTERN + ADR-005 null safety, never coerce to 0.0):
//   - balance OR income empty                    -> NOT_CALCULABLE
//   - longTermDebt null                          -> INDETERMINATE
//   - netIncome null OR netIncome <= 0           -> INDETERMINATE
//
// [^src: management/kanban/EP-003-rule-engine-quantitativo/US-009-regola-solidita-finanziaria/US-009.md §Business Rules + §AC]
// [^src: management/kanban/EP-003-rule-engine-quantitativo/US-009-regola-solidita-finanziaria/TSK-014.md §Scope tecnico]
// [^src: wiki/concepts/value-investing-rule-engine.md §Solidita' Finanziaria]
// [^src: design_&_architecture/components/backend-components.md §Validazione null safety]
@Component
class DebtToIncomeRule : ValuationRule {

    override val ruleId: String = "DEBT_TO_INCOME_LATEST"

    override fun evaluate(dataset: FinancialDataset): RuleSignal {
        if (dataset.balance.isEmpty() || dataset.income.isEmpty()) {
            return RuleSignal(
                ruleId = ruleId,
                signal = Signal.NOT_CALCULABLE,
                observedValue = null,
                threshold = THRESHOLD_LABEL,
                rationale = "Balance Sheet o Income Statement non disponibili.",
            )
        }

        val latestBalance = latestBalance(dataset.balance)
        val latestIncome = latestIncome(dataset.income)
        val yearLabel = latestBalance.date
            ?: latestBalance.calendarYear
            ?: latestIncome.date
            ?: latestIncome.calendarYear
            ?: "ultimo esercizio"

        val longTermDebt = latestBalance.longTermDebt
        val netIncome = latestIncome.netIncome

        if (longTermDebt == null) {
            return RuleSignal(
                ruleId = ruleId,
                signal = Signal.INDETERMINATE,
                observedValue = null,
                threshold = THRESHOLD_LABEL,
                rationale = "Long-Term Debt mancante per $yearLabel.",
            )
        }
        if (netIncome == null) {
            return RuleSignal(
                ruleId = ruleId,
                signal = Signal.INDETERMINATE,
                observedValue = null,
                threshold = THRESHOLD_LABEL,
                rationale = "Utile Netto mancante per $yearLabel: rapporto debito/utile non definito.",
            )
        }
        if (netIncome <= 0.0) {
            // US-009 AC verbatim: NEVER classify as RED here.
            return RuleSignal(
                ruleId = ruleId,
                signal = Signal.INDETERMINATE,
                observedValue = null,
                threshold = THRESHOLD_LABEL,
                rationale = "Utile Netto non positivo ($netIncome) per $yearLabel: rapporto debito/utile indeterminato.",
            )
        }

        val ratio = longTermDebt / netIncome
        val signal = when {
            ratio < GREEN_THRESHOLD -> Signal.GREEN
            ratio <= YELLOW_UPPER_BOUND -> Signal.YELLOW
            else -> Signal.RED
        }
        return RuleSignal(
            ruleId = ruleId,
            signal = signal,
            observedValue = ratio,
            threshold = THRESHOLD_LABEL,
            rationale = "Long-Term Debt / Net Income = %.2f per %s.".format(ratio, yearLabel),
        )
    }

    private fun latestBalance(rows: List<BalanceSheetDto>): BalanceSheetDto =
        rows.maxByOrNull { it.date ?: it.calendarYear ?: "" } ?: rows.first()

    private fun latestIncome(rows: List<IncomeStatementDto>): IncomeStatementDto =
        rows.maxByOrNull { it.date ?: it.calendarYear ?: "" } ?: rows.first()

    private companion object {
        const val GREEN_THRESHOLD = 4.0
        const val YELLOW_UPPER_BOUND = 5.0
        const val THRESHOLD_LABEL = "< 4 (GREEN), 4-5 (YELLOW), > 5 (RED)"
    }
}
