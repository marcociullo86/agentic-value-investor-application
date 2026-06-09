package com.valueinvesting.webapp.backtest

import com.valueinvesting.webapp.api.model.BacktestExitReason
import com.valueinvesting.webapp.api.model.BacktestStrategy
import com.valueinvesting.webapp.api.model.BacktestTimingEdgeLabel
import com.valueinvesting.webapp.fmp.dto.EodPriceRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

// Tests TSK-347 / TSK-349: round-trip causale uscita + metriche aggregate +
// timingEdge.
//
// [^src: management/kanban/EP-024-.../US-105-.../TSK-347.md §Scope]
// [^src: management/kanban/EP-024-.../US-105-.../TSK-349.md §"Test round-trip causale uscita", §"Test timingEdge"]
class BacktestRoundTripSimulatorTest {

    private val sim = BacktestRoundTripSimulator()

    /**
     * Helper: serie EOD lineare con close che varia tramite `fn(day)`.
     */
    private fun linearEod(start: LocalDate, days: Int, fn: (Int) -> Double): List<EodPriceRecord> =
        (0 until days).map { day ->
            EodPriceRecord(
                date = start.plusDays(day.toLong()),
                close = fn(day),
                open = fn(day),
                high = fn(day),
                low = fn(day),
                volume = 1_000_000L,
            )
        }

    @Test
    fun `round-trip closes on VI_TARGET when price reaches dcfIntrinsicValue`() {
        val entry = LocalDate.of(2024, 1, 2)
        // Prezzo cresce linearmente: 100 → 120 → 145.
        val eod = linearEod(entry, days = 200) { 100.0 + it * 0.5 }

        val trade = sim.simulateRoundTrip(
            strategy = BacktestStrategy.EP024_ENTER_NOW,
            entryDate = entry,
            entryPrice = 100.0,
            dcfIntrinsicValue = 130.0, // raggiunto verso giorno 60
            stopPrice = 90.0,
            horizonMonths = 6,
            sortedEod = eod,
        )
        assertThat(trade).isNotNull
        assertThat(trade!!.exitReason).isEqualTo(BacktestExitReason.VI_TARGET)
        assertThat(trade.exitPrice).isGreaterThanOrEqualTo(130.0)
        assertThat(trade.returnPct).isGreaterThan(0.0)
    }

    @Test
    fun `round-trip closes on STOP_HIT when price falls below stopPrice`() {
        val entry = LocalDate.of(2024, 1, 2)
        // Prezzo scende linearmente.
        val eod = linearEod(entry, days = 200) { 100.0 - it * 0.5 }

        val trade = sim.simulateRoundTrip(
            strategy = BacktestStrategy.EP024_ENTER_NOW,
            entryDate = entry,
            entryPrice = 100.0,
            dcfIntrinsicValue = 150.0, // mai raggiunto
            stopPrice = 90.0,
            horizonMonths = 6,
            sortedEod = eod,
        )
        assertThat(trade).isNotNull
        assertThat(trade!!.exitReason).isEqualTo(BacktestExitReason.STOP_HIT)
        assertThat(trade.exitPrice).isLessThanOrEqualTo(90.0)
        assertThat(trade.returnPct).isLessThan(0.0)
    }

    @Test
    fun `round-trip closes on HORIZON when neither target nor stop hit within horizon`() {
        val entry = LocalDate.of(2024, 1, 2)
        // Prezzo oscilla in un range che NON tocca ne target ne stop.
        val eod = linearEod(entry, days = 200) { 100.0 + (it % 5).toDouble() }

        val trade = sim.simulateRoundTrip(
            strategy = BacktestStrategy.EP024_ENTER_NOW,
            entryDate = entry,
            entryPrice = 100.0,
            dcfIntrinsicValue = 150.0,
            stopPrice = 90.0,
            horizonMonths = 3,
            sortedEod = eod,
        )
        assertThat(trade).isNotNull
        assertThat(trade!!.exitReason).isEqualTo(BacktestExitReason.HORIZON)
    }

    @Test
    fun `metricsFor empty trade list returns noSignalsInPeriod=true`() {
        val metrics = sim.metricsFor(BacktestStrategy.EP024_ENTER_NOW, emptyList())
        assertThat(metrics.trades).isEqualTo(0)
        assertThat(metrics.noSignalsInPeriod).isTrue
        assertThat(metrics.winRate).isNull()
        assertThat(metrics.avgReturnPct).isNull()
        assertThat(metrics.totalReturnPct).isNull()
    }

    @Test
    fun `timingEdge POSITIVE when EP024 avgReturnPct exceeds VI_ONLY by more than 2pp`() {
        val ep024 = sim.metricsFor(
            BacktestStrategy.EP024_ENTER_NOW,
            listOf(
                trade(entryPrice = 100.0, exitPrice = 110.0),
                trade(entryPrice = 100.0, exitPrice = 115.0),
            ),
        )
        val viOnly = sim.metricsFor(
            BacktestStrategy.VI_ONLY,
            listOf(
                trade(entryPrice = 100.0, exitPrice = 105.0),
                trade(entryPrice = 100.0, exitPrice = 100.0),
            ),
        )
        val edge = sim.computeTimingEdge(ep024, viOnly)
        // EP024 avg = 12.5%, VI_ONLY avg = 2.5% → edge = 10pp → POSITIVE.
        assertThat(edge.label).isEqualTo(BacktestTimingEdgeLabel.POSITIVE_EDGE)
        assertThat(edge.timingEdgePct).isCloseTo(10.0, org.assertj.core.api.Assertions.within(0.01))
        assertThat(edge.noSignalsInPeriod).isFalse
    }

    @Test
    fun `timingEdge NEGATIVE when EP024 avgReturnPct undershoots VI_ONLY by more than 2pp`() {
        val ep024 = sim.metricsFor(
            BacktestStrategy.EP024_ENTER_NOW,
            listOf(trade(entryPrice = 100.0, exitPrice = 95.0)),
        )
        val viOnly = sim.metricsFor(
            BacktestStrategy.VI_ONLY,
            listOf(trade(entryPrice = 100.0, exitPrice = 110.0)),
        )
        val edge = sim.computeTimingEdge(ep024, viOnly)
        assertThat(edge.label).isEqualTo(BacktestTimingEdgeLabel.NEGATIVE_EDGE)
    }

    @Test
    fun `timingEdge NEUTRAL when edge within plus-minus 2pp`() {
        val ep024 = sim.metricsFor(
            BacktestStrategy.EP024_ENTER_NOW,
            listOf(trade(entryPrice = 100.0, exitPrice = 105.0)),
        )
        val viOnly = sim.metricsFor(
            BacktestStrategy.VI_ONLY,
            listOf(trade(entryPrice = 100.0, exitPrice = 104.0)),
        )
        val edge = sim.computeTimingEdge(ep024, viOnly)
        assertThat(edge.label).isEqualTo(BacktestTimingEdgeLabel.NEUTRAL)
    }

    @Test
    fun `timingEdge with 0 EP024 signals returns NEUTRAL with noSignalsInPeriod=true`() {
        val ep024 = sim.metricsFor(BacktestStrategy.EP024_ENTER_NOW, emptyList())
        val viOnly = sim.metricsFor(
            BacktestStrategy.VI_ONLY,
            listOf(trade(entryPrice = 100.0, exitPrice = 110.0)),
        )
        val edge = sim.computeTimingEdge(ep024, viOnly)
        assertThat(edge.label).isEqualTo(BacktestTimingEdgeLabel.NEUTRAL)
        assertThat(edge.noSignalsInPeriod).isTrue
        assertThat(edge.timingEdgePct).isNull()
    }

    @Test
    fun `metricsFor computes winRate avgReturnPct median totalReturnPct correctly`() {
        val trades = listOf(
            trade(entryPrice = 100.0, exitPrice = 110.0), // +10%
            trade(entryPrice = 100.0, exitPrice = 105.0), // +5%
            trade(entryPrice = 100.0, exitPrice = 95.0),  // -5%
        )
        val metrics = sim.metricsFor(BacktestStrategy.EP024_ENTER_NOW, trades)
        assertThat(metrics.trades).isEqualTo(3)
        assertThat(metrics.winRate).isCloseTo(2.0 / 3.0, org.assertj.core.api.Assertions.within(0.01))
        assertThat(metrics.avgReturnPct).isCloseTo(10.0 / 3.0, org.assertj.core.api.Assertions.within(0.01))
        assertThat(metrics.medianReturnPct).isCloseTo(5.0, org.assertj.core.api.Assertions.within(0.01))
        // Composed: 1.10 * 1.05 * 0.95 = 1.09725 → 9.725%
        assertThat(metrics.totalReturnPct).isCloseTo(9.725, org.assertj.core.api.Assertions.within(0.01))
    }

    private fun trade(entryPrice: Double, exitPrice: Double): com.valueinvesting.webapp.api.model.BacktestTrade {
        val ret = ((exitPrice - entryPrice) / entryPrice) * 100.0
        return com.valueinvesting.webapp.api.model.BacktestTrade(
            strategy = BacktestStrategy.EP024_ENTER_NOW,
            entryDate = LocalDate.of(2024, 1, 2),
            entryPrice = entryPrice,
            exitDate = LocalDate.of(2024, 4, 2),
            exitPrice = exitPrice,
            exitReason = if (exitPrice >= entryPrice * 1.10) BacktestExitReason.VI_TARGET else BacktestExitReason.HORIZON,
            returnPct = ret,
            holdingDays = 90,
            maxIntraTradeDrawdownPct = if (ret < 0) -ret else 5.0,
        )
    }
}
