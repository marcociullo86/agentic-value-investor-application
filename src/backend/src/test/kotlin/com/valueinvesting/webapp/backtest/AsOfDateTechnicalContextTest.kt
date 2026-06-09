package com.valueinvesting.webapp.backtest

import com.valueinvesting.webapp.fmp.dto.EodPriceRecord
import com.valueinvesting.webapp.fmp.dto.TechnicalIndicatorRecord
import com.valueinvesting.webapp.technicalanalysis.EntryTimingAdvisor
import com.valueinvesting.webapp.technicalanalysis.StopPlacementAdvisor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

// Tests TSK-346 / TSK-349: indicatori TA calcolati SOLO su EOD ≤ t
// (anti look-ahead).
//
// [^src: management/kanban/EP-024-.../US-105-.../TSK-346.md §"Indicatori TA as-of-date"]
// [^src: management/kanban/EP-024-.../US-105-.../TSK-349.md §"Test no look-ahead"]
class AsOfDateTechnicalContextTest {

    private val ctx = AsOfDateTechnicalContext(
        entryTimingAdvisor = EntryTimingAdvisor(),
        stopPlacementAdvisor = StopPlacementAdvisor(),
    )

    @Test
    fun `currentPrice at asOf is the latest EOD on or before asOf — future EOD excluded`() {
        val series = listOf(
            EodPriceRecord(date = LocalDate.of(2024, 1, 10), close = 100.0),
            EodPriceRecord(date = LocalDate.of(2024, 6, 15), close = 110.0),
            EodPriceRecord(date = LocalDate.of(2024, 9, 30), close = 120.0),
            // Future close: NEVER used at asOf=2024-06-30.
            EodPriceRecord(date = LocalDate.of(2024, 12, 31), close = 200.0),
        )
        val snap = ctx.snapshotAt(
            asOf = LocalDate.of(2024, 6, 30),
            fullEodSeries = series,
            fullSma50 = emptyList(),
            fullSma200 = emptyList(),
            fullRsi = emptyList(),
            fullMacdDaily = emptyList(),
            fullMacdWeekly = emptyList(),
            fullAtr = emptyList(),
        )
        assertThat(snap.currentPrice).isEqualTo(110.0)
        assertThat(snap.historyDaysAvailable).isEqualTo(2)
    }

    @Test
    fun `indicator records with date after asOf are excluded`() {
        val rsi = listOf(
            TechnicalIndicatorRecord(date = "2024-03-15", value = 45.0),
            TechnicalIndicatorRecord(date = "2024-06-15", value = 55.0),
            // Future: NEVER used at asOf=2024-06-30.
            TechnicalIndicatorRecord(date = "2024-09-15", value = 75.0),
        )
        val series = (1..300).map {
            EodPriceRecord(
                date = LocalDate.of(2024, 1, 1).plusDays(it.toLong()),
                close = 100.0 + it,
            )
        }
        val snap = ctx.snapshotAt(
            asOf = LocalDate.of(2024, 6, 30),
            fullEodSeries = series,
            fullSma50 = emptyList(),
            fullSma200 = emptyList(),
            fullRsi = rsi,
            fullMacdDaily = emptyList(),
            fullMacdWeekly = emptyList(),
            fullAtr = emptyList(),
        )
        assertThat(snap.rsi14).isEqualTo(55.0)
    }

    @Test
    fun `closeOnOrAfter returns first trading day at or after target`() {
        val series = listOf(
            EodPriceRecord(date = LocalDate.of(2024, 1, 4), close = 50.0),
            EodPriceRecord(date = LocalDate.of(2024, 1, 8), close = 51.0),
            EodPriceRecord(date = LocalDate.of(2024, 1, 11), close = 52.0),
        )
        // Target Sat 2024-01-06: il primo close ≥ e' Mon 2024-01-08.
        val result = ctx.closeOnOrAfter(series, LocalDate.of(2024, 1, 6))
        assertThat(result).isNotNull
        assertThat(result!!.first).isEqualTo(LocalDate.of(2024, 1, 8))
        assertThat(result.second).isEqualTo(51.0)
    }

    @Test
    fun `closeOnOrAfter returns null when target is past end of series`() {
        val series = listOf(EodPriceRecord(date = LocalDate.of(2024, 1, 4), close = 50.0))
        val result = ctx.closeOnOrAfter(series, LocalDate.of(2025, 1, 1))
        assertThat(result).isNull()
    }

    @Test
    fun `closeOnOrBefore returns last trading day at or before target`() {
        val series = listOf(
            EodPriceRecord(date = LocalDate.of(2024, 1, 4), close = 50.0),
            EodPriceRecord(date = LocalDate.of(2024, 1, 8), close = 51.0),
            EodPriceRecord(date = LocalDate.of(2024, 1, 11), close = 52.0),
        )
        // Target Wed 2024-01-10: l'ultimo close ≤ e' Mon 2024-01-08.
        val result = ctx.closeOnOrBefore(series, LocalDate.of(2024, 1, 10))
        assertThat(result).isNotNull
        assertThat(result!!.first).isEqualTo(LocalDate.of(2024, 1, 8))
        assertThat(result.second).isEqualTo(51.0)
    }
}
