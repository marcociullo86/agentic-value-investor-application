package com.valueinvesting.webapp.backtest

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.valueinvesting.webapp.api.model.BacktestResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

/**
 * Cache Caffeine per il payload Backtest (EP-024 / US-105 / TSK-348).
 *
 * Chiave: `(ticker, years, horizonMonths)` — `equity` ESCLUSA dalla chiave
 * (non viene mai persistita, e' un valore locale del calcolo single-trade).
 *
 * TTL: 24h fisso, allineato alla policy `ADR-030 §1` (cache-aside FMP 24h come
 * Summary / Technical). Coerente con la cache FMP a monte (`FmpCacheService`):
 * mentre i fondamentali sono freschi, la ricostruzione storica non cambia.
 *
 * Max size: 2000 entries (~ 30KB/entry stimato sul payload → cap ~ 60 MB
 * worst-case; trascurabile su VM 4 GiB ADR-015).
 *
 * Invalidazione naturale: scaduto il TTL, una nuova chiamata ricostruisce il
 * verdetto sul nuovo snapshot FMP — coerente con la semantica di freschezza
 * del Summary.
 *
 * [^src: management/kanban/EP-024-.../US-105-.../TSK-348.md §"Caching"]
 * [^src: design_&_architecture/decisions/ADR-030-decision-layer-vi-ta-summary.md §1]
 */
@Configuration
class BacktestCacheConfig {

    @Bean
    fun backtestCache(): Cache<BacktestCacheKey, BacktestResponse> =
        Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(BACKTEST_CACHE_TTL_HOURS))
            .maximumSize(MAX_BACKTEST_CACHE_SIZE)
            .recordStats()
            .build()

    companion object {
        const val BACKTEST_CACHE_TTL_HOURS: Long = 24
        const val MAX_BACKTEST_CACHE_SIZE: Long = 2_000
    }
}

/**
 * Chiave di cache per il Backtest. `equity` ESCLUSA per design (US-105
 * §"Vincoli di scope": "equity mai persistita").
 */
data class BacktestCacheKey(
    val ticker: String,
    val years: Int,
    val horizonMonths: Int,
)
