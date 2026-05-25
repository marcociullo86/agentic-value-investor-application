package com.valueinvesting.webapp.service

import com.valueinvesting.webapp.ruleengine.RuleSignal
import com.valueinvesting.webapp.ruleengine.Signal
import org.springframework.stereotype.Service

/**
 * Pure-logic verdict cascade (Munger-style) over pre-computed inputs.
 * No IO, no DB — deterministic: same inputs always produce the same output.
 *
 * Cascade order (first match wins):
 *   1. RISCHIO_ESTREMO           → BOCCIATO_QUALITATIVO
 *   2. STRUCTURAL_DAMAGE         → BOCCIATO_VALUE_TRAP
 *   3. ≥ 4 RED rules             → BOCCIATO_NUMERICO
 *   4. panic buy conditions      → APPROVATO_PANIC_BUY
 *   5. all GREEN/YELLOW + safe   → APPROVATO
 *   6. fallback                  → WATCHLIST
 *
 * [^src: wiki/syntheses/graham-investing-philosophy.md §Genealogia dei criteri difensivi]
 * [^src: wiki/concepts/value-investing-rule-engine.md §Output del Rule Engine: il "Traffic Light"]
 * [^src: wiki/runbooks/sec-10k-10q-analysis-playbook.md §Step 5 — Sintesi]
 */
@Service
class MungerDecisionService {

    companion object {
        private const val EXPECTED_RULE_COUNT = 13
        private const val BOCCIATO_NUMERICO_RED_THRESHOLD = 4
        private const val PANIC_BUY_MAX_RED = 2
        private val LOW_RISK_LEVELS = setOf(
            LivelloRischio.RISCHIO_BASSO,
            LivelloRischio.RISCHIO_MODERATO,
        )
    }

    fun compute(input: MungerDecisionInput): VerdictPayload {
        val counts = countByColor(input.ruleResults)
        val partialBasis = input.ruleResults.size < EXPECTED_RULE_COUNT
        val (verdetto, motivazione) = cascade(input, counts)
        val positionSizePct = positionSize(verdetto)

        return VerdictPayload(
            ticker = input.ticker,
            verdettoClasse = verdetto,
            positionSizePct = positionSizePct,
            partialBasis = partialBasis,
            motivazioneAggregata = motivazione.take(500),
            ruleCountByColor = counts,
            inputRiferimenti = InputRiferimenti(
                ruleResultCount = input.ruleResults.size,
                livelloRischio = input.livelloRischio,
                newsSentimentDominante = input.newsSentimentDominante,
                panicDiscount = input.panicDiscount,
                deteriorationWarning = input.deteriorationWarning,
            ),
        )
    }

    private fun cascade(
        input: MungerDecisionInput,
        counts: RuleCountByColor,
    ): Pair<VerdictClass, String> {
        // Step 1
        if (input.livelloRischio == LivelloRischio.RISCHIO_ESTREMO) {
            return VerdictClass.BOCCIATO_QUALITATIVO to
                "Bocciato: RISCHIO_ESTREMO da analisi qualitativa Munger (inversione critica)"
        }

        // Step 2
        if (input.newsSentimentDominante == SentimentClass.STRUCTURAL_DAMAGE) {
            return VerdictClass.BOCCIATO_VALUE_TRAP to
                "Bocciato: sentiment dominante STRUCTURAL_DAMAGE — rischio value trap"
        }

        // Step 3
        if (counts.red >= BOCCIATO_NUMERICO_RED_THRESHOLD) {
            return VerdictClass.BOCCIATO_NUMERICO to
                "Bocciato: ${counts.red} regole RED su ${input.ruleResults.size} (soglia >=$BOCCIATO_NUMERICO_RED_THRESHOLD)"
        }

        // Step 4
        if (input.panicDiscount &&
            input.newsSentimentDominante == SentimentClass.TEMPORARY_PANIC &&
            input.livelloRischio in LOW_RISK_LEVELS &&
            counts.red <= PANIC_BUY_MAX_RED
        ) {
            return VerdictClass.APPROVATO_PANIC_BUY to
                "Approvato panic buy: sconto panico con fondamentali solidi (${counts.red} RED, rischio ${input.livelloRischio})"
        }

        // Step 5
        val allGreenOrYellow = input.ruleResults.all {
            it.signal == Signal.GREEN || it.signal == Signal.YELLOW
        }
        if (allGreenOrYellow &&
            input.livelloRischio in LOW_RISK_LEVELS &&
            input.newsSentimentDominante != SentimentClass.STRUCTURAL_DAMAGE
        ) {
            return VerdictClass.APPROVATO to
                "Approvato: tutte le regole GREEN/YELLOW, rischio ${input.livelloRischio}, nessun danno strutturale"
        }

        // Step 6
        return VerdictClass.WATCHLIST to
            "Watchlist: condizioni miste (${counts.green}G/${counts.yellow}Y/${counts.red}R, rischio ${input.livelloRischio})"
    }

    private fun positionSize(verdetto: VerdictClass): Double = when (verdetto) {
        VerdictClass.APPROVATO_PANIC_BUY -> 5.0
        VerdictClass.APPROVATO -> 3.0
        VerdictClass.WATCHLIST -> 0.0
        VerdictClass.BOCCIATO_NUMERICO -> 0.0
        VerdictClass.BOCCIATO_QUALITATIVO -> 0.0
        VerdictClass.BOCCIATO_VALUE_TRAP -> 0.0
    }

    private fun countByColor(results: List<RuleSignal>): RuleCountByColor = RuleCountByColor(
        green = results.count { it.signal == Signal.GREEN },
        yellow = results.count { it.signal == Signal.YELLOW },
        red = results.count { it.signal == Signal.RED },
        indeterminate = results.count { it.signal == Signal.INDETERMINATE },
        notCalculable = results.count { it.signal == Signal.NOT_CALCULABLE },
    )
}

// --- Enums ---

enum class LivelloRischio {
    RISCHIO_BASSO,
    RISCHIO_MODERATO,
    RISCHIO_ALTO,
    RISCHIO_ESTREMO,
}

// VerdictClass lives in VerdictClass.kt (canonical definition, US-044).

// --- Input / Output DTOs ---

data class MungerDecisionInput(
    val ticker: String,
    val ruleResults: List<RuleSignal>,
    val livelloRischio: LivelloRischio,
    val newsSentimentDominante: SentimentClass,
    val panicDiscount: Boolean,
    val deteriorationWarning: Boolean,
)

data class VerdictPayload(
    val ticker: String,
    val verdettoClasse: VerdictClass,
    val positionSizePct: Double,
    val partialBasis: Boolean,
    val motivazioneAggregata: String,
    val ruleCountByColor: RuleCountByColor,
    val inputRiferimenti: InputRiferimenti,
)

data class RuleCountByColor(
    val green: Int,
    val yellow: Int,
    val red: Int,
    val indeterminate: Int,
    val notCalculable: Int,
)

data class InputRiferimenti(
    val ruleResultCount: Int,
    val livelloRischio: LivelloRischio,
    val newsSentimentDominante: SentimentClass,
    val panicDiscount: Boolean,
    val deteriorationWarning: Boolean,
)
