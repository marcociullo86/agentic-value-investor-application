package com.valueinvesting.webapp.llm

// Sealed exception hierarchy for Anthropic API errors.
// Maps HTTP status codes to typed exceptions for Resilience4j classification.
//
// Transient (retry + circuit-breaker eligible): RateLimited, Overloaded, ServerError, Timeout.
// Permanent (fail-fast, no retry):              AuthError, InvalidRequest.
//
// [^src: design_&_architecture/decisions/ADR-017-anthropic-sdk-jvm.md §4]
sealed class LlmException(msg: String, cause: Throwable? = null) : RuntimeException(msg, cause) {

    class RateLimited(
        val retryAfterSec: Int? = null,
        cause: Throwable? = null,
    ) : LlmException("Anthropic 429 rate limited (retryAfter=${retryAfterSec}s)", cause)

    class Overloaded(
        cause: Throwable? = null,
    ) : LlmException("Anthropic 529 overloaded", cause)

    class InvalidRequest(
        detail: String,
        cause: Throwable? = null,
    ) : LlmException("Anthropic 400 invalid request: $detail", cause)

    class AuthError(
        cause: Throwable? = null,
    ) : LlmException("Anthropic 401/403 auth error", cause)

    class ServerError(
        val status: Int,
        cause: Throwable? = null,
    ) : LlmException("Anthropic 5xx server error ($status)", cause)

    class Timeout(
        cause: Throwable? = null,
    ) : LlmException("Anthropic call timeout", cause)
}
