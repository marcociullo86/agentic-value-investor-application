package com.valueinvesting.webapp.universe

import com.fasterxml.jackson.core.type.TypeReference
import com.valueinvesting.webapp.fmp.CachedPayload
import com.valueinvesting.webapp.fmp.FmpAdapter
import com.valueinvesting.webapp.fmp.FmpCacheService
import com.valueinvesting.webapp.fmp.dto.ScreenedStockDto
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Unit / integration tests for [UniverseScreenerService] — EP-012, US-047, TSK-130.
 *
 * Design decisions:
 * - Pure MockK (no Spring context, no Testcontainers, no WireMock) for speed:
 *   target execution < 10 s per AC.
 * - [FmpCacheService] is mocked as a pass-through that delegates to the captured
 *   fetchFn, so each test controls the FMP response via [FmpAdapter.screen] stubs
 *   without needing to replicate DB/cache logic.
 * - Covers all 10 ACs from TSK-130 + US-047 Acceptance Criteria.
 *
 * [^src: management/kanban/EP-012-batch-top-value-picks/US-047-universe-screener-service/TSK-130.md]
 * [^src: management/kanban/EP-012-batch-top-value-picks/US-047-universe-screener-service/US-047.md]
 */
@DisplayName("UniverseScreenerService — US-047 / TSK-130")
class UniverseScreenerServiceTest {

    private val fmpAdapter = mockk<FmpAdapter>()
    private val fmpCacheService = mockk<FmpCacheService>()
    private val holdingsProvider = mockk<InstitutionalHoldingsProvider>()
    private val newsScoutProvider = mockk<NewsScoutProvider>()
    private val properties = UniverseProperties() // all defaults (cap=500, etc.)

    // Il rate limiting FMP vive ora nel ResilientFmpAdapter (limiter unico `fmp`),
    // non piu' iniettato nello screener: qui `fmpAdapter` e' un mock, quindi non
    // c'e' throttling da verificare a questo livello (coperto da
    // FmpResilienceConfigTest).
    private val service = UniverseScreenerService(
        fmpAdapter, fmpCacheService, holdingsProvider, newsScoutProvider, properties,
    )

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Stubs [FmpCacheService.getOrFetch] to behave as a transparent pass-through:
     * it captures the fetchFn lambda and invokes it immediately, returning a
     * [CachedPayload] wrapping the result. This allows tests to control the FMP
     * response entirely via [FmpAdapter.screen] stubs without needing to replicate
     * the cache's internal DB/TTL logic.
     *
     * Note on generics: `getOrFetch<T>` has signature
     *   `fun <T> getOrFetch(ticker, endpoint, typeRef: TypeReference<List<T>>, fetchFn: () -> List<T>): CachedPayload<List<T>>`
     * So with T=ScreenedStockDto the fetchFn type is `() -> List<ScreenedStockDto>` and
     * the return type is `CachedPayload<List<ScreenedStockDto>>`.
     */
    private fun stubCachePassThrough() {
        every {
            fmpCacheService.getOrFetch<ScreenedStockDto>(any(), any(), any(), any())
        } answers {
            @Suppress("UNCHECKED_CAST")
            val fetchFn = arg<() -> List<ScreenedStockDto>>(3)
            CachedPayload(
                value = fetchFn(),
                fetchedAt = Instant.EPOCH,
                stale = false,
            )
        }
    }

    private fun screened(
        symbol: String,
        marketCap: Double = 10_000_000_000.0,
        sector: String = "Technology",
    ) = ScreenedStockDto(
        symbol = symbol,
        companyName = "$symbol Inc",
        marketCap = marketCap,
        sector = sector,
        exchangeShortName = "NASDAQ",
    )

    private fun thirteenFCandidate(ticker: String, marketCap: Long? = null) =
        UniverseCandidate(
            ticker = ticker,
            source = CandidateSource.THIRTEEN_F,
            marketCapUsd = marketCap,
            sector = null,
        )

    private fun newsCandidate(ticker: String) =
        UniverseCandidate(
            ticker = ticker,
            source = CandidateSource.NEWS_SCOUT,
            marketCapUsd = null,
            sector = null,
        )

    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    // -----------------------------------------------------------------------
    // AC-1: Happy path — 100 SCREENER + 10 new THIRTEEN_F = 110 deduplicated
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("AC-1 Happy path: 100 SCREENER + 10 new 13-F = 110 candidates, sources correct")
    fun `happy path 100 screener plus 10 new thirteenF yields 110 deduped with correct sources`() {
        // 100 FMP tickers (all Technology)
        val fmpTickers = (1..100).map { "TICK$it" }
        val fmpResult = fmpTickers.map { screened(it) }

        // 13-F: 10 tickers already in FMP + 10 brand-new
        val thirteenFOverlap = (1..10).map { thirteenFCandidate("TICK$it") }
        val thirteenFNew = (101..110).map { thirteenFCandidate("TICK$it") }
        val allThirteenF = thirteenFOverlap + thirteenFNew

        stubCachePassThrough()
        every { fmpAdapter.screen(any(), any(), any(), any(), any(), any()) } returns fmpResult
        every { holdingsProvider.thirteenFTickers() } returns allThirteenF
        every { newsScoutProvider.scoutTickers(any()) } returns emptyList()

        val result = service.screen()

        // 100 unique SCREENER + 10 new 13-F = 110 total
        assertThat(result).hasSize(110)

        // TICK1..TICK10 overlap: 13-F wins priority
        val overlappingTickers = result.filter { it.ticker in (1..10).map { i -> "TICK$i" } }
        assertThat(overlappingTickers).allSatisfy { candidate ->
            assertThat(candidate.source).isEqualTo(CandidateSource.THIRTEEN_F)
        }

        // TICK11..TICK100 are pure SCREENER
        val screenerOnlyTickers = result.filter { it.ticker in (11..100).map { i -> "TICK$i" } }
        assertThat(screenerOnlyTickers).hasSize(90)
        assertThat(screenerOnlyTickers).allSatisfy { candidate ->
            assertThat(candidate.source).isEqualTo(CandidateSource.SCREENER)
        }

        // TICK101..TICK110 are pure 13-F
        val newThirteenFTickers = result.filter { it.ticker in (101..110).map { i -> "TICK$i" } }
        assertThat(newThirteenFTickers).hasSize(10)
        assertThat(newThirteenFTickers).allSatisfy { candidate ->
            assertThat(candidate.source).isEqualTo(CandidateSource.THIRTEEN_F)
        }
    }

    // -----------------------------------------------------------------------
    // AC-2: Cap 500 with 600 FMP results
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("AC-2 Cap 500: 600 FMP results are capped to 500")
    fun `cap 500 respected when screener returns 600 tickers`() {
        // Generate 600 tickers with descending market cap so ordering is deterministic
        val fmpResult = (1..600).map { i ->
            screened("T$i", marketCap = (600 - i + 1) * 1_000_000_000.0)
        }

        stubCachePassThrough()
        every { fmpAdapter.screen(any(), any(), any(), any(), any(), any()) } returns fmpResult
        every { holdingsProvider.thirteenFTickers() } returns emptyList()
        every { newsScoutProvider.scoutTickers(any()) } returns emptyList()

        val result = service.screen()

        assertThat(result).hasSize(500)
    }

    // -----------------------------------------------------------------------
    // AC-3: THIRTEEN_F prevails over SCREENER on same ticker
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("AC-3 Dedupe: THIRTEEN_F source wins over SCREENER for same ticker")
    fun `duplicate ticker in thirteenF and screener yields THIRTEEN_F source`() {
        val fmpResult = listOf(screened("AAPL"), screened("MSFT"))
        val thirteenFResult = listOf(thirteenFCandidate("AAPL"))

        stubCachePassThrough()
        every { fmpAdapter.screen(any(), any(), any(), any(), any(), any()) } returns fmpResult
        every { holdingsProvider.thirteenFTickers() } returns thirteenFResult
        every { newsScoutProvider.scoutTickers(any()) } returns emptyList()

        val result = service.screen()

        val aapl = result.single { it.ticker == "AAPL" }
        assertThat(aapl.source).isEqualTo(CandidateSource.THIRTEEN_F)

        // MSFT is SCREENER-only
        val msft = result.single { it.ticker == "MSFT" }
        assertThat(msft.source).isEqualTo(CandidateSource.SCREENER)
    }

    // -----------------------------------------------------------------------
    // AC-4: SECTOR_BLACKLIST excludes Financials and Biotechnology
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("AC-4 SECTOR_BLACKLIST: Financials and Biotechnology tickers excluded")
    fun `sector blacklist removes Financials and Biotechnology from screener results`() {
        val fmpResult = listOf(
            screened("TECH1", sector = "Technology"),
            screened("FIN1", sector = "Financials"),
            screened("FIN2", sector = "Financial Services"),
            screened("BIO1", sector = "Biotechnology"),
            screened("CONS1", sector = "Consumer Staples"),
        )

        stubCachePassThrough()
        every { fmpAdapter.screen(any(), any(), any(), any(), any(), any()) } returns fmpResult
        every { holdingsProvider.thirteenFTickers() } returns emptyList()
        every { newsScoutProvider.scoutTickers(any()) } returns emptyList()

        val result = service.screen()

        assertThat(result).hasSize(2)
        assertThat(result.map { it.ticker }).containsExactlyInAnyOrder("TECH1", "CONS1")
        assertThat(result.map { it.ticker }).doesNotContain("FIN1", "FIN2", "BIO1")
    }

    // -----------------------------------------------------------------------
    // AC-5: News Scout integration
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("AC-5 News Scout: candidates added with source=NEWS_SCOUT for tickers not in 13F/SCREENER")
    fun `news scout candidates included with NEWS_SCOUT source for new tickers`() {
        val fmpResult = listOf(screened("AAPL"), screened("MSFT"))
        val newsResult = listOf(
            newsCandidate("NEWS1"),
            newsCandidate("NEWS2"),
            newsCandidate("AAPL"), // already in SCREENER — should not override
        )

        stubCachePassThrough()
        every { fmpAdapter.screen(any(), any(), any(), any(), any(), any()) } returns fmpResult
        every { holdingsProvider.thirteenFTickers() } returns emptyList()
        every { newsScoutProvider.scoutTickers(any()) } returns newsResult

        val result = service.screen()

        // AAPL comes from SCREENER (dedupe: SCREENER wins over NEWS_SCOUT)
        val aapl = result.single { it.ticker == "AAPL" }
        assertThat(aapl.source).isEqualTo(CandidateSource.SCREENER)

        // NEWS1 and NEWS2 are new — source = NEWS_SCOUT
        val news1 = result.single { it.ticker == "NEWS1" }
        assertThat(news1.source).isEqualTo(CandidateSource.NEWS_SCOUT)
        val news2 = result.single { it.ticker == "NEWS2" }
        assertThat(news2.source).isEqualTo(CandidateSource.NEWS_SCOUT)

        // Total: AAPL + MSFT + NEWS1 + NEWS2 = 4
        assertThat(result).hasSize(4)
    }

    // -----------------------------------------------------------------------
    // AC-6: Cache integration — FmpCacheService.getOrFetch invoked once per call
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("AC-6 Cache: fmpCacheService.getOrFetch called once per screen() invocation")
    fun `cache layer invoked exactly once per screen call with correct key`() {
        val fmpResult = listOf(screened("AAPL"))

        stubCachePassThrough()
        every { fmpAdapter.screen(any(), any(), any(), any(), any(), any()) } returns fmpResult
        every { holdingsProvider.thirteenFTickers() } returns emptyList()
        every { newsScoutProvider.scoutTickers(any()) } returns emptyList()

        service.screen()
        service.screen()

        // FmpCacheService.getOrFetch should be called once per screen() invocation
        // (cache internals handled by FmpCacheService itself in production)
        verify(exactly = 2) {
            fmpCacheService.getOrFetch<ScreenedStockDto>(
                ticker = "ALL",
                endpoint = "company-screener",
                typeRef = any(),
                fetchFn = any(),
            )
        }
    }

    // -----------------------------------------------------------------------
    // AC-7: 13-F provider fail-safe via runCatching
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("AC-7 13-F fail-safe: exception from holdingsProvider does not propagate")
    fun `thirteenF provider exception does not propagate and screener continues with SCREENER only`() {
        val fmpResult = listOf(screened("AAPL"), screened("MSFT"))

        stubCachePassThrough()
        every { fmpAdapter.screen(any(), any(), any(), any(), any(), any()) } returns fmpResult
        every { holdingsProvider.thirteenFTickers() } throws RuntimeException("SEC API down")
        every { newsScoutProvider.scoutTickers(any()) } returns emptyList()

        // Must not throw
        val result = service.screen()

        assertThat(result).hasSize(2)
        assertThat(result).allSatisfy { candidate ->
            assertThat(candidate.source).isEqualTo(CandidateSource.SCREENER)
        }
    }

    // -----------------------------------------------------------------------
    // AC-8: News scout fail-safe via runCatching
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("AC-8 News Scout fail-safe: exception from newsScoutProvider does not propagate")
    fun `newsScout provider exception does not propagate and screener continues without NEWS_SCOUT candidates`() {
        val fmpResult = listOf(screened("AAPL"), screened("MSFT"))

        stubCachePassThrough()
        every { fmpAdapter.screen(any(), any(), any(), any(), any(), any()) } returns fmpResult
        every { holdingsProvider.thirteenFTickers() } returns emptyList()
        every { newsScoutProvider.scoutTickers(any()) } throws IllegalStateException("Anthropic timeout")

        val result = service.screen()

        assertThat(result).hasSize(2)
        assertThat(result.map { it.source })
            .doesNotContain(CandidateSource.NEWS_SCOUT)
    }

    // -----------------------------------------------------------------------
    // AC-9: Empty FMP screener — only 13-F + News Scout produce candidates
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("AC-9 Empty FMP screener: 13-F and News Scout still produce candidates")
    fun `empty FMP screener result still returns thirteenF and newsScout candidates`() {
        stubCachePassThrough()
        every { fmpAdapter.screen(any(), any(), any(), any(), any(), any()) } returns emptyList()
        every { holdingsProvider.thirteenFTickers() } returns listOf(
            thirteenFCandidate("BRK"),
            thirteenFCandidate("KO"),
        )
        every { newsScoutProvider.scoutTickers(any()) } returns listOf(
            newsCandidate("COST"),
        )

        val result = service.screen()

        assertThat(result).hasSize(3)
        assertThat(result.single { it.ticker == "BRK" }.source).isEqualTo(CandidateSource.THIRTEEN_F)
        assertThat(result.single { it.ticker == "KO" }.source).isEqualTo(CandidateSource.THIRTEEN_F)
        assertThat(result.single { it.ticker == "COST" }.source).isEqualTo(CandidateSource.NEWS_SCOUT)
    }

    // -----------------------------------------------------------------------
    // AC-10: Sort DESC by marketCap
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("AC-10 Sort DESC by marketCap: result sorted descending by marketCapUsd")
    fun `result is sorted descending by marketCapUsd`() {
        val fmpResult = listOf(
            screened("SMALL", marketCap = 5_000_000_000.0),
            screened("LARGE", marketCap = 500_000_000_000.0),
            screened("MID", marketCap = 50_000_000_000.0),
        )
        val thirteenFResult = listOf(
            thirteenFCandidate("MEGA", marketCap = 2_000_000_000_000L),
        )

        stubCachePassThrough()
        every { fmpAdapter.screen(any(), any(), any(), any(), any(), any()) } returns fmpResult
        every { holdingsProvider.thirteenFTickers() } returns thirteenFResult
        every { newsScoutProvider.scoutTickers(any()) } returns emptyList()

        val result = service.screen()

        assertThat(result).hasSize(4)

        // Verify strictly descending order (or equal) on marketCapUsd
        val allDescending = result.zipWithNext().all { (a, b) ->
            (a.marketCapUsd ?: 0L) >= (b.marketCapUsd ?: 0L)
        }
        assertThat(allDescending)
            .withFailMessage("Expected result sorted DESC by marketCapUsd but was: %s",
                result.map { "${it.ticker}=${it.marketCapUsd}" })
            .isTrue()

        // Top element must be MEGA (2T market cap)
        assertThat(result.first().ticker).isEqualTo("MEGA")
    }

    // -----------------------------------------------------------------------
    // Bonus: ticker case-insensitive deduplication (uppercase normalisation)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Dedupe is case-insensitive: 'aapl' and 'AAPL' treated as same ticker")
    fun `deduplication normalises ticker case so mixed-case duplicates collapse to single entry`() {
        val fmpResult = listOf(screened("AAPL")) // uppercase from FMP
        // 13-F returns lowercase variant — should still deduplicate
        val thirteenFResult = listOf(thirteenFCandidate("aapl"))

        stubCachePassThrough()
        every { fmpAdapter.screen(any(), any(), any(), any(), any(), any()) } returns fmpResult
        every { holdingsProvider.thirteenFTickers() } returns thirteenFResult
        every { newsScoutProvider.scoutTickers(any()) } returns emptyList()

        val result = service.screen()

        // One entry only, with THIRTEEN_F winning
        assertThat(result).hasSize(1)
        assertThat(result.single().source).isEqualTo(CandidateSource.THIRTEEN_F)
    }

    // -----------------------------------------------------------------------
    // Bonus: sector with null value is not blacklisted
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Null sector is not blacklisted: ticker without sector passes through")
    fun `screened ticker with null sector is not excluded by sector blacklist`() {
        val fmpResult = listOf(
            screened("NOSECTOR", sector = "Technology").copy(sector = null),
            screened("TECH", sector = "Technology"),
        )

        stubCachePassThrough()
        every { fmpAdapter.screen(any(), any(), any(), any(), any(), any()) } returns fmpResult
        every { holdingsProvider.thirteenFTickers() } returns emptyList()
        every { newsScoutProvider.scoutTickers(any()) } returns emptyList()

        val result = service.screen()

        assertThat(result).hasSize(2)
        assertThat(result.map { it.ticker }).containsExactlyInAnyOrder("NOSECTOR", "TECH")
    }

    // -----------------------------------------------------------------------
    // Bonus: null or blank symbol from FMP is filtered out (mapNotNull)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("FMP results with null or blank symbol are filtered out")
    fun `screened stock with null symbol is discarded silently`() {
        val fmpResult = listOf(
            ScreenedStockDto(symbol = null, sector = "Technology", marketCap = 10_000_000_000.0),
            ScreenedStockDto(symbol = "  ", sector = "Technology", marketCap = 10_000_000_000.0),
            screened("VALID"),
        )

        stubCachePassThrough()
        every { fmpAdapter.screen(any(), any(), any(), any(), any(), any()) } returns fmpResult
        every { holdingsProvider.thirteenFTickers() } returns emptyList()
        every { newsScoutProvider.scoutTickers(any()) } returns emptyList()

        val result = service.screen()

        assertThat(result).hasSize(1)
        assertThat(result.single().ticker).isEqualTo("VALID")
    }
}
