package com.valueinvesting.webapp.secedgar

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import io.github.resilience4j.ratelimiter.RateLimiterRegistry
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

// Resilience4j chain configuration per il modulo SEC EDGAR.
//
// DESIGN — identico al pattern FmpResilienceConfig (programmatic over YAML):
//   - config in Kotlin = single source of truth (testable, type-checked)
//   - adapter usa l'API programmatica `*Registry`/`decorateSupplier`
//
// CHAIN ORDER (per allineamento con FMP module):
//   Request → CircuitBreaker → Retry → HTTP call
//   RateLimiter applicato all'outermost layer del decorator.
//
// DIFFERENZE vs FMP:
//   - RateLimiter è 10 req/s (vs FMP 30 req/min) — fair-access SEC è hard cap
//     per User-Agent: superarlo = 429 immediato, potenziale 403 ban.
//   - No Bulkhead/TimeLimiter: i caller SEC sono sync sequential (DeepAnalysis
//     pipeline scarica 1 10-K alla volta), no fan-out tipo TSK-018.
//   - Bean prefix `secEdgar*` per coesistere con `fmp*` (no Spring conflict).
//   - `recordExceptions` include solo le 2 transient: RateLimit + Service.
//     AccessDenied (403) è permanente → IGNORE (no retry, no CB trip).
//
// [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-038-sec-edgar-adapter/TSK-091.md §4]
// [^src: raw/tech_stack.md §Backend - Resilience]
@Configuration
class SecEdgarResilienceConfig(
    private val properties: SecEdgarProperties = SecEdgarProperties(),
) {

    @Bean
    fun secEdgarCircuitBreakerRegistry(): CircuitBreakerRegistry =
        CircuitBreakerRegistry.of(
            CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(properties.circuitBreaker.slidingWindowSize)
                .minimumNumberOfCalls(properties.circuitBreaker.minimumNumberOfCalls)
                .failureRateThreshold(properties.circuitBreaker.failureRateThreshold)
                .waitDurationInOpenState(
                    Duration.ofSeconds(properties.circuitBreaker.waitDurationInOpenStateSeconds),
                )
                .permittedNumberOfCallsInHalfOpenState(
                    properties.circuitBreaker.permittedNumberOfCallsInHalfOpenState,
                )
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordExceptions(
                    SecEdgarServiceException::class.java,
                    SecEdgarRateLimitException::class.java,
                )
                .ignoreExceptions(SecEdgarAccessDeniedException::class.java)
                .build(),
        )

    @Bean
    fun secEdgarCircuitBreaker(registry: CircuitBreakerRegistry): CircuitBreaker =
        registry.circuitBreaker(SEC_EDGAR_INSTANCE)

    @Bean
    fun secEdgarRetryRegistry(): RetryRegistry =
        RetryRegistry.of(
            RetryConfig.custom<Any>()
                .maxAttempts(properties.retry.maxAttempts)
                .waitDuration(Duration.ofMillis(properties.retry.waitDurationMs))
                .retryExceptions(
                    SecEdgarServiceException::class.java,
                    SecEdgarRateLimitException::class.java,
                )
                .ignoreExceptions(SecEdgarAccessDeniedException::class.java)
                .build(),
        )

    @Bean
    fun secEdgarRetry(registry: RetryRegistry): Retry = registry.retry(SEC_EDGAR_INSTANCE)

    @Bean
    fun secEdgarRateLimiterRegistry(): RateLimiterRegistry =
        RateLimiterRegistry.of(
            RateLimiterConfig.custom()
                // 10 req/s SEC fair-access cap. NON aumentare.
                .limitForPeriod(properties.rateLimitPerSecond)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ofSeconds(properties.rateLimitTimeoutSeconds))
                .build(),
        )

    @Bean
    fun secEdgarRateLimiter(registry: RateLimiterRegistry): RateLimiter =
        registry.rateLimiter(SEC_EDGAR_INSTANCE)

    companion object {
        /** Registry key condiviso da tutte le istanze Resilience4j SEC-scoped. */
        const val SEC_EDGAR_INSTANCE = "secEdgar"
    }
}
