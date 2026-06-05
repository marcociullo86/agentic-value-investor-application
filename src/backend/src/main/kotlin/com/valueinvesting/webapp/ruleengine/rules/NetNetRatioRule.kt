package com.valueinvesting.webapp.ruleengine.rules

import com.valueinvesting.webapp.ruleengine.RuleSignal
import com.valueinvesting.webapp.ruleengine.Signal
import com.valueinvesting.webapp.ruleengine.ValuationRule
import com.valueinvesting.webapp.ruleengine.calculators.NcavCalculator
import com.valueinvesting.webapp.service.FinancialDataset
import org.springframework.stereotype.Component

// Rule: NET_NET_RATIO — Graham Cap.15 Enterprising Investor net-net decisional signal.
//
// Calcola il rapporto `price / NCAV_per_share` sull'ultimo balance sheet annuale e
// confronta con la soglia Graham `2/3` (= 0.6667 con precisione double). Sotto la
// soglia, il prezzo è inferiore ai due terzi del Net Current Asset Value: l'azione
// è "net-net" e offre un margin-of-safety strutturale di liquidazione (~33% sul
// valore di liquidazione conservativo). Sopra la soglia, il titolo non qualifica
// come net-net Graham. Riusa NcavCalculator (TSK-316) per evitare duplicazione
// con NcavLatestRule.
//
// Formula (ADR-029 §1 + §3):
//   ratio = priceLatest / ncavPerShare
//
// Soglia (ADR-029 §3, companion object): THRESHOLD_RATIO = 2.0 / 3.0
//
// Signal policy (ADR-029 §3):
//   ncavPerShare non calcolabile (NcavCalculator missing_*) OR
//   priceLatest == null                              → INDETERMINATE
//   ncavPerShare <= 0 (NcavCalculator "negative")    → NOT_CALCULABLE
//   ratio < 0.6667                                   → GREEN  (opportunità net-net Graham)
//   ratio >= 0.6667                                  → RED    (titolo non net-net)
//
// Edge-case mapping da NcavCalculator.Result.reason → Signal:
//   "missing_balance_sheet"  → INDETERMINATE
//   "missing_shares"         → INDETERMINATE
//   "negative"               → NOT_CALCULABLE  (coerente con ADR-029 §3, distinto da NCAV_LATEST.RED)
//   "ok" + priceLatest null  → INDETERMINATE
//   "ok" + priceLatest ok    → GREEN/RED secondo soglia
//
// Coerenza con NCAV_LATEST (ADR-029 §2 vs §3):
//   - NCAV_LATEST mappa "negative" a RED (segnale informativo: passivo > attivo corrente).
//   - NET_NET_RATIO mappa "negative" a NOT_CALCULABLE (il ratio sarebbe negativo
//     o privo di senso; semantica decisionale: "la rule non è applicabile").
//   La distinzione è esplicita in ADR-029 §3 ed è desiderata.
//
// Determinismo / purezza (PATTERN §11):
//   - nessun I/O, nessun timestamp, nessuna randomness
//   - NcavCalculator è puro (latest-year picker stabile, no clock)
//   - dataset.currentPrice è popolato a monte da AnalyzeTickerService da
//     ProfileDto.price (vedi Pe3yAvgRule/PbLatestRule pattern, EP-010)
//   - per lo stesso FinancialDataset la rule restituisce sempre lo stesso signal
//
// currentPrice via dataset.currentPrice (NOT FmpAdapter):
//   Coerente con Pe3yAvgRule (TSK-079) e PbLatestRule (TSK-081): la rule è pure
//   per contratto ValuationRule. Il fetch ProfileDto è centralizzato da
//   AnalyzeTickerService (ADR-014, EP-010).
//
// Output typed-native (NOT factory shim):
//   Costruiamo RuleSignal.NetNetRatio(...) direttamente. La factory shim
//   (TSK-311 → TSK-312) è stata rimossa; il sotto-tipo nasce typed-native fin
//   dalla nascita, stesso pattern di NcavLatest (TSK-316). I 3 campi legacy
//   `observedValue`/`rationale`/`threshold` restano popolati per la transition
//   window R+1/R+2 (ADR-028 §8).
//
// [^src: design_&_architecture/decisions/ADR-029-net-net-stocks-ncav.md §1, §3, §4]
// [^src: management/kanban/EP-023-net-net-stocks-ncav/US-096-regola-ncav-net-net-be/TSK-317.md §Scope]
// [^src: management/kanban/EP-023-net-net-stocks-ncav/US-096-regola-ncav-net-net-be/US-096.md §Business Rules]
// [^src: wiki/concepts/net-net-stocks.md §Definizione + §Strategia Operativa]
// [^src: wiki/runbooks/enterprising-investor-checklist.md §Step 7 — Criterio Net-Net]
@Component
class NetNetRatioRule : ValuationRule {

    override val ruleId: String = "NET_NET_RATIO"

    override fun evaluate(dataset: FinancialDataset): RuleSignal {
        val result = NcavCalculator.compute(dataset)
        val priceLatest = dataset.currentPrice

        // 1) dati NCAV mancanti → INDETERMINATE (priceLatest può essere null o no — non rileva)
        if (result.reason == "missing_balance_sheet") {
            return indeterminate(
                priceLatest = priceLatest,
                ncavPerShare = null,
                rationale = "Net-Net ratio non calcolabile: Balance Sheet o voci Current Assets/Total Liabilities mancanti.",
            )
        }
        if (result.reason == "missing_shares") {
            return indeterminate(
                priceLatest = priceLatest,
                ncavPerShare = null,
                rationale = "Net-Net ratio non calcolabile: sharesOutstanding non disponibile.",
            )
        }

        // 2) ncavPerShare ≤ 0 (NCAV negativo) → NOT_CALCULABLE
        //    (coerente con NCAV_LATEST RED; il ratio sarebbe negativo o privo di senso)
        if (result.reason == "negative") {
            return RuleSignal.NetNetRatio(
                signal = Signal.NOT_CALCULABLE,
                priceLatest = priceLatest,
                ncavPerShare = result.ncavPerShare,
                ratio = null,
                thresholdRatio = RuleSignal.NetNetRatio.THRESHOLD_RATIO,
                observedValue = null,
                threshold = THRESHOLD_LABEL,
                rationale = "Net-Net ratio non applicabile: NCAV per share non positivo (${"%.2f".format(result.ncavPerShare ?: 0.0)} USD). Il rapporto sarebbe negativo o privo di significato.",
            )
        }

        // 3) reason "ok" da qui in poi — guarding extra per i null (difensivo, non dovrebbe accadere)
        val ncavPerShare = result.ncavPerShare
        if (ncavPerShare == null) {
            return indeterminate(
                priceLatest = priceLatest,
                ncavPerShare = null,
                rationale = "Net-Net ratio non calcolabile: NCAV per share assente nonostante reason='ok'.",
            )
        }

        // 4) priceLatest mancante → INDETERMINATE
        if (priceLatest == null) {
            return indeterminate(
                priceLatest = null,
                ncavPerShare = ncavPerShare,
                rationale = "Net-Net ratio non calcolabile: prezzo corrente non disponibile.",
            )
        }

        // 5) calcolo + soglia Graham 2/3
        val ratio = priceLatest / ncavPerShare
        val signal = if (ratio < THRESHOLD_RATIO) Signal.GREEN else Signal.RED
        val rationale = "Prezzo $%.2f vs NCAV per share $%.2f → ratio %.4f (soglia Graham < %.4f = 2/3)"
            .format(priceLatest, ncavPerShare, ratio, THRESHOLD_RATIO)
        return RuleSignal.NetNetRatio(
            signal = signal,
            priceLatest = priceLatest,
            ncavPerShare = ncavPerShare,
            ratio = ratio,
            thresholdRatio = THRESHOLD_RATIO,
            observedValue = ratio,
            threshold = THRESHOLD_LABEL,
            rationale = rationale,
        )
    }

    private fun indeterminate(
        priceLatest: Double?,
        ncavPerShare: Double?,
        rationale: String,
    ): RuleSignal.NetNetRatio = RuleSignal.NetNetRatio(
        signal = Signal.INDETERMINATE,
        priceLatest = priceLatest,
        ncavPerShare = ncavPerShare,
        ratio = null,
        thresholdRatio = THRESHOLD_RATIO,
        observedValue = null,
        threshold = THRESHOLD_LABEL,
        rationale = rationale,
    )

    private companion object {
        // Soglia Graham Cap.15 verbatim: prezzo < 2/3 × NCAV per share.
        // Alias della costante della data class per leggibilità; il valore canonico
        // vive in `RuleSignal.NetNetRatio.Companion.THRESHOLD_RATIO` (single source
        // of truth — ADR-029 §3 + RuleSignal.kt). Non `const val` perché in Kotlin
        // un `const val` richiede inizializzatore costante a compile-time;
        // l'espressione `2.0 / 3.0` non è una compile-time constant (è un Double
        // operation), quindi qui dichiariamo `val` e referenziamo via JvmStatic
        // sulla companion della data class.
        val THRESHOLD_RATIO: Double = RuleSignal.NetNetRatio.THRESHOLD_RATIO

        // Etichetta human-readable della soglia, coerente con gli altri 13 ruleId
        // (campo legacy `threshold: String` — rimozione a R+3, ADR-028 §8).
        const val THRESHOLD_LABEL = "< 0.6667 = 2/3 (GREEN), ≥ 0.6667 (RED)"
    }
}
