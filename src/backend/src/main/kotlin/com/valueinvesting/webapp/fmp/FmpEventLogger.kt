package com.valueinvesting.webapp.fmp

import com.valueinvesting.webapp.persistence.entity.FmpApiEventLog
import com.valueinvesting.webapp.persistence.repository.FmpApiEventLogRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

// Audit/observability sink for FMP integration events.  Persists rows to
// fmp_api_event_log asynchronously (`@Async("eventLoggerExecutor")`) so that
// the hot HTTP path is never blocked by a DB write — and so that a slow or
// failed insert NEVER masks the original FMP outcome.
//
// Trigger points:
//   - FmpAdapterRestClient: on 429 (rate limited) and 5xx responses.
//   - FmpResilienceWrapper: when the chain falls back to stale cache.
//   - CircuitBreaker event consumer (FmpHealthIndicator binding): on OPEN
//     state transition.
//   - FinancialDataService / AnalyzeTickerService (TSK-018): on
//     FmpTickerNotFoundException (semantica non-error ma osservata).
//
// Failure semantics: if the DB write itself fails (DB down, schema mismatch),
// we swallow the exception and emit a WARN log — losing an audit row must
// never escalate to the caller.  Aggregate metrics from Micrometer remain the
// authoritative health signal in that degraded scenario.
//
// [^src: design_&_architecture/decisions/ADR-008-observability-logging.md]
// [^src: design_&_architecture/data/er-diagram.md §fmp_api_event_log]
// [^src: management/kanban/.../TSK-011.md §FmpEventLogger]
@Component
class FmpEventLogger(
    private val repository: FmpApiEventLogRepository,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Async("eventLoggerExecutor")
    open fun log429RateLimited(ticker: String?, endpoint: String?, detail: String?) {
        persist(EventType.FMP_429_RATE_LIMITED, ticker, endpoint, 429, detail)
    }

    @Async("eventLoggerExecutor")
    open fun log5xx(ticker: String?, endpoint: String?, httpStatus: Int, detail: String?) {
        persist(EventType.FMP_5XX, ticker, endpoint, httpStatus, detail)
    }

    @Async("eventLoggerExecutor")
    open fun logCircuitOpen(detail: String?) {
        // CB events have no ticker/endpoint context — they are registry-level.
        persist(EventType.FMP_CIRCUIT_OPEN, null, null, null, detail)
    }

    @Async("eventLoggerExecutor")
    open fun logFallbackStale(ticker: String, endpoint: String, detail: String?) {
        persist(EventType.FMP_FALLBACK_STALE, ticker, endpoint, null, detail)
    }

    @Async("eventLoggerExecutor")
    open fun logTickerNotFound(ticker: String, endpoint: String?) {
        persist(EventType.FMP_TICKER_NOT_FOUND, ticker, endpoint, 404, null)
    }

    private fun persist(
        type: EventType,
        ticker: String?,
        endpoint: String?,
        httpStatus: Int?,
        detail: String?,
    ) {
        try {
            repository.save(
                FmpApiEventLog(
                    occurredAt = Instant.now(clock),
                    eventType = type.name,
                    ticker = ticker?.uppercase(),
                    endpoint = endpoint,
                    httpStatus = httpStatus,
                    detail = detail?.take(MAX_DETAIL_LEN),
                ),
            )
        } catch (ex: Exception) {
            // Audit write must never fail the caller — Micrometer carries the
            // authoritative health signal if persistence is degraded.
            log.warn(
                "Failed to persist fmp_api_event_log type={} ticker={} endpoint={}: {}",
                type, ticker, endpoint, ex.message,
            )
        }
    }

    /** Canonical event names — must match the CHECK constraint on fmp_api_event_log.event_type. */
    enum class EventType {
        FMP_429_RATE_LIMITED,
        FMP_5XX,
        FMP_CIRCUIT_OPEN,
        FMP_FALLBACK_STALE,
        FMP_TICKER_NOT_FOUND,
    }

    companion object {
        // detail TEXT column has no hard cap but very long stack traces would
        // bloat the table.  Truncate to 4 KB.
        const val MAX_DETAIL_LEN = 4_000
    }
}
