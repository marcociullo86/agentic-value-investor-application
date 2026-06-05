package com.valueinvesting.webapp.service

import com.valueinvesting.webapp.ruleengine.RuleSignal
import com.valueinvesting.webapp.ruleengine.Signal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Integration tests verifying the full verdict pipeline:
 * MungerDecisionService (cascade) → PositionSizeCalculator (MoS-proportional sizing).
 *
 * Pure logic, no Spring context.
 */
class VerdictCascadeIntegrationTest {

    private val decisionService = MungerDecisionService()
    private val sizeCalculator = PositionSizeCalculator()

    // ── APPROVATO_PANIC_BUY pipeline ──

    @Test
    fun `APPROVATO_PANIC_BUY verdict feeds into PositionSizeCalculator with high MoS`() {
        val input = panicBuyInput()
        val verdict = decisionService.compute(input)

        assertThat(verdict.verdettoClasse).isEqualTo(VerdictClass.APPROVATO_PANIC_BUY)

        val sizing = sizeCalculator.calculate(verdict.verdettoClasse, 50.0)

        assertThat(sizing.recommendedPct).isBetween(4.0, 6.0)
        assertThat(sizing.basisVerdict).isEqualTo(VerdictClass.APPROVATO_PANIC_BUY)
    }

    @Test
    fun `APPROVATO_PANIC_BUY verdict with low MoS still within range`() {
        val input = panicBuyInput()
        val verdict = decisionService.compute(input)
        val sizing = sizeCalculator.calculate(verdict.verdettoClasse, 5.0)

        assertThat(sizing.recommendedPct).isBetween(4.0, 6.0)
    }

    // ── APPROVATO pipeline ──

    @Test
    fun `APPROVATO verdict feeds into PositionSizeCalculator with moderate MoS`() {
        val input = approvedInput()
        val verdict = decisionService.compute(input)

        assertThat(verdict.verdettoClasse).isEqualTo(VerdictClass.APPROVATO)

        val sizing = sizeCalculator.calculate(verdict.verdettoClasse, 20.0)

        assertThat(sizing.recommendedPct).isBetween(2.0, 4.0)
        assertThat(sizing.basisVerdict).isEqualTo(VerdictClass.APPROVATO)
    }

    // ── BOCCIATO pipeline: all three BOCCIATO variants yield 0% sizing ──

    @ParameterizedTest(name = "{0} pipeline yields 0 pct position")
    @EnumSource(
        value = VerdictClass::class,
        names = ["BOCCIATO_QUALITATIVO", "BOCCIATO_VALUE_TRAP", "BOCCIATO_NUMERICO"],
    )
    fun `BOCCIATO verdict pipeline always yields 0 pct position`(expected: VerdictClass) {
        val input = inputForVerdict(expected)
        val verdict = decisionService.compute(input)

        assertThat(verdict.verdettoClasse).isEqualTo(expected)

        val sizing = sizeCalculator.calculate(verdict.verdettoClasse, 40.0)

        assertThat(sizing.recommendedPct).isEqualTo(0.0)
    }

    // ── WATCHLIST pipeline ──

    @Test
    fun `WATCHLIST verdict pipeline yields 0 pct position`() {
        val input = watchlistInput()
        val verdict = decisionService.compute(input)

        assertThat(verdict.verdettoClasse).isEqualTo(VerdictClass.WATCHLIST)

        val sizing = sizeCalculator.calculate(verdict.verdettoClasse, 30.0)

        assertThat(sizing.recommendedPct).isEqualTo(0.0)
    }

    // ── Determinism: full pipeline 100 runs ──

    @Test
    fun `full pipeline is deterministic over 100 runs for APPROVATO_PANIC_BUY`() {
        val input = panicBuyInput()
        val mos = 35.0

        val results = (1..100).map {
            val verdict = decisionService.compute(input)
            sizeCalculator.calculate(verdict.verdettoClasse, mos)
        }

        val first = results.first()
        results.forEach { result ->
            assertThat(result.recommendedPct).isEqualTo(first.recommendedPct)
            assertThat(result.basisVerdict).isEqualTo(first.basisVerdict)
        }
    }

    // ── partialBasis propagation ──

    @Test
    fun `partial basis with 7 rules still produces valid pipeline result`() {
        val input = approvedInput().copy(ruleResults = allGreenRules(7))
        val verdict = decisionService.compute(input)

        assertThat(verdict.partialBasis).isTrue()
        assertThat(verdict.verdettoClasse).isEqualTo(VerdictClass.APPROVATO)

        val sizing = sizeCalculator.calculate(verdict.verdettoClasse, 25.0)

        assertThat(sizing.recommendedPct).isBetween(2.0, 4.0)
    }

    // ── Disclaimer propagation ──

    @Test
    fun `PositionSizeResult disclaimer is present in pipeline output`() {
        val input = approvedInput()
        val verdict = decisionService.compute(input)
        val sizing = sizeCalculator.calculate(verdict.verdettoClasse, 20.0)

        assertThat(sizing.disclaimer).isEqualTo("Indicazione tecnica, non consiglio finanziario")
    }

    // --- helpers ---

    private fun panicBuyInput() = MungerDecisionInput(
        ticker = "PANIC",
        ruleResults = rulesWithRedCount(1, 13),
        livelloRischio = LivelloRischio.RISCHIO_BASSO,
        newsSentimentDominante = SentimentClass.TEMPORARY_PANIC,
        panicDiscount = true,
        deteriorationWarning = false,
    )

    private fun approvedInput() = MungerDecisionInput(
        ticker = "SAFE",
        ruleResults = allGreenRules(13),
        livelloRischio = LivelloRischio.RISCHIO_BASSO,
        newsSentimentDominante = SentimentClass.NEUTRAL,
        panicDiscount = false,
        deteriorationWarning = false,
    )

    private fun watchlistInput() = MungerDecisionInput(
        ticker = "WATCH",
        ruleResults = rulesWithRedCount(3, 13),
        livelloRischio = LivelloRischio.RISCHIO_MODERATO,
        newsSentimentDominante = SentimentClass.NEUTRAL,
        panicDiscount = false,
        deteriorationWarning = false,
    )

    private fun inputForVerdict(verdict: VerdictClass): MungerDecisionInput = when (verdict) {
        VerdictClass.BOCCIATO_QUALITATIVO -> MungerDecisionInput(
            ticker = "QUAL",
            ruleResults = allGreenRules(13),
            livelloRischio = LivelloRischio.RISCHIO_ESTREMO,
            newsSentimentDominante = SentimentClass.NEUTRAL,
            panicDiscount = false,
            deteriorationWarning = false,
        )
        VerdictClass.BOCCIATO_VALUE_TRAP -> MungerDecisionInput(
            ticker = "TRAP",
            ruleResults = allGreenRules(13),
            livelloRischio = LivelloRischio.RISCHIO_MODERATO,
            newsSentimentDominante = SentimentClass.STRUCTURAL_DAMAGE,
            panicDiscount = false,
            deteriorationWarning = false,
        )
        VerdictClass.BOCCIATO_NUMERICO -> MungerDecisionInput(
            ticker = "NUMS",
            ruleResults = rulesWithRedCount(5, 13),
            livelloRischio = LivelloRischio.RISCHIO_MODERATO,
            newsSentimentDominante = SentimentClass.NEUTRAL,
            panicDiscount = false,
            deteriorationWarning = false,
        )
        VerdictClass.APPROVATO_PANIC_BUY -> panicBuyInput()
        VerdictClass.APPROVATO -> approvedInput()
        VerdictClass.WATCHLIST -> watchlistInput()
    }

    // Test fixtures (TSK-311 EP-021): cycle attraverso i 13 ruleId canonici.
    // Vedi MungerDecisionServiceTest §CANONICAL_RULE_IDS per il razionale.
    private fun ruleIdAt(index: Int): String =
        CANONICAL_RULE_IDS[(index - 1) % CANONICAL_RULE_IDS.size]

    private fun ruleSignal(id: String, signal: Signal): RuleSignal = typedRuleSignal(id, signal)

    private fun allGreenRules(count: Int): List<RuleSignal> =
        (1..count).map { ruleSignal(ruleIdAt(it), Signal.GREEN) }

    private fun rulesWithRedCount(redCount: Int, total: Int): List<RuleSignal> {
        require(redCount <= total)
        return (1..redCount).map { ruleSignal(ruleIdAt(it), Signal.RED) } +
            (redCount + 1..total).map { ruleSignal(ruleIdAt(it), Signal.GREEN) }
    }

    private companion object {
        val CANONICAL_RULE_IDS: List<String> = listOf(
            "ROE_10Y_AVG", "ROIC_10Y_AVG", "GROSS_MARGIN_10Y_AVG", "NET_MARGIN_10Y_AVG",
            "CURRENT_RATIO_LATEST", "DEBT_TO_INCOME_LATEST", "CAPEX_INTENSITY_10Y_AVG",
            "SIZE_LATEST", "EARNINGS_STABILITY_10Y", "EPS_GROWTH_10Y",
            "PE_3Y_AVG", "PB_LATEST", "DIVIDEND_CONTINUITY_20Y",
        )
    }
}
