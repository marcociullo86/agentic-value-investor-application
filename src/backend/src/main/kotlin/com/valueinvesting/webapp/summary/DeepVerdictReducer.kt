package com.valueinvesting.webapp.summary

import com.valueinvesting.webapp.service.LivelloRischio
import com.valueinvesting.webapp.service.VerdictClass
import org.springframework.stereotype.Component

/**
 * Riduce il verdetto Munger (`VerdictClass` cascade + `LivelloRischio`) ai 3
 * segnali rilevanti per il gate del Summary ([DeepVerdict]):
 *
 *   - `livelloRischio = RISCHIO_ESTREMO` → `DeepVerdict.RISCHIO_ESTREMO`
 *     (override gate VI per la regola assoluta US-103 §"Regola assoluta" + ADR-030 §3).
 *     Ha priorita' su tutti gli altri stati Munger.
 *   - `verdettoClasse ∈ {APPROVATO, APPROVATO_PANIC_BUY}` → `DeepVerdict.OK`.
 *   - `verdettoClasse = WATCHLIST` → `DeepVerdict.WATCHLIST`.
 *   - tutti gli altri BOCCIATO_* → `DeepVerdict.WATCHLIST` (degrada il
 *     Summary a WAIT_FOR_SETUP/AVOID a seconda di VI+MoS; non e' MAI un OK
 *     per il gate).
 *
 * Pure-function. Riusabile dal backtest US-105 in modalita' as-of-date.
 *
 * [^src: management/kanban/EP-024-.../US-103-.../US-103.md §"Tabella di mapping"]
 * [^src: design_&_architecture/decisions/ADR-030-decision-layer-vi-ta-summary.md §3]
 * [^src: wiki/concepts/munger-inversion-rag.md §Cascade Logica]
 */
@Component
class DeepVerdictReducer {

    fun reduce(verdettoClasse: VerdictClass, livelloRischio: LivelloRischio): DeepVerdict {
        // Override Munger RISCHIO_ESTREMO sempre vincolante (regola assoluta).
        if (livelloRischio == LivelloRischio.RISCHIO_ESTREMO) {
            return DeepVerdict.RISCHIO_ESTREMO
        }
        return when (verdettoClasse) {
            VerdictClass.APPROVATO,
            VerdictClass.APPROVATO_PANIC_BUY -> DeepVerdict.OK
            VerdictClass.WATCHLIST -> DeepVerdict.WATCHLIST
            VerdictClass.BOCCIATO_NUMERICO,
            VerdictClass.BOCCIATO_QUALITATIVO,
            VerdictClass.BOCCIATO_VALUE_TRAP -> DeepVerdict.WATCHLIST
        }
    }
}
