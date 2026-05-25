package com.valueinvesting.webapp.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Golden tests: verifies the exact prompt strings produced by
 * MungerPromptContextBuilder when integrated with RoeCalculator outputs
 * matching TSK-163 specified fixtures.
 *
 * These tests complement MungerPromptContextBuilderTest (unit) by verifying
 * the precise golden strings the Munger LLM receives for canonical scenarios.
 *
 * [^src: design_&_architecture/decisions/ADR-020-roe-lookback-policy-deep-analysis.md §Input al report Munger LLM]
 * [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-045-endpoint-deep-analysis/TSK-163.md §Golden test prompt Munger]
 */
class MungerPromptGoldenTest {

    @Test
    @DisplayName("Divergence > 5pp: prompt contains both ROE values and divergence note")
    fun `golden - divergence fixture fiveYearAvg 030 tenYearAvg 018`() {
        val prompt = MungerPromptContextBuilder.buildRoeContext(
            fiveYearAvg = 0.30,
            tenYearAvg = 0.18,
        )

        assertThat(prompt).isEqualTo(
            "ROE 5y: 30.0% (growth/turnaround signal).\n" +
                "ROE 10y: 18.0% (Graham defensive stability signal).\n" +
                "Discutere divergenza significativa (|5y − 10y| > 5pp) " +
                "come indicatore di cambio strutturale del business.",
        )
    }

    @Test
    @DisplayName("IPO recent (fiveYearAvg null): prompt shows N/A placeholder")
    fun `golden - IPO recent fiveYearAvg null tenYearAvg present`() {
        val prompt = MungerPromptContextBuilder.buildRoeContext(
            fiveYearAvg = null,
            tenYearAvg = 0.25,
        )

        assertThat(prompt).isEqualTo(
            "ROE 5y: N/A (dati insufficienti) (growth/turnaround signal).\n" +
                "ROE 10y: 25.0% (Graham defensive stability signal).",
        )
    }

    @Test
    @DisplayName("Both null: prompt shows N/A for both, no divergence note")
    fun `golden - both null shows dual NA placeholders`() {
        val prompt = MungerPromptContextBuilder.buildRoeContext(
            fiveYearAvg = null,
            tenYearAvg = null,
        )

        assertThat(prompt).isEqualTo(
            "ROE 5y: N/A (dati insufficienti) (growth/turnaround signal).\n" +
                "ROE 10y: N/A (dati insufficienti) (Graham defensive stability signal).",
        )
    }

    @Test
    @DisplayName("No divergence (within 5pp): prompt has both values but no divergence note")
    fun `golden - no divergence within threshold`() {
        val prompt = MungerPromptContextBuilder.buildRoeContext(
            fiveYearAvg = 0.20,
            tenYearAvg = 0.22,
        )

        assertThat(prompt).isEqualTo(
            "ROE 5y: 20.0% (growth/turnaround signal).\n" +
                "ROE 10y: 22.0% (Graham defensive stability signal).",
        )
    }

    @Test
    @DisplayName("Negative 5y ROE with large divergence from positive 10y")
    fun `golden - negative five year avg with divergence`() {
        val prompt = MungerPromptContextBuilder.buildRoeContext(
            fiveYearAvg = -0.08,
            tenYearAvg = 0.15,
        )

        assertThat(prompt).isEqualTo(
            "ROE 5y: -8.0% (growth/turnaround signal).\n" +
                "ROE 10y: 15.0% (Graham defensive stability signal).\n" +
                "Discutere divergenza significativa (|5y − 10y| > 5pp) " +
                "come indicatore di cambio strutturale del business.",
        )
    }
}
