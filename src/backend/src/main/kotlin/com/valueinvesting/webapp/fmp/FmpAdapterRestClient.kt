package com.valueinvesting.webapp.fmp

import com.valueinvesting.webapp.config.AppProperties
import com.valueinvesting.webapp.fmp.dto.BalanceSheetDto
import com.valueinvesting.webapp.fmp.dto.CashFlowDto
import com.valueinvesting.webapp.fmp.dto.IncomeStatementDto
import com.valueinvesting.webapp.fmp.dto.KeyMetricsDto
import com.valueinvesting.webapp.fmp.dto.ProfileDto
import com.valueinvesting.webapp.fmp.dto.ScreenedStockDto
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

    // `/profile/{ticker}` returns a single-element list.  We reuse fetchList and
    // take .first() — semantics for empty (404 / not found) are already enforced
    // there.  `limit` is irrelevant for /profile but we pass 1 for safety.
    override fun getProfile(ticker: String): ProfileDto =
        fetchList(
            endpoint = "profile",
            ticker = ticker,
            limit = 1,
            typeRef = object : ParameterizedTypeReference<List<ProfileDto>>() {},
        ).first()

    // `/stock-screener` ha shape diversa dagli altri endpoint (niente {ticker} nel
    // path, query params arbitrari, lista vuota legittima) → fetch dedicato.
    override fun screen(
        marketCapMoreThan: Long?,
        marketCapLowerThan: Long?,
        sector: String?,
        limit: Int,
    ): List<ScreenedStockDto> {
        require(limit > 0) { "limit must be > 0" }
        val typeRef = object : ParameterizedTypeReference<List<ScreenedStockDto>>() {}

        val result: List<ScreenedStockDto>? = try {
            client.get()
                .uri { builder ->
                    val b = builder
                        .path("/stock-screener")
                        .queryParam("apikey", appProperties.fmp.apiKey)
                        .queryParam("limit", limit)
                    if (marketCapMoreThan != null) b.queryParam("marketCapMoreThan", marketCapMoreThan)
                    if (marketCapLowerThan != null) b.queryParam("marketCapLowerThan", marketCapLowerThan)
                    if (!sector.isNullOrBlank()) b.queryParam("sector", sector)
                    b.build()
                }
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError) { _, response ->
                    val status = response.statusCode
                    if (status.value() == 429) {
                        log.warn("FMP 429 (rate limited) on /stock-screener")
                        throw FmpUnavailableException(
                            "FMP rate limited for stock-screener",
                            httpStatus = 429,
                        )
                    }
                    log.warn("FMP 4xx on /stock-screener status={}", status)
                    // Per lo screener, un 4xx non è "ticker not found": è una
                    // condizione anomala (parametri rifiutati). La trattiamo come
                    // unavailable così Resilience4j / GlobalExceptionHandler
                    // mappano a 503 invece che a 404 fuorviante.
                    throw FmpUnavailableException(
                        "FMP returned $status for stock-screener",
                        httpStatus = status.value(),
                    )
                }
                .onStatus(HttpStatusCode::is5xxServerError) { _, response ->
                    val status = response.statusCode
                    log.warn("FMP 5xx on /stock-screener status={}", status)
                    throw FmpUnavailableException(
                        "FMP returned $status for stock-screener",
                        httpStatus = status.value(),
                    )
                }
                .body(typeRef)
        } catch (ex: FmpUnavailableException) {
            throw ex
        } catch (ex: RestClientResponseException) {
            throw FmpUnavailableException(
                "FMP call failed: ${ex.statusCode} for stock-screener",
                cause = ex,
                httpStatus = ex.statusCode.value(),
            )
        }

        // Lista vuota = zero match (legittimo, NOT a not-found).
        return result ?: emptyList()
    }

    // Generic GET on /{endpoint}/{ticker}?apikey=...&limit=...
    // Empty list response -> FmpTickerNotFoundException (semantica FMP).
    // Errori HTTP non-2xx → propagati come FmpUnavailableException (la mappatura a 503
    // avverrà nel GlobalExceptionHandler una volta esteso da TSK-011).
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
                        .path("/{endpoint}/{ticker}")
                        .queryParam("apikey", appProperties.fmp.apiKey)
                        .queryParam("limit", limit)
                        .build(endpoint, upperTicker)
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
