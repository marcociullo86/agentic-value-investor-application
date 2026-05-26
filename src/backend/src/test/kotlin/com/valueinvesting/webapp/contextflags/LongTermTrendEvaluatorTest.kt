package com.valueinvesting.webapp.contextflags

import com.valueinvesting.webapp.fmp.FmpAdapter
import com.valueinvesting.webapp.fmp.FmpUnavailableException
import com.valueinvesting.webapp.fmp.dto.TechnicalIndicatorRecord
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

// Unit tests for LongTermTrendEvaluator (TSK-166, US-057, EP-013).
//
// Covers US-057 Acceptance Criteria verbatim:
//   - priceVsSmaPct < -5%   → BELOW_TREND
//   - priceVsSmaPct > +20%  → ABOVE_TREND
//   - else                  → NEAR_TREND
//   - currentPrice null / sma null / FMP throws → INDETERMINATE
//
// Boundary rules verified against LongTermTrendEvaluator.kt impl:
//   - BELOW_TREND_THRESHOLD  = -0.05 (strict `<`)   → -0.05 exact = NEAR_TREND
//   - ABOVE_TREND_THRESHOLD  = 0.20  (strict `>`)   → +0.20 exact = NEAR_TREND
//
// priceVsSmaPct = (currentPrice - sma) / sma (computed inside evaluator).
//
// Pattern: MockK for FmpAdapter; no Spring context needed.
//
// [^src: management/kanban/EP-013-mr-market-context-flags/US-057-sma200-trend-context-flag/TSK-166.md]
// [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/contextflags/LongTermTrendEvaluator.kt]
class LongTermTrendEvaluatorTest {

    private val fmpAdapter: FmpAdapter = mockk()
    private val evaluator = LongTermTrendEvaluator(fmpAdapter)

    // Helper to build a single SMA record
    private fun singleSmaRecord(value: Double, date: String = "2026-05-26 16:00:00") =
        listOf(TechnicalIndicatorRecord(date = date, value = value))

    // -------------------------------------------------------------------------
    // Test 1: BELOW_TREND — price=80, sma=100 → pct=-0.20 (< -0.05)
    // -------------------------------------------------------------------------

    @Test
    fun `evaluate returns BELOW_TREND when price is 80 and sma200 is 100 (pct=-20%)`() {
        every {
            fmpAdapter.getTechnicalIndicator(
                ticker = "AAPL",
                indicator = "sma",
                periodLength = 200,
                timeframe = "1day",
            )
        } returns singleSmaRecord(100.0)

        val result = evaluator.evaluate("AAPL", currentPrice = 80.0)

        assertAll(
            { assertThat(result.flag).isEqualTo(LongTermTrendSignal.BELOW_TREND) },
            { assertThat(result.currentPrice).isEqualTo(80.0) },
            { assertThat(result.sma200Latest).isEqualTo(100.0) },
            // pct = (80 - 100) / 100 = -0.20
            { assertThat(result.priceVsSmaPct).isCloseTo(-0.20, within(1e-9)) },
            { assertThat(result.periodLength).isEqualTo(200) },
            { assertThat(result.timeframe).isEqualTo("1day") },
        )
        verify(exactly = 1) { fmpAdapter.getTechnicalIndicator(any(), any(), any(), any()) }
    }

    // -------------------------------------------------------------------------
    // Test 2: NEAR_TREND — price=100, sma=98 → pct≈+2% (in [-5%, +20%])
    // -------------------------------------------------------------------------

    @Test
    fun `evaluate returns NEAR_TREND when price is 100 and sma200 is 98 (pct approx +2%)`() {
        every {
            fmpAdapter.getTechnicalIndicator(any(), any(), any(), any())
        } returns singleSmaRecord(98.0)

        val result = evaluator.evaluate("MSFT", currentPrice = 100.0)

        assertAll(
            { assertThat(result.flag).isEqualTo(LongTermTrendSignal.NEAR_TREND) },
            // pct = (100 - 98) / 98 ≈ +0.0204
            { assertThat(result.priceVsSmaPct!!).isCloseTo(0.0204, within(0.001)) },
        )
    }

    // -------------------------------------------------------------------------
    // Test 3: ABOVE_TREND — price=150, sma=100 → pct=+50% (> +20%)
    // -------------------------------------------------------------------------

    @Test
    fun `evaluate returns ABOVE_TREND when price is 150 and sma200 is 100 (pct=+50%)`() {
        every {
            fmpAdapter.getTechnicalIndicator(any(), any(), any(), any())
        } returns singleSmaRecord(100.0)

        val result = evaluator.evaluate("NVDA", currentPrice = 150.0)

        assertAll(
            { assertThat(result.flag).isEqualTo(LongTermTrendSignal.ABOVE_TREND) },
            { assertThat(result.priceVsSmaPct).isCloseTo(0.50, within(1e-9)) },
        )
    }

    // -------------------------------------------------------------------------
    // Test 4: Boundary BELOW=-5% exact — impl uses strict `<-0.05`, so -0.05 = NEAR_TREND
    // -------------------------------------------------------------------------

    @Test
    fun `evaluate returns NEAR_TREND at boundary pct=-5% exact (strict less-than rule)`() {
        // LongTermTrendEvaluator.kt: `pct < BELOW_TREND_THRESHOLD` where BELOW_TREND_THRESHOLD=-0.05
        // Therefore pct=-0.05 is NOT BELOW_TREND (strict less-than), falls into `else` → NEAR_TREND.
        // price=95, sma=100 → pct = (95-100)/100 = -0.05 exactly.
        every {
            fmpAdapter.getTechnicalIndicator(any(), any(), any(), any())
        } returns singleSmaRecord(100.0)

        val result = evaluator.evaluate("AAPL", currentPrice = 95.0)

        assertAll(
            { assertThat(result.flag).isEqualTo(LongTermTrendSignal.NEAR_TREND) },
            { assertThat(result.flag).isNotEqualTo(LongTermTrendSignal.BELOW_TREND) },
            { assertThat(result.priceVsSmaPct).isCloseTo(-0.05, within(1e-9)) },
        )
    }

    // -------------------------------------------------------------------------
    // Test 5: Boundary ABOVE=+20% exact — impl uses strict `>0.20`, so +0.20 = NEAR_TREND
    // -------------------------------------------------------------------------

    @Test
    fun `evaluate returns NEAR_TREND at boundary pct=+20% exact (strict greater-than rule)`() {
        // LongTermTrendEvaluator.kt: `pct > ABOVE_TREND_THRESHOLD` where ABOVE_TREND_THRESHOLD=0.20
        // Therefore pct=+0.20 is NOT ABOVE_TREND (strict greater-than), falls into `else` → NEAR_TREND.
        // price=120, sma=100 → pct = (120-100)/100 = +0.20 exactly.
        every {
            fmpAdapter.getTechnicalIndicator(any(), any(), any(), any())
        } returns singleSmaRecord(100.0)

        val result = evaluator.evaluate("AAPL", currentPrice = 120.0)

        assertAll(
            { assertThat(result.flag).isEqualTo(LongTermTrendSignal.NEAR_TREND) },
            { assertThat(result.flag).isNotEqualTo(LongTermTrendSignal.ABOVE_TREND) },
            { assertThat(result.priceVsSmaPct).isCloseTo(0.20, within(1e-9)) },
        )
    }

    // -------------------------------------------------------------------------
    // Test 6: INDETERMINATE — currentPrice=null (no HTTP call to FMP)
    // -------------------------------------------------------------------------

    @Test
    fun `evaluate returns INDETERMINATE when currentPrice is null (no FMP call)`() {
        // Evaluator short-circuits before calling FmpAdapter when currentPrice is null.
        val result = evaluator.evaluate("AAPL", currentPrice = null)

        assertAll(
            { assertThat(result.flag).isEqualTo(LongTermTrendSignal.INDETERMINATE) },
            { assertThat(result.priceVsSmaPct).isNull() },
            { assertThat(result.sma200Latest).isNull() },
            { assertThat(result.currentPrice).isNull() },
        )
        // No interaction with FmpAdapter (guard before network call)
        verify(exactly = 0) { fmpAdapter.getTechnicalIndicator(any(), any(), any(), any()) }
    }

    // -------------------------------------------------------------------------
    // Test 7: INDETERMINATE — SMA value is null in the record
    // -------------------------------------------------------------------------

    @Test
    fun `evaluate returns INDETERMINATE when SMA record value is null`() {
        // FMP returns a record but `value` field is null (FMP data gap).
        every {
            fmpAdapter.getTechnicalIndicator(any(), any(), any(), any())
        } returns listOf(TechnicalIndicatorRecord(date = "2026-05-26 16:00:00", value = null))

        val result = evaluator.evaluate("AAPL", currentPrice = 150.0)

        assertAll(
            { assertThat(result.flag).isEqualTo(LongTermTrendSignal.INDETERMINATE) },
            { assertThat(result.priceVsSmaPct).isNull() },
            { assertThat(result.sma200Latest).isNull() },
        )
    }

    // -------------------------------------------------------------------------
    // Test 8: INDETERMINATE — FmpAdapter throws FmpUnavailableException (runCatching catch)
    // -------------------------------------------------------------------------

    @Test
    fun `evaluate returns INDETERMINATE when FmpAdapter throws FmpUnavailableException`() {
        // Simulates circuit-open / 5xx / 429 scenario.
        // LongTermTrendEvaluator wraps the call in runCatching → degrades to INDETERMINATE
        // to preserve primary analysis output (13 signals + MoS).
        every {
            fmpAdapter.getTechnicalIndicator(any(), any(), any(), any())
        } throws FmpUnavailableException("FMP 500", httpStatus = 500)

        val result = evaluator.evaluate("AAPL", currentPrice = 150.0)

        assertAll(
            { assertThat(result.flag).isEqualTo(LongTermTrendSignal.INDETERMINATE) },
            { assertThat(result.priceVsSmaPct).isNull() },
            // currentPrice is preserved in INDETERMINATE result (passed explicitly by caller)
            { assertThat(result.currentPrice).isEqualTo(150.0) },
        )
        verify(exactly = 1) { fmpAdapter.getTechnicalIndicator(any(), any(), any(), any()) }
    }
}
