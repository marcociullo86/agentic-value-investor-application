package com.valueinvesting.webapp.fmp

import com.valueinvesting.webapp.config.AppProperties
import com.valueinvesting.webapp.fmp.dto.BalanceSheetDto
import com.valueinvesting.webapp.fmp.dto.CashFlowDto
import com.valueinvesting.webapp.fmp.dto.DividendRecord
import com.valueinvesting.webapp.fmp.dto.EodPriceRecord
import com.valueinvesting.webapp.fmp.dto.IncomeStatementDto
import com.valueinvesting.webapp.fmp.dto.KeyMetricsDto
import com.valueinvesting.webapp.fmp.dto.ProfileDto
import com.valueinvesting.webapp.fmp.dto.ScreenedStockDto
import com.valueinvesting.webapp.fmp.dto.SearchHitDto
import com.valueinvesting.webapp.fmp.dto.SecFilingFmpDto
import com.valueinvesting.webapp.fmp.dto.StockNewsItem
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

// Sync implementation of FmpAdapter using Spring 6 RestClient.
// Razionale sync (no WebClient): la parallelizzazione delle 4 chiamate è demandata al
// caller (AnalyzeTickerService) via CompletableFuture su un Executor dedicato — vedi
// TSK-018. Mantenere l'adapter sync semplifica i test e l'eventuale wrap Resilience4j.
// [^src: design_&_architecture/components/backend-components.md §Decisioni di concorrenza]
// [^src: design_&_architecture/decisions/ADR-004-fmp-integration.md §endpoint base URL]
@Component
class FmpAdapterRestClient(
    private val restClientBuilder: RestClient.Builder,
    private val appProperties: AppProperties,
) : FmpAdapter {

    private val log = LoggerFactory.getLogger(javaClass)

    // Dedicated FMP-scoped RestClient built once with the base URL property.
    // We don't reuse the generic `restClient()` bean because that one has no baseUrl
    // and we want each fetch to be a relative URI against fmp.base-url.
    private val client: RestClient by lazy {
        restClientBuilder
            .baseUrl(appProperties.fmp.baseUrl)
            .defaultHeader("Accept", "application/json")
            .build()
    }

    override fun getIncomeStatement(ticker: String, limit: Int): List<IncomeStatementDto> =
        fetchList(
            endpoint = "income-statement",
            ticker = ticker,
            limit = limit,
            typeRef = object : ParameterizedTypeReference<List<IncomeStatementDto>>() {},
        )

    override fun getBalanceSheet(ticker: String, limit: Int): List<BalanceSheetDto> =
        fetchList(
            endpoint = "balance-sheet-statement",
            ticker = ticker,
            limit = limit,
            typeRef = object : ParameterizedTypeReference<List<BalanceSheetDto>>() {},
        )

    override fun getCashFlow(ticker: String, limit: Int): List<CashFlowDto> =
        fetchList(
            endpoint = "cash-flow-statement",
            ticker = ticker,
            limit = limit,
            typeRef = object : ParameterizedTypeReference<List<CashFlowDto>>() {},
        )

    override fun getKeyMetrics(ticker: String, limit: Int): List<KeyMetricsDto> =
        fetchList(
            endpoint = "key-metrics",
            ticker = ticker,
            limit = limit,
            typeRef = object : ParameterizedTypeReference<List<KeyMetricsDto>>() {},
        )

    // `/profile?symbol={ticker}` returns a single-element list.  We reuse
    // fetchList and take .first() — semantics for empty (404 / not found) are
    // already enforced there.  `limit` is irrelevant for /profile but we pass 1
    // for safety; FMP stable accepts it as a no-op query param.
    override fun getProfile(ticker: String): ProfileDto =
        fetchList(
            endpoint = "profile",
            ticker = ticker,
            limit = 1,
            typeRef = object : ParameterizedTypeReference<List<ProfileDto>>() {},
        ).first()

    // `/company-screener` ha shape diversa dagli altri endpoint (niente {ticker} nel
    // path, query params arbitrari, lista vuota legittima) → fetch dedicato.
    override fun screen(
        marketCapMoreThan: Long?,
        marketCapLowerThan: Long?,
        sector: String?,
        exchange: String?,
        country: String?,
        limit: Int,
    ): List<ScreenedStockDto> {
        require(limit > 0) { "limit must be > 0" }
        val typeRef = object : ParameterizedTypeReference<List<ScreenedStockDto>>() {}

        val result: List<ScreenedStockDto>? = try {
            client.get()
                .uri { builder ->
                    val b = builder
                        .path("/company-screener")
                        .queryParam("apikey", appProperties.fmp.apiKey)
                        .queryParam("limit", limit)
                    if (marketCapMoreThan != null) b.queryParam("marketCapMoreThan", marketCapMoreThan)
                    if (marketCapLowerThan != null) b.queryParam("marketCapLowerThan", marketCapLowerThan)
                    if (!sector.isNullOrBlank()) b.queryParam("sector", sector)
                    if (!exchange.isNullOrBlank()) b.queryParam("exchange", exchange)
                    if (!country.isNullOrBlank()) b.queryParam("country", country)
                    b.build()
                }
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError) { _, response ->
                    val status = response.statusCode
                    if (status.value() == 429) {
                        log.warn("FMP 429 (rate limited) on /company-screener")
                        throw FmpUnavailableException(
                            "FMP rate limited for company-screener",
                            httpStatus = 429,
                        )
                    }
                    log.warn("FMP 4xx on /company-screener status={}", status)
                    // Per lo screener, un 4xx non è "ticker not found": è una
                    // condizione anomala (parametri rifiutati). La trattiamo come
                    // unavailable così Resilience4j / GlobalExceptionHandler
                    // mappano a 503 invece che a 404 fuorviante.
                    throw FmpUnavailableException(
                        "FMP returned $status for company-screener",
                        httpStatus = status.value(),
                    )
                }
                .onStatus(HttpStatusCode::is5xxServerError) { _, response ->
                    val status = response.statusCode
                    log.warn("FMP 5xx on /company-screener status={}", status)
                    throw FmpUnavailableException(
                        "FMP returned $status for company-screener",
                        httpStatus = status.value(),
                    )
                }
                .body(typeRef)
        } catch (ex: FmpUnavailableException) {
            throw ex
        } catch (ex: RestClientResponseException) {
            throw FmpUnavailableException(
                "FMP call failed: ${ex.statusCode} for company-screener",
                cause = ex,
                httpStatus = ex.statusCode.value(),
            )
        }

        // Lista vuota = zero match (legittimo, NOT a not-found).
        return result ?: emptyList()
    }

    // `/search` ha shape simile a `/company-screener`: nessun {ticker} nel path,
    // query param `query` arbitrario, lista vuota legittima (zero match) e NON
    // mappata a FmpTickerNotFoundException — vedi javadoc su FmpAdapter.searchSymbol.
    //
    // Error policy:
    //   - 4xx (non 429) → lista vuota: trattiamo come "nessun match" per
    //     allinearci alla semantica `/search` di FMP (parser query rifiutato →
    //     equivale a zero hit lato utente). Differente da fetchList() che mappa
    //     4xx a FmpTickerNotFoundException, ma `/search` non è per-ticker.
    //   - 429 → FmpUnavailableException(429) per coerenza con il resto del modulo
    //     (rate limit gate Resilience4j).
    //   - 5xx → FmpUnavailableException(status).
    override fun searchSymbol(query: String, limit: Int): List<SearchHitDto> {
        require(query.isNotBlank()) { "query must not be blank" }
        require(limit > 0) { "limit must be > 0" }
        val typeRef = object : ParameterizedTypeReference<List<SearchHitDto>>() {}

        val result: List<SearchHitDto>? = try {
            client.get()
                .uri { builder ->
                    builder
                        .path("/search-symbol")
                        .queryParam("apikey", appProperties.fmp.apiKey)
                        .queryParam("query", query)
                        .queryParam("limit", limit)
                        .build()
                }
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError) { _, response ->
                    val status = response.statusCode
                    if (status.value() == 429) {
                        log.warn("FMP 429 (rate limited) on /search query={}", query)
                        throw FmpUnavailableException(
                            "FMP rate limited for /search",
                            httpStatus = 429,
                        )
                    }
                    log.warn("FMP 4xx on /search status={} query={}", status, query)
                    // Per /search un 4xx non è "ticker not found" né anomalia
                    // bloccante — trattiamo come zero hit (vedi nota in alto).
                    // Usiamo un marker locale per attraversare il lambda onStatus
                    // (che richiede throw); il catch poco sotto ritorna emptyList.
                    throw EmptySearchSentinelException()
                }
                .onStatus(HttpStatusCode::is5xxServerError) { _, response ->
                    val status = response.statusCode
                    log.warn("FMP 5xx on /search status={}", status)
                    throw FmpUnavailableException(
                        "FMP returned $status for /search",
                        httpStatus = status.value(),
                    )
                }
                .body(typeRef)
        } catch (ex: EmptySearchSentinelException) {
            return emptyList()
        } catch (ex: FmpUnavailableException) {
            throw ex
        } catch (ex: RestClientResponseException) {
            throw FmpUnavailableException(
                "FMP call failed: ${ex.statusCode} for /search",
                cause = ex,
                httpStatus = ex.statusCode.value(),
            )
        }

        // Lista vuota = zero match (legittimo, NOT a not-found).
        return result ?: emptyList()
    }

    // Sentinel interno per attraversare il lambda onStatus senza wrapping in
    // FmpUnavailableException. Marker-only, mai propagata fuori dall'adapter
    // (catturata nel try/catch del chiamante searchSymbol).
    private class EmptySearchSentinelException : RuntimeException()

    // `/stable/dividends?symbol={ticker}` — serie storica dividendi.
    //
    // Differenza con fetchList():
    //   - lista vuota è risultato legittimo (ticker senza dividendi, es. growth
    //     stock pre-2024) → ritorna emptyList(), NON FmpTickerNotFoundException.
    //   - 4xx (non 429) → emptyList() per coerenza semantica "no dividends",
    //     non not-found ticker (il ticker può esistere ma non aver mai pagato).
    //   - nessun parametro `limit` (l'endpoint non lo documenta).
    //
    // Error policy:
    //   - 429 → FmpUnavailableException(429) (rate-limited, route resilienza).
    //   - 5xx → FmpUnavailableException(status).
    //   - 4xx (non 429) → emptyList() (tratta come zero dividendi).
    //
    // Ordinamento: DESC by `date` (string ISO `yyyy-MM-dd` → lex ordering ==
    // cronologico). Record con `date == null` finiscono in coda (compareBy nullsLast).
    override fun getDividendHistory(ticker: String): List<DividendRecord> {
        require(ticker.isNotBlank()) { "ticker must not be blank" }
        val upperTicker = ticker.uppercase()
        val typeRef = object : ParameterizedTypeReference<List<DividendRecord>>() {}

        val result: List<DividendRecord>? = try {
            client.get()
                .uri { builder ->
                    builder
                        .path("/dividends")
                        .queryParam("apikey", appProperties.fmp.apiKey)
                        .queryParam("symbol", upperTicker)
                        .build()
                }
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError) { _, response ->
                    val status = response.statusCode
                    if (status.value() == 429) {
                        log.warn(
                            "FMP 429 (rate limited) on /dividends ticker={}",
                            upperTicker,
                        )
                        throw FmpUnavailableException(
                            "FMP rate limited for dividends/$upperTicker",
                            httpStatus = 429,
                        )
                    }
                    // Per /dividends un 4xx non è "ticker not found" né anomalia
                    // bloccante — trattiamo come "nessun dividendo" (zero record).
                    log.warn(
                        "FMP 4xx on /dividends ticker={} status={} — treating as empty",
                        upperTicker, status,
                    )
                    throw EmptyDividendsSentinelException()
                }
                .onStatus(HttpStatusCode::is5xxServerError) { _, response ->
                    val status = response.statusCode
                    log.warn(
                        "FMP 5xx on /dividends ticker={} status={}",
                        upperTicker, status,
                    )
                    throw FmpUnavailableException(
                        "FMP returned $status for dividends/$upperTicker",
                        httpStatus = status.value(),
                    )
                }
                .body(typeRef)
        } catch (ex: EmptyDividendsSentinelException) {
            return emptyList()
        } catch (ex: FmpUnavailableException) {
            throw ex
        } catch (ex: RestClientResponseException) {
            throw FmpUnavailableException(
                "FMP call failed: ${ex.statusCode} for dividends/$upperTicker",
                cause = ex,
                httpStatus = ex.statusCode.value(),
            )
        }

        if (result.isNullOrEmpty()) {
            return emptyList()
        }
        // DESC by ex-dividend date (ISO `yyyy-MM-dd` lex ordering == cronologico).
        // Record con date null finiscono in coda.
        val dateDescNullsLast: Comparator<DividendRecord> = Comparator { a, b ->
            val da = a.date
            val db = b.date
            when {
                da == null && db == null -> 0
                da == null -> 1   // null after non-null
                db == null -> -1
                else -> db.compareTo(da)   // descending
            }
        }
        return result.sortedWith(dateDescNullsLast)
    }

    override fun getStockNews(ticker: String, days: Int): List<StockNewsItem> {
        require(ticker.isNotBlank()) { "ticker must not be blank" }
        val upperTicker = ticker.uppercase()
        val from = LocalDate.now().minusDays(days.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val typeRef = object : ParameterizedTypeReference<List<StockNewsItem>>() {}

        val result: List<StockNewsItem>? = try {
            client.get()
                .uri { builder ->
                    builder
                        .path("/news/stock")
                        .queryParam("apikey", appProperties.fmp.apiKey)
                        .queryParam("tickers", upperTicker)
                        .queryParam("from", from)
                        .build()
                }
                .retrieve()
                .body(typeRef)
        } catch (ex: RestClientResponseException) {
            log.warn("FMP news error for ticker={} status={}", upperTicker, ex.statusCode)
            return emptyList()
        }

        return result?.filter {
            it.publishedDate != null && it.publishedDate.toLocalDate() >= LocalDate.now().minusDays(days.toLong())
        } ?: emptyList()
    }

    override fun getHistoricalEodPrices(ticker: String, days: Int): List<EodPriceRecord> {
        require(ticker.isNotBlank()) { "ticker must not be blank" }
        val upperTicker = ticker.uppercase()
        val from = LocalDate.now().minusDays(days.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val typeRef = object : ParameterizedTypeReference<List<EodPriceRecord>>() {}

        val result: List<EodPriceRecord>? = try {
            client.get()
                .uri { builder ->
                    builder
                        .path("/historical-price-eod/full")
                        .queryParam("apikey", appProperties.fmp.apiKey)
                        .queryParam("symbol", upperTicker)
                        .queryParam("from", from)
                        .build()
                }
                .retrieve()
                .body(typeRef)
        } catch (ex: RestClientResponseException) {
            log.warn("FMP EOD error for ticker={} status={}", upperTicker, ex.statusCode)
            throw FmpUnavailableException(
                "FMP historical EOD failed: ${ex.statusCode} for $upperTicker",
                cause = ex,
                httpStatus = ex.statusCode.value(),
            )
        }

        return result ?: emptyList()
    }

    // Sentinel locale per /dividends 4xx (non 429) → emptyList senza errore.
    // Mai propagata fuori dall'adapter.
    private class EmptyDividendsSentinelException : RuntimeException()

    // `/stable/sec-filings-search/symbol?symbol={ticker}&limit={limit}` — discovery
    // SEC filing per ticker via FMP search aggregato (TSK-094, US-039, EP-011).
    //
    // Endpoint verificato in raw/fmp_docs.md:10815. Pattern di error-handling
    // identico a getDividendHistory:
    //   - 429 → FmpUnavailableException(429).
    //   - 5xx → FmpUnavailableException(status).
    //   - 4xx (non 429) → emptyList() (ticker valido ma nessun filing visibile).
    //
    // Filtro `formTypes` applicato lato client dopo fetch — FMP /symbol endpoint
    // non documenta filtro server-side per form type. Default ["10-K", "10-Q"].
    //
    // Limit passato a FMP (max 100 per page) + take(limit) client-side per
    // double-safety. NB: l'endpoint supporta paginazione via `page=N` ma per il
    // caso d'uso EP-011 (ultimi 10 filing) basta page=0.
    //
    // Ordinamento conservato dall'API FMP (DESC by filingDate tipicamente);
    // se la garanzia futura cambiasse, il consumer può riordinare.
    //
    // [^src: raw/fmp_docs.md §Sec Filings — SEC Filings By Symbol API]
    // [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-039-download-cache-filings/TSK-094.md]
    override fun getSecFilings(
        ticker: String,
        formTypes: List<String>,
        limit: Int,
    ): List<SecFilingFmpDto> {
        require(ticker.isNotBlank()) { "ticker must not be blank" }
        require(limit > 0) { "limit must be > 0" }
        val upperTicker = ticker.uppercase()
        val typeRef = object : ParameterizedTypeReference<List<SecFilingFmpDto>>() {}

        val result: List<SecFilingFmpDto>? = try {
            client.get()
                .uri { builder ->
                    builder
                        .path("/sec-filings-search/symbol")
                        .queryParam("apikey", appProperties.fmp.apiKey)
                        .queryParam("symbol", upperTicker)
                        .queryParam("limit", limit)
                        .build()
                }
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError) { _, response ->
                    val status = response.statusCode
                    if (status.value() == 429) {
                        log.warn(
                            "FMP 429 (rate limited) on /sec-filings-search/symbol ticker={}",
                            upperTicker,
                        )
                        throw FmpUnavailableException(
                            "FMP rate limited for sec-filings/$upperTicker",
                            httpStatus = 429,
                        )
                    }
                    // 4xx non-429 = ticker valido ma zero filing → emptyList sentinel.
                    log.warn(
                        "FMP 4xx on /sec-filings-search/symbol ticker={} status={} — treating as empty",
                        upperTicker, status,
                    )
                    throw EmptySecFilingsSentinelException()
                }
                .onStatus(HttpStatusCode::is5xxServerError) { _, response ->
                    val status = response.statusCode
                    log.warn(
                        "FMP 5xx on /sec-filings-search/symbol ticker={} status={}",
                        upperTicker, status,
                    )
                    throw FmpUnavailableException(
                        "FMP returned $status for sec-filings/$upperTicker",
                        httpStatus = status.value(),
                    )
                }
                .body(typeRef)
        } catch (ex: EmptySecFilingsSentinelException) {
            return emptyList()
        } catch (ex: FmpUnavailableException) {
            throw ex
        } catch (ex: RestClientResponseException) {
            throw FmpUnavailableException(
                "FMP call failed: ${ex.statusCode} for sec-filings/$upperTicker",
                cause = ex,
                httpStatus = ex.statusCode.value(),
            )
        }

        if (result.isNullOrEmpty()) {
            return emptyList()
        }
        val formTypesUpper = formTypes.map { it.uppercase() }.toSet()
        return result
            .filter { it.formType?.uppercase() in formTypesUpper }
            .take(limit)
    }

    // Sentinel locale per /sec-filings-search/symbol 4xx (non 429) → emptyList.
    // Mai propagata fuori dall'adapter.
    private class EmptySecFilingsSentinelException : RuntimeException()

    // Generic GET on /{endpoint}?symbol={ticker}&apikey=...&limit=...
    // Stable API (TSK-050): il ticker passa da path-variable a query parameter
    // `symbol`. Empty list response -> FmpTickerNotFoundException (semantica FMP).
    // Errori HTTP non-2xx → propagati come FmpUnavailableException (la mappatura a 503
    // avverrà nel GlobalExceptionHandler una volta esteso da TSK-011).
    // [^src: wiki/concepts/fmp-financial-statements-stable.md]
    // [^src: wiki/concepts/fmp-company-information.md §profile]
    private fun <T> fetchList(
        endpoint: String,
        ticker: String,
        limit: Int,
        typeRef: ParameterizedTypeReference<List<T>>,
    ): List<T> {
        require(ticker.isNotBlank()) { "ticker must not be blank" }
        require(limit > 0) { "limit must be > 0" }
        val upperTicker = ticker.uppercase()

        val result: List<T>? = try {
            client.get()
                .uri { builder ->
                    builder
                        .path("/{endpoint}")
                        .queryParam("apikey", appProperties.fmp.apiKey)
                        .queryParam("symbol", upperTicker)
                        .queryParam("limit", limit)
                        .build(endpoint)
                }
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError) { _, response ->
                    val status = response.statusCode
                    // 429 is rate-limiting, a transient failure -> route through the
                    // resilience chain as Unavailable (so Retry/CB engage), NOT as
                    // not-found.  TSK-011 §FmpEventLogger types: FMP_429_RATE_LIMITED.
                    if (status.value() == 429) {
                        log.warn(
                            "FMP 429 (rate limited) for endpoint={} ticker={}",
                            endpoint, upperTicker,
                        )
                        throw FmpUnavailableException(
                            "FMP rate limited for $endpoint/$upperTicker",
                            httpStatus = 429,
                        )
                    }
                    // Other 4xx: FMP returns 404 only in rare cases; usually returns
                    // 200 + [] for unknown tickers. We surface 4xx as not-found to
                    // keep semantics consistent for downstream callers.
                    log.warn(
                        "FMP 4xx for endpoint={} ticker={} status={}",
                        endpoint, upperTicker, status,
                    )
                    throw FmpTickerNotFoundException(upperTicker)
                }
                .onStatus(HttpStatusCode::is5xxServerError) { _, response ->
                    val status = response.statusCode
                    log.warn(
                        "FMP 5xx for endpoint={} ticker={} status={}",
                        endpoint, upperTicker, status,
                    )
                    throw FmpUnavailableException(
                        "FMP returned $status for $endpoint/$upperTicker",
                        httpStatus = status.value(),
                    )
                }
                .body(typeRef)
        } catch (ex: FmpTickerNotFoundException) {
            throw ex
        } catch (ex: FmpUnavailableException) {
            throw ex
        } catch (ex: RestClientResponseException) {
            // Defensive: in case onStatus didn't fire (unusual codes).
            throw FmpUnavailableException(
                "FMP call failed: ${ex.statusCode} for $endpoint/$upperTicker",
                cause = ex,
                httpStatus = ex.statusCode.value(),
            )
        }

        if (result.isNullOrEmpty()) {
            // FMP convention: empty array for unknown ticker.
            throw FmpTickerNotFoundException(upperTicker)
        }
        return result
    }
}
