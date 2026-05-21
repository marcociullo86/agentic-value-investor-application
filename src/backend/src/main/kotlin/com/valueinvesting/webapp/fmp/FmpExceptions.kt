package com.valueinvesting.webapp.fmp

// Custom exceptions for the FMP module.
// FmpTickerNotFoundException -> mapped to HTTP 404 (RFC 9457) in GlobalExceptionHandler.
// FmpUnavailableException    -> mapped to HTTP 503 (TSK-011 will introduce resilience layer).
// [^src: design_&_architecture/decisions/ADR-004-fmp-integration.md §Fallback su cache scaduta]
// [^src: design_&_architecture/decisions/ADR-007-api-contract.md §Status codes]

class FmpTickerNotFoundException(
    val ticker: String,
    message: String = "Ticker not found on FMP: $ticker",
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class FmpUnavailableException(
    message: String = "FMP service unavailable",
    cause: Throwable? = null,
    // HTTP status that triggered the unavailability classification, when available
    // (5xx from FMP, 429 rate limited, or null for client-side / circuit-open).
    // Carried so ResilientFmpAdapter can route to the correct FmpEventLogger
    // method (log5xx vs log429RateLimited) without re-parsing the message.
    val httpStatus: Int? = null,
) : RuntimeException(message, cause)
