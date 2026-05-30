package com.valueinvesting.webapp.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.valueinvesting.webapp.fmp.FmpAdapter
import com.valueinvesting.webapp.fmp.dto.StockNewsItem
import com.valueinvesting.webapp.llm.AnthropicClient
import com.valueinvesting.webapp.persistence.entity.NewsClassificationEntity
import com.valueinvesting.webapp.persistence.repository.NewsClassificationRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneOffset

/**
 * Classifies stock news as TEMPORARY_PANIC / STRUCTURAL_DAMAGE / NEUTRAL
 * using LLM (Claude Opus). Caches classifications in DB to avoid re-processing.
 * [^src: wiki/concepts/market-fluctuations-graham.md §Distinzione tra panic e damage]
 */
@Service
class NewsSentimentService(
    private val fmpAdapter: FmpAdapter,
    private val anthropicClient: AnthropicClient,
    private val newsRepo: NewsClassificationRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = jacksonObjectMapper()

    companion object {
        private const val MAX_LLM_CALLS_PER_TICKER = 50
        private const val CACHE_TTL_HOURS = 24L
    }

    @Transactional
    fun classify(ticker: String): NewsSentimentResult {
        val news = fmpAdapter.getStockNews(ticker, 90)
        if (news.isEmpty()) {
            return NewsSentimentResult(
                ticker = ticker,
                total = 0,
                panicCount = 0,
                structuralCount = 0,
                neutralCount = 0,
                dominantClass = SentimentClass.NEUTRAL,
                classifications = emptyList(),
            )
        }

        val cutoff = Instant.now().minusSeconds(CACHE_TTL_HOURS * 3600)
        var llmCallsUsed = 0

        val classifications = news.map { item ->
            // news_id è VARCHAR(512) (V029). FMP /news/stock non ha un id stabile,
            // quindi il fallback è url/title (URL spesso > limite): tronchiamo qui
            // alla fonte così lookup di dedup e insert usano la stessa chiave.
            val newsId = (item.newsId ?: item.url ?: item.title ?: return@map null).take(512)

            val cached = newsRepo.findByNewsId(newsId)
            if (cached != null && cached.classifiedAt.isAfter(cutoff)) {
                return@map cached
            }

            if (llmCallsUsed >= MAX_LLM_CALLS_PER_TICKER) {
                return@map persistClassification(ticker, item, newsId, SentimentClass.NEUTRAL, "Limit exceeded")
            }

            val classification = classifyWithLlm(item)
            llmCallsUsed++

            persistClassification(ticker, item, newsId, classification.first, classification.second)
        }.filterNotNull()

        val panicCount = classifications.count { it.sentimentClass == SentimentClass.TEMPORARY_PANIC.name }
        val structuralCount = classifications.count { it.sentimentClass == SentimentClass.STRUCTURAL_DAMAGE.name }
        val neutralCount = classifications.count { it.sentimentClass == SentimentClass.NEUTRAL.name }

        val dominant = when {
            panicCount >= structuralCount && panicCount >= neutralCount -> SentimentClass.TEMPORARY_PANIC
            structuralCount >= panicCount && structuralCount >= neutralCount -> SentimentClass.STRUCTURAL_DAMAGE
            else -> SentimentClass.NEUTRAL
        }

        return NewsSentimentResult(
            ticker = ticker,
            total = classifications.size,
            panicCount = panicCount,
            structuralCount = structuralCount,
            neutralCount = neutralCount,
            dominantClass = dominant,
            classifications = classifications.map {
                NewsClassificationSummary(it.newsId, it.headline, SentimentClass.valueOf(it.sentimentClass))
            },
        )
    }

    private fun classifyWithLlm(item: StockNewsItem): Pair<SentimentClass, String?> {
        val truncatedText = item.text?.take(500) ?: ""
        val prompt = """Classifica questa news finanziaria in una delle tre categorie:
            |TEMPORARY_PANIC (vendita emotiva senza danni fondamentali),
            |STRUCTURAL_DAMAGE (danno permanente al business),
            |NEUTRAL.
            |News: ${item.title}. $truncatedText.
            |Rispondi SOLO con JSON valido, motivazione max 200 caratteri:
            |{"classe": "...", "motivazione": "..."}""".trimMargin()

        return try {
            // maxTokens=512: a 200 il JSON veniva troncato a metà stringa
            // ("Unexpected end-of-input") quando la motivazione era verbosa.
            val response = anthropicClient.complete(prompt, maxTokens = 512)
            val parsed = mapper.readValue<Map<String, String>>(extractJsonObject(response))
            val classe = SentimentClass.valueOf(parsed["classe"]?.uppercase() ?: "NEUTRAL")
            val motivazione = parsed["motivazione"]
            classe to motivazione
        } catch (e: Exception) {
            log.warn("LLM classification failed for news '{}': {}", item.title?.take(50), e.message)
            SentimentClass.NEUTRAL to "Classification failed"
        }
    }

    // Estrae l'oggetto JSON dalla risposta LLM tollerando code-fence markdown
    // o prosa attorno: prende dal primo '{' all'ultimo '}'. Se non c'è un blocco
    // bilanciato (es. risposta troncata) ritorna il testo grezzo -> il parse
    // fallisce e il caller degrada a NEUTRAL.
    private fun extractJsonObject(raw: String): String {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        return if (start in 0 until end) raw.substring(start, end + 1) else raw
    }

    private fun persistClassification(
        ticker: String,
        item: StockNewsItem,
        newsId: String,
        classe: SentimentClass,
        motivazione: String?,
    ): NewsClassificationEntity {
        val existing = newsRepo.findByNewsId(newsId)
        val entity = existing ?: NewsClassificationEntity()
        entity.ticker = ticker
        // news_id è VARCHAR(512) (V029): tronchiamo difensivamente, dato che
        // il fallback usa l'URL FMP come id e alcuni URL sforano.
        entity.newsId = newsId.take(512)
        entity.publishedAt = item.publishedDate?.toInstant(ZoneOffset.UTC)
        entity.headline = item.title
        entity.url = item.url
        entity.sentimentClass = classe.name
        entity.motivazione = motivazione?.take(250)
        entity.classifiedAt = Instant.now()
        return newsRepo.save(entity)
    }
}

enum class SentimentClass {
    TEMPORARY_PANIC, STRUCTURAL_DAMAGE, NEUTRAL
}

data class NewsSentimentResult(
    val ticker: String,
    val total: Int,
    val panicCount: Int,
    val structuralCount: Int,
    val neutralCount: Int,
    val dominantClass: SentimentClass,
    val classifications: List<NewsClassificationSummary>,
)

data class NewsClassificationSummary(
    val newsId: String,
    val headline: String?,
    val sentimentClass: SentimentClass,
)
