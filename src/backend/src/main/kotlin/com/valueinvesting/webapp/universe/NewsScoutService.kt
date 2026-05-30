package com.valueinvesting.webapp.universe

import com.fasterxml.jackson.databind.ObjectMapper
import com.valueinvesting.webapp.fmp.FmpAdapter
import com.valueinvesting.webapp.fmp.dto.StockNewsItem
import com.valueinvesting.webapp.llm.AnthropicClient
import com.valueinvesting.webapp.llm.LlmException
import com.valueinvesting.webapp.llm.LlmRequest
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.ratelimiter.RequestNotPermitted
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

// Implementazione concreta di `NewsScoutProvider` (news scout LLM panic-buy).
//
// SCOPE — EP-012, US-047, TSK-128. Sostituisce il default `NoopNewsScoutProvider`
// (TSK-126) via @Primary quando la property `news-scout.enabled = true`
// (default true; impostare a false in test/dev o quando ANTHROPIC_API_KEY non
// e' configurato per disabilitare).
//
// PACKAGE NOTE — Il TSK-128 originale prescriveva
// `com/valueinvesting/service/`. Override post-TSK-126/127 al package canonico
// `com.valueinvesting.webapp.universe` per coerenza con UniverseScreenerService
// (TSK-126), che e' il consumatore del provider. Path drift documentato in
// wiki/log.md (develop entry TSK-128).
//
// SIGNATURE NOTE — Il TSK-128 originale prescriveva
// `scoutPanicBuyCandidates(candidates: List<UniverseCandidate>): List<String>`.
// Override al port `NewsScoutProvider.scoutTickers(seedTickers: List<String>):
// List<UniverseCandidate>` definito in TSK-126 — l'input ticker stringa basta
// (l'unica info usata e' il ticker) e l'output UniverseCandidate propaga
// `source = NEWS_SCOUT` per il dedupe a valle (priorita' 13F > SCREENER >
// NEWS_SCOUT).
//
// PIPELINE
//   1. Take top-N seed (cap `news-scout.max-input-seeds`, default 200).
//   2. Per ogni ticker: `FmpAdapter.getStockNews(ticker, newsDays)` —
//      runCatching, skip silenzioso su failure singolo ticker (per non
//      perdere i 199 buoni se 1 fallisce).
//   3. Skip se `newsContext.isEmpty()` (0 ticker con news) → no LLM call,
//      ritorna emptyList.
//   4. Costruisci 1 prompt aggregato (system + user) batch-style.
//   5. AnthropicClient.complete(LlmRequest) — fail-safe: catch LlmException +
//      Resilience4j exceptions (RequestNotPermitted, CallNotPermittedException)
//      + qualsiasi altra eccezione, ritorna emptyList + log warn (no block).
//   6. Parser regex+Jackson per estrarre array JSON anche se LLM aggiunge
//      fluff prima/dopo (canonico per Claude su prompt JSON-only).
//   7. Cap finale a `news-scout.max-results` (default 50).
//
// LLM MODEL — Usa il modello configurato in anthropic.model (env ANTHROPIC_MODEL,
// default claude-opus-4-8) via AnthropicClient: LlmRequest non forza un model,
// quindi il client risolve dalla config. ADR-019 v2 raccomanda Gemini 2.5 Flash per cost
// optimization news scout; drift documentato in log: "Gemini 2.5 Flash
// improvement out-of-scope, AnthropicClient sufficiente con budget gate".
//
// LLM BUDGET — AnthropicConfig non auto-wrappa un budget gate sul bean
// AnthropicClient; il gate `LlmBudgetConfigService` espone la config admin
// (US-055) ma non intercetta automaticamente la chiamata. In ottica fail-safe
// non-bloccante, il news scout NON pre-checks il budget: se Anthropic risponde
// 429/5xx l'LlmException viene catturata e il batch prosegue con emptyList,
// che e' il comportamento corretto per uno step "best-effort opzionale".
// Pre-check del budget = future scope (es. injecting LlmBudgetConfigService e
// short-circuit prima di costruire il prompt). [^src: ADR-019 §LLM cost gate]
//
// [^src: management/kanban/EP-012-batch-top-value-picks/US-047-universe-screener-service/TSK-128.md]
// [^src: wiki/concepts/market-fluctuations-graham.md §Distinzione tra panic e damage]
// [^src: wiki/syntheses/graham-investing-philosophy.md §Genealogia dei criteri difensivi]
@Component
@Primary
@ConditionalOnProperty(
    name = ["news-scout.enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class NewsScoutService(
    // Il fan-out news (~200 ticker) passa dall'unico RateLimiter FMP `fmp`
    // (280/min, condiviso online+batch — ADR-016 §Appendice A). Girando dentro
    // il batch (FmpBatchContext attivo), se il bucket e' esaurito il
    // ResilientFmpAdapter attende il refresh e ritenta invece di far fallire la
    // chiamata: il `runCatching` qui sotto resta come safety-net per errori veri
    // (FMP down), non piu' per il throttling.
    private val fmpAdapter: FmpAdapter,
    private val anthropicClient: AnthropicClient,
    private val properties: NewsScoutProperties,
    private val objectMapper: ObjectMapper,
) : NewsScoutProvider {

    override fun scoutTickers(seedTickers: List<String>): List<UniverseCandidate> {
        if (seedTickers.isEmpty()) {
            log.debug("NewsScout: seedTickers vuota, skip")
            return emptyList()
        }

        // Step 1 — cap input.
        val seeds = seedTickers.take(properties.maxInputSeeds)

        // Step 2 — fetch news per ognuno (best-effort per ticker).
        val newsContext: Map<String, List<StockNewsItem>> = seeds.associateWith { ticker ->
            runCatching { fmpAdapter.getStockNews(ticker, properties.newsDays) }
                .getOrElse { ex ->
                    log.warn(
                        "NewsScout: getStockNews failed for {} (skip): {}",
                        ticker, ex.message,
                    )
                    emptyList()
                }
        }.filterValues { it.isNotEmpty() }

        // Step 3 — short-circuit se nessuna news.
        if (newsContext.isEmpty()) {
            log.info(
                "NewsScout: 0 ticker su {} con news nei {} giorni — skip LLM call",
                seeds.size, properties.newsDays,
            )
            return emptyList()
        }

        // Step 4 — costruisci prompt aggregato.
        val systemPrompt = buildSystemPrompt()
        val userPrompt = buildUserPrompt(newsContext)

        val request = LlmRequest(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            maxTokens = properties.llmMaxTokens,
        )

        // Step 5 — chiama LLM con fail-safe globale (no block batch su LLM fail).
        val responseContent = try {
            anthropicClient.complete(request).content
        } catch (ex: LlmException) {
            log.warn(
                "NewsScout: LlmException (fail-safe, no-block batch): {} — {}",
                ex.javaClass.simpleName, ex.message,
            )
            return emptyList()
        } catch (ex: RequestNotPermitted) {
            log.warn(
                "NewsScout: Anthropic rate-limited (Resilience4j), skip: {}",
                ex.message,
            )
            return emptyList()
        } catch (ex: CallNotPermittedException) {
            log.warn(
                "NewsScout: Anthropic circuit-breaker open, skip: {}",
                ex.message,
            )
            return emptyList()
        } catch (ex: Exception) {
            log.warn(
                "NewsScout: unexpected exception calling Anthropic (fail-safe): {} — {}",
                ex.javaClass.simpleName, ex.message,
            )
            return emptyList()
        }

        // Step 6-7 — parse + cap.
        val parsed = parseTickersFromResponse(responseContent)
            .take(properties.maxResults)

        log.info(
            "NewsScout: input={} seeds, withNews={} ticker, LLM output={} ticker (cap {})",
            seeds.size, newsContext.size, parsed.size, properties.maxResults,
        )

        return parsed.map { (ticker, motivation) ->
            UniverseCandidate(
                ticker = ticker.uppercase(),
                source = CandidateSource.NEWS_SCOUT,
                marketCapUsd = null,
                sector = null,
                exchange = null,
                // Propaga la motivation breve LLM nel campo companyName: il
                // dedupe a valle (priorita' 13F > SCREENER > NEWS_SCOUT) tipicamente
                // sovrascrive con la versione SCREENER se il ticker era gia' in
                // pipeline; per ticker net-new dal scout la motivation resta
                // visibile per logging/debug.
                companyName = motivation.ifBlank { null },
            )
        }
    }

    /**
     * System prompt invariante: definisce il ruolo, il criterio Graham
     * "panic-buy zone" e il formato di output JSON strict.
     */
    private fun buildSystemPrompt(): String = """
        Sei un value investor expert in stile Benjamin Graham. Riceverai una lista di ticker quotati
        US (NASDAQ/NYSE, marketCap >3B USD) con le loro news degli ultimi ${properties.newsDays} giorni.

        TASK: identifica quali ticker mostrano segnali di "panic-buy zone" secondo Graham:
        - calo recente significativo (>=20% nelle ultime 4 settimane) percepito dalle news,
          MA fondamentali invariati (no scandali strutturali, no profit warning gravi,
          solo correzione di mercato emotiva / overreaction).
        - Esempi positivi: market overreaction a guidance prudente, calo settoriale, hype FUD.
        - Esempi negativi: fraude contabile, ristrutturazione del business, calo strutturale ricavi,
          regolatore che impone sanzioni gravi.

        OUTPUT: rispondi SOLO con un array JSON valido, niente testo prima o dopo.
        Schema: [{"ticker":"AAPL","motivation":"breve frase max 100 char"}, ...]
        Cap massimo ${properties.maxResults} entry. Se non identifichi nessun panic-buy: array vuoto [].
    """.trimIndent()

    /**
     * User prompt: blocco news per ticker, cap `maxNewsPerTicker` per evitare
     * prompt giganti (high-coverage ticker hanno 100+ news/30gg).
     */
    private fun buildUserPrompt(newsContext: Map<String, List<StockNewsItem>>): String {
        val sb = StringBuilder()
        sb.append("Ticker e news (max ${properties.maxNewsPerTicker} per ticker, piu' recenti prima):\n\n")
        newsContext.forEach { (ticker, news) ->
            sb.append("=== ").append(ticker).append(" ===\n")
            news
                .sortedByDescending { it.publishedDate }
                .take(properties.maxNewsPerTicker)
                .forEach { item ->
                    val title = item.title?.trim().orEmpty()
                    val date = item.publishedDate?.toLocalDate()?.toString().orEmpty()
                    if (title.isNotBlank()) {
                        sb.append("- [").append(date).append("] ").append(title).append('\n')
                    }
                }
            sb.append('\n')
        }
        return sb.toString()
    }

    /**
     * Parsing best-effort della risposta LLM. Claude segue il system prompt
     * "SOLO array JSON" ~95% del tempo, ma occasionalmente aggiunge testo
     * pre/post (es. "Ecco i candidati: [...]"). Regex DOTALL estrae il primo
     * array JSON e Jackson parsa.
     */
    private fun parseTickersFromResponse(response: String): List<Pair<String, String>> {
        if (response.isBlank()) return emptyList()

        val jsonArrayRegex = Regex("\\[\\s*(?:\\{.*?})?\\s*(?:,\\s*\\{.*?})*\\s*]", RegexOption.DOT_MATCHES_ALL)
        val match = jsonArrayRegex.find(response) ?: run {
            log.warn(
                "NewsScout: nessun array JSON estratto dalla response LLM (len={})",
                response.length,
            )
            return emptyList()
        }

        return runCatching {
            val node = objectMapper.readTree(match.value)
            if (!node.isArray) return@runCatching emptyList<Pair<String, String>>()
            node.mapNotNull { entry ->
                val ticker = entry.get("ticker")?.asText()?.trim()
                val motivation = entry.get("motivation")?.asText()?.trim().orEmpty()
                if (ticker.isNullOrBlank()) null else ticker to motivation
            }
        }.getOrElse { ex ->
            log.warn(
                "NewsScout: JSON parse fallito (response head='{}'): {}",
                response.take(120).replace('\n', ' '),
                ex.message,
            )
            emptyList()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(NewsScoutService::class.java)
    }
}
