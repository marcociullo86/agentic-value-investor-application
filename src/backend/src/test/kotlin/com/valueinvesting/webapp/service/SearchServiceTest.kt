package com.valueinvesting.webapp.service

import com.valueinvesting.webapp.domain.GicsSector
import com.valueinvesting.webapp.domain.MarketCapBand
import com.valueinvesting.webapp.fmp.FmpAdapter
import com.valueinvesting.webapp.fmp.dto.ScreenedStockDto
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SearchServiceTest {

    private lateinit var fmpAdapter: FmpAdapter
    private lateinit var service: SearchService

    @BeforeEach
    fun setup() {
        fmpAdapter = mockk()
        service = SearchService(fmpAdapter)
    }

    // Mapping band → coppia (minUsd, maxUsd) USD verificato: LARGE = [$10B, $200B).
    @Test
    fun `screen with single band maps to FMP min and max USD`() {
        every {
            fmpAdapter.screen(
                marketCapMoreThan = 10_000_000_000L,
                marketCapLowerThan = 200_000_000_000L,
                sector = null,
                limit = 50,
            )
        } returns listOf(
            ScreenedStockDto(symbol = "AAPL", companyName = "Apple Inc.", marketCap = 3_000_000_000_000.0, sector = "Technology"),
        )

        val page = service.screen(
            ScreenerCriteria(marketCapBands = listOf(MarketCapBand.LARGE)),
        )

        assertThat(page.items).hasSize(1)
        assertThat(page.items.first().ticker).isEqualTo("AAPL")
        verify(exactly = 1) { fmpAdapter.screen(any(), any(), any(), any()) }
    }

    // MEGA band → maxUsd null (fascia aperta verso l'alto).
    @Test
    fun `screen with MEGA band sends null upper bound to FMP`() {
        every {
            fmpAdapter.screen(
                marketCapMoreThan = 200_000_000_000L,
                marketCapLowerThan = null,
                sector = null,
                limit = 50,
            )
        } returns emptyList()

        val page = service.screen(
            ScreenerCriteria(marketCapBands = listOf(MarketCapBand.MEGA)),
        )

        assertThat(page.items).isEmpty()
        assertThat(page.nextCursor).isNull()
    }

    // Multi-sector → N chiamate FMP (1 per ogni settore) + merge.
    @Test
    fun `screen with multiple sectors fanouts to N FMP calls and merges`() {
        every {
            fmpAdapter.screen(any(), any(), eq("Technology"), any())
        } returns listOf(
            ScreenedStockDto(symbol = "AAPL", companyName = "Apple", sector = "Technology", marketCap = 3e12),
            ScreenedStockDto(symbol = "MSFT", companyName = "Microsoft", sector = "Technology", marketCap = 2.8e12),
        )
        every {
            fmpAdapter.screen(any(), any(), eq("Healthcare"), any())
        } returns listOf(
            ScreenedStockDto(symbol = "JNJ", companyName = "Johnson & Johnson", sector = "Healthcare", marketCap = 4e11),
        )

        val page = service.screen(
            ScreenerCriteria(
                sectors = listOf(GicsSector.INFORMATION_TECHNOLOGY, GicsSector.HEALTH_CARE),
                limit = 50,
            ),
        )

        assertThat(page.items).hasSize(3)
        assertThat(page.items.map { it.ticker }).containsExactlyInAnyOrder("AAPL", "MSFT", "JNJ")
        verify(exactly = 1) { fmpAdapter.screen(any(), any(), eq("Technology"), any()) }
        verify(exactly = 1) { fmpAdapter.screen(any(), any(), eq("Healthcare"), any()) }
    }

    // Combo (2 bande × 2 settori) → 4 chiamate.
    @Test
    fun `screen with bands x sectors does N x M FMP calls`() {
        every {
            fmpAdapter.screen(any(), any(), any(), any())
        } returns emptyList()

        service.screen(
            ScreenerCriteria(
                marketCapBands = listOf(MarketCapBand.LARGE, MarketCapBand.MEGA),
                sectors = listOf(GicsSector.INFORMATION_TECHNOLOGY, GicsSector.HEALTH_CARE),
            ),
        )

        verify(exactly = 4) { fmpAdapter.screen(any(), any(), any(), any()) }
    }

    // De-duplica per symbol quando lo stesso ticker compare in più call.
    @Test
    fun `screen deduplicates same symbol across calls`() {
        every {
            fmpAdapter.screen(any(), any(), eq("Technology"), any())
        } returns listOf(
            ScreenedStockDto(symbol = "AAPL", companyName = "Apple", sector = "Technology", marketCap = 3e12),
        )
        every {
            fmpAdapter.screen(any(), any(), eq("Communication Services"), any())
        } returns listOf(
            // Stesso ticker che FMP reclassifica — non deve apparire duplicato.
            ScreenedStockDto(symbol = "AAPL", companyName = "Apple", sector = "Communication Services", marketCap = 3e12),
        )

        val page = service.screen(
            ScreenerCriteria(
                sectors = listOf(GicsSector.INFORMATION_TECHNOLOGY, GicsSector.COMMUNICATION_SERVICES),
            ),
        )

        assertThat(page.items).hasSize(1)
        assertThat(page.items.first().ticker).isEqualTo("AAPL")
    }

    // excludeHardToPredict=true SENZA sectors espliciti → post-filter sui risultati.
    @Test
    fun `screen with excludeHardToPredict filters out FINANCIALS REAL_ESTATE ENERGY from results`() {
        every {
            fmpAdapter.screen(any(), any(), null, any())
        } returns listOf(
            ScreenedStockDto(symbol = "AAPL", companyName = "Apple", sector = "Technology", marketCap = 3e12),
            ScreenedStockDto(symbol = "JPM", companyName = "JPMorgan", sector = "Financial Services", marketCap = 5e11),
            ScreenedStockDto(symbol = "XOM", companyName = "Exxon", sector = "Energy", marketCap = 4e11),
            ScreenedStockDto(symbol = "SPG", companyName = "Simon Property", sector = "Real Estate", marketCap = 5e10),
            ScreenedStockDto(symbol = "JNJ", companyName = "JNJ", sector = "Healthcare", marketCap = 4e11),
        )

        val page = service.screen(
            ScreenerCriteria(excludeHardToPredict = true),
        )

        assertThat(page.items.map { it.ticker }).containsExactly("AAPL", "JNJ")
    }

    // excludeHardToPredict=true CON solo settori hard-to-predict → empty (no FMP call).
    @Test
    fun `screen with only hard-to-predict sectors and excludeHardToPredict returns empty without FMP call`() {
        val page = service.screen(
            ScreenerCriteria(
                sectors = listOf(GicsSector.FINANCIALS, GicsSector.ENERGY),
                excludeHardToPredict = true,
            ),
        )

        assertThat(page.items).isEmpty()
        assertThat(page.nextCursor).isNull()
        verify(exactly = 0) { fmpAdapter.screen(any(), any(), any(), any()) }
    }

    // Lista vuota da FMP → 200 con items vuota (DoD #3).
    @Test
    fun `screen with no FMP match returns empty page`() {
        every { fmpAdapter.screen(any(), any(), any(), any()) } returns emptyList()

        val page = service.screen(
            ScreenerCriteria(
                marketCapBands = listOf(MarketCapBand.MICRO),
                sectors = listOf(GicsSector.UTILITIES),
            ),
        )

        assertThat(page.items).isEmpty()
        assertThat(page.nextCursor).isNull()
    }

    // nextCursor presente se la pagina è "piena" (size == limit).
    @Test
    fun `screen emits nextCursor when page is full`() {
        val items = (1..3).map {
            ScreenedStockDto(symbol = "T$it", companyName = "Co$it", sector = "Technology", marketCap = 1e10)
        }
        every { fmpAdapter.screen(any(), any(), any(), any()) } returns items

        val page = service.screen(ScreenerCriteria(limit = 3))

        assertThat(page.items).hasSize(3)
        assertThat(page.nextCursor).isNotNull()
    }

    // limit > 200 → IllegalArgumentException (gestita dal GlobalExceptionHandler come 400).
    @Test
    fun `screen rejects limit greater than 200`() {
        val ex = runCatching {
            service.screen(ScreenerCriteria(limit = 300))
        }.exceptionOrNull()
        assertThat(ex).isInstanceOf(IllegalArgumentException::class.java)
    }
}
