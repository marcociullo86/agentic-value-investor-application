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

    // TSK-308 F2: rimosso `@MockkBean EmbeddingService`. NewsSentimentService
    // non lo inietta nel proprio costruttore; era mockato solo per soddisfare
    // l'ApplicationContext, ma con la dipendenza reale (RestClient + sidecar
    // URL stub `http://localhost:19999`) il bean non viene mai contattato
    // durante questi test e il context boota correttamente.

    @Autowired
    private lateinit var newsSentimentService: NewsSentimentService

    @Autowired
    private lateinit var newsRepo: NewsClassificationRepository

    @BeforeEach
    fun setup() {
        newsRepo.deleteAll()
    }

    // Notizie generiche (nessun pattern di rumore, nessuna keyword di materialità):
    // sopravvivono al pre-filtro e sono ordinate per recency. Il `symbol`
    // riflette il ticker passato a getStockNews così le fixture non
    // hardcodano AAPL anche per i test su KO/MSFT/GOOG (TSK-308 F3).
    private fun makeNews(count: Int, symbol: String = "AAPL"): List<StockNewsItem> =
        (1..count).map { i ->
            StockNewsItem(
                newsId = "news-$i",
                publishedDate = LocalDateTime.now().minusDays(i.toLong()),
                title = "Quarterly update item $i",
                text = "Routine company update number $i with ordinary commentary.",
                url = "https://example.com/news/$i",
                site = "TestSite",
                symbol = symbol,
            )
        }

    // Costruisce un JSON di sintesi valido per `count` item, con classi opzionali.
    private fun synthJson(
        count: Int,
        impairment: String = "no",
        structuralIdx: Set<Int> = emptySet(),
        panicIdx: Set<Int> = emptySet(),
    ): String {
        val items = (0 until count).joinToString(",") { idx ->
            val classe = when {
                idx in structuralIdx -> "STRUCTURAL_DAMAGE"
                idx in panicIdx -> "TEMPORARY_PANIC"
                else -> "NEUTRAL"
            }
            """{"idx":$idx,"classe":"$classe","motivazione":"m$idx"}"""
        }
        return """{"impairment_permanente":"$impairment","items":[$items],"sintesi":"s"}"""
    }

    @Test
    fun `synthesis runs once and caps curated set at MAX_CURATED (12)`() {
        every { fmpAdapter.getStockNews("AAPL", 90) } returns makeNews(20)
        every { anthropicClient.complete(any(), any()) } returns synthJson(count = 12)

        val result = newsSentimentService.classify("AAPL")

        // 20 news in input, ma il funnel taglia ai 12 più rilevanti/recenti.
        assertThat(result.total).isEqualTo(12)
        assertThat(result.dominantClass).isEqualTo(SentimentClass.NEUTRAL)
        // UNA sola chiamata LLM (sintesi olistica), non 20.
        verify(exactly = 1) { anthropicClient.complete(any(), any()) }
        assertThat(newsRepo.count()).isEqualTo(12)
    }

    @Test
    fun `classifications expose textExcerpt and motivazione for analyzed news (US-091)`() {
        every { fmpAdapter.getStockNews("AAPL", 90) } returns makeNews(3)
        every { anthropicClient.complete(any(), any()) } returns synthJson(count = 3)

        val result = newsSentimentService.classify("AAPL")

        assertThat(result.classifications).isNotEmpty
        assertThat(result.classifications).allSatisfy {
            assertThat(it.textExcerpt).isNotBlank()
            // TSK-308 F3: isNotBlank invece di isNotNull — il fixture synthJson
            // produce sempre motivazioni non-blank ("m0","m1",…); isNotNull
            // accetterebbe la stringa vuota per errore.
            assertThat(it.motivazione).isNotBlank()
        }
    }

    @Test
    fun `cache reconstruction preserves textExcerpt motivazione and url (US-091)`() {
        every { fmpAdapter.getStockNews("KO", 90) } returns makeNews(3, symbol = "KO")
        every { anthropicClient.complete(any(), any()) } returns synthJson(count = 3)

        val first = newsSentimentService.classify("KO") // 1ª: sintesi + persist
        val result = newsSentimentService.classify("KO") // 2ª: ricostruzione da cache

        verify(exactly = 1) { anthropicClient.complete(any(), any()) }
        // TSK-306 F3 + TSK-308 F3: la ricostruzione da cache deve preservare
        // textExcerpt (identità byte-per-byte, non solo non-blank), motivazione
        // (m0/m1/m2 dal fixture) e url (https://example.com/news/N) — una
        // regressione che troncasse o riscrivesse uno di questi campi
        // verrebbe intercettata qui.
        assertThat(result.classifications).hasSize(first.classifications.size)
        result.classifications.forEachIndexed { idx, item ->
            assertThat(item.textExcerpt).isEqualTo(first.classifications[idx].textExcerpt)
            assertThat(item.textExcerpt).isNotBlank()
            assertThat(item.motivazione).isNotBlank()
            // synthJson genera motivazione = "m<idx>"; gli indici nell'output
            // seguono l'ordering del pre-filtro (materialità desc + recency
            // desc): con notizie tutte non-materiali sono ordinate per
            // recency, quindi makeNews(i=1) (la più recente) viene per prima
            // ⇒ idx=0 ⇒ motivazione="m0". Verifichiamo il set completo.
            assertThat(item.url).isNotBlank()
            assertThat(item.url).startsWith("https://example.com/news/")
        }
        assertThat(result.classifications.map { it.motivazione })
            .containsExactlyInAnyOrder("m0", "m1", "m2")
    }

    @Test
    fun `second call within 24h reuses cache - no new LLM call`() {
        every { fmpAdapter.getStockNews("MSFT", 90) } returns makeNews(5, symbol = "MSFT")
        every { anthropicClient.complete(any(), any()) } returns synthJson(count = 5)

        newsSentimentService.classify("MSFT")
        newsSentimentService.classify("MSFT")

        // Sintesi eseguita solo la prima volta; la seconda ricostruisce dalla cache.
        verify(exactly = 1) { anthropicClient.complete(any(), any()) }
    }

    @Test
    fun `single STRUCTURAL_DAMAGE drives dominant even when neutrals are majority`() {
        every { fmpAdapter.getStockNews("KO", 90) } returns makeNews(6, symbol = "KO")
        // 1 solo item strutturale, 5 neutri: la dominante deve essere STRUCTURAL.
        every { anthropicClient.complete(any(), any()) } returns
            synthJson(count = 6, structuralIdx = setOf(0))

        val result = newsSentimentService.classify("KO")

        assertThat(result.structuralCount).isEqualTo(1)
        assertThat(result.neutralCount).isEqualTo(5)
        assertThat(result.dominantClass).isEqualTo(SentimentClass.STRUCTURAL_DAMAGE)
    }

    @Test
    fun `impairment_permanente=si promotes to structural even if all items neutral`() {
        every { fmpAdapter.getStockNews("XYZ", 90) } returns makeNews(4, symbol = "XYZ")
        every { anthropicClient.complete(any(), any()) } returns
            synthJson(count = 4, impairment = "si")

        val result = newsSentimentService.classify("XYZ")

        assertThat(result.structuralCount).isGreaterThanOrEqualTo(1)
        assertThat(result.dominantClass).isEqualTo(SentimentClass.STRUCTURAL_DAMAGE)
    }

    @Test
    fun `panic majority among material news yields TEMPORARY_PANIC dominant`() {
        every { fmpAdapter.getStockNews("GOOG", 90) } returns makeNews(5, symbol = "GOOG")
        every { anthropicClient.complete(any(), any()) } returns
            synthJson(count = 5, panicIdx = setOf(0, 1, 2))

        val result = newsSentimentService.classify("GOOG")

        assertThat(result.panicCount).isEqualTo(3)
        assertThat(result.dominantClass).isEqualTo(SentimentClass.TEMPORARY_PANIC)
    }

    @Test
    fun `noise headlines are filtered out before the LLM`() {
        val noise = listOf(
            StockNewsItem(newsId = "n1", publishedDate = LocalDateTime.now().minusDays(1),
                title = "5 stocks to watch this week", text = "listicle", url = "u1", site = "s", symbol = "T"),
            StockNewsItem(newsId = "n2", publishedDate = LocalDateTime.now().minusDays(2),
                title = "Analyst raises price target to \$120", text = "pt", url = "u2", site = "s", symbol = "T"),
        )
        val material = StockNewsItem(newsId = "m1", publishedDate = LocalDateTime.now().minusDays(3),
            title = "Company faces SEC investigation over accounting", text = "probe", url = "u3", site = "s", symbol = "T")
        every { fmpAdapter.getStockNews("T", 90) } returns (noise + material)
        every { anthropicClient.complete(any(), any()) } returns synthJson(count = 1, structuralIdx = setOf(0))

        val result = newsSentimentService.classify("T")

        // Solo la notizia materiale sopravvive al pre-filtro.
        assertThat(result.total).isEqualTo(1)
        assertThat(result.dominantClass).isEqualTo(SentimentClass.STRUCTURAL_DAMAGE)
    }

    @Test
    fun `empty news returns NEUTRAL without invoking LLM`() {
        every { fmpAdapter.getStockNews("EMPTY", 90) } returns emptyList()

        val result = newsSentimentService.classify("EMPTY")

        assertThat(result.total).isEqualTo(0)
        assertThat(result.dominantClass).isEqualTo(SentimentClass.NEUTRAL)
        verify(exactly = 0) { anthropicClient.complete(any(), any()) }
    }
}
