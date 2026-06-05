package com.valueinvesting.webapp.ruleengine.rules

import com.valueinvesting.webapp.fmp.dto.IncomeStatementDto
import com.valueinvesting.webapp.ruleengine.RuleSignal
import com.valueinvesting.webapp.ruleengine.Signal
import com.valueinvesting.webapp.ruleengine.ValuationRule
import com.valueinvesting.webapp.service.FinancialDataset
import org.springframework.stereotype.Component

// Rule: Graham "Earnings Growth" — 10-year EPS growth check using triennial
// averages at the endpoints (anni 1-3 vs. anni 8-10) to attenuate cyclicality.
//
// Thresholds (US-034 Business Rules, TSK-077 §Cosa fare):
//   growth >= +33%                  -> GREEN
//   0% <= growth < 33%              -> YELLOW
//   growth < 0%                     -> RED
//   serie < 10 anni / triennale-iniziale con <2 valori non-null
//     / triennale-finale con <2 valori non-null
//     / avgEpsInitial <= 0          -> INDETERMINATE
//   dataset.income vuoto            -> NOT_CALCULABLE
//
// Design note — perché media triennale agli endpoint:
//   Graham, "The Intelligent Investor" cap.14 (Criterio 5 della checklist
//   defensive, in questa wiki §Criterio 3 — Crescita degli Utili) chiede
//   crescita del +33% sui 10 anni, ma per attenuare ciclicità e one-off
//   confronta MEDIA degli ultimi 3 con MEDIA dei primi 3. Implementiamo
//   verbatim: anni 1-2-3 (più vecchi) come baseline, anni 8-9-10 (più recenti)
//   come finale, growth = (final - initial) / initial.
//   [^src: US-034 §Business Rules]
//
// Design note — ordering ASC vs DESC:
//   EarningsStabilityRule (TSK-075) ordina DESC e prende top-10 più recenti.
//   Qui invece servono "i 10 più recenti MA con indici espliciti 1..10 dal più
//   vecchio". Ordiniamo quindi DESC, tagliamo a top-10, e poi REVERSED → ASC.
//   Risultato: window[0..2] = anni 1-2-3 (più vecchi, baseline), window[7..9] =
//   anni 8-9-10 (più recenti, finale). Year-key extraction identica a
//   EarningsStabilityRule (ISO `date` lex==chrono, fallback `calendarYear`).
//
// Design note — gestione null in media triennale:
//   PATTERN §7 r.13 "campi mancanti = assenti, mai 0": un eps == null NON
//   viene coerced a 0. Lo escludiamo dalla media. TSK-077 §Note implementative
//   ammette esplicitamente "se la triennale è calcolabile su 2 soli anni
//   procedere ugualmente"; se invece scendiamo a <2 valori non-null su uno dei
//   due endpoint, l'aggregato non è significativo → INDETERMINATE.
//
// Design note — avgEpsInitial <= 0 → INDETERMINATE (non RED):
//   Se la baseline è ≤ 0 (company in perdita 10 anni fa), la growth-% perde
//   significato matematico: una company che passa da -$1 a +$2 ha growth=-300%
//   pur essendo turnaround positivo. Convenzione US-034: il criterio non è
//   leggibile in questo schema, quindi INDETERMINATE (NOT RED, allineato a
//   US-007 "INDETERMINATE ≠ RED"). [^src: TSK-077 AC]
//
// Design note — metadati strutturati NON in RuleSignal:
//   TSK-077 §Cosa fare 6 menziona avgEpsInitial / avgEpsFinal / growthPct /
//   thresholdPct come campi del RuleResult. Il contratto attuale `RuleSignal`
//   (TSK-012, stabile in 10 rule pre-esistenti) espone solo
//   `observedValue: Double?` + `rationale: String`. Estenderlo impatterebbe
//   TUTTE le rule (regressione) ed è scope di TSK-087 (già done, niente
//   breaking schema). Qui veicoliamo:
//     - `observedValue` = growth ratio (Double, es. 0.40 per +40%) oppure
//                         null su INDETERMINATE / NOT_CALCULABLE
//     - `rationale`     = stringa italiana con avgEpsInitial, avgEpsFinal,
//                         growth-% formattata
//   Stesso pattern di EarningsStabilityRule (TSK-075).
//
// Edge cases (PATTERN §7 r.13 null safety):
//   - dataset.income.isEmpty()                              -> NOT_CALCULABLE
//   - dataset.income.size < 10                              -> INDETERMINATE
//   - triennale-iniziale o finale con <2 valori non-null    -> INDETERMINATE
//   - avgEpsInitial <= 0                                    -> INDETERMINATE
//   - tutti gli altri casi                                  -> classify on growth
//
// [^src: management/kanban/EP-010-graham-defensive-completeness/US-034-regola-crescita-eps-graham/TSK-077.md §Cosa fare]
// [^src: wiki/concepts/seven-criteria-defensive-stock-selection.md §Criterio 5 — Crescita degli Utili]
// [^src: wiki/concepts/value-investing-rule-engine.md §Redditività]
@Component
class EpsGrowthRule : ValuationRule {

    override val ruleId: String = "EPS_GROWTH_10Y"

    override fun evaluate(dataset: FinancialDataset): RuleSignal {
        if (dataset.income.isEmpty()) {
            return RuleSignal.EpsGrowth10y(
                signal = Signal.NOT_CALCULABLE,
                cagrPercent = null,
                thresholdPercent = GREEN_THRESHOLD * 100.0,
                epsStart = null,
                epsEnd = null,
                yearStart = null,
                yearEnd = null,
                observedValue = null,
                threshold = THRESHOLD_LABEL,
                rationale = "Income Statement non disponibile: crescita EPS non valutabile.",
            )
        }

        // Sort DESC by fiscal date, take top-10 most recent, then reverse to ASC
        // so window[0..2] are the OLDEST (baseline / anni 1-2-3) and window[7..9]
        // are the NEWEST (finale / anni 8-9-10). Year-key extraction mirrors
        // EarningsStabilityRule.yearLabel().
        val sortedDesc: List<IncomeStatementDto> = dataset.income
            .sortedByDescending { it.date ?: it.calendarYear ?: "" }

        if (sortedDesc.size < REQUIRED_YEARS) {
            return RuleSignal.EpsGrowth10y(
                signal = Signal.INDETERMINATE,
                cagrPercent = null,
                thresholdPercent = GREEN_THRESHOLD * 100.0,
                epsStart = null,
                epsEnd = null,
                yearStart = null,
                yearEnd = null,
                observedValue = null,
                threshold = THRESHOLD_LABEL,
                rationale = "Serie storica insufficiente: ${sortedDesc.size} esercizi disponibili (richiesti $REQUIRED_YEARS).",
            )
        }

        val window: List<IncomeStatementDto> = sortedDesc.take(REQUIRED_YEARS).reversed()

        // anni 1-2-3 (più vecchi, baseline)
        val initialEps: List<Double> = window.subList(0, 3).mapNotNull { it.eps }
        // anni 8-9-10 (più recenti, finale)
        val finalEps: List<Double> = window.subList(7, 10).mapNotNull { it.eps }

        // Year endpoints for typed payload (parse YYYY from ISO date, fallback to
        // calendarYear). Best-effort: null if unparseable.
        val yearStart: Int? = extractYear(window[0])
        val yearEnd: Int? = extractYear(window[9])

        if (initialEps.size < MIN_NON_NULL_PER_ENDPOINT) {
            return RuleSignal.EpsGrowth10y(
                signal = Signal.INDETERMINATE,
                cagrPercent = null,
                thresholdPercent = GREEN_THRESHOLD * 100.0,
                epsStart = null,
                epsEnd = null,
                yearStart = yearStart,
                yearEnd = yearEnd,
                observedValue = null,
                threshold = THRESHOLD_LABEL,
                rationale = "Dati EPS insufficienti nella triennale iniziale (anni 1-3): ${initialEps.size} valori non-null su 3 (minimo $MIN_NON_NULL_PER_ENDPOINT).",
            )
        }
        if (finalEps.size < MIN_NON_NULL_PER_ENDPOINT) {
            return RuleSignal.EpsGrowth10y(
                signal = Signal.INDETERMINATE,
                cagrPercent = null,
                thresholdPercent = GREEN_THRESHOLD * 100.0,
                epsStart = initialEps.average(),
                epsEnd = null,
                yearStart = yearStart,
                yearEnd = yearEnd,
                observedValue = null,
                threshold = THRESHOLD_LABEL,
                rationale = "Dati EPS insufficienti nella triennale finale (anni 8-10): ${finalEps.size} valori non-null su 3 (minimo $MIN_NON_NULL_PER_ENDPOINT).",
            )
        }

        val avgEpsInitial = initialEps.average()
        val avgEpsFinal = finalEps.average()

        if (avgEpsInitial <= 0.0) {
            return RuleSignal.EpsGrowth10y(
                signal = Signal.INDETERMINATE,
                cagrPercent = null,
                thresholdPercent = GREEN_THRESHOLD * 100.0,
                epsStart = avgEpsInitial,
                epsEnd = avgEpsFinal,
                yearStart = yearStart,
                yearEnd = yearEnd,
                observedValue = null,
                threshold = THRESHOLD_LABEL,
                rationale = "EPS medio iniziale non significativo (${formatEps(avgEpsInitial)}): crescita non leggibile su baseline ≤ 0.",
            )
        }

        val growth = (avgEpsFinal - avgEpsInitial) / avgEpsInitial

        val signal = when {
            growth >= GREEN_THRESHOLD -> Signal.GREEN
            growth >= YELLOW_THRESHOLD -> Signal.YELLOW
            else -> Signal.RED
        }

        val rationale = "EPS medio anni 1-3: ${formatEps(avgEpsInitial)} → anni 8-10: ${formatEps(avgEpsFinal)}. Crescita: ${formatGrowth(growth)}."

        return RuleSignal.EpsGrowth10y(
            signal = signal,
            cagrPercent = growth * 100.0,
            thresholdPercent = GREEN_THRESHOLD * 100.0,
            epsStart = avgEpsInitial,
            epsEnd = avgEpsFinal,
            yearStart = yearStart,
            yearEnd = yearEnd,
            observedValue = growth,
            threshold = THRESHOLD_LABEL,
            rationale = rationale,
        )
    }

    private fun extractYear(row: IncomeStatementDto): Int? {
        val date = row.date
        if (date != null && date.length >= 4) {
            date.substring(0, 4).toIntOrNull()?.let { return it }
        }
        return row.calendarYear?.toIntOrNull()
    }

    private fun formatEps(value: Double): String = "$%.2f".format(value)

    private fun formatGrowth(value: Double): String {
        val pct = value * 100
        return if (pct >= 0) "+%.1f%%".format(pct) else "%.1f%%".format(pct)
    }

    private companion object {
        const val REQUIRED_YEARS = 10
        const val MIN_NON_NULL_PER_ENDPOINT = 2
        const val GREEN_THRESHOLD = 0.33
        const val YELLOW_THRESHOLD = 0.0
        const val THRESHOLD_LABEL = "≥ +33% (GREEN), 0%-33% (YELLOW), < 0% (RED)"
    }
}
