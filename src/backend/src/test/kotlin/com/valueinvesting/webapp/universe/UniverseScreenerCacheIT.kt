package com.valueinvesting.webapp.universe

import com.ninjasquad.springmockk.MockkBean
import com.valueinvesting.webapp.fmp.FmpAdapter
import com.valueinvesting.webapp.fmp.dto.ScreenedStockDto
import com.valueinvesting.webapp.persistence.repository.FmpFinancialSnapshotRepository
import com.valueinvesting.webapp.service.EmbeddingService
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Integration test (Testcontainers Postgres + DB reale) per il path di cache
 * dello screener dell'universo (EP-012).
 *
 * Regressione coperta: UniverseScreenerService cacha il company-screener in
 * fmp_financial_snapshot con lo PSEUDO-ticker "ALL", che ha una FK verso
 * stocks(ticker). Senza il sentinel stocks('ALL') (migration V032) il primo run
 * del batch falliva con `fmp_financial_snapshot_ticker_fkey` violation. Lo
 * UniverseScreenerServiceTest unitario mocka FmpCacheService, quindi NON
 * esercitava la FK reale — questo IT colma il gap usando il bean reale + DB.
 *
 * Provider 13-F e news-scout disabilitati (Noop) per isolare lo Step 1 (FMP
 * screener + cache) senza dipendenze SEC/LLM.
 */
@SpringBootTest(
    properties = [
        "news-scout.enabled=false",
        "universe.institutional.enabled=false",
    ],
)
@ActiveProfiles("test")
@Testcontainers
class UniverseScreenerCacheIT {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("pgvector/pgvector:pg17")
            .withDatabaseName("vi_universe_test")
            .withUsername("test")
            .withPassword("test")

        @DynamicPropertySource
        @JvmStatic
        fun registerDataSource(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("embeddings.sidecar.url") { "http://localhost:19999" }
        }
    }

    @MockkBean
    private lateinit var fmpAdapter: FmpAdapter

    @MockkBean
    private lateinit var embeddingService: EmbeddingService

    @Autowired
    private lateinit var universeScreenerService: UniverseScreenerService

    @Autowired
    private lateinit var financialSnapshotRepository: FmpFinancialSnapshotRepository

    @Test
    fun `screen persists company-screener cache under ALL sentinel without FK violation (V032)`() {
        every {
            fmpAdapter.screen(any(), any(), any(), any(), any(), any())
        } returns listOf(
            ScreenedStockDto(
                symbol = "AAPL",
                companyName = "Apple Inc.",
                marketCap = 3_500_000_000_000.0,
                sector = "Technology",
                exchangeShortName = "NASDAQ",
                country = "US",
                isEtf = false,
                isActivelyTrading = true,
            ),
        )

        // Non deve sollevare DataIntegrityViolationException: il sentinel
        // stocks('ALL') (V032) soddisfa la FK fmp_financial_snapshot_ticker_fkey.
        val result = universeScreenerService.screen()

        assertThat(result.map { it.ticker }).contains("AAPL")
        // La riga di cache è stata effettivamente scritta sotto lo pseudo-ticker "ALL".
        val cached = financialSnapshotRepository
            .findFirstByTickerAndEndpointOrderByFetchedAtDesc("ALL", "company-screener")
        assertThat(cached).isNotNull
    }
}
