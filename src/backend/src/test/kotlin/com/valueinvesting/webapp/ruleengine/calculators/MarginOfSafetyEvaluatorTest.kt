package com.valueinvesting.webapp.ruleengine.calculators

import com.valueinvesting.webapp.ruleengine.Signal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MarginOfSafetyEvaluatorTest {

    private val evaluator = MarginOfSafetyEvaluator()

    @Test
    fun `price below 70 percent of dcf is GREEN`() {
        val mos = evaluator.evaluate(
            currentPrice = 60.0,
            dcfResult = DcfResult(intrinsicValue = 100.0, method = DcfMethod.GREENWALD, rationale = "ok"),
        )
        assertThat(mos.signal).isEqualTo(Signal.GREEN)
    }

    @Test
    fun `price between 70 and 100 percent is YELLOW`() {
        val mos = evaluator.evaluate(
            currentPrice = 80.0,
            dcfResult = DcfResult(intrinsicValue = 100.0, method = DcfMethod.GREENWALD, rationale = "ok"),
        )
        assertThat(mos.signal).isEqualTo(Signal.YELLOW)
    }

    @Test
    fun `price at or above dcf is RED`() {
        val mos = evaluator.evaluate(
            currentPrice = 110.0,
            dcfResult = DcfResult(intrinsicValue = 100.0, method = DcfMethod.GREENWALD, rationale = "ok"),
        )
        assertThat(mos.signal).isEqualTo(Signal.RED)
    }

    @Test
    fun `null dcf is NOT_CALCULABLE`() {
        val mos = evaluator.evaluate(
            currentPrice = 50.0,
            dcfResult = DcfResult(intrinsicValue = null, method = DcfMethod.NOT_APPLICABLE, rationale = "n/a"),
        )
        assertThat(mos.signal).isEqualTo(Signal.NOT_CALCULABLE)
    }
}
