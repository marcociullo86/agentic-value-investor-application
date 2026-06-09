package com.valueinvesting.webapp.summary

import com.valueinvesting.webapp.api.model.EntryTimingVerdict
import com.valueinvesting.webapp.ruleengine.Signal
import org.springframework.stereotype.Component

/**
 * Gate VI hardcoded + tabella di mapping deterministica del Riepilogo
 * (US-103 §"Tabella di mapping (gate VI hardcoded)", ADR-030 §3+§5).
 *
 * **Mai LLM**: il `summaryVerdict` e' SEMPRE prodotto da questa funzione pura
 * a partire dai 4 verdetti tipati in input.
 *
 * Regole assolute (US-103 §"Regola assoluta"):
 *
 *  1. **VI RED → mai ENTER_NOW.** Un titolo con `viVerdict = RED_DOMINANT` esce
 *     come `AVOID` indipendentemente da TA o Deep. La TA NON puo' promuovere
 *     un VI-negativo a ENTER_NOW.
 *  2. **Munger RISCHIO_ESTREMO overrides.** Se `deepVerdict = RISCHIO_ESTREMO`,
 *     il Summary degrada a `AVOID` anche con VI GREEN_DOMINANT + TA favorevole.
 *  3. **VI INDETERMINATE_DOMINANT** → `INSUFFICIENT_DATA` (≥ 1/3 ruleId
 *     INDETERMINATE/NOT_CALCULABLE).
 *
 * Pattern dichiarativo (MatchRule + lookup, no if/else annidati > 1 livello —
 * stile coerente con `EntryTimingAdvisor.MAPPING_TABLE`, ADR-030 §5).
 *
 * Pure-function: niente I/O, niente persistenza, niente LLM. Riusabile dal
 * backtest US-105 in modalita' "as-of date" senza side-effects.
 *
 * [^src: management/kanban/EP-024-.../US-103-.../US-103.md §"Tabella di mapping"]
 * [^src: design_&_architecture/decisions/ADR-030-decision-layer-vi-ta-summary.md §3, §5]
 * [^src: wiki/syntheses/ta-vs-vi-decision-layer.md §"La regola sequenziale"]
 * [^src: wiki/concepts/munger-inversion-rag.md §Cascade Logica]
 */
@Component
class SummaryVerdictAggregator {

    /**
     * Input strutturati al gate. `mosSignal` mappa GREEN/YELLOW/RED del Margin
     * of Safety (EP-007 / MarginOfSafetyEvaluator). `deepVerdict` puo' essere
     * null se la Deep Analysis non e' stata indicizzata (`deepAnalysisStatus`
     * = NOT_INDEXED) — il gate continua a funzionare basandosi su VI + TA.
     * `taVerdict` puo' essere null se la TA e' indisponibile (FMP down).
     */
    data class Input(
        val viVerdict: ViVerdict,
        val mosSignal: Signal,
        val deepVerdict: DeepVerdict?,
        val taVerdict: EntryTimingVerdict?,
    )

    /**
     * Tabella di mapping VERBATIM da US-103 §"Tabella di mapping (gate VI
     * hardcoded)". L'ordine conta: la prima riga che matcha vince. Le righe
     * sono mutualmente esclusive nelle dimensioni rilevanti; il CATCH_ALL
     * chiude i casi non altrimenti codificati a `WAIT_FOR_SETUP` difensivo.
     */
    fun aggregate(input: Input): SummaryVerdict {
        // ---- Regola 3: INDETERMINATE_DOMINANT → INSUFFICIENT_DATA ---------
        if (input.viVerdict == ViVerdict.INDETERMINATE_DOMINANT) {
            return SummaryVerdict.INSUFFICIENT_DATA
        }

        // ---- Regola 1: VI RED → AVOID (gate VI primario, hardcoded) -------
        if (input.viVerdict == ViVerdict.RED_DOMINANT) {
            return SummaryVerdict.AVOID
        }

        // ---- Regola 2: Munger RISCHIO_ESTREMO overrides → AVOID -----------
        if (input.deepVerdict == DeepVerdict.RISCHIO_ESTREMO) {
            return SummaryVerdict.AVOID
        }

        // ---- Lookup tabella ------------------------------------------------
        val outcome = MAPPING_TABLE.firstOrNull { it.matches(input) }
        return outcome?.verdict ?: SummaryVerdict.WAIT_FOR_SETUP
    }

    // ------------------------------------------------------------------------
    // Tabella di mapping DICHIARATIVA
    // ------------------------------------------------------------------------

    private data class MatchRule(
        val viVerdicts: Set<ViVerdict>,
        val mosSignals: Set<Signal>,
        /** Insieme dei `DeepVerdict?` ammessi — `null` rappresenta "Deep non disponibile". */
        val deepVerdicts: Set<DeepVerdict?>,
        /** Insieme degli `EntryTimingVerdict?` ammessi — `null` rappresenta "TA indisponibile". */
        val taVerdicts: Set<EntryTimingVerdict?>,
        val verdict: SummaryVerdict,
    ) {
        fun matches(input: SummaryVerdictAggregator.Input): Boolean =
            input.viVerdict in viVerdicts &&
                input.mosSignal in mosSignals &&
                input.deepVerdict in deepVerdicts &&
                input.taVerdict in taVerdicts
    }

    private companion object {
        // Set di comodita' — wildcards. Per i nullable costruiamo direttamente
        // un HashSet<E?> esplicito ed enumeriamo tutti i valori + null.
        val ANY_DEEP: Set<DeepVerdict?> = buildSet {
            DeepVerdict.entries.forEach { add(it) }
            add(null)
        }
        val ANY_TA: Set<EntryTimingVerdict?> = buildSet {
            EntryTimingVerdict.entries.forEach { add(it) }
            add(null)
        }

        val GREEN_MOS: Set<Signal> = setOf(Signal.GREEN)
        val YELLOW_MOS: Set<Signal> = setOf(Signal.YELLOW)
        val NON_GREEN_MOS: Set<Signal> = setOf(Signal.YELLOW, Signal.RED, Signal.INDETERMINATE, Signal.NOT_CALCULABLE)

        // Deep OK o non disponibile (NOT_INDEXED / NOT_AVAILABLE → null).
        val DEEP_OK_OR_NONE: Set<DeepVerdict?> = setOf(DeepVerdict.OK, null)
        val DEEP_WATCHLIST: Set<DeepVerdict?> = setOf(DeepVerdict.WATCHLIST)

        // TA "verde" (entry compatibile con ENTER_NOW).
        val TA_FAVORABLE: Set<EntryTimingVerdict?> = setOf(
            EntryTimingVerdict.ENTRY_FAVORABLE,
            EntryTimingVerdict.ENTRY_NEUTRAL,
        )
        // TA "rossa" / "wait" (degrade a WAIT_FOR_SETUP).
        val TA_UNFAVORABLE: Set<EntryTimingVerdict?> = setOf(
            EntryTimingVerdict.WAIT,
            EntryTimingVerdict.ENTRY_UNFAVORABLE,
        )
        // TA indisponibile (null) o INDETERMINATE: conservativo → WAIT_FOR_SETUP.
        val TA_UNKNOWN: Set<EntryTimingVerdict?> = setOf(
            EntryTimingVerdict.INDETERMINATE,
            null,
        )

        /**
         * Tabella di mapping VERBATIM da US-103 §"Tabella di mapping".
         * L'ordine conta: la prima riga che matcha vince. Le regole sopra
         * (VI RED → AVOID, INDETERMINATE → INSUFFICIENT_DATA, Munger
         * RISCHIO_ESTREMO override) sono gia' state applicate.
         */
        val MAPPING_TABLE: List<MatchRule> = listOf(

            // ============== GREEN_DOMINANT (VI gate passato) ===============

            // 1) GREEN dominante + MoS GREEN + Deep OK/none + TA favorable/neutral → ENTER_NOW.
            MatchRule(
                viVerdicts = setOf(ViVerdict.GREEN_DOMINANT),
                mosSignals = GREEN_MOS,
                deepVerdicts = DEEP_OK_OR_NONE,
                taVerdicts = TA_FAVORABLE,
                verdict = SummaryVerdict.ENTER_NOW,
            ),
            // 2) GREEN dominante + MoS GREEN + Deep OK + TA WAIT/UNFAVORABLE → WAIT_FOR_SETUP.
            //    Caso COPART: VI positivo, MoS positivo, ma timing tecnico ostile.
            MatchRule(
                viVerdicts = setOf(ViVerdict.GREEN_DOMINANT),
                mosSignals = GREEN_MOS,
                deepVerdicts = DEEP_OK_OR_NONE,
                taVerdicts = TA_UNFAVORABLE,
                verdict = SummaryVerdict.WAIT_FOR_SETUP,
            ),
            // 3) GREEN dominante + MoS GREEN + Deep WATCHLIST + qualsiasi TA → WAIT_FOR_SETUP.
            MatchRule(
                viVerdicts = setOf(ViVerdict.GREEN_DOMINANT),
                mosSignals = GREEN_MOS,
                deepVerdicts = DEEP_WATCHLIST,
                taVerdicts = ANY_TA,
                verdict = SummaryVerdict.WAIT_FOR_SETUP,
            ),
            // 4) GREEN dominante + MoS GREEN + TA INDETERMINATE/null (TA indisponibile)
            //    → WAIT_FOR_SETUP (conservativo: senza segnale di timing, non promuoviamo
            //    ad ENTER_NOW).
            MatchRule(
                viVerdicts = setOf(ViVerdict.GREEN_DOMINANT),
                mosSignals = GREEN_MOS,
                deepVerdicts = DEEP_OK_OR_NONE,
                taVerdicts = TA_UNKNOWN,
                verdict = SummaryVerdict.WAIT_FOR_SETUP,
            ),
            // 5) GREEN dominante + MoS YELLOW (MoS marginale, aspettare prezzo migliore
            //    a prescindere dalla TA) → WAIT_FOR_SETUP.
            MatchRule(
                viVerdicts = setOf(ViVerdict.GREEN_DOMINANT),
                mosSignals = YELLOW_MOS,
                deepVerdicts = ANY_DEEP,
                taVerdicts = ANY_TA,
                verdict = SummaryVerdict.WAIT_FOR_SETUP,
            ),
            // 6) GREEN dominante + MoS RED/INDETERMINATE/NOT_CALCULABLE → WAIT_FOR_SETUP.
            //    MoS negativo: prezzo gia' a/oltre l'intrinsic value, non e' una entry.
            MatchRule(
                viVerdicts = setOf(ViVerdict.GREEN_DOMINANT),
                mosSignals = NON_GREEN_MOS - YELLOW_MOS,
                deepVerdicts = ANY_DEEP,
                taVerdicts = ANY_TA,
                verdict = SummaryVerdict.WAIT_FOR_SETUP,
            ),

            // ============== YELLOW_DOMINANT (VI gate intermedio) ===========

            // 7) YELLOW dominante + MoS GREEN → WAIT_FOR_SETUP.
            //    Aspettiamo che il VI consolidi (piu' GREEN) prima di entrare.
            MatchRule(
                viVerdicts = setOf(ViVerdict.YELLOW_DOMINANT),
                mosSignals = GREEN_MOS,
                deepVerdicts = ANY_DEEP,
                taVerdicts = ANY_TA,
                verdict = SummaryVerdict.WAIT_FOR_SETUP,
            ),
            // 8) YELLOW dominante + MoS non-GREEN → AVOID.
            //    Senza margin of safety adeguato il rischio non e' giustificato.
            MatchRule(
                viVerdicts = setOf(ViVerdict.YELLOW_DOMINANT),
                mosSignals = NON_GREEN_MOS,
                deepVerdicts = ANY_DEEP,
                taVerdicts = ANY_TA,
                verdict = SummaryVerdict.AVOID,
            ),
        )
    }
}
