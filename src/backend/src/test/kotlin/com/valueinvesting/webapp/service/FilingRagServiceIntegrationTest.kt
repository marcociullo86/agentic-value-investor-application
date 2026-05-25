package com.valueinvesting.webapp.service

import com.ninjasquad.springmockk.MockkBean
import com.valueinvesting.webapp.persistence.entity.FilingBlobEntity
import com.valueinvesting.webapp.persistence.repository.FilingBlobRepository
import com.valueinvesting.webapp.persistence.repository.FilingChunkRepository
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
import java.time.Instant
import java.time.LocalDate
import kotlin.random.Random

/**
 * Integration test for the filing RAG pipeline: chunking → embedding → pgvector persist → similarity search.
 * Uses mock EmbeddingService (random 1024-dim vectors) to isolate from sidecar dependency.
 * [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-040-vector-store-pgvector/TSK-103.md]
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class FilingRagServiceIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("pgvector/pgvector:pg17")
            .withDatabaseName("value_investing_rag_test")
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
    private lateinit var embeddingService: EmbeddingService

    @Autowired
    private lateinit var filingRagService: FilingRagService

    @Autowired
    private lateinit var filingBlobRepository: FilingBlobRepository

    @Autowired
    private lateinit var filingChunkRepository: FilingChunkRepository

    @Autowired
    private lateinit var chunkingService: FilingChunkingService

    @BeforeEach
    fun setup() {
        filingChunkRepository.deleteAll()
        filingBlobRepository.deleteAll()

        every { embeddingService.embed(any()) } answers {
            val texts = firstArg<List<String>>()
            texts.map { FloatArray(1024) { Random.nextFloat() } }
        }
    }

    private fun createBlob(ticker: String, textLength: Int): FilingBlobEntity {
        val blob = FilingBlobEntity(
            ticker = ticker,
            cik = "0000320193",
            formType = "10-K",
            accessionNumber = "0000320193-25-%06d".format(blobCounter++),
            filingDate = LocalDate.of(2025, 1, 15),
            extractedText = "x".repeat(textLength),
            fetchedAt = Instant.now(),
            expiresAt = Instant.now().plusSeconds(86400 * 90),
        )
        return filingBlobRepository.save(blob)
    }

    private var blobCounter = 1

    @Test
    fun `chunking produces expected number of chunks for 18000 chars`() {
        val text = "a".repeat(18000)
        val chunks = chunkingService.chunk(text)
        assertThat(chunks.size).isGreaterThanOrEqualTo(3)
    }

    @Test
    fun `indexFiling persists chunks with embeddings`() {
        val blob = createBlob("AAPL", 60000)
        filingRagService.indexFiling(blob.id!!)

        val persisted = filingChunkRepository.findByFilingBlobId(blob.id!!)
        assertThat(persisted).isNotEmpty
        assertThat(persisted.size).isGreaterThanOrEqualTo(10)
    }

    @Test
    fun `indexFiling is idempotent - no duplicate chunks`() {
        val blob = createBlob("MSFT", 30000)
        filingRagService.indexFiling(blob.id!!)
        val countFirst = filingChunkRepository.countByFilingBlobId(blob.id!!)

        filingRagService.indexFiling(blob.id!!)
        val countSecond = filingChunkRepository.countByFilingBlobId(blob.id!!)

        assertThat(countSecond).isEqualTo(countFirst)
    }

    @Test
    fun `similaritySearch returns topK results ordered by distance`() {
        for (i in 1..10) {
            val blob = createBlob("GOOG", 12000)
            filingRagService.indexFiling(blob.id!!)
        }

        val results = filingRagService.similaritySearch("business risks", "GOOG", 5)
        assertThat(results).hasSize(5)

        val distances = results.map { it.distance }
        assertThat(distances).isSorted
    }

    @Test
    fun `similaritySearch responds within 200ms on 10 filings`() {
        for (i in 1..10) {
            val blob = createBlob("KO", 18000)
            filingRagService.indexFiling(blob.id!!)
        }

        val start = System.currentTimeMillis()
        filingRagService.similaritySearch("earnings growth", "KO", 5)
        val elapsed = System.currentTimeMillis() - start

        assertThat(elapsed).isLessThan(200)
    }
}
