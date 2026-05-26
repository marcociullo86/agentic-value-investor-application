package com.valueinvesting.webapp.job

import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

// Resilience4j RateLimiter dedicato al batch notturno TopValuePicksJob (TSK-132).
//
// MOTIVAZIONE — separazione `fmp-batch` vs `fmp` (online)
// --------------------------------------------------------
// `FmpResilienceConfig.fmpRateLimiter` ha cap default 30 req/min (FMP plan
// stable v1) ed e' condiviso da TUTTO il traffico online (analyze endpoint,
// historical-series, watchlist refresh). Se il batch usasse lo stesso bucket,
// satureremmo il limiter durante le 2-3h notturne e l'UI dev/test (qualcuno
// connesso a 02:00 UTC = 04:00 CEST) tornerebbe TooManyRequests.
//
// Soluzione: il batch fa fan-out con un bucket SEPARATO (`fmp-batch`) calibrato
// alto (300 req/min default — sufficiente per 500 candidati x 3-4 endpoint in
// ~30min) e con `timeoutDuration` lungo (30s) perche' il batch puo' permettersi
// di attendere il refresh tick, mentre online non puo'.
//
// Il bean e' costruito PROGRAMMATICAMENTE (consistente con FmpResilienceConfig
// e con la decisione di disabilitare resilience4j auto-config in application.yml).
// I servizi consumer (UniverseScreenerService variant batch, TopValuePicksJob)
// possono iniettare il bean con `@Qualifier("fmpBatchRateLimiter")` e wrappare
// le chiamate via `RateLimiter.decorateSupplier(limiter) { ... }.get()`.
//
// [^src: management/kanban/EP-012-batch-top-value-picks/US-048-job-notturno-top-picks/TSK-132.md]
// [^src: design_&_architecture/decisions/ADR-016-fmp-operations-throttling.md §4. Throttling backend]
@Configuration
class BatchResilienceConfig {

    @Bean("fmpBatchRateLimiter")
    fun fmpBatchRateLimiter(
        @Value("\${fmp-batch.rate-limit-per-minute:300}") rateLimit: Int,
        @Value("\${fmp-batch.timeout-seconds:30}") timeoutSec: Long,
    ): RateLimiter {
        val config = RateLimiterConfig.custom()
            .limitForPeriod(rateLimit)
            .limitRefreshPeriod(Duration.ofMinutes(1))
            .timeoutDuration(Duration.ofSeconds(timeoutSec))
            .build()
        return RateLimiter.of(INSTANCE_NAME, config)
    }

    companion object {
        const val INSTANCE_NAME = "fmp-batch"
    }
}
