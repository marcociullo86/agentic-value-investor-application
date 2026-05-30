package com.valueinvesting.webapp.service

import com.ninjasquad.springmockk.MockkBean
import com.valueinvesting.webapp.fmp.FmpAdapter
import com.valueinvesting.webapp.fmp.dto.EodPriceRecord
import com.valueinvesting.webapp.fmp.dto.ProfileDto
import com.valueinvesting.webapp.persistence.repository.PriceActionSnapshotRepository
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class PriceActionAnalyzerTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("pgvector/pgvector:pg17")
            .withDatabaseName("vi_price_test")
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
    private lateinit var priceActionAnalyzer: PriceActionAnalyzer

    @Autowired
    private lateinit var snapshotRepo: PriceActionSnapshotRepository

    @BeforeEach
    fun setup() {
        snapshotRepo.deleteAll()
    }

    private fun makeEodPrices(count: Int, basePrice: Double, trend: Double = 0.0): List<EodPriceRecord> {
        return (0 until count).map { i ->
            EodPriceRecord(
                date = LocalDate.now().minusDays((count - i).toLong()),
                close = basePrice + trend * i,
                open = basePrice,
                high = basePrice + 5.0,
                low = basePrice - 5.0,
                volume = 1000000,
            )
        }
    }

    @Test
    fun `panic boundary - drawdown exactly -30 percent triggers flag`() {
        val prices = (0 until 252).map { i ->
            EodPriceRecord(
                date = LocalDate.now().minusDays((252 - i).toLong()),
                close = 100.0,
                open = 100.0,
                high = 100.0,
                low = 100.0,
                volume = 1000000,
            )
        }
        every { fmpAdapter.getHistoricalEodPrices("TEST", any()) } returns prices
        every { fmpAdapter.getProfile("TEST") } returns ProfileDto(price = 70.0)

        val result = priceActionAnalyzer.analyze("TEST")

        assertThat(result.drawdownPct).isEqualTo(-30.0)
        assertThat(result.panicDiscount).isTrue()
    }

    @Test
    fun `deterioration requires both conditions - trend and death cross`() {
        val prices = (0 until 252).map { i ->
            val close = if (i < 200) 150.0 else 100.0 - (i - 200) * 1.0
            EodPriceRecord(
                date = LocalDate.now().minusDays((252 - i).toLong()),
                close = close,
                open = close,
                high = close + 2.0,
                low = close - 2.0,
                volume = 1000000,
            )
        }
        every { fmpAdapter.getHistoricalEodPrices("FALL", any()) } returns prices
        every { fmpAdapter.getProfile("FALL") } returns ProfileDto(price = 48.0)

        val result = priceActionAnalyzer.analyze("FALL")

        assertThat(result.deteriorationWarning).isTrue()
    }

    @Test
    fun `no deterioration when trend bad but no death cross`() {
        val prices = makeEodPrices(252, 100.0, 0.1)
        every { fmpAdapter.getHistoricalEodPrices("UP", any()) } returns prices
        every { fmpAdapter.getProfile("UP") } returns ProfileDto(price = 120.0)

        val result = priceActionAnalyzer.analyze("UP")

        assertThat(result.deteriorationWarning).isFalse()
    }

    @Test
    fun `insufficient series returns null max52w and flags false`() {
        val prices = makeEodPrices(100, 50.0)
        every { fmpAdapter.getHistoricalEodPrices("SHORT", any()) } returns prices
        every { fmpAdapter.getProfile("SHORT") } returns ProfileDto(price = 50.0)

        val result = priceActionAnalyzer.analyze("SHORT")

        assertThat(result.max52w).isNull()
        assertThat(result.min52w).isNull()
        assertThat(result.panicDiscount).isFalse()
        assertThat(result.deteriorationWarning).isFalse()
        assertThat(result.seriesDays).isEqualTo(100)
    }

    @Test
    fun `cache hit - second call same day uses cached snapshot`() {
        val prices = makeEodPrices(252, 100.0)
        every { fmpAdapter.getHistoricalEodPrices("CACHE", any()) } returns prices
        every { fmpAdapter.getProfile("CACHE") } returns ProfileDto(price = 100.0)

        priceActionAnalyzer.analyze("CACHE")
        priceActionAnalyzer.analyze("CACHE")

        io.mockk.verify(exactly = 1) { fmpAdapter.getHistoricalEodPrices("CACHE", any()) }
    }
}
