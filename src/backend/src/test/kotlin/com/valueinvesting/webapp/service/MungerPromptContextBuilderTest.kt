package com.valueinvesting.webapp.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class MungerPromptContextBuilderTest {

    // ── Both ROE values present, no divergence ──

    @Nested
    inner class BothValuesPresent {

        @Test
        fun `includes both ROE lines with formatted percentages`() {
            val ctx = MungerPromptContextBuilder.buildRoeContext(
                fiveYearAvg = 0.265,
                tenYearAvg = 0.312,
            )

            assertThat(ctx).contains("ROE 5y: 26.5% (growth/turnaround signal).")
            assertThat(ctx).contains("ROE 10y: 31.2% (Graham defensive stability signal).")
        }

        @Test
        fun `no divergence note when difference is within 5pp`() {
            val ctx = MungerPromptContextBuilder.buildRoeContext(
                fiveYearAvg = 0.265,
                tenYearAvg = 0.312,
            )

            assertThat(ctx).doesNotContain("divergenza significativa")
        }

        @Test
        fun `no divergence note when difference is exactly 5pp`() {
            val ctx = MungerPromptContextBuilder.buildRoeContext(
                fiveYearAvg = 0.20,
                tenYearAvg = 0.25,
            )

            assertThat(ctx).doesNotContain("divergenza significativa")
        }
    }

    // ── Divergence > 5pp ──

    @Nested
    inner class DivergenceNote {

        @Test
        fun `divergence note present when 5y minus 10y exceeds 5pp`() {
            val ctx = MungerPromptContextBuilder.buildRoeContext(
                fiveYearAvg = 0.30,
                tenYearAvg = 0.18,
            )

            assertThat(ctx).contains("ROE 5y: 30.0% (growth/turnaround signal).")
            assertThat(ctx).contains("ROE 10y: 18.0% (Graham defensive stability signal).")
            assertThat(ctx).contains(
                "Discutere divergenza significativa (|5y − 10y| > 5pp) " +
                    "come indicatore di cambio strutturale del business.",
            )
        }

        @Test
        fun `divergence note present when 10y exceeds 5y by more than 5pp`() {
            val ctx = MungerPromptContextBuilder.buildRoeContext(
                fiveYearAvg = 0.10,
                tenYearAvg = 0.22,
            )

            assertThat(ctx).contains("divergenza significativa")
        }

        @Test
        fun `divergence note absent when difference is just above 5pp boundary`() {
            // |0.200 - 0.1499| * 100 = 5.01 pp → divergence note should appear
            val ctx = MungerPromptContextBuilder.buildRoeContext(
                fiveYearAvg = 0.200,
                tenYearAvg = 0.1499,
            )

            assertThat(ctx).contains("divergenza significativa")
        }
    }

    // ── Null handling (N/A) ──

    @Nested
    inner class NullHandling {

        @Test
        fun `fiveYearAvg null shows N-A placeholder`() {
            val ctx = MungerPromptContextBuilder.buildRoeContext(
                fiveYearAvg = null,
                tenYearAvg = 0.25,
            )

            assertThat(ctx).contains("ROE 5y: N/A (dati insufficienti) (growth/turnaround signal).")
            assertThat(ctx).contains("ROE 10y: 25.0% (Graham defensive stability signal).")
            assertThat(ctx).doesNotContain("divergenza significativa")
        }

        @Test
        fun `tenYearAvg null shows N-A placeholder`() {
            val ctx = MungerPromptContextBuilder.buildRoeContext(
                fiveYearAvg = 0.15,
                tenYearAvg = null,
            )

            assertThat(ctx).contains("ROE 5y: 15.0% (growth/turnaround signal).")
            assertThat(ctx).contains("ROE 10y: N/A (dati insufficienti) (Graham defensive stability signal).")
            assertThat(ctx).doesNotContain("divergenza significativa")
        }

        @Test
        fun `both null show N-A placeholders and no divergence note`() {
            val ctx = MungerPromptContextBuilder.buildRoeContext(
                fiveYearAvg = null,
                tenYearAvg = null,
            )

            assertThat(ctx).contains("ROE 5y: N/A (dati insufficienti)")
            assertThat(ctx).contains("ROE 10y: N/A (dati insufficienti)")
            assertThat(ctx).doesNotContain("divergenza significativa")
        }
    }

    // ── Edge cases ──

    @Nested
    inner class EdgeCases {

        @Test
        fun `negative ROE values are formatted correctly`() {
            val ctx = MungerPromptContextBuilder.buildRoeContext(
                fiveYearAvg = -0.05,
                tenYearAvg = 0.12,
            )

            assertThat(ctx).contains("ROE 5y: -5.0%")
            assertThat(ctx).contains("ROE 10y: 12.0%")
            assertThat(ctx).contains("divergenza significativa")
        }

        @Test
        fun `zero ROE values are formatted`() {
            val ctx = MungerPromptContextBuilder.buildRoeContext(
                fiveYearAvg = 0.0,
                tenYearAvg = 0.0,
            )

            assertThat(ctx).contains("ROE 5y: 0.0%")
            assertThat(ctx).contains("ROE 10y: 0.0%")
            assertThat(ctx).doesNotContain("divergenza significativa")
        }

        @Test
        fun `identical non-zero values produce no divergence note`() {
            val ctx = MungerPromptContextBuilder.buildRoeContext(
                fiveYearAvg = 0.185,
                tenYearAvg = 0.185,
            )

            assertThat(ctx).doesNotContain("divergenza significativa")
        }
    }
}
