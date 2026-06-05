package com.valueinvesting.webapp.ruleengine.rules

import com.valueinvesting.webapp.fmp.dto.IncomeStatementDto
import com.valueinvesting.webapp.ruleengine.RuleSignal
import com.valueinvesting.webapp.ruleengine.Signal
import com.valueinvesting.webapp.ruleengine.ValuationRule
import com.valueinvesting.webapp.service.FinancialDataset
import org.springframework.stereotype.Component

// Rule: Graham "Earnings Stability" — 10-year check that the company has
// reported POSITIVE net income in every fiscal year of the last decade.
//
// Thresholds (US-033 Business Rules, TSK-075 §Cosa fare):
//   10/10 anni con netIncome > 0          -> GREEN
//    9/10 anni positivi (1 anno perdita)  -> YELLOW
//   ≤ 8/10 anni positivi (2+ perdite)     -> RED
//   < 10 record disponibili               -> INDETERMINATE
//   dataset.income vuoto                  -> NOT_CALCULABLE
//
// Design note — perché serve esattamente 10 anni:
//   Graham, "The Intelligent Investor" cap.14: la stabilità degli utili è un
//   criterio di solidità storica, non un trend. La soglia di 10 anni è
//   esplicita in US-033 e nella wiki concept (criterio 3). Con < 10 anni di
//   serie non possiamo né confermare né smentire il criterio: ritorniamo
//   INDETERMINATE (US-033 AC verbatim, allineato a US-007 "INDETERMINATE
//   ≠ RED"). Una serie vuota è invece NOT_CALCULABLE (analogo a RoeRule
//   "nessun valore di ROE disponibile").
//
// Design note — netIncome == null vs. netIncome <= 0:
//   PATTERN §7 r.13 + US-004 "campi mancanti = assenti, mai 0": un netIncome
//   null NON viene coerced a 0 né conta come "positivo". Per il conteggio dei
//   yearsPositive un anno con netIncome == null è trattato come "anno non
//   positivo" — equivalente in label a "perdita o dato mancante". Il
//   rationale distingue i due casi quando li enumera (anno-perdita vs.
//   anno-NA). Questa è la stessa convenzione adottata dal listato in
//   raw/agent.py §earnings_stability_check (Graham defensive checklist).
//
// Design note — metadati strutturati (yearsPositive, lossYears) NON in RuleSignal:
//   TSK-075 §Cosa fare 4 menziona yearsPositive / yearsAvailable / lossYears
//   come campi del RuleResult. Tuttavia il contratto attuale `RuleSignal`
//   (TSK-012) espone solo `observedValue: Double?` + `rationale: String`.
//   Estendere lo shape impatterebbe TUTTE le rule (regressione potenziale
//   sui 8 ruleId preesistenti) ed è scope dichiarato di TSK-087 (OpenAPI
//   refactor del payload). Qui veicoliamo:
//     - `observedValue` = numero di anni positivi su 10 (Double, es. 9.0)
//                         oppure null per INDETERMINATE / NOT_CALCULABLE
//     - `rationale`     = stringa italiana che enumera count + anni di
//                         perdita / NA
//   così l'AC "ruleId presente nel payload" è soddisfatto e il dato di
//   dettaglio resta visibile al frontend senza rompere il contratto.
//
// Design note — selezione dei "primi 10 anni":
//   Ordiniamo per data fiscale decrescente (preferenza `date` ISO che è
//   lessicograficamente == cronologicamente, fallback `calendarYear`).
//   Prendiamo i primi 10. Se la lista è di 12 anni, gli ultimi 2 anni più
//   vecchi vengono ignorati: il criterio è "ultimi 10 esercizi", non
//   "qualsiasi 10 anni positivi della storia". Stesso year-key extraction
//   pattern di CapexIntensityRule.yearKey().
//
// Edge cases (PATTERN §7 r.13 null safety):
//   - dataset.income.isEmpty()                       -> NOT_CALCULABLE
//   - dataset.income.size < 10                       -> INDETERMINATE
//   - dataset.income.size >= 10                      -> classify on top-10
//
// [^src: management/kanban/EP-010-graham-defensive-completeness/US-033-regola-stabilita-utili-graham/TSK-075.md §Cosa fare]
// [^src: wiki/concepts/seven-criteria-defensive-stock-selection.md §Criterio 3 — Stabilita' degli Utili]
// [^src: wiki/concepts/value-investing-rule-engine.md §Redditività]
@Component
class EarningsStabilityRule : ValuationRule {

    override val ruleId: String = "EARNINGS_STABILITY_10Y"

    override fun evaluate(dataset: FinancialDataset): RuleSignal {
        if (dataset.income.isEmpty()) {
            return RuleSignal.EarningsStability10y(
                signal = Signal.NOT_CALCULABLE,
                yearsPositive = 0,
                yearsAvailable = 0,
                lossYears = emptyList(),
                observedValue = null,
                threshold = THRESHOLD_LABEL,
                rationale = "Income Statement non disponibile: stabilità degli utili non valutabile.",
            )
        }

        // Sort fiscal years descending (latest first) and take the most recent
        // REQUIRED_YEARS. Year-key extraction mirrors CapexIntensityRule.yearKey():
        // prefer ISO `date` (lex == chrono), fallback to `calendarYear`.
        val sorted: List<IncomeStatementDto> = dataset.income
            .sortedByDescending { it.date ?: it.calendarYear ?: "" }

        if (sorted.size < REQUIRED_YEARS) {
            return RuleSignal.EarningsStability10y(
                signal = Signal.INDETERMINATE,
                yearsPositive = 0,
                yearsAvailable = sorted.size,
                lossYears = emptyList(),
                observedValue = null,
                threshold = THRESHOLD_LABEL,
                rationale = "Serie storica insufficiente: ${sorted.size} esercizi disponibili (richiesti $REQUIRED_YEARS).",
            )
        }

        val window = sorted.take(REQUIRED_YEARS)

        // Partition the window into positive years vs. non-positive ones.
        // Non-positive includes BOTH netIncome <= 0 AND netIncome == null
        // (PATTERN §7 r.13: null is "missing", not 0 — but it's also NOT a
        // confirmed positive year, so it counts against the stability check).
        val lossOrNaLabels: List<String> = window.mapNotNull { row ->
            val ni = row.netIncome
            if (ni != null && ni > 0.0) null
            else {
                val year = yearLabel(row)
                if (ni == null) "$year (n/d)" else year
            }
        }

        // Typed payload: lossYears = list of Int (calendar years) where netIncome
        // <= 0 OR null. Strip the "(n/d)" suffix from the label to get a clean
        // year string, then parse to Int. Labels that aren't parseable (e.g.
        // "n/d") are skipped — they couldn't be a meaningful int anyway.
        val lossYearsTyped: List<Int> = lossOrNaLabels.mapNotNull { label ->
            label.substringBefore(" ").toIntOrNull()
        }

        val yearsPositive = REQUIRED_YEARS - lossOrNaLabels.size
        val observedValue = yearsPositive.toDouble()

        val signal = when {
            yearsPositive == REQUIRED_YEARS -> Signal.GREEN
            yearsPositive == REQUIRED_YEARS - 1 -> Signal.YELLOW
            else -> Signal.RED
        }

        val rationale = buildRationale(yearsPositive, lossOrNaLabels)
        return RuleSignal.EarningsStability10y(
            signal = signal,
            yearsPositive = yearsPositive,
            yearsAvailable = REQUIRED_YEARS,
            lossYears = lossYearsTyped,
            observedValue = observedValue,
            threshold = THRESHOLD_LABEL,
            rationale = rationale,
        )
    }

    private fun yearLabel(row: IncomeStatementDto): String {
        // Prefer 4-char YYYY from ISO date; fallback to calendarYear; final fallback "n/d".
        val date = row.date
        if (date != null && date.length >= 4) return date.substring(0, 4)
        return row.calendarYear ?: "n/d"
    }

    private fun buildRationale(yearsPositive: Int, lossOrNaLabels: List<String>): String {
        val head = "$yearsPositive/$REQUIRED_YEARS esercizi con utili positivi."
        if (lossOrNaLabels.isEmpty()) return head
        val joiner = if (lossOrNaLabels.size == 1) "Anno di perdita" else "Anni di perdita"
        return "$head $joiner: ${lossOrNaLabels.joinToString(", ")}."
    }

    private companion object {
        const val REQUIRED_YEARS = 10
        const val THRESHOLD_LABEL = "10/10 (GREEN), 9/10 (YELLOW), ≤ 8/10 (RED)"
    }
}
