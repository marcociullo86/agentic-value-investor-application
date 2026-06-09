package com.valueinvesting.webapp.summary

import com.valueinvesting.webapp.ruleengine.RuleSignal
import com.valueinvesting.webapp.ruleengine.Signal
import org.springframework.stereotype.Component

/**
 * Aggrega i `RuleSignal` del rule engine VI in un singolo [ViVerdict]
 * proporzionale sui ruleId DECISIONALI disponibili (ADR-030 §3 — verbatim).
 *
 * Esclusioni dal denominatore:
 *   - `NCAV_LATEST` (ADR-029 §2): ruleId INFORMATIVO, mai decisionale — la
 *     decisione net-net e' delegata a `NET_NET_RATIO` ([[ADR-029]] §2).
 *   - segnali in stato `INDETERMINATE` o `NOT_CALCULABLE` (ADR-030 §3): non
 *     contribuiscono al denominatore. Se troppi (≥ 1/3 dei ruleId totali) il
 *     verdetto degrada a `INDETERMINATE_DOMINANT`.
 *
 * Soglie (verbatim ADR-030 §3, US-103 §"Tabella di mapping"):
 *   GREEN_DOMINANT          = quota GREEN ≥ 0.60 dei decisionali disponibili
 *   RED_DOMINANT            = quota GREEN < 0.33 dei decisionali disponibili
 *   YELLOW_DOMINANT         = intervallo intermedio
 *   INDETERMINATE_DOMINANT  = (INDETERMINATE + NOT_CALCULABLE) >= ceil(total / 3)
 *
 * Pure-function: niente I/O, niente persistenza, niente LLM. Sicuro per riuso
 * dal backtest US-105 in modalita' "as-of date".
 *
 * [^src: design_&_architecture/decisions/ADR-030-decision-layer-vi-ta-summary.md §3]
 * [^src: design_&_architecture/decisions/ADR-029-net-net-stocks-ncav.md §2]
 * [^src: management/kanban/EP-024-.../US-103-.../US-103.md §"Definizione delle classi *_DOMINANT"]
 * [^src: wiki/concepts/value-investing-rule-engine.md §Output del Rule Engine]
 */
@Component
class ViVerdictAggregator {

    /**
     * Esito tipato dell'aggregazione. `greenShare` e' la quota di GREEN sui
     * ruleId DECISIONALI DISPONIBILI (denominatore escluso NCAV_LATEST,
     * INDETERMINATE e NOT_CALCULABLE).
     */
    data class Result(
        val verdict: ViVerdict,
        /** Totale ruleId in input (informativo). */
        val totalRuleIds: Int,
        /** Ruleid in stato INDETERMINATE + NOT_CALCULABLE. */
        val indeterminateCount: Int,
        /** Ruleid decisionali disponibili (= denominatore della quota). */
        val decisionalAvailable: Int,
        /** Ruleid decisionali disponibili con signal=GREEN. */
        val greenCount: Int,
        /** greenCount / decisionalAvailable; null se decisionalAvailable=0. */
        val greenShare: Double?,
    )

    fun aggregate(signals: List<RuleSignal>): Result {
        val total = signals.size

        if (total == 0) {
            return Result(
                verdict = ViVerdict.INDETERMINATE_DOMINANT,
                totalRuleIds = 0,
                indeterminateCount = 0,
                decisionalAvailable = 0,
                greenCount = 0,
                greenShare = null,
            )
        }

        // INDETERMINATE / NOT_CALCULABLE sul totale (denominatore = TUTTI i
        // ruleId, NCAV_LATEST incluso): la soglia ≥ 1/3 di ADR-030 §3 vale sui
        // ruleId nel loro insieme, non solo sui decisionali (altrimenti
        // basterebbe avere tutti i decisionali INDETERMINATE per sbloccare la
        // soglia in modo non intuitivo). Usiamo ceil(total / 3.0) per coerenza
        // con "≥ 1/3 dei ruleId" — su 15 ruleId → soglia 5; su 14 → 5; su 13 → 5.
        val indeterminate = signals.count {
            it.signal == Signal.INDETERMINATE || it.signal == Signal.NOT_CALCULABLE
        }
        val indeterminateThreshold = Math.ceil(total / 3.0).toInt()
        if (indeterminate >= indeterminateThreshold) {
            return Result(
                verdict = ViVerdict.INDETERMINATE_DOMINANT,
                totalRuleIds = total,
                indeterminateCount = indeterminate,
                decisionalAvailable = 0,
                greenCount = 0,
                greenShare = null,
            )
        }

        // Denominatore: ruleId DECISIONALI disponibili.
        //   - NCAV_LATEST escluso (informativo, ADR-029 §2).
        //   - INDETERMINATE / NOT_CALCULABLE esclusi (ADR-030 §3).
        val decisional = signals.filter {
            it.ruleId !in INFORMATIONAL_RULE_IDS &&
                it.signal != Signal.INDETERMINATE &&
                it.signal != Signal.NOT_CALCULABLE
        }
        val decisionalCount = decisional.size

        if (decisionalCount == 0) {
            // Defensive: se per qualunque motivo (es. rule set mutato) il
            // denominatore va a zero senza che la soglia INDETERMINATE sia
            // stata superata, comunque non possiamo classificare → degradiamo
            // a INDETERMINATE_DOMINANT.
            return Result(
                verdict = ViVerdict.INDETERMINATE_DOMINANT,
                totalRuleIds = total,
                indeterminateCount = indeterminate,
                decisionalAvailable = 0,
                greenCount = 0,
                greenShare = null,
            )
        }

        val green = decisional.count { it.signal == Signal.GREEN }
        val share = green.toDouble() / decisionalCount.toDouble()
        val verdict = when {
            share >= GREEN_DOMINANT_THRESHOLD -> ViVerdict.GREEN_DOMINANT
            share < RED_DOMINANT_THRESHOLD -> ViVerdict.RED_DOMINANT
            else -> ViVerdict.YELLOW_DOMINANT
        }

        return Result(
            verdict = verdict,
            totalRuleIds = total,
            indeterminateCount = indeterminate,
            decisionalAvailable = decisionalCount,
            greenCount = green,
            greenShare = share,
        )
    }

    companion object {
        /** Soglia quota GREEN per classificare GREEN_DOMINANT (ADR-030 §3 verbatim). */
        const val GREEN_DOMINANT_THRESHOLD: Double = 0.60

        /** Soglia quota GREEN per classificare RED_DOMINANT (strict <, ADR-030 §3 verbatim). */
        const val RED_DOMINANT_THRESHOLD: Double = 0.33

        /**
         * Ruleid che NON contribuiscono al denominatore perche' informativi
         * (ADR-029 §2). Set hardcoded per esplicito vincolo architetturale —
         * NON desumibile dal Signal (un NCAV_LATEST con signal=GREEN resta
         * informativo: la decisione net-net e' del NET_NET_RATIO).
         */
        val INFORMATIONAL_RULE_IDS: Set<String> = setOf("NCAV_LATEST")
    }
}
