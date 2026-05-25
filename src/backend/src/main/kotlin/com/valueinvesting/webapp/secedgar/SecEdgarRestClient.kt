package com.valueinvesting.webapp.secedgar

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.benmanes.caffeine.cache.Cache
import com.valueinvesting.webapp.secedgar.dto.SecFilingMetadata
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.time.LocalDate
import java.time.format.DateTimeParseException

// Sync implementation di `SecEdgarAdapter` su Spring 6 `RestClient`.
//
// Razionale sync — coerente con `FmpAdapterRestClient`: chiamate sequenziali,
// resilienza wrappata dal decorator (`ResilientSecEdgarAdapter`). I caller
// (DeepAnalysisService, TSK-094+) parallelizzano a livello servizio.
//
// SCHEMA SEC API (empiricamente verificato 2026-05-25 su CIK0000320193):
//
// 1) GET https://www.sec.gov/files/company_tickers.json
//    Body: { "0": { "cik_str": 320193, "ticker": "AAPL", "title": "Apple Inc." }, ... }
//    Map index→object. ~10k entries (~3 MB). Caricato 1x in memoria, cache lazy.
//
// 2) GET https://data.sec.gov/submissions/CIK{padded10}.json
//    Body: { "filings": { "recent": {
//        "accessionNumber": ["0001140361-26-020871", ...],
//        "form":            ["4", "4", "144", "10-Q", "8-K", ...],
//        "filingDate":      ["2026-05-12", "2026-05-08", ...],
//        "primaryDocument": ["xslF345X06/form4.xml", ...]
//    } }, ... }
//    PARALLEL ARRAYS by index. Zip → filter by form ∈ formTypes → take limit.
//
// 3) GET {primaryDocumentUrl}  — testo HTML/XBRL del filing.
//    URL ricostruita: https://www.sec.gov/Archives/edgar/data/{cik_no_lead_zeros}
//                     /{accession_no_dashes}/{primaryDocument}
//
// FAIR-ACCESS HEADERS (obbligatori SEC):
//   - User-Agent: ValueInvesting-App/1.0 {email}
//   - Accept-Encoding: gzip, deflate  (RestClient lo gestisce di default)
//   - Host: data.sec.gov  /  www.sec.gov  (settato dal client HTTP automaticamente)
//
// MAPPING HTTP→exception:
//   - 403 → SecEdgarAccessDeniedException (User-Agent invalido o ban)
//   - 429 → SecEdgarRateLimitException (cap 10 req/s superato)
//   - 404 → null per downloadFilingHtml; emptyList per listFilings
//   - 5xx → SecEdgarServiceException(httpStatus)
//
// [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-038-sec-edgar-adapter/TSK-091.md]
// [^src: wiki/concepts/sec-filings-analysis.md §Accesso ai filing]
@Component
class SecEdgarRestClient(
    private val restClientBuilder: RestClient.Builder,
    private val properties: SecEdgarProperties,
    private val objectMapper: ObjectMapper,
    /**
     * Caffeine cache ticker (uppercase) → CIK (10-digit zero-padded).
     * Iniettata dal bean `secEdgarTickerToCikCache` in `SecEdgarCacheConfig`.
     * TTL = `sec.edgar.cik-cache-ttl-days` (default 30 giorni).
     * [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-038-sec-edgar-adapter/TSK-092.md §1]
     */
    private val tickerToCikCache: Cache<String, String>,
) : SecEdgarAdapter {

    private val log = LoggerFactory.getLogger(javaClass)

    // SEC fair-access policy: User-Agent identifies the caller. Format raccomandato:
    // "AppName/Version contact-email". `dev@example.com` è un placeholder dev — il
    // prod DEVE overridare via env var SEC_EDGAR_USER_AGENT_EMAIL.
    private val userAgent: String by lazy {
        "ValueInvesting-App/1.0 ${properties.userAgent.email}"
    }

    // Client per data.sec.gov (submissions API).
    private val dataClient: RestClient by lazy {
        restClientBuilder
            .baseUrl(properties.baseUrl)
            .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build()
    }

    // Client per www.sec.gov (company_tickers + Archives raw HTML).
    // Separato perché Accept default è text/html anziché JSON e baseUrl differisce.
    private val filesClient: RestClient by lazy {
        restClientBuilder
            .baseUrl(properties.filesUrl)
            .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
            .build()
    }

    // ---------------------------------------------------------------------------------
    // resolveCikFromTicker
    // ---------------------------------------------------------------------------------
    //
    // Cache strategy (TSK-092): Caffeine `expireAfterWrite(cikCacheTtlDays)` + bulk
    // populate on first miss. Trade-off documentato in `SecEdgarCacheConfig`.
    //   - cache hit (subsequent lookups entro TTL): 0 HTTP, O(1) — DEBUG log
    //   - cache miss (cold-start OR primo lookup post-TTL): 1 HTTP GET company_tickers.json
    //     popola ~10k entries d'un colpo via `loadTickerCikMap()`
    //
    // Note: il check "cache is empty" è approssimazione di "TTL scaduto" che evita
    // di ri-scaricare il JSON ad ogni chiamata se il ticker richiesto non esiste
    // (delisted/typo). Dopo expireAll, il primo `getIfPresent` ritorna null → triggera
    // load → cache ri-popolata; se il ticker richiesto continua a non esistere,
    // getIfPresent post-load ritorna ancora null ma le altre ~10k entries sono in cache
    // e fresche per i 30 giorni successivi.
    override fun resolveCikFromTicker(ticker: String): String? {
        require(ticker.isNotBlank()) { "ticker must not be blank" }
        val upper = ticker.uppercase()

        val cached = tickerToCikCache.getIfPresent(upper)
        if (cached != null) {
            log.debug("CIK cache hit: {} → {}", upper, cached)
            return cached
        }

        // Cache miss: o cold-start o TTL scaduto o ticker mai visto.
        // Se la cache è completamente vuota (stato post-startup o post-expireAll),
        // ricarica l'intero JSON. Altrimenti ticker non esiste → ritorna null senza
        // ulteriore I/O (evita storm su typo ripetuti).
        if (tickerToCikCache.estimatedSize() == 0L) {
            loadTickerCikMap()
        }

        val resolved = tickerToCikCache.getIfPresent(upper)
        if (resolved != null) {
            log.debug("CIK resolved: {} → {}", upper, resolved)
        } else {
            log.debug("CIK not found in SEC company_tickers.json: {}", upper)
        }
        return resolved
    }

    /**
     * Scarica company_tickers.json e popola la cache Caffeine in-memory.
     *
     * Strategia (TSK-091/TSK-092 — A: bulk populate):
     *   Alla prima chiamata `resolveCikFromTicker` post-TTL (cold-start o scadenza
     *   30 giorni), carica TUTTI ~10k ticker in un singolo HTTP GET. Trade-off vs
     *   eager startup load: prima chiamata ha latenza ~1-2s + 3MB download; chiamate
     *   successive sono O(1) cache hit fino a expireAfterWrite. Accettabile per VI
     *   use-case (analisi on-demand, non bulk batch).
     *
     * Errori:
     *   - 403 → SecEdgarAccessDeniedException (propagata, cache vuota → retry next)
     *   - 5xx → SecEdgarServiceException
     *   - Body non parseable → cache vuota + log warn, NON throw (degrada a null su
     *     resolveCikFromTicker — l'app continua a girare).
     */
    private fun loadTickerCikMap() {
        log.info("Loading SEC company_tickers.json (cold-start cache populate)")
        val body: String? = try {
            filesClient.get()
                .uri("/files/company_tickers.json")
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError) { _, response ->
                    val status = response.statusCode
                    when (status.value()) {
                        403 -> {
                            log.error("SEC 403 on /files/company_tickers.json — fair-access policy violation")
                            throw SecEdgarAccessDeniedException(
                                "SEC 403 on /files/company_tickers.json — verifica User-Agent email",
                            )
                        }
                        429 -> {
                            log.warn("SEC 429 on /files/company_tickers.json — rate limited")
                            throw SecEdgarRateLimitException(
                                "SEC 429 on /files/company_tickers.json",
                            )
                        }
                        else -> {
                            log.warn("SEC 4xx on /files/company_tickers.json status={}", status)
                            throw SecEdgarServiceException(
                                "SEC 4xx on /files/company_tickers.json: $status",
                                httpStatus = status.value(),
                            )
                        }
                    }
                }
                .onStatus(HttpStatusCode::is5xxServerError) { _, response ->
                    val status = response.statusCode
                    log.warn("SEC 5xx on /files/company_tickers.json status={}", status)
                    throw SecEdgarServiceException(
                        "SEC 5xx on /files/company_tickers.json: $status",
                        httpStatus = status.value(),
                    )
                }
                .body(String::class.java)
        } catch (ex: SecEdgarAccessDeniedException) {
            throw ex
        } catch (ex: SecEdgarRateLimitException) {
            throw ex
        } catch (ex: SecEdgarServiceException) {
            throw ex
        } catch (ex: RestClientResponseException) {
            throw SecEdgarServiceException(
                "SEC call failed: ${ex.statusCode} on /files/company_tickers.json",
                httpStatus = ex.statusCode.value(),
                cause = ex,
            )
        }

        if (body.isNullOrBlank()) {
            log.warn("SEC /files/company_tickers.json returned empty body — cache stays empty")
            return
        }

        try {
            val root: JsonNode = objectMapper.readTree(body)
            // Schema: { "0": {"cik_str": N, "ticker": "X", "title": "..."}, "1": {...}, ... }
            // Itera sui valori (i nomi-chiave "0","1",... non ci servono).
            var count = 0
            for (entry in root) {
                val tickerNode = entry.get("ticker")
                val cikNode = entry.get("cik_str")
                if (tickerNode != null && cikNode != null && tickerNode.isTextual && cikNode.isNumber) {
                    val t = tickerNode.asText().uppercase()
                    // Pad a 10 cifre con zeri leading: SEC submissions API richiede questo formato.
                    val cikPadded = cikNode.asLong().toString().padStart(10, '0')
                    tickerToCikCache.put(t, cikPadded)
                    count++
                }
            }
            log.info("SEC ticker→CIK cache populated: {} entries (TTL {} days)",
                count, properties.cikCacheTtlDays)
        } catch (ex: Exception) {
            log.warn("Failed to parse SEC company_tickers.json — cache stays empty", ex)
            // Non rilanciamo: degradazione gracefuls al null successivo invece di crash.
        }
    }

    // ---------------------------------------------------------------------------------
    // listFilings
    // ---------------------------------------------------------------------------------
    override fun listFilings(
        cik: String,
        formTypes: List<String>,
        limit: Int,
    ): List<SecFilingMetadata> {
        require(cik.isNotBlank()) { "cik must not be blank" }
        require(limit > 0) { "limit must be > 0" }
        require(cik.length == 10 && cik.all { it.isDigit() }) {
            "cik must be 10-digit zero-padded (got: '$cik')"
        }

        val body: String? = try {
            dataClient.get()
                .uri("/submissions/CIK{cik}.json", cik)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError) { _, response ->
                    val status = response.statusCode
                    when (status.value()) {
                        403 -> throw SecEdgarAccessDeniedException(
                            "SEC 403 on /submissions/CIK$cik.json",
                        )
                        404 -> {
                            log.warn("SEC 404 on /submissions/CIK{}.json — CIK unknown", cik)
                            throw SecEdgarCikNotFoundSentinel()
                        }
                        429 -> throw SecEdgarRateLimitException(
                            "SEC 429 on /submissions/CIK$cik.json",
                        )
                        else -> throw SecEdgarServiceException(
                            "SEC 4xx on /submissions/CIK$cik.json: $status",
                            httpStatus = status.value(),
                        )
                    }
                }
                .onStatus(HttpStatusCode::is5xxServerError) { _, response ->
                    val status = response.statusCode
                    throw SecEdgarServiceException(
                        "SEC 5xx on /submissions/CIK$cik.json: $status",
                        httpStatus = status.value(),
                    )
                }
                .body(String::class.java)
        } catch (ex: SecEdgarCikNotFoundSentinel) {
            return emptyList()
        } catch (ex: SecEdgarAccessDeniedException) {
            throw ex
        } catch (ex: SecEdgarRateLimitException) {
            throw ex
        } catch (ex: SecEdgarServiceException) {
            throw ex
        } catch (ex: RestClientResponseException) {
            throw SecEdgarServiceException(
                "SEC call failed: ${ex.statusCode} on /submissions/CIK$cik.json",
                httpStatus = ex.statusCode.value(),
                cause = ex,
            )
        }

        if (body.isNullOrBlank()) {
            log.warn("SEC /submissions/CIK{}.json returned empty body", cik)
            return emptyList()
        }

        return parseFilingsResponse(body, cik, formTypes, limit)
    }

    /**
     * Sentinel privato per 404 → emptyList() senza crollare la chain Resilience4j.
     * 404 su un CIK = CIK inesistente, NOT a service failure. Non deve far tripping
     * il CircuitBreaker (configurato in `SecEdgarResilienceConfig` per ignorare
     * questa exception non perché compaia nei record, ma perché mai lasciata uscire
     * dall'adapter — la catturiamo immediatamente).
     */
    private class SecEdgarCikNotFoundSentinel : RuntimeException()

    /**
     * Estrae i filing dal JSON SEC submissions e li mappa a SecFilingMetadata.
     * Parallel-arrays approach: zip per indice + filter per form + take limit.
     */
    private fun parseFilingsResponse(
        body: String,
        cik: String,
        formTypes: List<String>,
        limit: Int,
    ): List<SecFilingMetadata> {
        val recent: JsonNode = try {
            objectMapper.readTree(body).path("filings").path("recent")
        } catch (ex: Exception) {
            log.warn("Failed to parse SEC submissions JSON for CIK={}", cik, ex)
            return emptyList()
        }

        if (recent.isMissingNode || !recent.isObject) {
            log.warn("SEC submissions JSON for CIK={} missing 'filings.recent' object", cik)
            return emptyList()
        }

        val accessionArr = recent.path("accessionNumber")
        val formArr = recent.path("form")
        val filingDateArr = recent.path("filingDate")
        val primaryDocArr = recent.path("primaryDocument")

        if (!accessionArr.isArray || !formArr.isArray) {
            log.warn("SEC submissions JSON for CIK={} missing parallel arrays", cik)
            return emptyList()
        }

        val size = listOf(
            accessionArr.size(),
            formArr.size(),
            filingDateArr.size(),
            primaryDocArr.size(),
        ).min()

        // CIK senza zeri leading per costruire URL Archives.
        val cikNumeric = cik.trimStart('0').ifEmpty { "0" }
        val formTypeSet = formTypes.map { it.uppercase() }.toSet()

        val result = mutableListOf<SecFilingMetadata>()
        for (i in 0 until size) {
            val form = formArr[i]?.asText()
            if (form == null || form.uppercase() !in formTypeSet) continue

            val accession = accessionArr[i]?.asText()
            val filingDate = filingDateArr[i]?.asText()?.let { parseLocalDate(it) }
            val primaryDoc = primaryDocArr[i]?.asText()

            val url = if (!accession.isNullOrBlank() && !primaryDoc.isNullOrBlank()) {
                val accessionNoDash = accession.replace("-", "")
                "${properties.filesUrl}/Archives/edgar/data/$cikNumeric/$accessionNoDash/$primaryDoc"
            } else {
                null
            }

            result.add(
                SecFilingMetadata(
                    accessionNumber = accession,
                    formType = form,
                    filedAt = filingDate,
                    primaryDocumentUrl = url,
                ),
            )
            if (result.size >= limit) break
        }
        return result
    }

    private fun parseLocalDate(raw: String): LocalDate? = try {
        LocalDate.parse(raw)
    } catch (ex: DateTimeParseException) {
        log.debug("SEC filingDate non parseable as ISO LocalDate: {}", raw)
        null
    }

    // ---------------------------------------------------------------------------------
    // downloadFilingHtml
    // ---------------------------------------------------------------------------------
    override fun downloadFilingHtml(url: String): String? {
        require(url.isNotBlank()) { "url must not be blank" }
        // Per security/sandbox sempre verifichiamo che la URL sia SEC-domain.
        require(url.startsWith(properties.filesUrl) || url.startsWith(properties.baseUrl)) {
            "url must be on sec.gov domain (got: '$url')"
        }

        // Usa un client dedicato senza baseUrl così possiamo passare URL absolute.
        // Il default User-Agent e Accept-Encoding sono ereditati comunque.
        val absClient = restClientBuilder
            .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
            .build()

        return try {
            absClient.get()
                .uri(url)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError) { _, response ->
                    val status = response.statusCode
                    when (status.value()) {
                        403 -> throw SecEdgarAccessDeniedException(
                            "SEC 403 downloading filing url=$url",
                        )
                        404 -> {
                            log.warn("SEC 404 downloading filing url={}", url)
                            throw SecEdgarFilingNotFoundSentinel()
                        }
                        429 -> throw SecEdgarRateLimitException(
                            "SEC 429 downloading filing url=$url",
                        )
                        else -> throw SecEdgarServiceException(
                            "SEC 4xx downloading filing: $status url=$url",
                            httpStatus = status.value(),
                        )
                    }
                }
                .onStatus(HttpStatusCode::is5xxServerError) { _, response ->
                    val status = response.statusCode
                    throw SecEdgarServiceException(
                        "SEC 5xx downloading filing: $status url=$url",
                        httpStatus = status.value(),
                    )
                }
                .body(String::class.java)
        } catch (ex: SecEdgarFilingNotFoundSentinel) {
            null
        } catch (ex: SecEdgarAccessDeniedException) {
            throw ex
        } catch (ex: SecEdgarRateLimitException) {
            throw ex
        } catch (ex: SecEdgarServiceException) {
            throw ex
        } catch (ex: RestClientResponseException) {
            throw SecEdgarServiceException(
                "SEC call failed: ${ex.statusCode} downloading filing url=$url",
                httpStatus = ex.statusCode.value(),
                cause = ex,
            )
        }
    }

    /**
     * Sentinel privato per 404 download → null senza crollare la chain Resilience4j.
     */
    private class SecEdgarFilingNotFoundSentinel : RuntimeException()
}
