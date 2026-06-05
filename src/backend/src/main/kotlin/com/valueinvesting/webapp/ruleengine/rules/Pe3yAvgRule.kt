package com.valueinvesting.webapp.ruleengine.rules

import com.valueinvesting.webapp.fmp.dto.IncomeStatementDto
import com.valueinvesting.webapp.ruleengine.RuleSignal
import com.valueinvesting.webapp.ruleengine.Signal
import com.valueinvesting.webapp.ruleengine.ValuationRule
import com.valueinvesting.webapp.service.FinancialDataset
import org.springframework.stereotype.Component

// Rule: Graham "P/E Moderato" — current price relative to AVERAGE 3-year EPS.
//
// Thresholds (US-035 Business Rules, TSK-079 §Cosa fare, Graham cap.14 §Criterio 5):
//   pe3yAvg <= 15            -> GREEN
//   pe3yAvg in (15, 20]      -> YELLOW
//   pe3yAvg > 20             -> RED
//   currentPrice == null
//     OR income.size < 3
//     OR fewer than 2 non-null EPS in top-3
//     OR avgEps3y <= 0       -> INDETERMINATE
//   dataset.income.isEmpty() -> NOT_CALCULABLE
//
// Design note — purity:
//   ValuationRule.evaluate MUST be pure and side-effect free (cf. ValuationRule
//   kdoc + ADR-005 Strategy pattern). The rule therefore does NOT inject
//   FmpAdapter — it reads:
//     - currentPrice from `dataset.currentPrice` (populated by
//       AnalyzeTickerService from ProfileDto.price BEFORE evaluateAll())
//     - eps from `dataset.income` (the last 3 fiscal years, latest first).
//   This deviates from the literal wording of TSK-079 §Cosa fare 2 ("recuperare
//   currentPrice da FmpAdapter.getQuote(ticker)") in favour of the binding
//   architectural constraint (rule purity). The data source is logically
//   identical because AnalyzeTickerService funnels ProfileDto.price into
//   FinancialDataset.currentPrice (EP-010 wiring).
//
// Design note — top-N most-recent selection (NOT top-10 like EarningsStability):
//   Graham Criterion 5 uses the AVERAGE EPS over the LAST 3 fiscal years
//   ("media EPS triennale piu' recente"). We sort the income list by date
//   descending (ISO `date` is lex == chrono, fallback `calendarYear`) and pick
//   the first 3. With < 3 records the average is not well defined for the
//   defensive criterion, so we return INDETERMINATE (US-035 AC alignment with
//   "INDETERMINATE != RED").
//
// Design note — null EPS handling inside the 3-year window:
//   PATTERN §7 r.13 + US-004: a null EPS is "missing", never coerced to 0.
//   For the average we compute over non-null values:
//     - 3 non-null EPS -> standard average.
//     - 2 non-null EPS -> partial average (still meaningful, average of 2).
//     - <= 1 non-null EPS -> INDETERMINATE (no statistically meaningful mean).
//   The window length stays 3 to satisfy the "ultimi 3 esercizi" requirement;
//   the count check is on the non-null EPS subset.
//
// Design note — avgEps3y <= 0 -> INDETERMINATE (not RED):
//   When average EPS is negative or zero, the P/E ratio is mathematically not
//   meaningful (a loss-making company has no readable P/E). This is INDETERMINATE
//   rather than RED because the criterion cannot be evaluated, not because the
//   company failed the threshold. Consistent with US-007 "INDETERMINATE != RED".
//
// Design note — metadati strutturati (avgEps3y, pe3yAvg, currentPrice, priceTimestamp)
//   NOT in RuleSignal:
//   TSK-079 §Cosa fare 6 mentions currentPrice / priceTimestamp / avgEps3y /
//   pe3yAvg / thresholdGreen / thresholdYellow as RuleResult fields. The
//   current RuleSignal contract (TSK-012) only exposes `observedValue: Double?`
//   + `rationale: String`. Extending the shape would impact ALL rules
//   (regression risk on the 9 pre-existing ruleId) and is the declared scope
//   of TSK-087 (OpenAPI payload refactor). Here we veicolate:
//     - `observedValue` = pe3yAvg (the primary numeric metric)
//                         OR null for INDETERMINATE / NOT_CALCULABLE
//     - `rationale`     = italian string carrying currentPrice + avgEps3y + pe3yAvg
//
// Design note — priceTimestamp out-of-scope:
//   TSK-079 §Cosa fare 6 + §Note implementative request `priceTimestamp` as
//   metadata. ProfileDto in /stable does NOT expose a timestamp for the quote
//   (only `price`). No upstream source for an audit-grade sampling instant.
//   Skipped here, to be reconsidered if/when a quote endpoint with timestamp
//   is wired into FinancialDataset.
//
// Edge cases (PATTERN §7 r.13 null safety):
//   - dataset.income.isEmpty()                       -> NOT_CALCULABLE
//   - dataset.currentPrice == null                   -> INDETERMINATE
//   - dataset.income.size < 3                        -> INDETERMINATE
//   - top-3 records with < 2 non-null eps            -> INDETERMINATE
//   - avgEps3y <= 0                                  -> INDETERMINATE
//   - otherwise                                      -> classify on pe3yAvg
//
// [^src: management/kanban/EP-010-graham-defensive-completeness/US-035-regola-pe-moderato-graham/TSK-079.md §Cosa fare]
// [^src: wiki/concepts/seven-criteria-defensive-stock-selection.md §Criterio 5 — Rapporto P/E Moderato]
// [^src: wiki/concepts/value-investing-rule-engine.md §Calcolo Valore Intrinseco (RF4)]
@Component
class Pe3yAvgRule : ValuationRule {

    override val ruleId: String = "PE_3Y_AVG"

    override fun evaluate(dataset: FinancialDataset): RuleSignal {
        if (dataset.income.isEmpty()) {
            return RuleSignal.Pe3yAvg(
                signal = Signal.NOT_CALCULABLE,
                pe3yAvg = null,
                thresholdGreen = GREEN_THRESHOLD,
                thresholdYellow = YELLOW_THRESHOLD,
                observedValue = null,
                threshold = THRESHOLD_LABEL,
                rationale = "Income Statement non disponibile: P/E triennale non valutabile.",
            )
        }

        val currentPrice = dataset.currentPrice
        if (currentPrice == null) {
            return RuleSignal.Pe3yAvg(
                signal = Signal.INDETERMINATE,
                pe3yAvg = null,
                thresholdGreen = GREEN_THRESHOLD,
                thresholdYellow = YELLOW_THRESHOLD,
                observedValue = null,
                threshold = THRESHOLD_LABEL,
                rationale = "Prezzo corrente non disponibile: P/E triennale indeterminato.",
            )
        }

        // Sort fiscal years descending (latest first) and take the most recent
        // REQUIRED_YEARS. Year-key extraction mirrors EarningsStabilityRule /
        // CapexIntensityRule: prefer ISO `date` (lex == chrono), fallback to
        // `calendarYear`.
        val sorted: List<IncomeStatementDto> = dataset.income
            .sortedByDescending { it.date ?: it.calendarYear ?: "" }

        if (sorted.size < REQUIRED_YEARS) {
            return RuleSignal.Pe3yAvg(
                signal = Signal.INDETERMINATE,
                pe3yAvg = null,
                thresholdGreen = GREEN_THRESHOLD,
                thresholdYellow = YELLOW_THRESHOLD,
                observedValue = null,
                threshold = THRESHOLD_LABEL,
                rationale = "Serie storica insufficiente: ${sorted.size} esercizi disponibili (richiesti $REQUIRED_YEARS).",
            )
        }

        val window = sorted.take(REQUIRED_YEARS)
        val nonNullEps: List<Double> = window.mapNotNull { it.eps }

        if (nonNullEps.size < MIN_NON_NULL_EPS) {
            return RuleSignal.Pe3yAvg(
                signal = Signal.INDETERMINATE,
                pe3yAvg = null,
                thresholdGreen = GREEN_THRESHOLD,
                thresholdYellow = YELLOW_THRESHOLD,
                observedValue = null,
                threshold = THRESHOLD_LABEL,
                rationale = "EPS disponibili insufficienti nei top-$REQUIRED_YEARS esercizi (${nonNullEps.size}/$REQUIRED_YEARS non-null): P/E triennale indeterminato.",
            )
        }

        val avgEps3y = nonNullEps.average()
        if (avgEps3y <= 0.0) {
            return RuleSignal.Pe3yAvg(
                signal = Signal.INDETERMINATE,
                pe3yAvg = null,
                thresholdGreen = GREEN_THRESHOLD,
                thresholdYellow = YELLOW_THRESHOLD,
                observedValue = null,
                threshold = THRESHOLD_LABEL,
                rationale = "EPS medio triennale non positivo (${"%.2f".format(avgEps3y)}): P/E non significativo.",
            )
        }

        val pe3yAvg = currentPrice / avgEps3y
        val signal = when {
            pe3yAvg <= GREEN_THRESHOLD -> Signal.GREEN
            pe3yAvg <= YELLOW_THRESHOLD -> Signal.YELLOW
            else -> Signal.RED
        }

        // TODO: priceTimestamp out-of-scope (ProfileDto non lo espone in /stable)
        val rationale = "Prezzo \$${"%.2f".format(currentPrice)} / EPS medio 3y \$${"%.2f".format(avgEps3y)} = P/E ${"%.2f".format(pe3yAvg)}"
        return RuleSignal.Pe3yAvg(
            signal = signal,
            pe3yAvg = pe3yAvg,
            thresholdGreen = GREEN_THRESHOLD,
            thresholdYellow = YELLOW_THRESHOLD,
            observedValue = pe3yAvg,
            threshold = THRESHOLD_LABEL,
            rationale = rationale,
        )
    }

    private companion object {
        const val REQUIRED_YEARS = 3
        const val MIN_NON_NULL_EPS = 2
        const val GREEN_THRESHOLD = 15.0
        const val YELLOW_THRESHOLD = 20.0
        const val THRESHOLD_LABEL = "≤ 15 (GREEN), 15-20 (YELLOW), > 20 (RED)"
    }
}
