package com.valueinvesting.webapp.config

import org.springframework.boot.context.properties.ConfigurationProperties

// Outbound FMP rate limit (Resilience4j RateLimiter).
// [^src: design_&_architecture/decisions/ADR-016-fmp-operations-throttling.md §4. Throttling backend]
@ConfigurationProperties(prefix = "fmp")
data class FmpRateLimitProperties(
    /** Max logical FMP calls per 60s refresh window. Env: `FMP_RATE_LIMIT_PER_MINUTE`. */
    val rateLimitPerMinute: Int = DEFAULT_RATE_LIMIT_PER_MINUTE,
) {
    companion object {
        const val DEFAULT_RATE_LIMIT_PER_MINUTE = 30
    }
}
