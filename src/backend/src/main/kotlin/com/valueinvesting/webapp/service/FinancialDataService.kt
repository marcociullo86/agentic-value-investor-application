package com.valueinvesting.webapp.service

import com.fasterxml.jackson.core.type.TypeReference
import com.valueinvesting.webapp.fmp.CachedPayload
import com.valueinvesting.webapp.fmp.FmpAdapter
import com.valueinvesting.webapp.fmp.FmpCacheService
import com.valueinvesting.webapp.fmp.FmpEventLogger
import com.valueinvesting.webapp.fmp.FmpUnavailableException
import com.valueinvesting.webapp.fmp.dto.BalanceSheetDto
import com.valueinvesting.webapp.fmp.dto.CashFlowDto
import com.valueinvesting.webapp.fmp.dto.IncomeStatementDto
import com.valueinvesting.webapp.fmp.dto.KeyMetricsDto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

// Facade che coordina le 4 chiamate FmpAdapter e assembla FinancialDataset.
//
// Cache-aside delegation (TSK-010): le 4 chiamate passano tutte per
// `FmpCacheService.getOrFetch(...)` — non chiamiamo più direttamente l'adapter.
// La firma pubblica resta identica per non rompere il `FinancialsController`.
//
// `dataSnapshotAt` ora riflette il MIN(fetchedAt) tra i 4 snapshot: rappresenta
// il "data al" effettivo del payload più vecchio servito al caller, coerente con
// US-005 AC "timestamp dati al" e con er-diagram §rule_engine_result.source_snapshot_fetched_at.
//
// La parallelizzazione delle 4 fetch via CompletableFuture è demandata a TSK-018
// in AnalyzeTickerService — qui restano sequenziali per semplicità.
//
// TSK-011 — Resilience fallback path (US-006 AC):
//   When the underlying FmpAdapter (now wrapped by ResilientFmpAdapter) has
//   exhausted retries and finally throws FmpUnavailableException, we try to
//   serve the LAST KNOWN cached payload via `FmpCacheService.getStale()`.
//   - Cache hit → mark `isStale=true`, dataset returned with header X-Data-Stale.
//   - Cache miss → re-throw FmpUnavailableException → GlobalExceptionHandler
//     maps to HTTP 503 ProblemDetails.
//
// [^src: design_&_architecture/components/backend-components.md §FinancialDataService]
// [^src: design_&_architecture/decisions/ADR-004-fmp-integration.md §Cache layer 24h]
// [^src: design_&_architecture/decisions/ADR-004-fmp-integration.md §Fallback su cache scaduta]
// [^src: management/kanban/.../TSK-010.md §Refactor FinancialDataService]
// [^src: management/kanban/.../TSK-011.md §Fallback in FinancialDataService]
@Service
class FinancialDataService(
    private val fmpAdapter: FmpAdapter,
    private val fmpCacheService: FmpCacheService,
    private val fmpEventLogger: FmpEventLogger,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun getFinancialDataset(ticker: String, limit: Int = 10): FinancialDataset {
        require(ticker.isNotBlank()) { "ticker must not be blank" }
        val t = ticker.uppercase()

        val income = fetchWithFallback(
            ticker = t,
            endpoint = ENDPOINT_INCOME,
            typeRef = object : TypeReference<List<IncomeStatementDto>>() {},
            fetchFn = { fmpAdapter.getIncomeStatement(t, limit) },
        )
        val balance = fetchWithFallback(
            ticker = t,
            endpoint = ENDPOINT_BALANCE,
            typeRef = object : TypeReference<List<BalanceSheetDto>>() {},
            fetchFn = { fmpAdapter.getBalanceSheet(t, limit) },
        )
        val cashFlow = fetchWithFallback(
            ticker = t,
            endpoint = ENDPOINT_CASHFLOW,
            typeRef = object : TypeReference<List<CashFlowDto>>() {},
            fetchFn = { fmpAdapter.getCashFlow(t, limit) },
        )
        val keyMetrics = fetchWithFallback(
            ticker = t,
            endpoint = ENDPOINT_KEY_METRICS,
            typeRef = object : TypeReference<List<KeyMetricsDto>>() {},
            fetchFn = { fmpAdapter.getKeyMetrics(t, limit) },
        )

        // Snapshot timestamp is the oldest among the 4 — that's the most honest
        // "data freshness as of" answer for the assembled dataset.
        val snapshotAt = listOf(income, balance, cashFlow, keyMetrics)
            .map { it.fetchedAt }
            .min()
        val anyStale = listOf(income, balance, cashFlow, keyMetrics).any { it.stale }

        return FinancialDataset(
            ticker = t,
            income = income.value,
            balance = balance.value,
            cashFlow = cashFlow.value,
            keyMetrics = keyMetrics.value,
            dataSnapshotAt = snapshotAt,
            isStale = anyStale,
            staleReason = if (anyStale) "fmp-unavailable" else null,
        )
    }

    /**
     * Cache-aside fetch with resilience fallback:
     *   1. Try `FmpCacheService.getOrFetch` (normal cache-aside path).
     *   2. If the adapter throws FmpUnavailableException (after CB/Retry
     *      exhaustion), reach into `getStale()` for the last cached payload.
     *   3. If even the stale lookup is empty, re-throw → HTTP 503.
     *
     * FmpTickerNotFoundException is NOT caught here — it must surface as 404
     * regardless of cache state.
     */
    private fun <T> fetchWithFallback(
        ticker: String,
        endpoint: String,
        typeRef: TypeReference<List<T>>,
        fetchFn: () -> List<T>,
    ): CachedPayload<List<T>> {
        return try {
            fmpCacheService.getOrFetch(ticker, endpoint, typeRef, fetchFn)
        } catch (ex: FmpUnavailableException) {
            log.warn(
                "FMP unavailable for ticker={} endpoint={} — attempting stale fallback: {}",
                ticker, endpoint, ex.message,
            )
            val stale = fmpCacheService.getStale(ticker, endpoint, typeRef)
            if (stale == null) {
                log.warn(
                    "No stale cache available for ticker={} endpoint={} — propagating 503",
                    ticker, endpoint,
                )
                throw ex
            }
            fmpEventLogger.logFallbackStale(
                ticker = ticker,
                endpoint = endpoint,
                detail = "served stale payload from ${stale.fetchedAt}",
            )
            stale
        }
    }

    companion object {
        const val ENDPOINT_INCOME = "income-statement"
        const val ENDPOINT_BALANCE = "balance-sheet-statement"
        const val ENDPOINT_CASHFLOW = "cash-flow-statement"
        const val ENDPOINT_KEY_METRICS = "key-metrics"
    }
}
