package com.valueinvesting.webapp.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class PositionSizeCalculatorTest {

    private val calculator = PositionSizeCalculator()

    @Test
    fun `APPROVATO_PANIC_BUY with MoS 50 pct yields position in 5_5 to 6 pct`() {
        val result = calculator.calculate(VerdictClass.APPROVATO_PANIC_BUY, 50.0)

        assertThat(result.recommendedPct).isBetween(5.5, 6.0)
        assertThat(result.range).isEqualTo(4.0 to 6.0)
        assertThat(result.basisVerdict).isEqualTo(VerdictClass.APPROVATO_PANIC_BUY)
        assertThat(result.marginOfSafetyPct).isEqualTo(50.0)
    }

    @Test
    fun `APPROVATO with MoS 20 pct yields position approx 2_4 to 2_8 pct`() {
        val result = calculator.calculate(VerdictClass.APPROVATO, 20.0)

        assertThat(result.recommendedPct).isBetween(2.4, 2.8)
        assertThat(result.range).isEqualTo(2.0 to 4.0)
        assertThat(result.basisVerdict).isEqualTo(VerdictClass.APPROVATO)
    }

    @ParameterizedTest
    @EnumSource(
        value = VerdictClass::class,
        names = ["BOCCIATO_NUMERICO", "BOCCIATO_QUALITATIVO", "BOCCIATO_VALUE_TRAP"],
    )
    fun `BOCCIATO variants always yield 0 pct`(verdict: VerdictClass) {
        val result = calculator.calculate(verdict, 40.0)

        assertThat(result.recommendedPct).isEqualTo(0.0)
        assertThat(result.range).isEqualTo(0.0 to 0.0)
        assertThat(result.basisVerdict).isEqualTo(verdict)
    }

    @Test
    fun `WATCHLIST yields 0 pct`() {
        val result = calculator.calculate(VerdictClass.WATCHLIST, 30.0)

        assertThat(result.recommendedPct).isEqualTo(0.0)
    }

    @Test
    fun `range is always respected via clamping - APPROVATO_PANIC_BUY extreme MoS`() {
        val highMos = calculator.calculate(VerdictClass.APPROVATO_PANIC_BUY, 200.0)
        assertThat(highMos.recommendedPct).isLessThanOrEqualTo(6.0)

        val zeroMos = calculator.calculate(VerdictClass.APPROVATO_PANIC_BUY, 0.0)
        assertThat(zeroMos.recommendedPct).isGreaterThanOrEqualTo(4.0)
    }

    @Test
    fun `range is always respected via clamping - APPROVATO extreme MoS`() {
        val highMos = calculator.calculate(VerdictClass.APPROVATO, 150.0)
        assertThat(highMos.recommendedPct).isLessThanOrEqualTo(4.0)

        val zeroMos = calculator.calculate(VerdictClass.APPROVATO, 0.0)
        assertThat(zeroMos.recommendedPct).isGreaterThanOrEqualTo(2.0)
    }

    @Test
    fun `negative MoS clamps to range minimum`() {
        val result = calculator.calculate(VerdictClass.APPROVATO, -10.0)
        assertThat(result.recommendedPct).isEqualTo(2.0)
    }

    @Test
    fun `basisVerdict is always populated`() {
        VerdictClass.entries.forEach { verdict ->
            val result = calculator.calculate(verdict, 25.0)
            assertThat(result.basisVerdict).isEqualTo(verdict)
        }
    }

    @Test
    fun `disclaimer is present in result`() {
        val result = calculator.calculate(VerdictClass.APPROVATO, 30.0)
        assertThat(result.disclaimer).isEqualTo("Indicazione tecnica, non consiglio finanziario")
    }

    @Test
    fun `deterministic - same inputs produce same output`() {
        val results = (1..100).map {
            calculator.calculate(VerdictClass.APPROVATO_PANIC_BUY, 35.0)
        }
        val first = results.first()
        results.forEach { assertThat(it).isEqualTo(first) }
    }
}
