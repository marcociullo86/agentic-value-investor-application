package com.valueinvesting.webapp.ruleengine.rules

import com.valueinvesting.webapp.ruleengine.RuleSignal
import com.valueinvesting.webapp.ruleengine.Signal
import com.valueinvesting.webapp.ruleengine.ValuationRule
import com.valueinvesting.webapp.ruleengine.calculators.NcavCalculator
import com.valueinvesting.webapp.service.FinancialDataset
import org.springframework.stereotype.Component

// Rule: NCAV_LATEST — Graham Cap.15 Enterprising Investor net-net informational signal.
//
// Computes Net Current Asset Value (NCAV = totalCurrentAssets − totalLiabilities)
// on the latest annual balance sheet and exposes it for the Traffic Light panel.
// The purchase decision (Graham's "< 2/3 NCAV" threshold) lives in NET_NET_RATIO
// (TSK-317), NOT here — this rule is informativo per design.
//
// Signal policy (ADR-029 §2):
//   missing data (balance sheet/shares)  → INDETERMINATE
//   ncavTotal  > 0                       → GREEN  (NCAV calcolato; net-net teoricamente possibile)
//   ncavTotal <= 0                       → RED    (passivo totale > attivo corrente; net-net impossibile)
//
// Edge-case mapping from NcavCalculator.Result.reason → Signal:
//   "missing_balance_sheet"  → INDETERMINATE
//   "missing_shares"         → INDETERMINATE
//   "negative"               → RED              (ncavTotal ≤ 0; deterministic, NOT NOT_CALCULABLE)
//   "ok"                     → GREEN
//
// Deviazione vs altri 13 ruleId su NOT_CALCULABLE:
//   Le rule pre-EP-023 emettono `NOT_CALCULABLE` quando il dataset è vuoto e
//   `INDETERMINATE` quando i singoli campi sono null. ADR-029 §2 unifica entrambi
//   i casi sotto `INDETERMINATE` per NCAV_LATEST (semantica: "dati mancanti", non
//   importa se vuoto o parziale). Adottiamo INDETERMINATE come da ADR. La
//   distinzione NOT_CALCULABLE/INDETERMINATE resta valida per NET_NET_RATIO (§3).
//
// Output typed-native (NOT factory shim):
//   Costruiamo RuleSignal.NcavLatest(...) direttamente. La factory shim
//   (TSK-311 → TSK-312) viene rimossa da TSK-312 e questa nuova rule nasce
//   typed-native fin dalla nascita per non conflittare con TSK-312 in parallelo.
//   I 3 campi legacy `observedValue`/`rationale`/`threshold` restano popolati
//   per la transition window R+1/R+2 (ADR-028 §8); il consumer FE legacy può
//   ancora leggerli mentre EP-021 viaggia attraverso il refactor.
//
// Determinismo / purezza:
//   - nessun I/O, nessun timestamp, nessuna randomness
//   - NcavCalculator è puro (latest-year picker stabile, no clock)
//   - per lo stesso `FinancialDataset` la rule restituisce sempre lo stesso signal
//
// [^src: design_&_architecture/decisions/ADR-029-net-net-stocks-ncav.md §2 + §4]
// [^src: management/kanban/EP-023-net-net-stocks-ncav/US-096-regola-ncav-net-net-be/TSK-316.md §Scope]
// [^src: management/kanban/EP-023-net-net-stocks-ncav/US-096-regola-ncav-net-net-be/US-096.md §Business Rules]
// [^src: wiki/concepts/net-net-stocks.md §Definizione]
// [^src: wiki/runbooks/enterprising-investor-checklist.md §Step 7 — Criterio Net-Net]
@Component
class NcavLatestRule : ValuationRule {

    override val ruleId: String = "NCAV_LATEST"

    override fun evaluate(dataset: FinancialDataset): RuleSignal {
        val result = NcavCalculator.compute(dataset)
        return when (result.reason) {
            "missing_balance_sheet" -> indeterminate(
                rationale = "NCAV non calcolabile: Balance Sheet o voci Current Assets/Total Liabilities mancanti.",
            )
            "missing_shares" -> indeterminate(
                rationale = "NCAV non calcolabile: sharesOutstanding non disponibile (weightedAverageShsOutDil/weightedAverageShsOut entrambi assenti o non positivi).",
            )
            "negative" -> RuleSignal.NcavLatest(
                signal = Signal.RED,
                ncavTotal = result.ncavTotal,
                ncavPerShare = result.ncavPerShare,
                observedValue = result.ncavPerShare,
                threshold = THRESHOLD_LABEL,
                rationale = "NCAV totale negativo o nullo (${"%.0f".format(result.ncavTotal)} USD): passivo totale > attivo corrente. Net-net impossibile a priori.",
            )
            "ok" -> RuleSignal.NcavLatest(
                signal = Signal.GREEN,
                ncavTotal = result.ncavTotal,
                ncavPerShare = result.ncavPerShare,
                observedValue = result.ncavPerShare,
                threshold = THRESHOLD_LABEL,
                rationale = "NCAV totale ${"%.0f".format(result.ncavTotal)} USD, NCAV per azione ${"%.2f".format(result.ncavPerShare)} USD (la decisione di acquisto spetta a NET_NET_RATIO).",
            )
            else -> indeterminate(
                rationale = "NCAV non calcolabile: reason='${result.reason}' non riconosciuta.",
            )
        }
    }

    private fun indeterminate(rationale: String): RuleSignal.NcavLatest = RuleSignal.NcavLatest(
        signal = Signal.INDETERMINATE,
        ncavTotal = null,
        ncavPerShare = null,
        observedValue = null,
        threshold = THRESHOLD_LABEL,
        rationale = rationale,
    )

    private companion object {
        // Etichetta human-readable della soglia, coerente con il pattern degli altri 13 ruleId.
        // NCAV_LATEST è informativo: la "soglia" è semplicemente "NCAV > 0".
        const val THRESHOLD_LABEL = "> 0 (GREEN), ≤ 0 (RED)"
    }
}
