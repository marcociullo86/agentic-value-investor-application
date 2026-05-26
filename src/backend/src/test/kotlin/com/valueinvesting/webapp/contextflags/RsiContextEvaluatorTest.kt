package com.valueinvesting.webapp.contextflags

import com.valueinvesting.webapp.fmp.FmpAdapter
import com.valueinvesting.webapp.fmp.FmpUnavailableException
import com.valueinvesting.webapp.fmp.dto.TechnicalIndicatorRecord
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

// Unit tests for RsiContextEvaluator (TSK-165, US-056, EP-013).
//
// Covers US-056 Acceptance Criteria verbatim:
//   - RSI < 30    → OVERSOLD
//   - RSI > 70    → OVERBOUGHT
//   - else        → NEUTRAL
//   - No data     → INDETERMINATE (empty list or FmpAdapter throws)
//
// Boundary rules verified against RsiContextEvaluator.kt impl:
//   - OVERSOLD_THRESHOLD  = 30.0 (strict `<`)  → 30.0 exact = NEUTRAL
//   - OVERBOUGHT_THRESHOLD = 70.0 (strict `>`) → 70.0 exact = NEUTRAL
//
// Pattern: MockK for FmpAdapter; no Spring context needed.
//
// [^src: management/kanban/EP-013-mr-market-context-flags/US-056-rsi-mr-market-context-flag/TSK-165.md]
// [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/contextflags/RsiContextEvaluator.kt]
class RsiContextEvaluatorTest {

    private val fmpAdapter: FmpAdapter = mockk()
    private val evaluator = RsiContextEvaluator(fmpAdapter)

    // Helper to build a single-record response for a given RSI value
    private fun singleRsiRecord(value: Double, date: String = "2026-05-26 16:00:00") =
        listOf(TechnicalIndicatorRecord(date = date, value = value))

    // -------------------------------------------------------------------------
    // Test 1: OVERSOLD — RSI 25.0 (< 30 threshold, strictly below)
    // -------------------------------------------------------------------------

    @Test
    fun `evaluate returns OVERSOLD when RSI is 25 (strictly below 30 threshold)`() {
        every {
            fmpAdapter.getTechnicalIndicator(
                ticker = "AAPL",
                indicator = "rsi",
                periodLength = 14,
                timeframe = "1day",
            )
        } returns singleRsiRecord(25.0)

        val result = evaluator.evaluate("AAPL")

        assertAll(
            { assertThat(result.flag).isEqualTo(MrMarketRsiSignal.OVERSOLD) },
            { assertThat(result.rsiLatest).isEqualTo(25.0) },
            { assertThat(result.rsiTimestamp).isEqualTo("2026-05-26 16:00:00") },
            { assertThat(result.periodLength).isEqualTo(14) },
            { assertThat(result.timeframe).isEqualTo("1day") },
        )
        verify(exactly = 1) { fmpAdapter.getTechnicalIndicator(any(), any(), any(), any()) }
    }

    // -------------------------------------------------------------------------
    // Test 2: NEUTRAL — RSI 50.0 (in range [30, 70])
    // -------------------------------------------------------------------------

    @Test
    fun `evaluate returns NEUTRAL when RSI is 50 (mid-range)`() {
        every {
            fmpAdapter.getTechnicalIndicator(any(), any(), any(), any())
        } returns singleRsiRecord(50.0)

        val result = evaluator.evaluate("MSFT")

        assertAll(
            { assertThat(result.flag).isEqualTo(MrMarketRsiSignal.NEUTRAL) },
            { assertThat(result.rsiLatest).isEqualTo(50.0) },
        )
    }

    // -------------------------------------------------------------------------
    // Test 3: OVERBOUGHT — RSI 75.0 (> 70 threshold, strictly above)
    // -------------------------------------------------------------------------

    @Test
    fun `evaluate returns OVERBOUGHT when RSI is 75 (strictly above 70 threshold)`() {
        every {
            fmpAdapter.getTechnicalIndicator(any(), any(), any(), any())
        } returns singleRsiRecord(75.0)

        val result = evaluator.evaluate("NVDA")

        assertAll(
            { assertThat(result.flag).isEqualTo(MrMarketRsiSignal.OVERBOUGHT) },
            { assertThat(result.rsiLatest).isEqualTo(75.0) },
        )
    }

    // -------------------------------------------------------------------------
    // Test 4: Boundary OVERSOLD=30 exact — impl uses strict `<30`, so 30.0 = NEUTRAL
    // -------------------------------------------------------------------------

    @Test
    fun `evaluate returns NEUTRAL at boundary RSI 30 exact (strict less-than rule)`() {
        // RsiContextEvaluator.kt: `value < OVERSOLD_THRESHOLD` with OVERSOLD_THRESHOLD=30.0
        // Therefore 30.0 is NOT OVERSOLD (strict less-than), it falls into `else` → NEUTRAL.
        every {
            fmpAdapter.getTechnicalIndicator(any(), any(), any(), any())
        } returns singleRsiRecord(30.0)

        val result = evaluator.evaluate("AAPL")

        assertAll(
            { assertThat(result.flag).isEqualTo(MrMarketRsiSignal.NEUTRAL) },
            { assertThat(result.flag).isNotEqualTo(MrMarketRsiSignal.OVERSOLD) },
            { assertThat(result.rsiLatest).isEqualTo(30.0) },
        )
    }

    // -------------------------------------------------------------------------
    // Test 5: Boundary OVERBOUGHT=70 exact — impl uses strict `>70`, so 70.0 = NEUTRAL
    // -------------------------------------------------------------------------

    @Test
    fun `evaluate returns NEUTRAL at boundary RSI 70 exact (strict greater-than rule)`() {
        // RsiContextEvaluator.kt: `value > OVERBOUGHT_THRESHOLD` with OVERBOUGHT_THRESHOLD=70.0
        // Therefore 70.0 is NOT OVERBOUGHT (strict greater-than), it falls into `else` → NEUTRAL.
        every {
            fmpAdapter.getTechnicalIndicator(any(), any(), any(), any())
        } returns singleRsiRecord(70.0)

        val result = evaluator.evaluate("AAPL")

        assertAll(
            { assertThat(result.flag).isEqualTo(MrMarketRsiSignal.NEUTRAL) },
            { assertThat(result.flag).isNotEqualTo(MrMarketRsiSignal.OVERBOUGHT) },
            { assertThat(result.rsiLatest).isEqualTo(70.0) },
        )
    }

    // -------------------------------------------------------------------------
    // Test 6: INDETERMINATE — FmpAdapter returns empty list (ticker IPO / no data)
    // -------------------------------------------------------------------------

    @Test
    fun `evaluate returns INDETERMINATE with rsiLatest null when records list is empty`() {
        every {
            fmpAdapter.getTechnicalIndicator(any(), any(), any(), any())
        } returns emptyList()

        val result = evaluator.evaluate("NEWIPO")

        assertAll(
            { assertThat(result.flag).isEqualTo(MrMarketRsiSignal.INDETERMINATE) },
            { assertThat(result.rsiLatest).isNull() },
            { assertThat(result.rsiTimestamp).isNull() },
        )
    }

    // -------------------------------------------------------------------------
    // Test 7: INDETERMINATE — FmpAdapter throws FmpUnavailableException (runCatching catch)
    // -------------------------------------------------------------------------

    @Test
    fun `evaluate returns INDETERMINATE when FmpAdapter throws FmpUnavailableException`() {
        // Simulates circuit-open / 5xx / 429 scenario.
        // RsiContextEvaluator wraps the call in runCatching → degrades to INDETERMINATE
        // rather than propagating the exception. This ensures the 13 rule signals
        // are never blocked by a context-flag failure.
        every {
            fmpAdapter.getTechnicalIndicator(any(), any(), any(), any())
        } throws FmpUnavailableException("FMP rate limited", httpStatus = 429)

        val result = evaluator.evaluate("AAPL")

        assertAll(
            { assertThat(result.flag).isEqualTo(MrMarketRsiSignal.INDETERMINATE) },
            { assertThat(result.rsiLatest).isNull() },
        )
        // Adapter was called exactly once (no retry inside evaluator — retry is Resilience4j's job)
        verify(exactly = 1) { fmpAdapter.getTechnicalIndicator(any(), any(), any(), any()) }
    }
}
