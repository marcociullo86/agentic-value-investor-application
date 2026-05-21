package com.valueinvesting.webapp.fmp

import io.github.resilience4j.bulkhead.Bulkhead
import io.github.resilience4j.bulkhead.BulkheadConfig
import io.github.resilience4j.bulkhead.BulkheadRegistry
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import io.github.resilience4j.ratelimiter.RateLimiterRegistry
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import io.github.resilience4j.timelimiter.TimeLimiter
import io.github.resilience4j.timelimiter.TimeLimiterConfig
import io.github.resilience4j.timelimiter.TimeLimiterRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

// Resilience4j chain configuration for the FMP integration.
//
// DESIGN DECISION — programmatic over YAML
// -----------------------------------------
// The `resilience4j-spring-boot3` starter supports declarative config via
// `application.yml` AND annotation-driven AOP (e.g. `@CircuitBreaker(name="fmp")`).
// We adopt a hybrid: instance config lives here in Kotlin (precise, type-checked,
// testable, single source of truth for thresholds) and the adapter uses the
// `*Registry.executeSupplier` programmatic API rather than annotations.
// Rationale:
//   - Adapter chain order is explicit (PATTERN §raw/tech_stack.md: Request →
//     CircuitBreaker → Retry → HTTP) — annotation ordering depends on
//     reflection-aware aspect order and is fragile across CG/AOT.
//   - Tests don't need full @SpringBootTest to swap thresholds — we can build
//     a registry in-place with the same factory used here.
//   - Avoids hidden Spring AOP failure modes (`final` methods, Kotlin
//     all-open caveats) on a Kotlin codebase where data classes / final
//     methods are the norm.
//
// The `resilience4j.*` block in application.yml is intentionally kept as a
// minimal SAFETY NET (defaults that match these beans within tolerance) so a
// future contributor wiring annotations gets sensible behavior — but the
// adapter wires through the beans below, not through AOP.
//
// CHAIN ORDER (per raw/tech_stack.md §Backend):
//   Request → Bulkhead → CircuitBreaker → Retry → TimeLimiter → HTTP call
//
// RateLimiter sits outside the chain (call-rate guard, applied at adapter
// entrypoint).  Bulkhead caps concurrent in-flight requests.
//
// [^src: raw/tech_stack.md §Backend]
// [^src: design_&_architecture/decisions/ADR-004-fmp-integration.md §Resilienza]
// [^src: management/kanban/.../TSK-011.md §FmpResilienceConfig]
@Configuration
class FmpResilienceConfig {

    @Bean
    fun fmpCircuitBreakerRegistry(): CircuitBreakerRegistry =
        CircuitBreakerRegistry.of(
            CircuitBreakerConfig.custom()
                // Sliding window of last 20 calls; OPEN if >=50% fail.
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(SLIDING_WINDOW)
                .minimumNumberOfCalls(MIN_CALLS_BEFORE_EVAL)
                .failureRateThreshold(FAILURE_RATE_PCT)
                // OPEN -> HALF_OPEN after 60s; allow N probes before re-CLOSE.
                .waitDurationInOpenState(Duration.ofSeconds(HALF_OPEN_AFTER_SEC))
                .permittedNumberOfCallsInHalfOpenState(HALF_OPEN_PROBES)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                // Only "FMP unavailable" counts as failure for the breaker.  Not-found
                // (4xx) is a legitimate semantic answer, NOT a circuit-tripping event.
                .recordExceptions(FmpUnavailableException::class.java)
                .ignoreExceptions(FmpTickerNotFoundException::class.java)
                .build(),
        )

    @Bean
    fun fmpCircuitBreaker(registry: CircuitBreakerRegistry): CircuitBreaker =
        registry.circuitBreaker(FMP_INSTANCE)

    @Bean
    fun fmpRetryRegistry(): RetryRegistry =
        RetryRegistry.of(
            RetryConfig.custom<Any>()
                .maxAttempts(MAX_ATTEMPTS) // 1 original + 2 retries = 3 attempts
                // Exponential backoff: 500ms -> 1s -> 2s -> ... cap at 4s.
                // (multiplier=2: 500,1000,2000,4000 — we cap maxAttempts=3 so the
                // actual sleeps observed are ~500ms then ~1s.)
                .intervalFunction(
                    io.github.resilience4j.core.IntervalFunction
                        .ofExponentialBackoff(
                            INITIAL_BACKOFF.toMillis(),
                            BACKOFF_MULTIPLIER,
                            MAX_BACKOFF.toMillis(),
                        ),
                )
                .retryExceptions(FmpUnavailableException::class.java)
                .ignoreExceptions(FmpTickerNotFoundException::class.java)
                .build(),
        )

    @Bean
    fun fmpRetry(registry: RetryRegistry): Retry = registry.retry(FMP_INSTANCE)

    @Bean
    fun fmpRateLimiterRegistry(): RateLimiterRegistry =
        RateLimiterRegistry.of(
            RateLimiterConfig.custom()
                // 30 calls / 60s = 0.5 req/s steady-state.  Conservative cap;
                // gap `fmp-rate-limiting` open for product to confirm FMP plan quota.
                .limitForPeriod(LIMIT_PER_PERIOD)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                // Wait up to 2s for a token before failing fast — keeps bursty
                // CompletableFuture fan-out (TSK-018) from blowing the limiter.
                .timeoutDuration(Duration.ofSeconds(2))
                .build(),
        )

    @Bean
    fun fmpRateLimiter(registry: RateLimiterRegistry): RateLimiter =
        registry.rateLimiter(FMP_INSTANCE)

    @Bean
    fun fmpBulkheadRegistry(): BulkheadRegistry =
        BulkheadRegistry.of(
            BulkheadConfig.custom()
                .maxConcurrentCalls(BULKHEAD_CONCURRENT)
                // Don't queue: fail fast (the caller can still serve stale).
                .maxWaitDuration(Duration.ZERO)
                .build(),
        )

    @Bean
    fun fmpBulkhead(registry: BulkheadRegistry): Bulkhead = registry.bulkhead(FMP_INSTANCE)

    @Bean
    fun fmpTimeLimiterRegistry(): TimeLimiterRegistry =
        TimeLimiterRegistry.of(
            TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(REQUEST_TIMEOUT_SEC))
                .cancelRunningFuture(true)
                .build(),
        )

    @Bean
    fun fmpTimeLimiter(registry: TimeLimiterRegistry): TimeLimiter =
        registry.timeLimiter(FMP_INSTANCE)

    companion object {
        /** Registry key shared by all FMP-scoped resilience instances. */
        const val FMP_INSTANCE = "fmp"

        // CircuitBreaker
        const val SLIDING_WINDOW = 20
        const val MIN_CALLS_BEFORE_EVAL = 10
        const val FAILURE_RATE_PCT = 50f
        const val HALF_OPEN_AFTER_SEC = 60L
        const val HALF_OPEN_PROBES = 3

        // Retry
        const val MAX_ATTEMPTS = 3
        val INITIAL_BACKOFF: Duration = Duration.ofMillis(500)
        val MAX_BACKOFF: Duration = Duration.ofSeconds(4)
        const val BACKOFF_MULTIPLIER = 2.0

        // RateLimiter — 30 req / minute (gap `fmp-rate-limiting`)
        const val LIMIT_PER_PERIOD = 30

        // Bulkhead — 10 concurrent FMP in-flight
        const val BULKHEAD_CONCURRENT = 10

        // TimeLimiter — per-call hard cap
        const val REQUEST_TIMEOUT_SEC = 10L
    }
}
