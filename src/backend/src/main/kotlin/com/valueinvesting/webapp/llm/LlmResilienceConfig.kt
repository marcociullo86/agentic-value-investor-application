package com.valueinvesting.webapp.llm

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.core.IntervalFunction
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import io.github.resilience4j.ratelimiter.RateLimiterRegistry
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

// Resilience4j chain configuration for Anthropic Claude LLM calls.
//
// DESIGN — programmatic over YAML (same rationale as FmpResilienceConfig):
//   - Config in Kotlin = single source of truth (type-checked, testable)
//   - Consumer applies the programmatic API: rateLimiter → circuitBreaker → retry → HTTP
//
// CHAIN ORDER (per raw/tech_stack.md §Backend + ADR-017 §5):
//   RateLimiter → CircuitBreaker → Retry → HTTP call
//
// The resilience4j.* block in application.yml is a safety-net mirror (see
// FmpResilienceConfig header comment for the rationale).
//
// DIFFERENCES vs FMP:
//   - RateLimiter cap = 12 calls/min (US-041: 10 queries + 1 synthesis + 1 fallback)
//   - SlidingWindow = 5 (smaller volume vs FMP's 20)
//   - Retry backoff 2s→4s→8s (longer than FMP's 500ms base — LLM calls are expensive)
//   - No Bulkhead/TimeLimiter in this TSK scope (can be added per ADR-017 §5)
//
// [^src: design_&_architecture/decisions/ADR-017-anthropic-sdk-jvm.md §5]
// [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-041-munger-inversion-llm/TSK-104.md §3]
// [^src: raw/tech_stack.md §Backend - Resilience]
@Configuration
class LlmResilienceConfig(
    private val properties: AnthropicProperties = AnthropicProperties(),
) {

    @Bean
    fun llmCircuitBreakerRegistry(): CircuitBreakerRegistry =
        CircuitBreakerRegistry.of(
            CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(SLIDING_WINDOW)
                .minimumNumberOfCalls(MIN_CALLS_BEFORE_EVAL)
                .failureRateThreshold(FAILURE_RATE_PCT)
                .waitDurationInOpenState(Duration.ofSeconds(WAIT_DURATION_OPEN_SEC))
                .permittedNumberOfCallsInHalfOpenState(HALF_OPEN_PROBES)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordExceptions(
                    LlmException.RateLimited::class.java,
                    LlmException.Overloaded::class.java,
                    LlmException.ServerError::class.java,
                    LlmException.Timeout::class.java,
                )
                .ignoreExceptions(
                    LlmException.AuthError::class.java,
                    LlmException.InvalidRequest::class.java,
                )
                .build(),
        )

    @Bean
    fun llmCircuitBreaker(llmCircuitBreakerRegistry: CircuitBreakerRegistry): CircuitBreaker =
        llmCircuitBreakerRegistry.circuitBreaker(LLM_INSTANCE)

    @Bean
    fun llmRetryRegistry(): RetryRegistry =
        RetryRegistry.of(
            RetryConfig.custom<Any>()
                .maxAttempts(MAX_ATTEMPTS)
                .intervalFunction(
                    IntervalFunction.ofExponentialBackoff(
                        INITIAL_BACKOFF.toMillis(),
                        BACKOFF_MULTIPLIER,
                        MAX_BACKOFF.toMillis(),
                    ),
                )
                .retryExceptions(
                    LlmException.RateLimited::class.java,
                    LlmException.Overloaded::class.java,
                    LlmException.ServerError::class.java,
                    LlmException.Timeout::class.java,
                )
                .ignoreExceptions(
                    LlmException.AuthError::class.java,
                    LlmException.InvalidRequest::class.java,
                )
                .build(),
        )

    @Bean
    fun llmRetry(llmRetryRegistry: RetryRegistry): Retry =
        llmRetryRegistry.retry(LLM_INSTANCE)

    @Bean
    fun llmRateLimiterRegistry(): RateLimiterRegistry =
        RateLimiterRegistry.of(
            RateLimiterConfig.custom()
                .limitForPeriod(properties.rateLimit.perMinute)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ofSeconds(RATE_LIMITER_TIMEOUT_SEC))
                .build(),
        )

    @Bean
    fun llmRateLimiter(llmRateLimiterRegistry: RateLimiterRegistry): RateLimiter =
        llmRateLimiterRegistry.rateLimiter(LLM_INSTANCE)

    companion object {
        /** Registry key shared by all LLM-scoped resilience instances. */
        const val LLM_INSTANCE = "llm-claude"

        // CircuitBreaker — ADR-017 §5
        const val SLIDING_WINDOW = 5
        const val MIN_CALLS_BEFORE_EVAL = 3
        const val FAILURE_RATE_PCT = 50f
        const val WAIT_DURATION_OPEN_SEC = 60L
        const val HALF_OPEN_PROBES = 2

        // Retry — exponential backoff 2s → 4s → 8s
        const val MAX_ATTEMPTS = 3
        val INITIAL_BACKOFF: Duration = Duration.ofSeconds(2)
        val MAX_BACKOFF: Duration = Duration.ofSeconds(8)
        const val BACKOFF_MULTIPLIER = 2.0

        // RateLimiter — wait up to 5s for a permit
        const val RATE_LIMITER_TIMEOUT_SEC = 5L
    }
}
