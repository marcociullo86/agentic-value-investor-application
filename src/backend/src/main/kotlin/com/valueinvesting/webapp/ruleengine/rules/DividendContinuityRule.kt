package com.valueinvesting.webapp.ruleengine.rules

import com.valueinvesting.webapp.fmp.dto.DividendRecord
import com.valueinvesting.webapp.ruleengine.RuleSignal
import com.valueinvesting.webapp.ruleengine.Signal
import com.valueinvesting.webapp.ruleengine.ValuationRule
import com.valueinvesting.webapp.service.FinancialDataset
import org.springframework.stereotype.Component
import java.time.LocalDate

// Rule: Graham "Continuita' dei Dividendi" (Criterio 4) — verifica che la societa'
// abbia distribuito dividendi per ALMENO 20 anni consecutivi a partire dall'anno
// piu' recente.
//
// Thresholds (US-037 Business Rules + TSK-085 §Scope 2):
//   consecutiveYears >= 20                                 -> GREEN
//   consecutiveYears in 15..19  (serie >= 20y disponibili) -> YELLOW
//   consecutiveYears < 15       (serie >= 20y disponibili) -> RED
//   dataset.dividends.isEmpty()                            -> INDETERMINATE
//   serie totale (maxYear - minYear + 1) < 20              -> INDETERMINATE
//
// Design note — INDETERMINATE vs NOT_CALCULABLE su lista vuota:
//   La spec TSK-085 §Scope 2(a) discute la scelta: una lista vuota di dividendi
//   puo' significare (i) ticker quotato da meno di 20 anni, (ii) growth stock
//   che non paga dividendi, (iii) FMP non ha coverage. Nessuno dei tre e' un
//   "fail Graham" -> safest = INDETERMINATE (coerente con US-007 "INDETERMINATE
//   != RED" e con PATTERN §7 r.13 "missing data != threshold violation").
//   NOT_CALCULABLE e' riservato ai casi in cui la rule non e' proprio applicabile
//   (es. EarningsStabilityRule su income vuoto: nessun dato finanziario tout
//   court). Qui i dati ci sono — sono i dividendi che mancano. Una rule che
//   verifica un comportamento di payout su una serie vuota non puo' concludere,
//   ma neanche e' "non applicabile". -> INDETERMINATE.
//
// Design note — purity:
//   ValuationRule.evaluate MUST be pure and side-effect free. La rule NON
//   inietta FmpAdapter: legge `dataset.dividends` popolato a monte da
//   AnalyzeTickerService.fetchDividendsWithFallback (TSK-085 §Scope 1).
//
// Design note — parsing date:
//   DividendRecord.date e' String? nel formato ISO `yyyy-MM-dd` (vedi DTO
//   kdoc). Parsing demandato al consumer (TSK-083 design intent). Record con
//   date null o non parseable sono SKIPPATI (PATTERN §7 r.13). Se TUTTI i
//   record hanno date non parseable -> INDETERMINATE (lista effettiva vuota).
//
// Design note — "anni consecutivi":
//   1. Raggruppa i record per anno solare estratto da `LocalDate.parse(date).year`.
//   2. Identifica `mostRecentYear` = max degli anni presenti.
//   3. Scorre all'indietro da mostRecentYear: incrementa `consecutiveYears`
//      finche' l'anno precedente e' presente nel grouping. Stop al primo gap.
//   4. La logica e' "almeno 1 pagamento per anno" — frequenze (Quarterly /
//      Annual) NON sono distinte. Una azienda quarterly con un pagamento
//      saltato ma altri tre nello stesso anno = anno ok.
//
// Design note — "serie totale < 20":
//   Se il range (maxYear - minYear + 1) < 20, anche se i `consecutiveYears`
//   tecnicamente coprono tutta la serie, NON possiamo dichiarare RED perche'
//   non sappiamo se la societa' ha pagato anche prima (es. ticker quotato da
//   12 anni con 12 anni consecutivi -> non e' RED, e' INDETERMINATE — non
//   abbiamo i dati pre-IPO). Conferma esplicita in TSK-085 §Scope 2(f) e
//   US-037 Business Rules: "Ticker quotato da < 20y -> INDETERMINATE".
//   Discriminante: l'INDETERMINATE viene returnato PRIMA del classify
//   GREEN/YELLOW/RED, quindi anche un ticker con 12y consecutivi finisce
//   INDETERMINATE (non YELLOW).
//
// Design note — metadati strutturati (firstDividendDate, lastDividendAmount,
//   lastDividendDate) NOT in RuleSignal:
//   TSK-085 §Cosa fare 5 menziona questi campi come parte del "RuleResult".
//   Il contratto attuale `RuleSignal` (TSK-012) espone solo `observedValue`
//   + `rationale`. Estensione fuori scope (TSK-087 payload refactor). Qui
//   veicoliamo:
//     - `observedValue` = consecutiveYears (Double, es. 20.0)
//                         null per INDETERMINATE
//     - `rationale`     = stringa italiana con firstDividendDate /
//                         lastDividendAmount / lastDividendDate enumerati
//
// Edge cases:
//   - dataset.dividends.isEmpty()                            -> INDETERMINATE
//   - tutti i record con date null o non parseable           -> INDETERMINATE
//   - serie totale < 20 anni                                 -> INDETERMINATE
//   - serie >= 20 e consecutiveYears >= 20                   -> GREEN
//   - serie >= 20 e consecutiveYears in 15..19               -> YELLOW
//   - serie >= 20 e consecutiveYears < 15                    -> RED
//
// [^src: management/kanban/EP-010-graham-defensive-completeness/US-037-regola-continuita-dividendi-graham/TSK-085.md §Cosa fare]
// [^src: wiki/concepts/seven-criteria-defensive-stock-selection.md §Criterio 4 — Regolarita' dei Dividendi]
// [^src: wiki/runbooks/defensive-investor-checklist.md §Step 4 — Continuita' dividendi]
@Component
class DividendContinuityRule : ValuationRule {

    override val ruleId: String = "DIVIDEND_CONTINUITY_20Y"

    override fun evaluate(dataset: FinancialDataset): RuleSignal {
        if (dataset.dividends.isEmpty()) {
            return RuleSignal.DividendContinuity20y(
                signal = Signal.INDETERMINATE,
                consecutiveYears = null,
                thresholdYears = REQUIRED_YEARS,
                observedValue = null,
                threshold = THRESHOLD_LABEL,
                rationale = "Serie storica dividendi non disponibile: continuita' a 20 anni indeterminata.",
            )
        }

        // Parse each record into (LocalDate, DividendRecord) skipping unparseable
        // dates (PATTERN §7 r.13: null/malformed != 0, skip silently).
        val parsed: List<Pair<LocalDate, DividendRecord>> = dataset.dividends
            .mapNotNull { rec ->
                val d = rec.date ?: return@mapNotNull null
                runCatching { LocalDate.parse(d) }.getOrNull()?.let { it to rec }
            }

        if (parsed.isEmpty()) {
            return RuleSignal.DividendContinuity20y(
                signal = Signal.INDETERMINATE,
                consecutiveYears = null,
                thresholdYears = REQUIRED_YEARS,
                observedValue = null,
                threshold = THRESHOLD_LABEL,
                rationale = "Nessuna data dividendo parseable: continuita' a 20 anni indeterminata.",
            )
        }

        // Group by calendar year and compute consecutive-year streak from the
        // most-recent year backwards.
        val recordsByYear: Map<Int, List<Pair<LocalDate, DividendRecord>>> =
            parsed.groupBy { it.first.year }
        val years: Set<Int> = recordsByYear.keys
        val mostRecentYear = years.max()
        val oldestYear = years.min()
        val totalSpanYears = mostRecentYear - oldestYear + 1

        var consecutiveYears = 0
        var cursor = mostRecentYear
        while (cursor in years) {
            consecutiveYears++
            cursor--
        }

        // Metadata for rationale (TSK-085 §Cosa fare 5).
        val firstDividendDate: LocalDate = parsed.minOf { it.first }
        val mostRecent: Pair<LocalDate, DividendRecord> = parsed.maxBy { it.first }
        val lastDividendDate: LocalDate = mostRecent.first
        val lastDividendAmount: Double? = mostRecent.second.dividend
            ?: mostRecent.second.adjDividend

        // "Ticker quotato da < 20y" guard: if the total observed span is shorter
        // than 20y we cannot confirm Graham criterion 4 even with a perfect
        // streak inside the window. (TSK-085 §Scope 2(f) + US-037 Business Rules.)
        if (totalSpanYears < REQUIRED_YEARS) {
            return RuleSignal.DividendContinuity20y(
                signal = Signal.INDETERMINATE,
                consecutiveYears = consecutiveYears,
                thresholdYears = REQUIRED_YEARS,
                observedValue = consecutiveYears.toDouble(),
                threshold = THRESHOLD_LABEL,
                rationale = buildShortSeriesRationale(
                    consecutiveYears = consecutiveYears,
                    totalSpanYears = totalSpanYears,
                    firstDividendDate = firstDividendDate,
                    lastDividendDate = lastDividendDate,
                    lastDividendAmount = lastDividendAmount,
                ),
            )
        }

        val signal = when {
            consecutiveYears >= REQUIRED_YEARS -> Signal.GREEN
            consecutiveYears >= YELLOW_THRESHOLD -> Signal.YELLOW
            else -> Signal.RED
        }

        return RuleSignal.DividendContinuity20y(
            signal = signal,
            consecutiveYears = consecutiveYears,
            thresholdYears = REQUIRED_YEARS,
            observedValue = consecutiveYears.toDouble(),
            threshold = THRESHOLD_LABEL,
            rationale = buildRationale(
                consecutiveYears = consecutiveYears,
                mostRecentYear = mostRecentYear,
                firstDividendDate = firstDividendDate,
                lastDividendDate = lastDividendDate,
                lastDividendAmount = lastDividendAmount,
            ),
        )
    }

    private fun buildRationale(
        consecutiveYears: Int,
        mostRecentYear: Int,
        firstDividendDate: LocalDate,
        lastDividendDate: LocalDate,
        lastDividendAmount: Double?,
    ): String {
        val streakStartYear = mostRecentYear - consecutiveYears + 1
        val head = "$consecutiveYears anni consecutivi di dividendi ($streakStartYear-$mostRecentYear)."
        val firstPart = "Primo dividendo: $firstDividendDate."
        val lastPart = if (lastDividendAmount != null) {
            "Ultimo: \$${"%.4f".format(lastDividendAmount)} il $lastDividendDate."
        } else {
            "Ultimo: $lastDividendDate (importo n/d)."
        }
        return "$head $firstPart $lastPart"
    }

    private fun buildShortSeriesRationale(
        consecutiveYears: Int,
        totalSpanYears: Int,
        firstDividendDate: LocalDate,
        lastDividendDate: LocalDate,
        lastDividendAmount: Double?,
    ): String {
        val head = "Serie storica dividendi copre solo $totalSpanYears anni " +
            "(richiesti $REQUIRED_YEARS): continuita' non confermabile."
        val streakPart = "Anni consecutivi osservati: $consecutiveYears."
        val firstPart = "Primo dividendo: $firstDividendDate."
        val lastPart = if (lastDividendAmount != null) {
            "Ultimo: \$${"%.4f".format(lastDividendAmount)} il $lastDividendDate."
        } else {
            "Ultimo: $lastDividendDate (importo n/d)."
        }
        return "$head $streakPart $firstPart $lastPart"
    }

    private companion object {
        const val REQUIRED_YEARS = 20
        const val YELLOW_THRESHOLD = 15
        const val THRESHOLD_LABEL = "20 anni consecutivi (GREEN), 15-19 (YELLOW), < 15 (RED)"
    }
}
