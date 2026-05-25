package com.valueinvesting.webapp.service

import com.valueinvesting.webapp.domain.GicsSector
import com.valueinvesting.webapp.domain.MarketCapBand
import com.valueinvesting.webapp.fmp.CachedPayload
import com.valueinvesting.webapp.fmp.FmpAdapter
import com.valueinvesting.webapp.fmp.FmpCacheService
import com.valueinvesting.webapp.fmp.FmpTickerNotFoundException
import com.valueinvesting.webapp.fmp.dto.ProfileDto
import com.valueinvesting.webapp.fmp.dto.ScreenedStockDto
import com.valueinvesting.webapp.fmp.dto.SearchHitDto
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class SearchServiceTest {

    private lateinit var fmpAdapter: FmpAdapter
    private lateinit var fmpCacheService: FmpCacheService
    private lateinit var service: SearchService

    @BeforeEach
    fun setup() {
        fmpAdapter = mockk()
        fmpCacheService = mockk()
        service = SearchService(fmpAdapter, fmpCacheService)
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
        verify(exactly = 1) { fmpAdapter.screen(any(), any(), any(), any(), any(), any()) }
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
            fmpAdapter.screen(any(), any(), eq("Technology"), any(), any(), any())
        } returns listOf(
            ScreenedStockDto(symbol = "AAPL", companyName = "Apple", sector = "Technology", marketCap = 3e12),
            ScreenedStockDto(symbol = "MSFT", companyName = "Microsoft", sector = "Technology", marketCap = 2.8e12),
        )
        every {
            fmpAdapter.screen(any(), any(), eq("Healthcare"), any(), any(), any())
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
        verify(exactly = 1) { fmpAdapter.screen(any(), any(), eq("Technology"), any(), any(), any()) }
        verify(exactly = 1) { fmpAdapter.screen(any(), any(), eq("Healthcare"), any(), any(), any()) }
    }

    // Combo (2 bande × 2 settori) → 4 chiamate.
    @Test
    fun `screen with bands x sectors does N x M FMP calls`() {
        every {
            fmpAdapter.screen(any(), any(), any(), any(), any(), any())
        } returns emptyList()

        service.screen(
            ScreenerCriteria(
                marketCapBands = listOf(MarketCapBand.LARGE, MarketCapBand.MEGA),
                sectors = listOf(GicsSector.INFORMATION_TECHNOLOGY, GicsSector.HEALTH_CARE),
            ),
        )

        verify(exactly = 4) { fmpAdapter.screen(any(), any(), any(), any(), any(), any()) }
    }

    // De-duplica per symbol quando lo stesso ticker compare in più call.
    @Test
    fun `screen deduplicates same symbol across calls`() {
        every {
            fmpAdapter.screen(any(), any(), eq("Technology"), any(), any(), any())
        } returns listOf(
            ScreenedStockDto(symbol = "AAPL", companyName = "Apple", sector = "Technology", marketCap = 3e12),
        )
        every {
            fmpAdapter.screen(any(), any(), eq("Communication Services"), any(), any(), any())
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
            fmpAdapter.screen(any(), any(), null, any(), any(), any())
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
        verify(exactly = 0) { fmpAdapter.screen(any(), any(), any(), any(), any(), any()) }
    }

    // Lista vuota da FMP → 200 con items vuota (DoD #3).
    @Test
    fun `screen with no FMP match returns empty page`() {
        every { fmpAdapter.screen(any(), any(), any(), any(), any(), any()) } returns emptyList()

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
        every { fmpAdapter.screen(any(), any(), any(), any(), any(), any()) } returns items

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

    // ===== US-001: search(query) =============================================

    // DoD #3: input `aapl` viene normalizzato a `AAPL` prima della chiamata FMP.
    @Test
    fun `search normalizes lowercase query to uppercase before calling FMP`() {
        val captured = slot<String>()
        every { fmpAdapter.searchSymbol(capture(captured), any()) } returns emptyList()

        service.search("aapl")

        assertThat(captured.captured).isEqualTo("AAPL")
        verify(exactly = 1) { fmpAdapter.searchSymbol("AAPL", 20) }
    }

    // DoD #1: lista hit non vuota → mapping a SearchResultItem ordinato.
    @Test
    fun `search maps FMP hits to SearchResultItem list`() {
        every { fmpAdapter.searchSymbol("AAPL", 20) } returns listOf(
            SearchHitDto(symbol = "AAPL", name = "Apple Inc.", currency = "USD", exchangeShortName = "NASDAQ"),
            SearchHitDto(symbol = "APC", name = "Apple Corp Holdings", currency = "USD", exchangeShortName = "NYSE"),
        )

        val result = service.search("AAPL")

        assertThat(result.items).hasSize(2)
        assertThat(result.items[0].ticker).isEqualTo("AAPL")
        assertThat(result.items[0].companyName).isEqualTo("Apple Inc.")
        // SearchResultItem OpenAPI schema: sector e marketCapUsd nullable, FMP
        // /search non li popola → null (vedi nota toSearchResultItem).
        assertThat(result.items[0].sector).isNull()
        assertThat(result.items[0].marketCapUsd).isNull()
    }

    // FMP empty list → 200 items vuota (NON eccezione, semantica /search).
    @Test
    fun `search with no FMP match returns empty list and not exception`() {
        every { fmpAdapter.searchSymbol(any(), any()) } returns emptyList()

        val result = service.search("ZZZNOMATCH")

        assertThat(result.items).isEmpty()
    }

    // FMP a volte restituisce duplicati (cross-listing) → dedupe per symbol.
    @Test
    fun `search deduplicates same symbol across hits`() {
        every { fmpAdapter.searchSymbol(any(), any()) } returns listOf(
            SearchHitDto(symbol = "AAPL", name = "Apple Inc.", exchangeShortName = "NASDAQ"),
            SearchHitDto(symbol = "AAPL", name = "Apple Inc.", exchangeShortName = "BATS"),
        )

        val result = service.search("AAPL")

        assertThat(result.items).hasSize(1)
        assertThat(result.items.first().ticker).isEqualTo("AAPL")
    }

    // Fallback companyName quando FMP omette `name` (raro ma documentato).
    @Test
    fun `search falls back to symbol when FMP name is null or blank`() {
        every { fmpAdapter.searchSymbol(any(), any()) } returns listOf(
            SearchHitDto(symbol = "XYZQ", name = null),
            SearchHitDto(symbol = "ABCQ", name = "  "),
        )

        val result = service.search("XYZ")

        assertThat(result.items.map { it.companyName }).containsExactly("XYZQ", "ABCQ")
    }

    // Edge: query blank/empty → 400 via IllegalArgumentException.
    @Test
    fun `search rejects blank query`() {
        assertThatThrownBy { service.search("   ") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("blank")
        verify(exactly = 0) { fmpAdapter.searchSymbol(any(), any()) }
    }

    // Edge: query con caratteri non in [A-Z0-9.-] (es. payload XSS).
    @Test
    fun `search rejects query with invalid characters`() {
        assertThatThrownBy { service.search("<script>") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("invalid characters")
    }

    // Edge: query oltre 32 char.
    @Test
    fun `search rejects query longer than 32 characters`() {
        val tooLong = "A".repeat(33)
        assertThatThrownBy { service.search(tooLong) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("length")
    }

    // ===== US-001: validateTicker(ticker) ====================================

    // DoD #3: input `aapl` viene normalizzato a `AAPL` prima della chiamata cache.
    @Test
    fun `validateTicker normalizes lowercase ticker to uppercase`() {
        val captured = slot<String>()
        every { fmpCacheService.getOrFetchProfile(capture(captured), any()) } returns CachedPayload(
            value = ProfileDto(symbol = "AAPL", companyName = "Apple Inc.", price = 150.0, marketCap = 3e12),
            fetchedAt = Instant.parse("2026-05-22T10:00:00Z"),
            stale = false,
        )

        service.validateTicker("aapl")

        assertThat(captured.captured).isEqualTo("AAPL")
    }

    // DoD #2: ticker esistente → 200 con StockProfile popolato.
    @Test
    fun `validateTicker returns StockProfile mapped from cache payload`() {
        every { fmpCacheService.getOrFetchProfile("AAPL", any()) } returns CachedPayload(
            value = ProfileDto(
                symbol = "AAPL",
                companyName = "Apple Inc.",
                sector = "Technology",
                industry = "Consumer Electronics",
                marketCap = 3e12,
                price = 150.0,
                currency = "USD",
                exchange = "NASDAQ",
            ),
            fetchedAt = Instant.parse("2026-05-22T10:00:00Z"),
            stale = false,
        )

        val profile = service.validateTicker("AAPL")

        assertThat(profile.ticker).isEqualTo("AAPL")
        assertThat(profile.companyName).isEqualTo("Apple Inc.")
        assertThat(profile.sector).isEqualTo("Technology")
        assertThat(profile.industry).isEqualTo("Consumer Electronics")
        assertThat(profile.marketCapUsd).isEqualTo(3e12)
        assertThat(profile.currentPrice).isEqualTo(150.0)
        assertThat(profile.dataSnapshotAt).isEqualTo(Instant.parse("2026-05-22T10:00:00Z"))
    }

    // DoD #2: ticker inesistente → FmpTickerNotFoundException dal cache layer
    // (propaga FMP empty list → adapter throw → mai 200).
    @Test
    fun `validateTicker propagates FmpTickerNotFoundException for unknown ticker`() {
        every { fmpCacheService.getOrFetchProfile("ZZZNOPE", any()) } throws
            FmpTickerNotFoundException("ZZZNOPE")

        assertThatThrownBy { service.validateTicker("zzznope") }
            .isInstanceOf(FmpTickerNotFoundException::class.java)
            .hasMessageContaining("ZZZNOPE")
    }

    // Edge: ticker blank/empty → 400 via IllegalArgumentException.
    @Test
    fun `validateTicker rejects blank input`() {
        assertThatThrownBy { service.validateTicker("") }
            .isInstanceOf(IllegalArgumentException::class.java)
        verify(exactly = 0) { fmpCacheService.getOrFetchProfile(any(), any()) }
    }

    // Edge: ticker oltre 10 char (limite OpenAPI §parameters/Ticker).
    @Test
    fun `validateTicker rejects ticker longer than 10 characters`() {
        assertThatThrownBy { service.validateTicker("ABCDEFGHIJK") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("length")
    }

    // Edge: ticker con caratteri invalidi.
    @Test
    fun `validateTicker rejects ticker with invalid characters`() {
        assertThatThrownBy { service.validateTicker("A B") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    // Fallback companyName quando FMP omette il nome — non deve essere null
    // (OpenAPI StockProfile.companyName è required).
    @Test
    fun `validateTicker falls back to ticker when FMP companyName is null`() {
        every { fmpCacheService.getOrFetchProfile("AAPL", any()) } returns CachedPayload(
            value = ProfileDto(symbol = "AAPL", companyName = null, price = 150.0),
            fetchedAt = Instant.now(),
            stale = false,
        )

        val profile = service.validateTicker("AAPL")

        assertThat(profile.companyName).isEqualTo("AAPL")
    }
}
