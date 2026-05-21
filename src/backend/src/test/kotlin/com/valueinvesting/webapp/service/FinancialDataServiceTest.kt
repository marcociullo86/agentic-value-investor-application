package com.valueinvesting.webapp.service

import com.fasterxml.jackson.core.type.TypeReference
import com.valueinvesting.webapp.fmp.CachedPayload
import com.valueinvesting.webapp.fmp.FmpAdapter
import com.valueinvesting.webapp.fmp.FmpCacheService
import com.valueinvesting.webapp.fmp.FmpEventLogger
import com.valueinvesting.webapp.fmp.FmpTickerNotFoundException
import com.valueinvesting.webapp.fmp.FmpUnavailableException
import com.valueinvesting.webapp.fmp.dto.BalanceSheetDto
import com.valueinvesting.webapp.fmp.dto.CashFlowDto
import com.valueinvesting.webapp.fmp.dto.IncomeStatementDto
import com.valueinvesting.webapp.fmp.dto.KeyMetricsDto
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

// Unit tests for the FinancialDataService facade post-TSK-010.
// The service now delegates ALL 4 statement calls through FmpCacheService — these
// tests stub the cache and assert the facade still:
//   - uppercases the ticker
//   - propagates FmpTickerNotFoundException
//   - rejects blank input before any downstream call
//   - stamps dataSnapshotAt = MIN(fetchedAt across the 4 cache payloads)
//   - marks isStale=true when ANY underlying payload is stale (US-006 fallback)
class FinancialDataServiceTest {

    private val adapter: FmpAdapter = mockk()
    private val cache: FmpCacheService = mockk()
    private val eventLogger: FmpEventLogger = mockk(relaxed = true)
    private val service = FinancialDataService(adapter, cache, eventLogger)

    @Test
    fun `delegates 4 calls through FmpCacheService and stamps oldest snapshotAt`() {
        val incomeAt = Instant.parse("2026-05-20T08:00:00Z")
        val balanceAt = Instant.parse("2026-05-20T09:00:00Z")
        val cashAt = Instant.parse("2026-05-20T10:00:00Z")
        val kmAt = Instant.parse("2026-05-20T11:00:00Z")

        every {
            cache.getOrFetch<IncomeStatementDto>("AAPL", "income-statement", any(), any())
        } returns CachedPayload(
            value = listOf(IncomeStatementDto(symbol = "AAPL", calendarYear = "2024")),
            fetchedAt = incomeAt,
            stale = false,
        )
        every {
            cache.getOrFetch<BalanceSheetDto>("AAPL", "balance-sheet-statement", any(), any())
        } returns CachedPayload(
            value = listOf(BalanceSheetDto(symbol = "AAPL", calendarYear = "2024")),
            fetchedAt = balanceAt,
            stale = false,
        )
        every {
            cache.getOrFetch<CashFlowDto>("AAPL", "cash-flow-statement", any(), any())
        } returns CachedPayload(
            value = listOf(CashFlowDto(symbol = "AAPL", calendarYear = "2024")),
            fetchedAt = cashAt,
            stale = false,
        )
        every {
            cache.getOrFetch<KeyMetricsDto>("AAPL", "key-metrics", any(), any())
        } returns CachedPayload(
            value = listOf(KeyMetricsDto(symbol = "AAPL", calendarYear = "2024")),
            fetchedAt = kmAt,
            stale = false,
        )

        val ds = service.getFinancialDataset("aapl") // lowercase to verify uppercasing

        assertThat(ds.ticker).isEqualTo("AAPL")
        assertThat(ds.income).hasSize(1)
        assertThat(ds.balance).hasSize(1)
        assertThat(ds.cashFlow).hasSize(1)
        assertThat(ds.keyMetrics).hasSize(1)
        // Oldest of {8h, 9h, 10h, 11h} = 8h
        assertThat(ds.dataSnapshotAt).isEqualTo(incomeAt)
        assertThat(ds.isStale).isFalse()
        assertThat(ds.staleReason).isNull()

        verify(exactly = 1) {
            cache.getOrFetch<IncomeStatementDto>("AAPL", "income-statement", any(), any())
        }
        verify(exactly = 1) {
            cache.getOrFetch<BalanceSheetDto>("AAPL", "balance-sheet-statement", any(), any())
        }
        verify(exactly = 1) {
            cache.getOrFetch<CashFlowDto>("AAPL", "cash-flow-statement", any(), any())
        }
        verify(exactly = 1) {
            cache.getOrFetch<KeyMetricsDto>("AAPL", "key-metrics", any(), any())
        }
    }

    @Test
    fun `marks dataset stale when any cached payload is stale`() {
        val baseAt = Instant.parse("2026-05-20T10:00:00Z")
        every {
            cache.getOrFetch<IncomeStatementDto>("AAPL", "income-statement", any(), any())
        } returns CachedPayload(emptyList(), baseAt, stale = true)
        every {
            cache.getOrFetch<BalanceSheetDto>("AAPL", "balance-sheet-statement", any(), any())
        } returns CachedPayload(emptyList(), baseAt, stale = false)
        every {
            cache.getOrFetch<CashFlowDto>("AAPL", "cash-flow-statement", any(), any())
        } returns CachedPayload(emptyList(), baseAt, stale = false)
        every {
            cache.getOrFetch<KeyMetricsDto>("AAPL", "key-metrics", any(), any())
        } returns CachedPayload(emptyList(), baseAt, stale = false)

        val ds = service.getFinancialDataset("AAPL")

        assertThat(ds.isStale).isTrue()
        assertThat(ds.staleReason).isEqualTo("fmp-unavailable")
    }

    @Test
    fun `propagates FmpTickerNotFoundException raised from cache fetch lambda`() {
        // Simulate: cache miss → it invokes the lambda which calls the adapter,
        // and the adapter throws FmpTickerNotFoundException upstream.
        val typeSlot = slot<TypeReference<List<IncomeStatementDto>>>()
        every {
            cache.getOrFetch(
                ticker = "ZZZZ",
                endpoint = "income-statement",
                typeRef = capture(typeSlot),
                fetchFn = any(),
            )
        } throws FmpTickerNotFoundException("ZZZZ")

        assertThatThrownBy { service.getFinancialDataset("ZZZZ") }
            .isInstanceOf(FmpTickerNotFoundException::class.java)
    }

    @Test
    fun `falls back to stale cache when adapter throws FmpUnavailableException (TSK-011)`() {
        val staleAt = Instant.parse("2026-05-19T10:00:00Z")
        val freshAt = Instant.parse("2026-05-20T10:00:00Z")
        // income-statement: cache.getOrFetch raises FmpUnavailable -> service hits getStale.
        every {
            cache.getOrFetch<IncomeStatementDto>("AAPL", "income-statement", any(), any())
        } throws FmpUnavailableException("FMP 5xx exhausted")
        every {
            cache.getStale<IncomeStatementDto>("AAPL", "income-statement", any())
        } returns CachedPayload(
            value = listOf(IncomeStatementDto(symbol = "AAPL", calendarYear = "2024")),
            fetchedAt = staleAt,
            stale = true,
        )
        // The other 3 endpoints succeed normally — verifies partial-stale composition.
        every {
            cache.getOrFetch<BalanceSheetDto>("AAPL", "balance-sheet-statement", any(), any())
        } returns CachedPayload(emptyList(), freshAt, stale = false)
        every {
            cache.getOrFetch<CashFlowDto>("AAPL", "cash-flow-statement", any(), any())
        } returns CachedPayload(emptyList(), freshAt, stale = false)
        every {
            cache.getOrFetch<KeyMetricsDto>("AAPL", "key-metrics", any(), any())
        } returns CachedPayload(emptyList(), freshAt, stale = false)
        justRun { eventLogger.logFallbackStale(any(), any(), any()) }

        val ds = service.getFinancialDataset("AAPL")

        assertThat(ds.isStale).isTrue()
        assertThat(ds.staleReason).isEqualTo("fmp-unavailable")
        // Snapshot timestamp = MIN(staleAt, freshAt × 3) = staleAt
        assertThat(ds.dataSnapshotAt).isEqualTo(staleAt)
        verify(exactly = 1) {
            eventLogger.logFallbackStale("AAPL", "income-statement", any())
        }
    }

    @Test
    fun `propagates 503 when adapter down and no stale cache available (TSK-011)`() {
        every {
            cache.getOrFetch<IncomeStatementDto>("AAPL", "income-statement", any(), any())
        } throws FmpUnavailableException("FMP 5xx exhausted")
        every {
            cache.getStale<IncomeStatementDto>("AAPL", "income-statement", any())
        } returns null

        assertThatThrownBy { service.getFinancialDataset("AAPL") }
            .isInstanceOf(FmpUnavailableException::class.java)
    }

    @Test
    fun `rejects blank ticker before calling cache`() {
        assertThatThrownBy { service.getFinancialDataset(" ") }
            .isInstanceOf(IllegalArgumentException::class.java)
        verify(exactly = 0) {
            cache.getOrFetch<IncomeStatementDto>(any(), any(), any(), any())
        }
    }
}
