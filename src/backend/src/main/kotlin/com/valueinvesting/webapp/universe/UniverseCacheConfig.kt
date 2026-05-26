package com.valueinvesting.webapp.universe

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

// Caffeine cache configuration per il modulo `universe` (EP-012, US-047).
//
// SCOPE attuale: holdings 13-F per CIK (TSK-127). Future scope: cache news-scout
// (TSK-128) puo' essere aggiunta qui come @Bean separato.
//
// DESIGN — `expireAfterWrite(cacheTtlDays)` invece di `expireAfterAccess` perche'
// i 13-F SEC sono pubblicati trimestralmente (45 gg post-quarter-end): la freschezza
// e' tempo-dominante, non frequency-dominante. TTL 7gg di default copre il caso
// medio (un refresh circa per ogni nuovo filing, +qualche margine).
//
// MAX SIZE 50 entries: i top value fund e' una lista corta (default 5 nel YAML,
// estendibile a ~20). Headroom 2.5x per future espansioni senza eviction LRU.
// Memory hard cap stimato ~50 * 500 holdings * 100 byte/UniverseCandidate = 2.5 MB,
// trascurabile su 4 GiB VM (ADR-015).
//
// [^src: management/kanban/EP-012-batch-top-value-picks/US-047-universe-screener-service/TSK-127.md §Step 5]
// [^src: design_&_architecture/decisions/ADR-002-backend-stack.md §Caching]
@Configuration
class UniverseCacheConfig(
    private val properties: InstitutionalHoldingsProperties,
) {

    /**
     * Cache CIK→holdings 13-F (List<UniverseCandidate> già risolti a ticker).
     *
     * Pattern di uso (vedi InstitutionalHoldingsService.getFundHoldings):
     *   - cache hit: 0 HTTP SEC/FMP, O(1) — return cached list
     *   - cache miss: 1 listFilings + 1 downloadFilingHtml + N searchCusip
     *     (N = numero holding nel 13-F, tipicamente 30-200 per fund). Il
     *     risultato finale (post-CUSIP-resolution) viene cached. Subsequent
     *     hits entro TTL evitano TUTTE le chiamate downstream.
     */
    @Bean(name = ["institutionalHoldingsCache"])
    fun institutionalHoldingsCache(): Cache<String, List<UniverseCandidate>> =
        Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofDays(properties.cacheTtlDays))
            .maximumSize(MAX_HOLDINGS_CACHE_SIZE)
            .recordStats()
            .build()

    companion object {
        const val MAX_HOLDINGS_CACHE_SIZE = 50L
    }
}
