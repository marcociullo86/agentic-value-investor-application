package com.valueinvesting.webapp.secedgar

// Custom exceptions per il modulo SEC EDGAR.
//
// Mapping HTTP → exception (applicato in `SecEdgarRestClient`):
//   - 403 Forbidden  → SecEdgarAccessDeniedException  (User-Agent malformato o
//                       banned per fair-access-policy violation)
//   - 429 Too Many   → SecEdgarRateLimitException     (SEC rate-limit-per-UA
//                       superato; transient)
//   - 5xx            → SecEdgarServiceException        (transient SEC infra)
//
// Pattern coerente con `FmpExceptions.kt` (FmpUnavailableException porta httpStatus).
// Le exception 429/5xx sono record-eligible dal CircuitBreaker Resilience4j.
// 403 è permanente (config errata, NON transient) → IGNORE dal CB e dal Retry.
//
// [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-038-sec-edgar-adapter/TSK-091.md §6]

class SecEdgarAccessDeniedException(
    message: String = "SEC EDGAR access denied (403) — verifica User-Agent e fair-access policy",
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class SecEdgarRateLimitException(
    message: String = "SEC EDGAR rate-limited (429) — superato cap 10 req/s per User-Agent",
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class SecEdgarServiceException(
    message: String,
    val httpStatus: Int,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
