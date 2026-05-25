package com.valueinvesting.webapp.service

import com.valueinvesting.webapp.ruleengine.RuleSignal
import com.valueinvesting.webapp.ruleengine.Signal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class MungerDecisionServiceTest {

    private val service = MungerDecisionService()

    // ── Step 1: RISCHIO_ESTREMO → BOCCIATO_QUALITATIVO ──

    @Test
    fun `step 1 - RISCHIO_ESTREMO produces BOCCIATO_QUALITATIVO regardless of other inputs`() {
        val input = baseInput().copy(
            livelloRischio = LivelloRischio.RISCHIO_ESTREMO,
            ruleResults = allGreenRules(13),
            newsSentimentDominante = SentimentClass.NEUTRAL,
        )

        val result = service.compute(input)

        assertThat(result.verdettoClasse).isEqualTo(VerdictClass.BOCCIATO_QUALITATIVO)
        assertThat(result.positionSizePct).isEqualTo(0.0)
        assertThat(result.motivazioneAggregata).contains("RISCHIO_ESTREMO")
    }

    @Test
    fun `step 1 wins over step 2 - RISCHIO_ESTREMO beats STRUCTURAL_DAMAGE`() {
        val input = baseInput().copy(
            livelloRischio = LivelloRischio.RISCHIO_ESTREMO,
            newsSentimentDominante = SentimentClass.STRUCTURAL_DAMAGE,
        )

        val result = service.compute(input)

        assertThat(result.verdettoClasse).isEqualTo(VerdictClass.BOCCIATO_QUALITATIVO)
    }

    // ── Step 2: STRUCTURAL_DAMAGE → BOCCIATO_VALUE_TRAP ──

    @Test
    fun `step 2 - STRUCTURAL_DAMAGE produces BOCCIATO_VALUE_TRAP`() {
        val input = baseInput().copy(
            livelloRischio = LivelloRischio.RISCHIO_BASSO,
            newsSentimentDominante = SentimentClass.STRUCTURAL_DAMAGE,
            ruleResults = allGreenRules(13),
        )

        val result = service.compute(input)

        assertThat(result.verdettoClasse).isEqualTo(VerdictClass.BOCCIATO_VALUE_TRAP)
        assertThat(result.positionSizePct).isEqualTo(0.0)
        assertThat(result.motivazioneAggregata).contains("STRUCTURAL_DAMAGE")
    }

    @Test
    fun `step 2 - RISCHIO_MODERATO with STRUCTURAL_DAMAGE produces BOCCIATO_VALUE_TRAP`() {
        val input = baseInput().copy(
            livelloRischio = LivelloRischio.RISCHIO_MODERATO,
            newsSentimentDominante = SentimentClass.STRUCTURAL_DAMAGE,
            ruleResults = allGreenRules(13),
        )

        val result = service.compute(input)

        assertThat(result.verdettoClasse).isEqualTo(VerdictClass.BOCCIATO_VALUE_TRAP)
        assertThat(result.positionSizePct).isEqualTo(0.0)
    }

    @Test
    fun `step 2 wins over step 3 - STRUCTURAL_DAMAGE beats 4 RED rules`() {
        val input = baseInput().copy(
            livelloRischio = LivelloRischio.RISCHIO_BASSO,
            newsSentimentDominante = SentimentClass.STRUCTURAL_DAMAGE,
            ruleResults = rulesWithRedCount(5, 13),
        )

        val result = service.compute(input)

        assertThat(result.verdettoClasse).isEqualTo(VerdictClass.BOCCIATO_VALUE_TRAP)
    }

    // ── Step 3: ≥ 4 RED → BOCCIATO_NUMERICO ──

    @Test
    fun `step 3 - exactly 4 RED rules produces BOCCIATO_NUMERICO`() {
        val input = baseInput().copy(
            livelloRischio = LivelloRischio.RISCHIO_BASSO,
            newsSentimentDominante = SentimentClass.NEUTRAL,
            ruleResults = rulesWithRedCount(4, 13),
        )

        val result = service.compute(input)

        assertThat(result.verdettoClasse).isEqualTo(VerdictClass.BOCCIATO_NUMERICO)
        assertThat(result.positionSizePct).isEqualTo(0.0)
        assertThat(result.ruleCountByColor.red).isEqualTo(4)
    }

    @Test
    fun `step 3 - 5 RED rules with RISCHIO_MODERATO and NEUTRAL produces BOCCIATO_NUMERICO`() {
        val input = baseInput().copy(
            livelloRischio = LivelloRischio.RISCHIO_MODERATO,
            newsSentimentDominante = SentimentClass.NEUTRAL,
            ruleResults = rulesWithRedCount(5, 13),
        )

        val result = service.compute(input)

        assertThat(result.verdettoClasse).isEqualTo(VerdictClass.BOCCIATO_NUMERICO)
        assertThat(result.positionSizePct).isEqualTo(0.0)
        assertThat(result.ruleCountByColor.red).isEqualTo(5)
    }

    @Test
    fun `step 3 - 3 RED rules does NOT trigger BOCCIATO_NUMERICO`() {
        val input = baseInput().copy(
            livelloRischio = LivelloRischio.RISCHIO_ALTO,
            newsSentimentDominante = SentimentClass.NEUTRAL,
            ruleResults = rulesWithRedCount(3, 13),
        )

        val result = service.compute(input)

        assertThat(result.verdettoClasse).isNotEqualTo(VerdictClass.BOCCIATO_NUMERICO)
    }

    // ── Step 4: APPROVATO_PANIC_BUY ──

    @Test
    fun `step 4 - panic buy with all conditions met`() {
        val input = baseInput().copy(
            livelloRischio = LivelloRischio.RISCHIO_BASSO,
            newsSentimentDominante = SentimentClass.TEMPORARY_PANIC,
            panicDiscount = true,
            ruleResults = rulesWithRedCount(2, 13),
        )

        val result = service.compute(input)

        assertThat(result.verdettoClasse).isEqualTo(VerdictClass.APPROVATO_PANIC_BUY)
        assertThat(result.positionSizePct).isEqualTo(5.0)
    }

    @Test
    fun `step 4 - panic buy with RISCHIO_MODERATO also qualifies`() {
        val input = baseInput().copy(
            livelloRischio = LivelloRischio.RISCHIO_MODERATO,
            newsSentimentDominante = SentimentClass.TEMPORARY_PANIC,
            panicDiscount = true,
            ruleResults = rulesWithRedCount(1, 13),
        )

        val result = service.compute(input)

        assertThat(result.verdettoClasse).isEqualTo(VerdictClass.APPROVATO_PANIC_BUY)
    }

    @Test
    fun `step 4 - panic buy fails when panicDiscount is false`() {
        val input = baseInput().copy(
            livelloRischio = LivelloRischio.RISCHIO_BASSO,
            newsSentimentDominante = SentimentClass.TEMPORARY_PANIC,
            panicDiscount = false,
            ruleResults = rulesWithRedCount(1, 13),
        )

        val result = service.compute(input)

        assertThat(result.verdettoClasse).isNotEqualTo(VerdictClass.APPROVATO_PANIC_BUY)
    }

    @Test
    fun `step 4 - panic buy fails when sentiment is not TEMPORARY_PANIC`() {
        val input = baseInput().copy(
            livelloRischio = LivelloRischio.RISCHIO_BASSO,
            newsSentimentDominante = SentimentClass.NEUTRAL,
            panicDiscount = true,
            ruleResults = rulesWithRedCount(1, 13),
        )

        val result = service.compute(input)

        assertThat(result.verdettoClasse).isNotEqualTo(VerdictClass.APPROVATO_PANIC_BUY)
    }

    @Test
    fun `step 4 - panic buy fails when risk is ALTO`() {
        val input = baseInput().copy(
            livelloRischio = LivelloRischio.RISCHIO_ALTO,
            newsSentimentDominante = SentimentClass.TEMPORARY_PANIC,
            panicDiscount = true,
            ruleResults = rulesWithRedCount(1, 13),
        )

        val result = service.compute(input)

        assertThat(result.verdettoClasse).isNotEqualTo(VerdictClass.APPROVATO_PANIC_BUY)
    }

    @Test
    fun `step 4 - panic buy fails when 3 RED rules (exceeds max 2)`() {
        val input = baseInput().copy(
            livelloRischio = LivelloRischio.RISCHIO_BASSO,
            newsSentimentDominante = SentimentClass.TEMPORARY_PANIC,
            panicDiscount = true,
            ruleResults = rulesWithRedCount(3, 13),
        )

        val result = service.compute(input)

        assertThat(result.verdettoClasse).isNotEqualTo(VerdictClass.APPROVATO_PANIC_BUY)
    }

    // ── Step 5: APPROVATO ──

    @Test
    fun `step 5 - all GREEN rules with low risk produces APPROVATO`() {
        val input = baseInput().copy(
            livelloRischio = LivelloRischio.RISCHIO_BASSO,
            newsSentimentDominante = SentimentClass.NEUTRAL,
            ruleResults = allGreenRules(13),
        )

        val result = service.compute(input)

        assertThat(result.verdettoClasse).isEqualTo(VerdictClass.APPROVATO)
        assertThat(result.positionSizePct).isEqualTo(3.0)
    }

    @Test
    fun `step 5 - mix of GREEN and YELLOW with RISCHIO_MODERATO produces APPROVATO`() {
        val rules = (1..10).map { ruleSignal("RULE_$it", Signal.GREEN) } +
            (11..13).map { ruleSignal("RULE_$it", Signal.YELLOW) }
        val input = baseInput().copy(
            livelloRischio = LivelloRischio.RISCHIO_MODERATO,
            newsSentimentDominante = SentimentClass.TEMPORARY_PANIC,
            ruleResults = rules,
        )

        val result = service.compute(input)

        assertThat(result.verdettoClasse).isEqualTo(VerdictClass.APPROVATO)
    }

    @Test
    fun `step 5 - APPROVATO fails when risk is ALTO even with all GREEN`() {
        val input = baseInput().copy(
            livelloRischio = LivelloRischio.RISCHIO_ALTO,
            newsSentimentDominante = SentimentClass.NEUTRAL,
            ruleResults = allGreenRules(13),
        )

        val result = service.compute(input)

        assertThat(result.verdettoClasse).isNotEqualTo(VerdictClass.APPROVATO)
    }

    @Test
    fun `step 5 - APPROVATO fails with any INDETERMINATE rule`() {
        val rules = allGreenRules(12) + ruleSignal("RULE_13", Signal.INDETERMINATE)
        val input = baseInput().copy(
            livelloRischio = LivelloRischio.RISCHIO_BASSO,
            newsSentimentDominante = SentimentClass.NEUTRAL,
            ruleResults = rules,
        )

        val result = service.compute(input)

        assertThat(result.verdettoClasse).isNotEqualTo(VerdictClass.APPROVATO)
    }

    // ── Step 6: WATCHLIST (default) ──

    @Test
    fun `step 6 - mixed conditions produce WATCHLIST`() {
        val input = baseInput().copy(
            livelloRischio = LivelloRischio.RISCHIO_ALTO,
            newsSentimentDominante = SentimentClass.NEUTRAL,
            ruleResults = rulesWithRedCount(2, 13),
        )

        val result = service.compute(input)

        assertThat(result.verdettoClasse).isEqualTo(VerdictClass.WATCHLIST)
        assertThat(result.positionSizePct).isEqualTo(0.0)
    }

    @Test
    fun `step 6 - 3 RED with RISCHIO_MODERATO produces WATCHLIST`() {
        val input = baseInput().copy(
            livelloRischio = LivelloRischio.RISCHIO_MODERATO,
            newsSentimentDominante = SentimentClass.NEUTRAL,
            ruleResults = rulesWithRedCount(3, 13),
        )

        val result = service.compute(input)

        assertThat(result.verdettoClasse).isEqualTo(VerdictClass.WATCHLIST)
        assertThat(result.positionSizePct).isEqualTo(0.0)
    }

    // ── partialBasis ──

    @Test
    fun `partialBasis is true when fewer than 13 rule results`() {
        val input = baseInput().copy(ruleResults = allGreenRules(7))

        val result = service.compute(input)

        assertThat(result.partialBasis).isTrue()
    }

    @Test
    fun `partialBasis is false when exactly 13 rule results`() {
        val input = baseInput().copy(ruleResults = allGreenRules(13))

        val result = service.compute(input)

        assertThat(result.partialBasis).isFalse()
    }

    @Test
    fun `partialBasis is false when more than 13 rule results`() {
        val input = baseInput().copy(ruleResults = allGreenRules(15))

        val result = service.compute(input)

        assertThat(result.partialBasis).isFalse()
    }

    // ── Determinism (AC: same 4 inputs → same output over 100 executions) ──

    @ParameterizedTest(name = "determinism run {index}")
    @EnumSource(VerdictClass::class)
    fun `deterministic - same input always produces same verdict class`(expectedClass: VerdictClass) {
        val input = inputForVerdict(expectedClass)
        val results = (1..100).map { service.compute(input) }

        assertThat(results).allMatch { it.verdettoClasse == expectedClass }
        assertThat(results.map { it.positionSizePct }.toSet()).hasSize(1)
        assertThat(results.map { it.motivazioneAggregata }.toSet()).hasSize(1)
    }

    // ── ruleCountByColor ──

    @Test
    fun `ruleCountByColor correctly tallies all signal types`() {
        val rules = listOf(
            ruleSignal("R1", Signal.GREEN),
            ruleSignal("R2", Signal.GREEN),
            ruleSignal("R3", Signal.YELLOW),
            ruleSignal("R4", Signal.RED),
            ruleSignal("R5", Signal.INDETERMINATE),
            ruleSignal("R6", Signal.NOT_CALCULABLE),
        )
        val input = baseInput().copy(ruleResults = rules)

        val result = service.compute(input)

        assertThat(result.ruleCountByColor).isEqualTo(
            RuleCountByColor(green = 2, yellow = 1, red = 1, indeterminate = 1, notCalculable = 1),
        )
    }

    // ── motivazioneAggregata max length ──

    @Test
    fun `motivazioneAggregata is capped at 500 characters`() {
        val input = baseInput().copy(ruleResults = allGreenRules(13))

        val result = service.compute(input)

        assertThat(result.motivazioneAggregata.length).isLessThanOrEqualTo(500)
    }

    // ── inputRiferimenti ──

    @Test
    fun `inputRiferimenti mirrors the input faithfully`() {
        val input = baseInput().copy(
            livelloRischio = LivelloRischio.RISCHIO_MODERATO,
            newsSentimentDominante = SentimentClass.TEMPORARY_PANIC,
            panicDiscount = true,
            deteriorationWarning = true,
            ruleResults = allGreenRules(7),
        )

        val result = service.compute(input)

        assertThat(result.inputRiferimenti).isEqualTo(
            InputRiferimenti(
                ruleResultCount = 7,
                livelloRischio = LivelloRischio.RISCHIO_MODERATO,
                newsSentimentDominante = SentimentClass.TEMPORARY_PANIC,
                panicDiscount = true,
                deteriorationWarning = true,
            ),
        )
    }

    // ── Cascade order verification: step 3 beats step 4 ──

    @Test
    fun `step 3 wins over step 4 - 4 RED blocks panic buy even with panic conditions`() {
        val input = baseInput().copy(
            livelloRischio = LivelloRischio.RISCHIO_BASSO,
            newsSentimentDominante = SentimentClass.TEMPORARY_PANIC,
            panicDiscount = true,
            ruleResults = rulesWithRedCount(4, 13),
        )

        val result = service.compute(input)

        assertThat(result.verdettoClasse).isEqualTo(VerdictClass.BOCCIATO_NUMERICO)
    }

    @Test
    fun `step 1 wins over step 4 - RISCHIO_ESTREMO beats panic buy conditions`() {
        val input = baseInput().copy(
            livelloRischio = LivelloRischio.RISCHIO_ESTREMO,
            newsSentimentDominante = SentimentClass.TEMPORARY_PANIC,
            panicDiscount = true,
            ruleResults = rulesWithRedCount(1, 13),
        )

        val result = service.compute(input)

        assertThat(result.verdettoClasse).isEqualTo(VerdictClass.BOCCIATO_QUALITATIVO)
    }

    // ── Edge case: empty rule list ──

    @Test
    fun `empty rule list produces APPROVATO with partialBasis true`() {
        val input = baseInput().copy(
            ruleResults = emptyList(),
            livelloRischio = LivelloRischio.RISCHIO_BASSO,
            newsSentimentDominante = SentimentClass.NEUTRAL,
        )

        val result = service.compute(input)

        assertThat(result.partialBasis).isTrue()
        // all() on empty list returns true, so step 5 matches
        assertThat(result.verdettoClasse).isEqualTo(VerdictClass.APPROVATO)
    }

    // --- helpers ---

    private fun baseInput() = MungerDecisionInput(
        ticker = "TEST",
        ruleResults = allGreenRules(13),
        livelloRischio = LivelloRischio.RISCHIO_BASSO,
        newsSentimentDominante = SentimentClass.NEUTRAL,
        panicDiscount = false,
        deteriorationWarning = false,
    )

    private fun ruleSignal(id: String, signal: Signal) = RuleSignal(
        ruleId = id,
        signal = signal,
        observedValue = null,
        threshold = "",
        rationale = "",
    )

    private fun allGreenRules(count: Int): List<RuleSignal> =
        (1..count).map { ruleSignal("RULE_$it", Signal.GREEN) }

    private fun rulesWithRedCount(redCount: Int, total: Int): List<RuleSignal> {
        require(redCount <= total)
        return (1..redCount).map { ruleSignal("RULE_$it", Signal.RED) } +
            (redCount + 1..total).map { ruleSignal("RULE_$it", Signal.GREEN) }
    }

    private fun inputForVerdict(verdict: VerdictClass): MungerDecisionInput = when (verdict) {
        VerdictClass.BOCCIATO_QUALITATIVO -> baseInput().copy(
            livelloRischio = LivelloRischio.RISCHIO_ESTREMO,
        )
        VerdictClass.BOCCIATO_VALUE_TRAP -> baseInput().copy(
            newsSentimentDominante = SentimentClass.STRUCTURAL_DAMAGE,
        )
        VerdictClass.BOCCIATO_NUMERICO -> baseInput().copy(
            ruleResults = rulesWithRedCount(5, 13),
        )
        VerdictClass.APPROVATO_PANIC_BUY -> baseInput().copy(
            panicDiscount = true,
            newsSentimentDominante = SentimentClass.TEMPORARY_PANIC,
            ruleResults = rulesWithRedCount(1, 13),
        )
        VerdictClass.APPROVATO -> baseInput()
        VerdictClass.WATCHLIST -> baseInput().copy(
            livelloRischio = LivelloRischio.RISCHIO_ALTO,
            ruleResults = rulesWithRedCount(2, 13),
        )
    }
}
