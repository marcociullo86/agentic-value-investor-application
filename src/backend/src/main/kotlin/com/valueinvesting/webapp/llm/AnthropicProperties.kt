package com.valueinvesting.webapp.llm

import org.springframework.boot.context.properties.ConfigurationProperties

// Typed configuration for the Anthropic Claude integration.
//
// API key MUST be set via env var ANTHROPIC_API_KEY in production; blank value
// activates the AnthropicClientStub fallback (dev/test mode).
//
// [^src: design_&_architecture/decisions/ADR-017-anthropic-sdk-jvm.md §2,§5]
// [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-041-munger-inversion-llm/TSK-104.md §4]
@ConfigurationProperties(prefix = "anthropic")
data class AnthropicProperties(
    /** Anthropic API key. Env: ANTHROPIC_API_KEY. Blank = stub mode. */
    val apiKey: String = "",

    /** Messages API base URL (no trailing slash). */
    val baseUrl: String = DEFAULT_BASE_URL,

    /** Default model for completions. */
    val model: String = DEFAULT_MODEL,

    /** Per-call HTTP read timeout in seconds. */
    val timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,

    /** Client implementation selector. */
    val client: ClientConfig = ClientConfig(),

    /** Rate-limit configuration for the Resilience4j RateLimiter. */
    val rateLimit: RateLimitConfig = RateLimitConfig(),
) {
    data class ClientConfig(
        /** "rest" (default, HTTP direct) or "sdk" (official SDK, requires classpath). */
        val impl: String = "rest",
    )

    data class RateLimitConfig(
        /** Max calls per 60s window. US-041 cap = 12 (10 query + 1 synthesis + 1 fallback). */
        val perMinute: Int = DEFAULT_RATE_LIMIT_PER_MINUTE,
    )

    companion object {
        const val DEFAULT_BASE_URL = "https://api.anthropic.com/v1"
        const val DEFAULT_MODEL = "claude-opus-4-7"
        const val DEFAULT_TIMEOUT_SECONDS = 60L
        const val DEFAULT_RATE_LIMIT_PER_MINUTE = 12
    }
}
