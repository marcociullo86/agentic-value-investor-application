package com.valueinvesting.webapp.fmp

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
import io.github.resilience4j.bulkhead.Bulkhead
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.retry.Retry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientResponseException
import java.util.function.Supplier

// Decorator that wraps every FMP call with the Resilience4j chain configured
// in FmpResilienceConfig.  Marked @Primary so all FmpAdapter injection points
// (FinancialDataService, FmpCacheService.upsertStock indirectly, etc.) pick
// THIS bean — the underlying FmpAdapterRestClient is still a Spring bean and
// is exposed through @Qualifier("fmpAdapterRestClient") for unit tests that
// need to bypass resilience.
//
// Chain order (per raw/tech_stack.md §Backend — Resilience):
//   Request → Bulkhead → CircuitBreaker → Retry → HTTP call
//
// (TimeLimiter applies to async/CompletableFuture pipelines — used by
//  TSK-018 AnalyzeTickerService.  For the sync facade here the request
//  timeout is enforced at the HTTP-connector level via RestClientConfig.)
//
// RateLimiter sits OUTSIDE the per-attempt chain: it gates the entire logical
// call (one logical call = potentially multiple retry attempts) at the
// adapter entrypoint so that a tight retry loop cannot exhaust the FMP quota.
//
// 429 / 5xx side effect: FmpEventLogger is invoked from `decorate()` so that
// the audit trail is the single source of truth, regardless of which call
// site originated the request.
//
// [^src: raw/tech_stack.md §Backend - Resilience]
// [^src: design_&_architecture/decisions/ADR-004-fmp-integration.md §Resilienza]
// [^src: management/kanban/.../TSK-011.md §FmpAdapterRestClient]
@Component
@Primary
class ResilientFmpAdapter(
    @Qualifier("fmpAdapterRestClient") private val delegate: FmpAdapter,
    @Qualifier("fmpCircuitBreaker") private val circuitBreaker: CircuitBreaker,
    @Qualifier("fmpRetry") private val retry: Retry,
    @Qualifier("fmpRateLimiter") private val rateLimiter: RateLimiter,
    @Qualifier("fmpBulkhead") private val bulkhead: Bulkhead,
    private val eventLogger: FmpEventLogger,
) : FmpAdapter {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun getIncomeStatement(ticker: String, limit: Int): List<IncomeStatementDto> =
        execute("income-statement", ticker) { delegate.getIncomeStatement(ticker, limit) }

    override fun getBalanceSheet(ticker: String, limit: Int): List<BalanceSheetDto> =
        execute("balance-sheet-statement", ticker) { delegate.getBalanceSheet(ticker, limit) }

    override fun getCashFlow(ticker: String, limit: Int): List<CashFlowDto> =
        execute("cash-flow-statement", ticker) { delegate.getCashFlow(ticker, limit) }

    override fun getKeyMetrics(ticker: String, limit: Int): List<KeyMetricsDto> =
        execute("key-metrics", ticker) { delegate.getKeyMetrics(ticker, limit) }

    override fun getProfile(ticker: String): ProfileDto =
        execute("profile", ticker) { delegate.getProfile(ticker) }

    override fun screen(
        marketCapMoreThan: Long?,
        marketCapLowerThan: Long?,
        sector: String?,
        exchange: String?,
        country: String?,
        limit: Int,
    ): List<ScreenedStockDto> =
        // ticker "-" è un placeholder per il logger (screener non è per-ticker).
        execute("company-screener", "-") {
            delegate.screen(marketCapMoreThan, marketCapLowerThan, sector, exchange, country, limit)
        }

    override fun searchSymbol(query: String, limit: Int): List<SearchHitDto> =
        // Placeholder "-" per il logger ticker: /search non è per-ticker.
        // La chain Resilience4j (Bulkhead/CB/Retry/RateLimiter) si applica
        // identica agli altri endpoint — la `/search` è soggetta al medesimo
        // rate limit FMP, quindi NON saltiamo la gate.
        execute("search", "-") {
            delegate.searchSymbol(query, limit)
        }

    override fun getDividendHistory(ticker: String): List<DividendRecord> =
        execute("dividends", ticker) { delegate.getDividendHistory(ticker) }

    override fun getStockNews(ticker: String, days: Int): List<StockNewsItem> =
        execute("news/stock", ticker) { delegate.getStockNews(ticker, days) }

    override fun getHistoricalEodPrices(ticker: String, days: Int): List<EodPriceRecord> =
        execute("historical-price-eod", ticker) { delegate.getHistoricalEodPrices(ticker, days) }

    override fun getSecFilings(
        ticker: String,
        formTypes: List<String>,
        limit: Int,
    ): List<SecFilingFmpDto> =
        execute("sec-filings", ticker) { delegate.getSecFilings(ticker, formTypes, limit) }

    override fun searchCusip(cusip: String): String? =
        // Il logger ticker e' "-" (search-cusip non e' per-ticker, e' per-CUSIP).
        // Il CUSIP stesso non viene loggato come ticker per evitare ambiguita'.
        execute("search-cusip", "-") { delegate.searchCusip(cusip) }

    /**
     * Apply chain decorators in order Bulkhead -> CB -> Retry, then gate the
     * top-level invocation with the RateLimiter.  The lambda is also wrapped
     * in an interceptor that classifies the outcome and dispatches to
     * FmpEventLogger.
     */
    private fun <T> execute(endpoint: String, ticker: String, block: () -> T): T {
        val instrumented: Supplier<T> = Supplier {
            try {
                block()
            } catch (ex: FmpTickerNotFoundException) {
                eventLogger.logTickerNotFound(ex.ticker, endpoint)
                throw ex
            } catch (ex: RestClientResponseException) {
                onHttpFailure(endpoint, ticker, ex.statusCode.value(), ex.message)
                // Re-wrap to a domain exception so CB/Retry recordExceptions match.
                throw FmpUnavailableException(
                    "FMP HTTP ${ex.statusCode} for $endpoint/$ticker",
                    cause = ex,
                    httpStatus = ex.statusCode.value(),
                )
            } catch (ex: FmpUnavailableException) {
                // The underlying adapter already wraps 5xx / 429 — route to the
                // correct logger method based on the carried httpStatus.
                onHttpFailure(endpoint, ticker, ex.httpStatus, ex.message)
                throw ex
            }
        }

        // Compose decorators (innermost runs first): Retry around CB around Bulkhead.
        val decorated: Supplier<T> = Bulkhead.decorateSupplier(bulkhead,
            CircuitBreaker.decorateSupplier(circuitBreaker,
                Retry.decorateSupplier(retry, instrumented),
            ),
        )

        // Apply RateLimiter at the outermost layer — one logical call ↔ one token.
        val gated: Supplier<T> = RateLimiter.decorateSupplier(rateLimiter, decorated)

        return try {
            gated.get()
        } catch (ex: CallNotPermittedException) {
            // CB is OPEN → fast-fail without burning a retry/token.
            log.warn("FMP circuit OPEN for {}/{} — call not permitted", endpoint, ticker)
            eventLogger.logCircuitOpen("call rejected: $endpoint/$ticker")
            throw FmpUnavailableException("FMP circuit open for $endpoint", ex)
        }
    }

    private fun onHttpFailure(endpoint: String, ticker: String, httpStatus: Int?, detail: String?) {
        when (httpStatus) {
            429 -> eventLogger.log429RateLimited(ticker, endpoint, detail)
            in 500..599 -> eventLogger.log5xx(ticker, endpoint, httpStatus!!, detail)
            null -> eventLogger.log5xx(ticker, endpoint, 0, detail) // unknown HTTP status
            else -> { /* 4xx (other than 429) is generally a not-found semantics, handled upstream */ }
        }
    }
}
