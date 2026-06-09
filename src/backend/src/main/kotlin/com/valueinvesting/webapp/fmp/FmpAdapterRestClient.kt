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
import com.valueinvesting.webapp.fmp.dto.TechnicalIndicatorRecord
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
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
    //
    // Connect + read timeout: NON impostiamo qui un requestFactory custom (romperebbe
    // MockRestServiceServer.bindTo nei test, che installa il proprio factory mock).
    // Il timeout arriva dal bean RestClient.Builder auto-configurato via
    // `spring.http.client.connect-timeout/read-timeout` (application.yml): senza un
    // read timeout una connessione FMP stallata bloccherebbe il thread all'infinito
    // (run async RUNNING per sempre). Con il timeout lo stallo diventa una
    // RestClientException → l'executor marca il run FAILED (errore visibile).
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
        } catch (ex: RestClientException) {
            // Connection timeout o errore di decode body (es. shape inattesa) non
            // sono sottotipi di RestClientResponseException: degradiamo come
            // hard-failure controllata invece di propagare un'eccezione non gestita.
            // Modello: getStockNews piu' sotto.
            log.warn("FMP dividends decode/transport error for ticker={}: {}", upperTicker, ex.message)
            throw FmpUnavailableException(
                "FMP dividends transport/decode error for $upperTicker: ${ex.message}",
                cause = ex,
                httpStatus = null,
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
        val today = LocalDate.now()
        val from = today.minusDays(days.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val to = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val typeRef = object : ParameterizedTypeReference<List<StockNewsItem>>() {}

        val result: List<StockNewsItem>? = try {
            client.get()
                .uri { builder ->
                    builder
                        .path("/news/stock")
                        .queryParam("apikey", appProperties.fmp.apiKey)
                        // FMP stable usa 'symbols' (non 'tickers'): con 'tickers'
                        // il filtro per ticker viene ignorato. Allineato ad agent.py v2.4
                        // e all'esempio docs /stable/news/stock?symbols=AAPL.
                        .queryParam("symbols", upperTicker)
                        // Finestra esplicita from..to + cap a NEWS_FETCH_LIMIT: bound
                        // del costo a monte (il funnel di materialita' lato service
                        // seleziona poi le notizie rilevanti). page=0 = prima pagina.
                        .queryParam("from", from)
                        .queryParam("to", to)
                        .queryParam("page", 0)
                        .queryParam("limit", NEWS_FETCH_LIMIT)
                        .build()
                }
                .retrieve()
                .body(typeRef)
        } catch (ex: RestClientResponseException) {
            log.warn("FMP news error for ticker={} status={}", upperTicker, ex.statusCode)
            return emptyList()
        } catch (ex: RestClientException) {
            // News non e' un segnale critico: un errore di decoding del body
            // (es. formato data inatteso) o di trasporto deve degradare a lista
            // vuota, non far fallire l'intera deep analysis.
            log.warn("FMP news decode/transport error for ticker={}: {}", upperTicker, ex.message)
            return emptyList()
        }

        return result?.filter {
            it.publishedDate != null && it.publishedDate.toLocalDate() >= today.minusDays(days.toLong())
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

    // `/stable/sec-filings-search/symbol?symbol={ticker}&formType={ft}&from={from}&to={to}&page=0&limit=...`
    // — discovery SEC filing per ticker via FMP search (TSK-094, US-039, EP-011).
    //
    // Endpoint verificato in raw/fmp_docs.md:10815. Comportamento reale FMP
    // (verificato sul campo, ticker TTD, mag 2026):
    //   - `from`/`to` sono OBBLIGATORI: senza finestra temporale → 400 BAD_REQUEST.
    //   - L'endpoint NON filtra per `formType` lato server (passarlo è innocuo);
    //     ritorna TUTTI i form type (Form-4, 8-K, SC 13G, 10-K, 10-Q, ...) ordinati
    //     DESC per filingDate. Il 10-K/10-Q va quindi filtrato lato client.
    //   - L'endpoint gemello `/sec-filings-search/form-type` IGNORA il `symbol`
    //     (ritorna i filing di TUTTE le aziende) → inutilizzabile per ticker singolo.
    //
    // Strategia (allineata all'aspettativa "una chiamata per 10-K e una per 10-Q"):
    // per ogni `formType` richiesto emettiamo una chiamata distinta all'endpoint
    // /symbol con quel `formType` in querystring (così la richiesta "esce" con
    // formType=10-K / formType=10-Q e resta forward-compatible se FMP attiverà il
    // filtro server-side), poi filtriamo client-side per quel form type. Usiamo un
    // page-limit ampio (SEC_FILINGS_PAGE_LIMIT) perché i 10-K/10-Q sono pochi ma
    // annegati tra decine di Form-4/8-K più recenti: con un limit basso (es. 10)
    // verrebbero esclusi dalla pagina → root cause del bug "No SEC filings".
    //
    // Finestra: `to = oggi`, `from = oggi - lookbackMonths` (default 15 → ultimo
    // 10-K annuale + ultimi 10-Q trimestrali, con margine per ritardi di deposito).
    //
    // Risultato: union dei form type richiesti, deduplicata per link, ordinata DESC
    // per filingDate, troncata a `limit` (cap totale, non per-tipo).
    //
    // Error policy per ogni chiamata:
    //   - 429 → FmpUnavailableException(429) (route Resilience4j).
    //   - 5xx → FmpUnavailableException(status).
    //   - 4xx (non 429) → trattato come "nessun filing per quel tipo" (emptyList).
    //
    // [^src: raw/fmp_docs.md §Sec Filings — SEC Filings By Symbol API]
    // [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-039-download-cache-filings/TSK-094.md]
    override fun getSecFilings(
        ticker: String,
        formTypes: List<String>,
        limit: Int,
        lookbackMonths: Long,
    ): List<SecFilingFmpDto> {
        require(ticker.isNotBlank()) { "ticker must not be blank" }
        require(limit > 0) { "limit must be > 0" }
        require(lookbackMonths > 0) { "lookbackMonths must be > 0" }
        val upperTicker = ticker.uppercase()
        val to = LocalDate.now()
        val from = to.minusMonths(lookbackMonths)

        // Dedup per link canonico, preservando l'inserimento; ordiniamo dopo.
        val byLink = LinkedHashMap<String, SecFilingFmpDto>()
        for (formType in formTypes) {
            val wanted = formType.uppercase()
            for (f in fetchSecFilingsByFormType(upperTicker, formType, from, to)) {
                if (f.formType?.uppercase() != wanted) continue
                val key = f.finalLink ?: f.link ?: continue
                byLink.putIfAbsent(key, f)
            }
        }

        return byLink.values
            .sortedByDescending { it.filingDate ?: "" }
            .take(limit)
    }

    // Singola chiamata GET /sec-filings-search/symbol con un dato formType e
    // finestra [from, to]. Vedi getSecFilings per il razionale del page-limit
    // ampio e del filtro client-side.
    private fun fetchSecFilingsByFormType(
        upperTicker: String,
        formType: String,
        from: LocalDate,
        to: LocalDate,
    ): List<SecFilingFmpDto> {
        val typeRef = object : ParameterizedTypeReference<List<SecFilingFmpDto>>() {}

        val result: List<SecFilingFmpDto>? = try {
            client.get()
                .uri { builder ->
                    builder
                        .path("/sec-filings-search/symbol")
                        .queryParam("apikey", appProperties.fmp.apiKey)
                        .queryParam("symbol", upperTicker)
                        .queryParam("formType", formType)
                        .queryParam("from", from.format(DateTimeFormatter.ISO_LOCAL_DATE))
                        .queryParam("to", to.format(DateTimeFormatter.ISO_LOCAL_DATE))
                        .queryParam("page", 0)
                        .queryParam("limit", SEC_FILINGS_PAGE_LIMIT)
                        .build()
                }
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError) { _, response ->
                    val status = response.statusCode
                    if (status.value() == 429) {
                        log.warn(
                            "FMP 429 (rate limited) on /sec-filings-search/symbol ticker={} formType={}",
                            upperTicker, formType,
                        )
                        throw FmpUnavailableException(
                            "FMP rate limited for sec-filings/$upperTicker",
                            httpStatus = 429,
                        )
                    }
                    // 4xx non-429 = ticker valido ma zero filing per quel tipo → emptyList sentinel.
                    log.warn(
                        "FMP 4xx on /sec-filings-search/symbol ticker={} formType={} status={} — treating as empty",
                        upperTicker, formType, status,
                    )
                    throw EmptySecFilingsSentinelException()
                }
                .onStatus(HttpStatusCode::is5xxServerError) { _, response ->
                    val status = response.statusCode
                    log.warn(
                        "FMP 5xx on /sec-filings-search/symbol ticker={} formType={} status={}",
                        upperTicker, formType, status,
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

        return result ?: emptyList()
    }

    // Sentinel locale per /sec-filings-search/symbol 4xx (non 429) → emptyList.
    // Mai propagata fuori dall'adapter.
    private class EmptySecFilingsSentinelException : RuntimeException()

    // `/stable/search-cusip?cusip={cusip}` — risoluzione CUSIP→ticker (TSK-127).
    //
    // Endpoint verificato in raw/fmp_docs.md §CUSIPAPI riga 281. Response shape:
    //   [ { "symbol": "AAPL.NE", "companyName": "Apple Inc.",
    //       "cusip": "037833100", "marketCap": ... } ]
    //
    // Error policy:
    //   - 429 → FmpUnavailableException(429) (rate-limited, route resilienza).
    //   - 5xx → FmpUnavailableException(status).
    //   - 4xx (non 429) → null (CUSIP non riconosciuto, semantica pragmatica).
    //   - Lista vuota → null (zero match legittimo).
    //
    // Il caller (InstitutionalHoldingsService) tratta null come "skip holding".
    override fun searchCusip(cusip: String): String? {
        require(cusip.isNotBlank()) { "cusip must not be blank" }
        val normalized = cusip.trim().uppercase()
        // Deserializzazione pragmatica: usiamo SearchHitDto (compatibile col
        // campo `symbol`) — gli altri campi (companyName/cusip/marketCap) sono
        // ignorati via @JsonIgnoreProperties.
        val typeRef = object : ParameterizedTypeReference<List<SearchHitDto>>() {}

        val result: List<SearchHitDto>? = try {
            client.get()
                .uri { builder ->
                    builder
                        .path("/search-cusip")
                        .queryParam("apikey", appProperties.fmp.apiKey)
                        .queryParam("cusip", normalized)
                        .build()
                }
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError) { _, response ->
                    val status = response.statusCode
                    if (status.value() == 429) {
                        log.warn("FMP 429 (rate limited) on /search-cusip cusip={}", normalized)
                        throw FmpUnavailableException(
                            "FMP rate limited for search-cusip",
                            httpStatus = 429,
                        )
                    }
                    // 4xx non-429 = CUSIP sconosciuto a FMP → null sentinel.
                    log.debug(
                        "FMP 4xx on /search-cusip cusip={} status={} — treating as null",
                        normalized, status,
                    )
                    throw EmptyCusipSentinelException()
                }
                .onStatus(HttpStatusCode::is5xxServerError) { _, response ->
                    val status = response.statusCode
                    log.warn("FMP 5xx on /search-cusip cusip={} status={}", normalized, status)
                    throw FmpUnavailableException(
                        "FMP returned $status for search-cusip",
                        httpStatus = status.value(),
                    )
                }
                .body(typeRef)
        } catch (ex: EmptyCusipSentinelException) {
            return null
        } catch (ex: FmpUnavailableException) {
            throw ex
        } catch (ex: RestClientResponseException) {
            throw FmpUnavailableException(
                "FMP call failed: ${ex.statusCode} for search-cusip",
                cause = ex,
                httpStatus = ex.statusCode.value(),
            )
        }

        if (result.isNullOrEmpty()) {
            return null
        }
        // Prendi il primo elemento; il `symbol` puo' contenere suffisso exchange
        // (es. "AAPL.NE" per Toronto NEO). Per le holding 13-F US-only ci aspettiamo
        // simboli "puri" o suffissi US-listing; il caller decide se filtrare.
        return result.first().symbol?.takeIf { it.isNotBlank() }
    }

    // Sentinel locale per /search-cusip 4xx (non 429) → null senza errore.
    // Mai propagata fuori dall'adapter.
    private class EmptyCusipSentinelException : RuntimeException()

    // `/stable/technical-indicators/{indicator}?symbol={ticker}&periodLength={n}&timeframe={tf}`
    // — endpoint generico per indicatori tecnici (EP-013, TSK-164).
    //
    // Whitelist enforced: solo `rsi` (US-056) e `sma` (US-057) ammessi in scope
    // EP-013. Estendere ALLOWED_INDICATORS per nuovi indicator (no breaking).
    //
    // Pattern di error-handling identico a getDividendHistory / getSecFilings:
    //   - 429 → FmpUnavailableException(429).
    //   - 5xx → FmpUnavailableException(status).
    //   - 4xx (non 429) → emptyList() (ticker IPO recente, semantica "no data").
    //
    // Ordinamento conservato dall'API FMP (DESC by date tipicamente); il consumer
    // (RsiContextEvaluator, LongTermTrendEvaluator) usa maxByOrNull { date } per
    // estrarre il record più recente, robusto a ordinamenti incerti.
    //
    // [^src: raw/fmp_docs.md §Technical Indicators]
    // [^src: management/kanban/EP-013-mr-market-context-flags/US-056-rsi-mr-market-context-flag/TSK-164.md]
    override fun getTechnicalIndicator(
        ticker: String,
        indicator: String,
        periodLength: Int,
        timeframe: String,
    ): List<TechnicalIndicatorRecord> {
        require(ticker.isNotBlank()) { "ticker must not be blank" }
        require(periodLength > 0) { "periodLength must be > 0" }
        require(timeframe.isNotBlank()) { "timeframe must not be blank" }
        require(indicator in ALLOWED_INDICATORS) {
            "indicator must be one of $ALLOWED_INDICATORS, was: $indicator"
        }
        val upperTicker = ticker.uppercase()
        val typeRef = object : ParameterizedTypeReference<List<TechnicalIndicatorRecord>>() {}

        val result: List<TechnicalIndicatorRecord>? = try {
            client.get()
                .uri { builder ->
                    builder
                        .path("/technical-indicators/{indicator}")
                        .queryParam("apikey", appProperties.fmp.apiKey)
                        .queryParam("symbol", upperTicker)
                        .queryParam("periodLength", periodLength)
                        .queryParam("timeframe", timeframe)
                        .build(indicator)
                }
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError) { _, response ->
                    val status = response.statusCode
                    if (status.value() == 429) {
                        log.warn(
                            "FMP 429 (rate limited) on /technical-indicators/{} ticker={}",
                            indicator, upperTicker,
                        )
                        throw FmpUnavailableException(
                            "FMP rate limited for technical-indicators/$indicator/$upperTicker",
                            httpStatus = 429,
                        )
                    }
                    // 4xx non-429 = ticker IPO recente (< periodLength giorni)
                    // o indicator non calcolabile → emptyList sentinel.
                    log.warn(
                        "FMP 4xx on /technical-indicators/{} ticker={} status={} — treating as empty",
                        indicator, upperTicker, status,
                    )
                    throw EmptyTechnicalIndicatorSentinelException()
                }
                .onStatus(HttpStatusCode::is5xxServerError) { _, response ->
                    val status = response.statusCode
                    log.warn(
                        "FMP 5xx on /technical-indicators/{} ticker={} status={}",
                        indicator, upperTicker, status,
                    )
                    throw FmpUnavailableException(
                        "FMP returned $status for technical-indicators/$indicator/$upperTicker",
                        httpStatus = status.value(),
                    )
                }
                .body(typeRef)
        } catch (ex: EmptyTechnicalIndicatorSentinelException) {
            return emptyList()
        } catch (ex: FmpUnavailableException) {
            throw ex
        } catch (ex: RestClientResponseException) {
            throw FmpUnavailableException(
                "FMP call failed: ${ex.statusCode} for technical-indicators/$indicator/$upperTicker",
                cause = ex,
                httpStatus = ex.statusCode.value(),
            )
        }

        return result ?: emptyList()
    }

    // Sentinel locale per /technical-indicators 4xx (non 429) → emptyList.
    // Mai propagata fuori dall'adapter.
    private class EmptyTechnicalIndicatorSentinelException : RuntimeException()

    private companion object {
        // Whitelist degli indicator FMP `/stable/technical-indicators/{indicator}`.
        // - rsi/sma: EP-013 (US-056/US-057) — Mr. Market Context Flags.
        // - macd/atr/obv: EP-024 (US-098, TSK-324) — pipeline Technical Analysis
        //   (Triple Screen Elder + struttura Murphy). Estendere ulteriormente solo
        //   con una US con rationale di valore (Elder §39 anti voting-rigging).
        // [^src: management/kanban/EP-024-riepilogo-e-technical-analysis-tab/US-098-pipeline-ta-payload-be/TSK-324.md]
        val ALLOWED_INDICATORS = setOf("rsi", "sma", "macd", "atr", "obv")

        // Page size per /sec-filings-search/symbol: ampio perché l'endpoint NON
        // filtra per formType lato server e restituisce TUTTI i filing del ticker
        // ordinati DESC per data. I 10-K/10-Q (pochi) vanno recuperati dentro una
        // pagina dominata da decine di Form-4/8-K. 1000 copre abbondantemente la
        // finestra di 15 mesi anche per filer molto attivi (TTD: ~102 righe).
        const val SEC_FILINGS_PAGE_LIMIT = 1000

        // Cap notizie scaricate per /news/stock: il funnel di materialita' lato
        // NewsSentimentService riduce poi al set rilevante. 50 copre con margine
        // 90 giorni di copertura per un ticker tipico senza scaricare rumore inutile.
        const val NEWS_FETCH_LIMIT = 50
    }

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
