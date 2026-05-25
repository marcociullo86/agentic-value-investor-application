package com.valueinvesting.webapp.secedgar

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

// Caffeine cache configuration per il modulo SEC EDGAR.
//
// SCOPE: solo cache ticker→CIK (TSK-092). Cache per listFilings/downloadFilingHtml
// rimandate a US-039 (TSK-094..097), su tabella DB dedicata diversa da in-memory.
//
// DESIGN — `expireAfterWrite(cikCacheTtlDays)` invece di `expireAfterAccess` perché:
//   - company_tickers.json SEC è quasi-statico (IPO/delist settimanali, non per-call);
//   - vogliamo refresh deterministico ogni 30 giorni indipendentemente dal traffico;
//   - alla scadenza la PRIMA chiamata post-TTL ri-popola l'intero JSON (~10k entries,
//     ~3 MB) in un colpo solo — strategia A coerente con TSK-091.
//
// MAX SIZE: 20_000 entries (sopra ~10k attuali con headroom 2× per future espansioni
// SEC; ~50 byte/entry × 20k = 1 MB RAM hard cap, trascurabile su 4 GiB VM ADR-015).
//
// [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-038-sec-edgar-adapter/TSK-092.md §1,4]
// [^src: design_&_architecture/decisions/ADR-002-backend-stack.md §Caching]
@Configuration
class SecEdgarCacheConfig(
    private val properties: SecEdgarProperties = SecEdgarProperties(),
) {

    /**
     * Cache ticker (uppercase) → CIK (10-digit zero-padded string).
     *
     * Pattern: bulk-populate on first miss (l'intero JSON ~10k entries viene
     * caricato da `SecEdgarRestClient.loadTickerCikMap()` alla prima chiamata
     * `resolveCikFromTicker` post-TTL). Subsequent lookups: O(1) all-hit fino
     * a scadenza.
     */
    @Bean
    fun secEdgarTickerToCikCache(): Cache<String, String> =
        Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofDays(properties.cikCacheTtlDays))
            .maximumSize(MAX_TICKER_CACHE_SIZE)
            .recordStats()
            .build()

    companion object {
        const val MAX_TICKER_CACHE_SIZE = 20_000L
    }
}
