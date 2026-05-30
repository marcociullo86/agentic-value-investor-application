package com.valueinvesting.webapp.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.valueinvesting.webapp.fmp.FmpAdapter
import com.valueinvesting.webapp.fmp.dto.StockNewsItem
import com.valueinvesting.webapp.llm.AnthropicClient
import com.valueinvesting.webapp.llm.LlmInteractionLogger
import com.valueinvesting.webapp.persistence.entity.NewsClassificationEntity
import com.valueinvesting.webapp.persistence.repository.NewsClassificationRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneOffset

/**
 * News sentiment per la deep analysis, in ottica value investing (Graham Cap.8 —
 * [[market-fluctuations-graham]]). Distingue il *panico temporaneo di Mr. Market*
 * (opportunità di acquisto) dal *danno strutturale permanente* del valore intrinseco
 * (value trap da evitare).
 *
 * Pipeline a imbuto (sostituisce il vecchio "classifica-tutto + voto di maggioranza",
 * che annegava un segnale strutturale reale nel rumore di N notizie neutre):
 *   1. **Pre-filtro deterministico** (zero LLM): dedup, scarto pattern di rumore
 *      (price target, listicle, premarket/movers…), ranking per materialità + recency,
 *      taglio ai top [MAX_CURATED].
 *   2. **Sintesi LLM unica** (1 chiamata, non N): un solo prompt valuta il set curato
 *      in modo olistico e ritorna la classe per-item + il flag di impairment permanente.
 *   3. **Dominante asimmetrica**: capital-preservation first — **un solo**
 *      STRUCTURAL_DAMAGE credibile vince sul conteggio (Buffett rule #1).
 *
 * Cache: le classificazioni del set curato sono persistite in `news_classification`
 * (TTL [CACHE_TTL_HOURS]h); se il set corrente è già tutto in cache fresca il verdetto
 * viene ricostruito senza nuove chiamate LLM.
 *
 * [^src: wiki/concepts/market-fluctuations-graham.md §Distinzione tra panic e damage]
 */
@Service
class NewsSentimentService(
    private val fmpAdapter: FmpAdapter,
    private val anthropicClient: AnthropicClient,
    private val newsRepo: NewsClassificationRepository,
    private val llmInteractionLogger: LlmInteractionLogger = LlmInteractionLogger(),
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = jacksonObjectMapper()

    companion object {
        // Finestra value-investing per l'analisi on-demand: 90gg ≈ ultimo ciclo
        // earnings, equilibrio tra segnale recente e rumore (US-042).
        private const val NEWS_WINDOW_DAYS = 90
        private const val CACHE_TTL_HOURS = 24L

        // Notizie passate all'LLM dopo il pre-filtro: cap olistico (1 sola chiamata).
        private const val MAX_CURATED = 12
        private const val SNIPPET_LEN = 300
        private const val SYNTH_MAX_TOKENS = 1024
        private const val NEWS_ID_MAX = 512
        private const val MOTIVAZIONE_MAX = 250

        // Pattern di rumore Mr. Market (titoli a basso/nullo valore informativo per
        // un value investor): listicle, recap di prezzo, premarket/movers, price target.
        private val NOISE_PATTERNS: List<Regex> = listOf(
            "price target", "stocks to watch", "stocks to buy", "best stocks",
            "things to know", "what to watch", "premarket", "pre-market",
            "market wrap", "market update", "movers", "week ahead", "zacks rank",
            "should you buy", "is it time to buy", "stocks to consider",
        ).map { Regex(Regex.escape(it), RegexOption.IGNORE_CASE) }

        // Keyword di materialità: eventi che possono intaccare il valore intrinseco.
        // Pesano il ranking così le notizie sostanziali galleggiano in cima.
        private val MATERIALITY_KEYWORDS: List<Regex> = listOf(
            "lawsuit", "sued", "\\bsec\\b", "investigation", "probe", "fraud",
            "accounting", "restatement", "guidance", "recall", "bankruptcy",
            "default", "downgrade", "resign", "steps down", "resignation",
            "\\bceo\\b", "\\bcfo\\b", "data breach", "\\bbreach\\b", "antitrust",
            "penalty", "settlement", "layoff", "impairment", "write-down",
            "writedown", "delisting", "going concern", "earnings miss", "misses",
            "plunge", "collapse", "short seller", "short-seller", "warning",
            "slash", "halt", "subpoena",
        ).map { Regex(it, RegexOption.IGNORE_CASE) }
    }

    @Transactional
    fun classify(ticker: String): NewsSentimentResult {
        val rawNews = fmpAdapter.getStockNews(ticker, NEWS_WINDOW_DAYS)
        if (rawNews.isEmpty()) return emptyResult(ticker)

        // Stage 1 — pre-filtro deterministico (zero LLM).
        val curated = curate(rawNews)
        if (curated.isEmpty()) return emptyResult(ticker)

        // Cache: se tutto il set curato è già classificato entro la TTL, ricostruisci
        // senza chiamare l'LLM (US-042: 2ª analisi entro 24h non riconsuma LLM).
        val cutoff = Instant.now().minusSeconds(CACHE_TTL_HOURS * 3600)
        val cachedRows = curated.map { newsRepo.findByNewsId(newsKey(it)) }
        val allFresh = cachedRows.all { it != null && it.classifiedAt.isAfter(cutoff) }

        val classified: List<ClassifiedItem> = if (allFresh) {
            cachedRows.filterNotNull().map {
                ClassifiedItem(
                    newsId = it.newsId,
                    headline = it.headline,
                    sentimentClass = SentimentClass.valueOf(it.sentimentClass),
                    textExcerpt = it.textExcerpt,
                    motivazione = it.motivazione,
                    url = it.url,
                )
            }
        } else {
            val synthesis = synthesize(ticker, curated)
            curated.mapIndexed { idx, item ->
                val classe = synthesis.classOf(idx)
                val entity = persistClassification(
                    ticker, item, newsKey(item), classe, synthesis.motivazioneOf(idx),
                )
                ClassifiedItem(
                    newsId = entity.newsId,
                    headline = entity.headline,
                    sentimentClass = classe,
                    textExcerpt = entity.textExcerpt,
                    motivazione = entity.motivazione,
                    url = entity.url,
                )
            }
        }

        val panic = classified.count { it.sentimentClass == SentimentClass.TEMPORARY_PANIC }
        val structural = classified.count { it.sentimentClass == SentimentClass.STRUCTURAL_DAMAGE }
        val neutral = classified.count { it.sentimentClass == SentimentClass.NEUTRAL }

        return NewsSentimentResult(
            ticker = ticker,
            total = classified.size,
            panicCount = panic,
            structuralCount = structural,
            neutralCount = neutral,
            dominantClass = deriveDominant(classified.map { it.sentimentClass }),
            classifications = classified.map {
                NewsClassificationSummary(
                    newsId = it.newsId,
                    headline = it.headline,
                    sentimentClass = it.sentimentClass,
                    textExcerpt = it.textExcerpt,
                    motivazione = it.motivazione,
                    url = it.url,
                )
            },
        )
    }

    // ---- Stage 1: pre-filtro deterministico ------------------------------------

    private fun curate(news: List<StockNewsItem>): List<StockNewsItem> =
        news.asSequence()
            // dedup per chiave news + per titolo normalizzato (repost/aggregatori).
            .distinctBy { newsKey(it) }
            .distinctBy { it.title?.lowercase()?.trim() ?: newsKey(it) }
            // scarta il rumore Mr. Market.
            .filterNot { isNoise(it) }
            // ranking: materialità desc, poi recency desc.
            .sortedWith(
                compareByDescending<StockNewsItem> { materialityScore(it) }
                    .thenByDescending { it.publishedDate ?: java.time.LocalDateTime.MIN },
            )
            .take(MAX_CURATED)
            .toList()

    private fun isNoise(item: StockNewsItem): Boolean {
        val title = item.title ?: return false
        return NOISE_PATTERNS.any { it.containsMatchIn(title) }
    }

    private fun materialityScore(item: StockNewsItem): Int {
        val haystack = "${item.title.orEmpty()} ${item.text.orEmpty()}"
        return MATERIALITY_KEYWORDS.count { it.containsMatchIn(haystack) }
    }

    // ---- Stage 2: sintesi LLM unica --------------------------------------------

    private fun synthesize(ticker: String, curated: List<StockNewsItem>): SynthesisResult {
        val itemsBlock = curated.mapIndexed { idx, item ->
            val date = item.publishedDate?.toLocalDate()?.toString() ?: "n/d"
            val snippet = item.text?.take(SNIPPET_LEN).orEmpty()
            "[$idx] ($date) ${item.title.orEmpty()} — $snippet"
        }.joinToString("\n")

        val prompt = """Sei un value investor (Graham/Buffett). Analizza queste notizie su un singolo titolo e classifica OGNI item in una delle tre classi:
            |- TEMPORARY_PANIC: volatilità/vendita emotiva senza intacco dei fondamentali (macro fears, panico generico, reazione di breve).
            |- STRUCTURAL_DAMAGE: evidenza di impairment PERMANENTE del valore intrinseco (frode, indagine/accusa SEC, perdita di licenza o del cliente principale, taglio guidance strutturale, going concern, restatement, default).
            |- NEUTRAL: notizia routinaria senza impatto sul valore.
            |Regola: la materialità conta più del numero. Sii rigoroso su STRUCTURAL_DAMAGE (solo evidenza credibile e corroborata).
            |
            |Notizie:
            |$itemsBlock
            |
            |Rispondi SOLO con JSON valido (motivazione max 200 caratteri):
            |{"impairment_permanente":"si|no|incerto","items":[{"idx":0,"classe":"NEUTRAL","motivazione":"..."}],"sintesi":"..."}""".trimMargin()

        return try {
            val start = System.currentTimeMillis()
            val response = anthropicClient.complete(prompt, maxTokens = SYNTH_MAX_TOKENS)
            llmInteractionLogger.log(
                "news-synthesis:$ticker", null, prompt, response, System.currentTimeMillis() - start,
            )
            parseSynthesis(extractJsonObject(response), curated.size)
        } catch (e: Exception) {
            // Degrado sicuro: nessun veto/panic-buy indotto da un parse fallito.
            log.warn("News synthesis LLM failed: {}", e.message)
            SynthesisResult(emptyMap(), emptyMap())
        }
    }

    private fun parseSynthesis(json: String, count: Int): SynthesisResult {
        val root = mapper.readTree(json)
        val classes = HashMap<Int, SentimentClass>()
        val motivazioni = HashMap<Int, String?>()
        root.path("items").forEach { node ->
            val idx = node.path("idx").asInt(-1)
            if (idx in 0 until count) {
                classes[idx] = parseClass(node.path("classe").asText("NEUTRAL"))
                motivazioni[idx] = node.path("motivazione").asText(null)
            }
        }
        // Cross-check difensivo: se l'LLM dichiara impairment permanente ma non ha
        // marcato alcun item STRUCTURAL, promuovi il più materiale (idx 0 = top rank).
        val impairment = root.path("impairment_permanente").asText("no").equals("si", ignoreCase = true)
        if (impairment && count > 0 && classes.values.none { it == SentimentClass.STRUCTURAL_DAMAGE }) {
            classes[0] = SentimentClass.STRUCTURAL_DAMAGE
            motivazioni.putIfAbsent(0, "Impairment permanente dichiarato dalla sintesi")
        }
        return SynthesisResult(classes, motivazioni)
    }

    private fun parseClass(raw: String): SentimentClass =
        runCatching { SentimentClass.valueOf(raw.trim().uppercase()) }.getOrDefault(SentimentClass.NEUTRAL)

    // Estrae l'oggetto JSON dalla risposta LLM tollerando code-fence o prosa attorno:
    // dal primo '{' all'ultimo '}'. Se non bilanciato ritorna il grezzo -> parse fallisce
    // -> degrado a NEUTRAL.
    private fun extractJsonObject(raw: String): String {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        return if (start in 0 until end) raw.substring(start, end + 1) else raw
    }

    // ---- Dominante asimmetrica (capital preservation first) --------------------

    private fun deriveDominant(classes: List<SentimentClass>): SentimentClass {
        val structural = classes.count { it == SentimentClass.STRUCTURAL_DAMAGE }
        val panic = classes.count { it == SentimentClass.TEMPORARY_PANIC }
        val neutral = classes.count { it == SentimentClass.NEUTRAL }
        return when {
            // Un solo danno strutturale credibile basta a far scattare il veto value-trap.
            structural > 0 -> SentimentClass.STRUCTURAL_DAMAGE
            panic > 0 && panic >= neutral -> SentimentClass.TEMPORARY_PANIC
            else -> SentimentClass.NEUTRAL
        }
    }

    // ---- Persistenza ------------------------------------------------------------

    private fun newsKey(item: StockNewsItem): String =
        (item.newsId ?: item.url ?: item.title ?: "").take(NEWS_ID_MAX)

    private fun persistClassification(
        ticker: String,
        item: StockNewsItem,
        newsId: String,
        classe: SentimentClass,
        motivazione: String?,
    ): NewsClassificationEntity {
        val entity = newsRepo.findByNewsId(newsId) ?: NewsClassificationEntity()
        entity.ticker = ticker
        entity.newsId = newsId.take(NEWS_ID_MAX)
        entity.publishedAt = item.publishedDate?.toInstant(ZoneOffset.UTC)
        entity.headline = item.title
        entity.url = item.url
        entity.sentimentClass = classe.name
        entity.motivazione = motivazione?.take(MOTIVAZIONE_MAX)
        entity.textExcerpt = item.text?.take(SNIPPET_LEN)
        entity.classifiedAt = Instant.now()
        return newsRepo.save(entity)
    }

    private fun emptyResult(ticker: String) = NewsSentimentResult(
        ticker = ticker,
        total = 0,
        panicCount = 0,
        structuralCount = 0,
        neutralCount = 0,
        dominantClass = SentimentClass.NEUTRAL,
        classifications = emptyList(),
    )

    private data class ClassifiedItem(
        val newsId: String,
        val headline: String?,
        val sentimentClass: SentimentClass,
        val textExcerpt: String?,
        val motivazione: String?,
        val url: String?,
    )

    private class SynthesisResult(
        private val classes: Map<Int, SentimentClass>,
        private val motivazioni: Map<Int, String?>,
    ) {
        fun classOf(idx: Int): SentimentClass = classes[idx] ?: SentimentClass.NEUTRAL
        fun motivazioneOf(idx: Int): String? = motivazioni[idx]
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
    val textExcerpt: String? = null,
    val motivazione: String? = null,
    val url: String? = null,
)
