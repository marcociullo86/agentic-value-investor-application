package com.valueinvesting.webapp.service

import com.fasterxml.jackson.core.type.TypeReference
import com.valueinvesting.webapp.fmp.CachedPayload
import com.valueinvesting.webapp.fmp.FmpAdapter
import com.valueinvesting.webapp.fmp.FmpCacheService
import com.valueinvesting.webapp.fmp.dto.IncomeStatementDto
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

// Unit tests per HistoricalSeriesService (TSK-023, US-015).
//
// Strategia:
//   - Mock FmpCacheService.getOrFetch: la fetchFn lambda viene catturata e MAI
//     invocata in cache-hit-style tests; nei test "cache miss" la invochiamo
//     manualmente per coprire la wire-up FmpAdapter.getIncomeStatement.
//   - Mock FmpAdapter solo per i test che esercitano il fetchFn path.
class HistoricalSeriesServiceTest {

    private val adapter: FmpAdapter = mockk()
    private val cache: FmpCacheService = mockk()
    private val service = HistoricalSeriesService(adapter, cache)

    private val snapshotAt: Instant = Instant.parse("2026-05-22T10:00:00Z")

    // ---- Test 1 — 10 anni di dati AAPL → 10 punti, isMissing=false ovunque. --
    @Test
    fun `maps 10 years of complete data to 10 points with isMissing=false`() {
        val rows = (2015..2024).map { y ->
            IncomeStatementDto(
                symbol = "AAPL",
                calendarYear = y.toString(),
                date = "$y-12-31",
                revenue = 100_000.0 + y,
                netIncome = 20_000.0 + y,
            )
        }.reversed() // FMP returns latest-first

        every {
            cache.getOrFetch<IncomeStatementDto>("AAPL", "income-statement", any(), any())
        } returns CachedPayload(rows, snapshotAt, stale = false)

        val series = service.getSeries("AAPL")

        assertThat(series.ticker).isEqualTo("AAPL")
        assertThat(series.points).hasSize(10)
        assertThat(series.points).allSatisfy { p ->
            assertThat(p.isMissing).isFalse()
            assertThat(p.revenue).isNotNull()
            assertThat(p.netIncome).isNotNull()
        }
        assertThat(series.dataSnapshotAt).isEqualTo(snapshotAt)
    }

    // ---- Test 2 — revenue null → isMissing=true, valore NON interpolato. -----
    @Test
    fun `flags isMissing=true when revenue is null and does not interpolate`() {
        val rows = listOf(
            IncomeStatementDto(calendarYear = "2024", revenue = 500.0, netIncome = 50.0),
            IncomeStatementDto(calendarYear = "2023", revenue = null, netIncome = 40.0),
            IncomeStatementDto(calendarYear = "2022", revenue = 400.0, netIncome = 35.0),
        )
        every {
            cache.getOrFetch<IncomeStatementDto>("AAPL", "income-statement", any(), any())
        } returns CachedPayload(rows, snapshotAt, stale = false)

        val series = service.getSeries("AAPL")

        val y2023 = series.points.first { it.fiscalYear == 2023 }
        assertThat(y2023.isMissing).isTrue()
        // PATTERN §7: mai sostituire null con 0.0.
        assertThat(y2023.revenue).isNull()
        // netIncome resta valorizzato (non interpolato dagli altri anni).
        assertThat(y2023.netIncome).isEqualTo(40.0)

        val y2024 = series.points.first { it.fiscalYear == 2024 }
        assertThat(y2024.isMissing).isFalse()
    }

    // ---- Test 3 — netIncome null → isMissing=true. ---------------------------
    @Test
    fun `flags isMissing=true when netIncome is null`() {
        val rows = listOf(
            IncomeStatementDto(calendarYear = "2024", revenue = 1000.0, netIncome = null),
            IncomeStatementDto(calendarYear = "2023", revenue = 900.0, netIncome = 90.0),
        )
        every {
            cache.getOrFetch<IncomeStatementDto>("AAPL", "income-statement", any(), any())
        } returns CachedPayload(rows, snapshotAt, stale = false)

        val series = service.getSeries("AAPL")

        val y2024 = series.points.first { it.fiscalYear == 2024 }
        assertThat(y2024.isMissing).isTrue()
        assertThat(y2024.netIncome).isNull()
        assertThat(y2024.revenue).isEqualTo(1000.0)
    }

    // ---- Test 4 — ordinamento crescente sull'asse X. -------------------------
    @Test
    fun `sorts points chronologically ascending for chart x-axis`() {
        // FMP-style: latest-first (decreasing) input.
        val rows = listOf(
            IncomeStatementDto(calendarYear = "2024", revenue = 1.0, netIncome = 1.0),
            IncomeStatementDto(calendarYear = "2023", revenue = 2.0, netIncome = 2.0),
            IncomeStatementDto(calendarYear = "2022", revenue = 3.0, netIncome = 3.0),
            IncomeStatementDto(calendarYear = "2021", revenue = 4.0, netIncome = 4.0),
        )
        every {
            cache.getOrFetch<IncomeStatementDto>("AAPL", "income-statement", any(), any())
        } returns CachedPayload(rows, snapshotAt, stale = false)

        val series = service.getSeries("AAPL")

        assertThat(series.points.map { it.fiscalYear })
            .containsExactly(2021, 2022, 2023, 2024)
    }

    // ---- Test 5 — cache hit: NO chiamata adapter. ----------------------------
    @Test
    fun `does not call adapter when cache serves payload`() {
        val rows = listOf(
            IncomeStatementDto(calendarYear = "2024", revenue = 1.0, netIncome = 1.0),
        )
        // Setup: cache layer NON invoca la fetchFn (simulato non chiamando il lambda).
        every {
            cache.getOrFetch<IncomeStatementDto>("AAPL", "income-statement", any(), any())
        } returns CachedPayload(rows, snapshotAt, stale = false)

        service.getSeries("AAPL")

        // Verifica difensiva: adapter mai chiamato (mockk() throws on unconfigured call).
        verify(exactly = 0) { adapter.getIncomeStatement(any(), any()) }
    }

    // ---- Test 6 — dataset vuoto: points = emptyList, no eccezione. ----------
    @Test
    fun `returns empty points list when FMP has no income statements`() {
        every {
            cache.getOrFetch<IncomeStatementDto>("AAPL", "income-statement", any(), any())
        } returns CachedPayload(emptyList(), snapshotAt, stale = false)

        val series = service.getSeries("AAPL")

        assertThat(series.points).isEmpty()
        assertThat(series.ticker).isEqualTo("AAPL")
        assertThat(series.dataSnapshotAt).isEqualTo(snapshotAt)
    }

    // ---- Test 7 — > 10 anni: tronca ai 10 piu' recenti. ----------------------
    @Test
    fun `truncates to most recent 10 years when FMP returns more`() {
        // 12 anni 2013..2024 latest-first.
        val rows = (2013..2024).reversed().map { y ->
            IncomeStatementDto(
                calendarYear = y.toString(),
                revenue = y.toDouble(),
                netIncome = y.toDouble(),
            )
        }
        every {
            cache.getOrFetch<IncomeStatementDto>("AAPL", "income-statement", any(), any())
        } returns CachedPayload(rows, snapshotAt, stale = false)

        val series = service.getSeries("AAPL")

        assertThat(series.points).hasSize(10)
        // Piu' recenti -> 2015..2024 (i 10 piu' grandi).
        assertThat(series.points.first().fiscalYear).isEqualTo(2015)
        assertThat(series.points.last().fiscalYear).isEqualTo(2024)
    }

    // ---- Test ticker normalizzato uppercase -> service ricomputa l'arg cache. -
    @Test
    fun `uppercases ticker before delegating to cache`() {
        val tickerSlot = slot<String>()
        every {
            cache.getOrFetch<IncomeStatementDto>(
                capture(tickerSlot),
                "income-statement",
                any(),
                any(),
            )
        } returns CachedPayload(emptyList(), snapshotAt, stale = false)

        service.getSeries("aapl")

        assertThat(tickerSlot.captured).isEqualTo("AAPL")
    }

    // ---- Test fetchFn lambda chiama FmpAdapter con ticker uppercase + limit 10.
    @Test
    fun `fetchFn wired to FmpAdapter getIncomeStatement uppercase ticker and limit 10`() {
        val fetchFnSlot = slot<() -> List<IncomeStatementDto>>()
        every {
            cache.getOrFetch<IncomeStatementDto>(
                "AAPL",
                "income-statement",
                any<TypeReference<List<IncomeStatementDto>>>(),
                capture(fetchFnSlot),
            )
        } returns CachedPayload(emptyList(), snapshotAt, stale = false)
        every { adapter.getIncomeStatement("AAPL", 10) } returns emptyList()

        service.getSeries("aapl")

        // Invoca il fetchFn catturato per verificare la wire-up adapter (cache-miss path).
        fetchFnSlot.captured.invoke()
        verify(exactly = 1) { adapter.getIncomeStatement("AAPL", 10) }
    }

    // ---- Test fallback year from `date` quando calendarYear e' null. ---------
    @Test
    fun `falls back to date prefix when calendarYear is null`() {
        val rows = listOf(
            IncomeStatementDto(calendarYear = null, date = "2024-09-30", revenue = 1.0, netIncome = 1.0),
            IncomeStatementDto(calendarYear = null, date = "2023-09-30", revenue = 2.0, netIncome = 2.0),
        )
        every {
            cache.getOrFetch<IncomeStatementDto>("AAPL", "income-statement", any(), any())
        } returns CachedPayload(rows, snapshotAt, stale = false)

        val series = service.getSeries("AAPL")

        assertThat(series.points.map { it.fiscalYear }).containsExactly(2023, 2024)
    }

    // ---- Test rows senza anno (date e calendarYear nulli) sono scartate. -----
    @Test
    fun `drops rows with no parsable year`() {
        val rows = listOf(
            IncomeStatementDto(calendarYear = "2024", revenue = 1.0, netIncome = 1.0),
            IncomeStatementDto(calendarYear = null, date = null, revenue = 9.0, netIncome = 9.0),
            IncomeStatementDto(calendarYear = "abc", date = "not-iso", revenue = 8.0, netIncome = 8.0),
        )
        every {
            cache.getOrFetch<IncomeStatementDto>("AAPL", "income-statement", any(), any())
        } returns CachedPayload(rows, snapshotAt, stale = false)

        val series = service.getSeries("AAPL")

        assertThat(series.points).hasSize(1)
        assertThat(series.points.first().fiscalYear).isEqualTo(2024)
    }
}
