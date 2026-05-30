package com.valueinvesting.webapp.service

import com.ninjasquad.springmockk.MockkBean
import com.valueinvesting.webapp.fmp.FmpAdapter
import com.valueinvesting.webapp.fmp.dto.StockNewsItem
import com.valueinvesting.webapp.llm.AnthropicClient
import com.valueinvesting.webapp.persistence.repository.NewsClassificationRepository
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
import java.time.LocalDateTime

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class NewsSentimentServiceTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("pgvector/pgvector:pg17")
            .withDatabaseName("vi_news_test")
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
    private lateinit var anthropicClient: AnthropicClient

    @MockkBean
    private lateinit var embeddingService: EmbeddingService

    @Autowired
    private lateinit var newsSentimentService: NewsSentimentService

    @Autowired
    private lateinit var newsRepo: NewsClassificationRepository

    @BeforeEach
    fun setup() {
        newsRepo.deleteAll()
        // Default: nessun press release (i test esercitano solo getStockNews).
        // Singoli test possono override-are questa stub se necessario.
        every { fmpAdapter.getPressReleases(any(), any()) } returns emptyList()
    }

    private fun makeNews(count: Int): List<StockNewsItem> {
        return (1..count).map { i ->
            StockNewsItem(
                newsId = "news-$i",
                publishedDate = LocalDateTime.now().minusDays(i.toLong()),
                title = "Test News $i",
                text = "Body of news $i about business performance",
                url = "https://example.com/news/$i",
                site = "TestSite",
                symbol = "AAPL",
            )
        }
    }

    @Test
    fun `classify with 20 news produces 20 classifications`() {
        val news = makeNews(20)
        every { fmpAdapter.getStockNews("AAPL", 90) } returns news
        every { anthropicClient.complete(any(), any()) } returns
            """{"classe": "NEUTRAL", "motivazione": "Normal business news"}"""

        val result = newsSentimentService.classify("AAPL")

        assertThat(result.total).isEqualTo(20)
        assertThat(result.classifications).hasSize(20)
        assertThat(newsRepo.count()).isEqualTo(20)
    }

    @Test
    fun `cache hit - second call does not invoke LLM`() {
        val news = makeNews(5)
        every { fmpAdapter.getStockNews("MSFT", 90) } returns news
        every { anthropicClient.complete(any(), any()) } returns
            """{"classe": "TEMPORARY_PANIC", "motivazione": "Sell-off"}"""

        newsSentimentService.classify("MSFT")
        newsSentimentService.classify("MSFT")

        verify(exactly = 5) { anthropicClient.complete(any(), any()) }
    }

    @Test
    fun `limit 50 - only 50 LLM calls with 60 news`() {
        val news = makeNews(60)
        every { fmpAdapter.getStockNews("GOOG", 90) } returns news
        every { anthropicClient.complete(any(), any()) } returns
            """{"classe": "STRUCTURAL_DAMAGE", "motivazione": "Permanent harm"}"""

        val result = newsSentimentService.classify("GOOG")

        verify(exactly = 50) { anthropicClient.complete(any(), any()) }
        val neutralCount = result.classifications.count { it.sentimentClass == SentimentClass.NEUTRAL }
        assertThat(neutralCount).isEqualTo(10)
    }

    @Test
    fun `dominant class is computed correctly`() {
        val news = makeNews(10)
        every { fmpAdapter.getStockNews("KO", 90) } returns news
        var callCount = 0
        every { anthropicClient.complete(any(), any()) } answers {
            callCount++
            if (callCount <= 6) """{"classe": "TEMPORARY_PANIC", "motivazione": "Fear"}"""
            else """{"classe": "NEUTRAL", "motivazione": "Normal"}"""
        }

        val result = newsSentimentService.classify("KO")

        assertThat(result.dominantClass).isEqualTo(SentimentClass.TEMPORARY_PANIC)
        assertThat(result.panicCount).isEqualTo(6)
    }
}
