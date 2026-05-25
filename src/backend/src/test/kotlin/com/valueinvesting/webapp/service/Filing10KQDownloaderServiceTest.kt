package com.valueinvesting.webapp.service

import com.ninjasquad.springmockk.MockkBean
import com.valueinvesting.webapp.fmp.FmpAdapter
import com.valueinvesting.webapp.fmp.dto.SecFilingFmpDto
import com.valueinvesting.webapp.persistence.entity.FilingBlobEntity
import com.valueinvesting.webapp.persistence.entity.Stock
import com.valueinvesting.webapp.persistence.repository.FilingBlobRepository
import com.valueinvesting.webapp.persistence.repository.StockRepository
import com.valueinvesting.webapp.secedgar.SecEdgarAdapter
import io.mockk.every
import io.mockk.verify
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
import java.time.Instant

/**
 * Integration test for [Filing10KQDownloaderService]: cache TTL, 50 MB limit,
 * FMP empty-list edge case, no regression.
 *
 * Testcontainers PostgreSQL runs Flyway migrations; FmpAdapter and SecEdgarAdapter
 * are mocked so no external HTTP calls are made.
 *
 * [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-039-download-cache-filings/TSK-097.md]
 * [^src: wiki/runbooks/sec-10k-10q-analysis-playbook.md §Step 1 — Recupero filing]
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class Filing10KQDownloaderServiceTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("pgvector/pgvector:pg17")
            .withDatabaseName("value_investing_dl_test")
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

        private const val TICKER = "TSLA"
        private const val CIK = "0001318605"
        private const val ACCESSION_NO_DASHES = "000131860525000042"
        private const val ACCESSION_DASHED = "0001318605-25-000042"
        private const val SEC_URL =
            "https://www.sec.gov/Archives/edgar/data/1318605/$ACCESSION_NO_DASHES/tsla-20250928.htm"
        private val SMALL_HTML = "<html><body><h1>Annual Report</h1><p>${"Revenue data. ".repeat(300)}</p></body></html>"
    }

    @MockkBean
    private lateinit var fmpAdapter: FmpAdapter

    @MockkBean
    private lateinit var secEdgarAdapter: SecEdgarAdapter

    @MockkBean
    private lateinit var embeddingService: EmbeddingService

    @Autowired
    private lateinit var service: Filing10KQDownloaderService

    @Autowired
    private lateinit var filingBlobRepository: FilingBlobRepository

    @Autowired
    private lateinit var stockRepository: StockRepository

    @BeforeEach
    fun setUp() {
        filingBlobRepository.deleteAll()
        if (!stockRepository.existsById(TICKER)) {
            stockRepository.save(Stock(ticker = TICKER, companyName = "Tesla Inc"))
        }
    }

    private fun fmpFiling(
        accessionNoDashes: String = ACCESSION_NO_DASHES,
        cik: String = CIK,
        formType: String = "10-K",
        filingDate: String = "2025-09-28",
    ): SecFilingFmpDto {
        val secUrl = "https://www.sec.gov/Archives/edgar/data/${cik.trimStart('0')}/$accessionNoDashes/doc.htm"
        return SecFilingFmpDto(
            symbol = TICKER,
            cik = cik,
            filingDate = filingDate,
            acceptedDate = "$filingDate 06:01:36",
            formType = formType,
            link = secUrl,
            finalLink = secUrl,
        )
    }

    // =========================================================================
    // AC — Happy path: record persisted with extractedText valorizzato
    // =========================================================================
    @Test
    fun `happy path - fetchAndCache persists filing with non-empty extractedText`() {
        every { fmpAdapter.getSecFilings(TICKER, any(), any()) } returns listOf(fmpFiling())
        every { secEdgarAdapter.downloadFilingHtml(any()) } returns SMALL_HTML

        val result = service.fetchAndCache(TICKER)

        assertThat(result).hasSize(1)
        val blob = result.first()
        assertThat(blob.ticker).isEqualTo(TICKER)
        assertThat(blob.accessionNumber).isEqualTo(ACCESSION_DASHED)
        assertThat(blob.formType).isEqualTo("10-K")
        assertThat(blob.extractedText).isNotBlank()
        assertThat(blob.htmlBody).isEqualTo(SMALL_HTML)
        assertThat(blob.expiresAt).isAfter(blob.fetchedAt)

        val dbRecord = filingBlobRepository.findByAccessionNumber(ACCESSION_DASHED)
        assertThat(dbRecord).isNotNull
        assertThat(dbRecord!!.extractedText).isNotBlank()
    }

    // =========================================================================
    // AC — Cache hit: no second SEC HTTP call for same accession within TTL
    // =========================================================================
    @Test
    fun `cache hit - second fetchAndCache does not call SEC for same accession`() {
        every { fmpAdapter.getSecFilings(TICKER, any(), any()) } returns listOf(fmpFiling())
        every { secEdgarAdapter.downloadFilingHtml(any()) } returns SMALL_HTML

        service.fetchAndCache(TICKER)
        service.fetchAndCache(TICKER)

        verify(exactly = 1) { secEdgarAdapter.downloadFilingHtml(any()) }

        assertThat(filingBlobRepository.findAll()).hasSize(1)
    }

    // =========================================================================
    // AC — Post-TTL: re-fetch occurs after expires_at is in the past
    // =========================================================================
    @Test
    fun `post TTL - re-fetches filing when expires_at is in the past`() {
        every { fmpAdapter.getSecFilings(TICKER, any(), any()) } returns listOf(fmpFiling())
        every { secEdgarAdapter.downloadFilingHtml(any()) } returns SMALL_HTML

        service.fetchAndCache(TICKER)

        val persisted = filingBlobRepository.findByAccessionNumber(ACCESSION_DASHED)!!
        persisted.expiresAt = Instant.EPOCH
        filingBlobRepository.saveAndFlush(persisted)

        val updatedHtml = "<html><body><p>Updated filing content</p></body></html>"
        every { secEdgarAdapter.downloadFilingHtml(any()) } returns updatedHtml

        service.fetchAndCache(TICKER)

        verify(exactly = 2) { secEdgarAdapter.downloadFilingHtml(any()) }

        val refreshed = filingBlobRepository.findByAccessionNumber(ACCESSION_DASHED)!!
        assertThat(refreshed.htmlBody).isEqualTo(updatedHtml)
        assertThat(refreshed.expiresAt).isAfter(Instant.now().minusSeconds(60))
        assertThat(filingBlobRepository.findAll()).hasSize(1)
    }

    // =========================================================================
    // AC — 50 MB limit: filing exceeding limit is NOT persisted + log event
    // =========================================================================
    @Test
    fun `limit 50MB - filing exceeding limit is not persisted`() {
        every { fmpAdapter.getSecFilings(TICKER, any(), any()) } returns listOf(fmpFiling())
        val oversizedHtml = "a".repeat(52_500_000)
        every { secEdgarAdapter.downloadFilingHtml(any()) } returns oversizedHtml

        val result = service.fetchAndCache(TICKER)

        assertThat(filingBlobRepository.findByAccessionNumber(ACCESSION_DASHED)).isNull()
        assertThat(result).isEmpty()
    }

    // =========================================================================
    // AC — FMP empty list: returns empty without exception
    // =========================================================================
    @Test
    fun `FMP empty list - fetchAndCache returns empty list without exception`() {
        every { fmpAdapter.getSecFilings(TICKER, any(), any()) } returns emptyList()

        val result = service.fetchAndCache(TICKER)

        assertThat(result).isEmpty()
        verify(exactly = 0) { secEdgarAdapter.downloadFilingHtml(any()) }
    }
}
