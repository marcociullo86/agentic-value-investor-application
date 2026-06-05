package com.valueinvesting.webapp.ruleengine.rules

import com.valueinvesting.webapp.fmp.dto.CashFlowDto
import com.valueinvesting.webapp.fmp.dto.IncomeStatementDto
import com.valueinvesting.webapp.ruleengine.RuleSignal
import com.valueinvesting.webapp.ruleengine.Signal
import com.valueinvesting.webapp.ruleengine.ValuationRule
import com.valueinvesting.webapp.service.FinancialDataset
import org.springframework.stereotype.Component
import kotlin.math.abs

// Rule: Capital intensity = CapEx / Net Income, 10-year average preferred,
// fallback to latest year. Lower ratio == more capital-light business
// (preferred by value investors as a proxy of moat / pricing power).
//
// Thresholds (US-010 Business Rules, TSK-015 §Scope tecnico):
//   ratio < 25%        -> GREEN
//   25% <= ratio <= 30% -> YELLOW
//   ratio > 30%        -> RED
//
// Strategy (mirrors TSK-013 NetMargin / TSK-012 ROE 10y pattern):
//   1. Pair every CashFlow row with the IncomeStatement row of the SAME year
//      (matched by `date`, falling back to `calendarYear`). Years present in
//      only one of the two lists are EXCLUDED.
//   2. For each pair: include in the sample iff
//        - capitalExpenditure non-null AND
//        - netIncome non-null AND netIncome > 0
//      (years with netIncome <= 0 are EXCLUDED from the 10y average, NEVER
//      coerced to 0 — see Design note below).
//   3. If at least MIN_YEARS (5) usable pairs exist -> classify on the average.
//   4. Otherwise fall back to LATEST-YEAR semantics:
//        - latest pair (max date) where capitalExpenditure non-null AND
//          netIncome > 0 -> classify on that single ratio.
//        - if no such latest pair exists AND netIncome of the most recent
//          income row is <= 0 or null -> INDETERMINATE (US-010 AC verbatim).
//
// Design note — CapEx SIGN (abs vs. negate):
//   FMP convention: `cashFlow.capitalExpenditure` is stored as a NEGATIVE
//   number (cash outflow, "investmentsInPropertyPlantAndEquipment"). The
//   business intent of the threshold "25%" is the share of profit reinvested
//   in tangible assets, i.e. a POSITIVE percentage. We normalise the sign
//   via `kotlin.math.abs(capitalExpenditure)` (rather than `-capEx`) so the
//   rule is robust against either signed convention should the upstream
//   provider ever flip. This matches the literature ("capital intensity =
//   |CapEx| / Earnings") and the wiki concept page (`value-investing-rule-engine
//   §Capitale Intensivo`).
//
// Design note — netIncome <= 0 -> INDETERMINATE (US-010 AC verbatim):
//   "Se Utile Netto è nullo o negativo, il segnale è 'Indeterminato'."
//   Same pattern as TSK-014 DebtToIncomeRule for the latest-year branch.
//   For the 10y branch, we EXCLUDE loss-making years from the mean (we do
//   NOT substitute 0.0 — PATTERN §7 r.13 "never coerce to 0"). If after
//   exclusion < 5 usable years remain, we degrade to the latest-year branch;
//   if even the latest year has netIncome <= 0 -> INDETERMINATE.
//
// Edge cases (PATTERN §7 r.13 null safety):
//   - cashFlow OR income empty                      -> NOT_CALCULABLE
//   - no usable pair at all (all years excluded)    -> see latest-year branch
//     -> if every income row has netIncome <= 0/null -> INDETERMINATE
//     -> else if no cashFlow with capex for that year -> NOT_CALCULABLE
//
// [^src: management/kanban/EP-003-rule-engine-quantitativo/US-010-regola-capitale-intensivo/US-010.md §Business Rules]
// [^src: management/kanban/EP-003-rule-engine-quantitativo/US-010-regola-capitale-intensivo/TSK-015.md §Scope tecnico]
// [^src: wiki/concepts/value-investing-rule-engine.md §Capitale Intensivo]
// [^src: design_&_architecture/components/backend-components.md §Validazione null safety]
@Component
class CapexIntensityRule : ValuationRule {

    override val ruleId: String = "CAPEX_INTENSITY_10Y_AVG"

    override fun evaluate(dataset: FinancialDataset): RuleSignal {
        if (dataset.cashFlow.isEmpty() || dataset.income.isEmpty()) {
            return RuleSignal.CapexIntensity10yAvg(
                signal = Signal.NOT_CALCULABLE,
                averagePercent = null,
                thresholdGreenPercent = GREEN_THRESHOLD * 100.0,
                thresholdYellowPercent = YELLOW_UPPER_BOUND * 100.0,
                observedValue = null,
                threshold = THRESHOLD_LABEL,
                rationale = "Cash Flow o Income Statement non disponibili.",
            )
        }

        // Index income by year-key for O(1) pairing.
        val incomeByYear: Map<String, IncomeStatementDto> = dataset.income
            .mapNotNull { row -> yearKey(row.date, row.calendarYear)?.let { it to row } }
            .toMap()

        // 10y average branch: build ratios where both capex and a POSITIVE
        // netIncome are available; exclude loss-making years (never 0.0).
        val ratios: List<Double> = dataset.cashFlow.mapNotNull { cf ->
            val key = yearKey(cf.date, cf.calendarYear) ?: return@mapNotNull null
            val incomeRow = incomeByYear[key] ?: return@mapNotNull null
            val capex = cf.capitalExpenditure ?: return@mapNotNull null
            val ni = incomeRow.netIncome ?: return@mapNotNull null
            if (ni <= 0.0) return@mapNotNull null
            abs(capex) / ni
        }

        if (ratios.size >= MIN_YEARS) {
            val avg = ratios.sum() / ratios.size
            val signal = classify(avg)
            val pct = "%.2f%%".format(avg * 100)
            return RuleSignal.CapexIntensity10yAvg(
                signal = signal,
                averagePercent = avg * 100.0,
                thresholdGreenPercent = GREEN_THRESHOLD * 100.0,
                thresholdYellowPercent = YELLOW_UPPER_BOUND * 100.0,
                observedValue = avg,
                threshold = THRESHOLD_LABEL,
                rationale = "Media 10y CapEx/Utile Netto su ${ratios.size} esercizi: $pct.",
            )
        }

        // Fallback: latest-year branch.
        return evaluateLatest(dataset, ratiosCount = ratios.size)
    }

    private fun evaluateLatest(dataset: FinancialDataset, ratiosCount: Int): RuleSignal {
        val latestIncome = dataset.income
            .maxByOrNull { it.date ?: it.calendarYear ?: "" }
            ?: return RuleSignal.CapexIntensity10yAvg(
                signal = Signal.NOT_CALCULABLE,
                averagePercent = null,
                thresholdGreenPercent = GREEN_THRESHOLD * 100.0,
                thresholdYellowPercent = YELLOW_UPPER_BOUND * 100.0,
                observedValue = null,
                threshold = THRESHOLD_LABEL,
                rationale = "Income Statement non disponibile.",
            )
        val yearLabel = latestIncome.date
            ?: latestIncome.calendarYear
            ?: "ultimo esercizio"

        val ni = latestIncome.netIncome
        if (ni == null) {
            return RuleSignal.CapexIntensity10yAvg(
                signal = Signal.INDETERMINATE,
                averagePercent = null,
                thresholdGreenPercent = GREEN_THRESHOLD * 100.0,
                thresholdYellowPercent = YELLOW_UPPER_BOUND * 100.0,
                observedValue = null,
                threshold = THRESHOLD_LABEL,
                rationale = "Utile Netto mancante per $yearLabel: rapporto CapEx/Utile non definito.",
            )
        }
        if (ni <= 0.0) {
            // US-010 AC verbatim: nullo o negativo -> Indeterminato.
            return RuleSignal.CapexIntensity10yAvg(
                signal = Signal.INDETERMINATE,
                averagePercent = null,
                thresholdGreenPercent = GREEN_THRESHOLD * 100.0,
                thresholdYellowPercent = YELLOW_UPPER_BOUND * 100.0,
                observedValue = null,
                threshold = THRESHOLD_LABEL,
                rationale = "Utile Netto non positivo ($ni) per $yearLabel: rapporto CapEx/Utile indeterminato.",
            )
        }

        // Pair with the cashFlow row of the same year (preferred) or the latest cashFlow.
        val matchingCashFlow = yearKey(latestIncome.date, latestIncome.calendarYear)?.let { key ->
            dataset.cashFlow.firstOrNull { yearKey(it.date, it.calendarYear) == key }
        } ?: dataset.cashFlow.maxByOrNull { it.date ?: it.calendarYear ?: "" }

        val capex = matchingCashFlow?.capitalExpenditure
        if (capex == null) {
            return RuleSignal.CapexIntensity10yAvg(
                signal = Signal.NOT_CALCULABLE,
                averagePercent = null,
                thresholdGreenPercent = GREEN_THRESHOLD * 100.0,
                thresholdYellowPercent = YELLOW_UPPER_BOUND * 100.0,
                observedValue = null,
                threshold = THRESHOLD_LABEL,
                rationale = "CapEx mancante per $yearLabel: impossibile calcolare il rapporto.",
            )
        }

        val ratio = abs(capex) / ni
        val signal = classify(ratio)
        val pct = "%.2f%%".format(ratio * 100)
        val sampleNote = if (ratiosCount > 0) " (storia parziale: $ratiosCount esercizi usabili, < $MIN_YEARS)" else ""
        return RuleSignal.CapexIntensity10yAvg(
            signal = signal,
            averagePercent = ratio * 100.0,
            thresholdGreenPercent = GREEN_THRESHOLD * 100.0,
            thresholdYellowPercent = YELLOW_UPPER_BOUND * 100.0,
            observedValue = ratio,
            threshold = THRESHOLD_LABEL,
            rationale = "CapEx/Utile Netto = $pct per $yearLabel$sampleNote.",
        )
    }

    private fun classify(ratio: Double): Signal = when {
        ratio < GREEN_THRESHOLD -> Signal.GREEN
        ratio <= YELLOW_UPPER_BOUND -> Signal.YELLOW
        else -> Signal.RED
    }

    private fun yearKey(date: String?, calendarYear: String?): String? {
        // Prefer ISO date (lexicographic == chronological), fallback to calendar year.
        // For pairing across CashFlow and Income we extract YYYY when a full date is
        // present so the same fiscal year matches even if month/day differ slightly.
        if (date != null && date.length >= 4) return date.substring(0, 4)
        return calendarYear
    }

    private companion object {
        const val GREEN_THRESHOLD = 0.25
        const val YELLOW_UPPER_BOUND = 0.30
        const val MIN_YEARS = 5
        const val THRESHOLD_LABEL = "< 25% (GREEN), 25%-30% (YELLOW), > 30% (RED)"
    }
}
