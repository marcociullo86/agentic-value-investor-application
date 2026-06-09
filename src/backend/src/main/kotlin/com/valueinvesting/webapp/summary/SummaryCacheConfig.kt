package com.valueinvesting.webapp.summary

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.valueinvesting.webapp.api.model.SummaryVerdictResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration
import java.util.UUID

/**
 * Cache Caffeine per il payload Summary (EP-024 / US-103 / TSK-340).
 *
 * Chiave logica: tupla `(userId, ticker, viSnapshot, taSnapshot, deepSnapshot)`
 * — coerente con la specifica US-103 §"Caching" (per-user perche' il VI
 * snapshot dipende dall'override DCF user-scoped via [DcfOverrideRepository]).
 *
 * Implementazione concreta: chiave [SummaryCacheKey] (data class hashable).
 *
 * TTL: 24h fisso (US-103 §"Caching") — coerente con la cache FMP `ADR-004` e
 * con il TTL di [com.valueinvesting.webapp.fmp.FmpCacheService]. La cache si
 * invalida naturalmente alla scadenza; in alternativa la cambio dello snapshot
 * VI/TA/Deep produce gia' una chiave nuova → invalidazione implicita.
 *
 * Max size: 5000 entries (~ 50KB/entry stimato sul payload con citazioni →
 * cap RAM ~ 250 MB worst-case; trascurabile sulla VM 4 GiB ADR-015).
 *
 * [^src: management/kanban/EP-024-.../US-103-.../TSK-340.md]
 * [^src: management/kanban/EP-024-.../US-103-.../US-103.md §"Caching"]
 */
@Configuration
class SummaryCacheConfig {

    @Bean
    fun summaryCache(): Cache<SummaryCacheKey, SummaryVerdictResponse> =
        Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(SUMMARY_CACHE_TTL_HOURS))
            .maximumSize(MAX_SUMMARY_CACHE_SIZE)
            .recordStats()
            .build()

    companion object {
        const val SUMMARY_CACHE_TTL_HOURS: Long = 24
        const val MAX_SUMMARY_CACHE_SIZE: Long = 5_000
    }
}

/**
 * Chiave di cache per il Summary. `userId` puo' essere null (anonymous);
 * mantenere il principio per-user e' essenziale perche' il VI snapshot puo'
 * differire per via dell'override DCF user-scoped.
 *
 * Snapshot:
 *  - `viSnapshotAt`   = `RuleEngineResultResponse.dataSnapshotAt`, che e' il
 *    MIN(fetchedAt) dei 4 snapshot FMP cached (vedi `FinancialDataService`).
 *    Stabile entro il TTL della cache FMP 24h; cambia con un re-fetch FMP.
 *  - `deepSnapshotAt` = `completedAt` della run SUCCESS della Deep (null se
 *    NOT_INDEXED / NOT_AVAILABLE). Cambia con una nuova run di Deep.
 *
 * NOTA su TA: non includiamo un `taSnapshotAt` nella chiave perche' la TA e'
 * cache-aside sopra la stessa cache FMP 24h (ADR-030 §1, no DB TA). La
 * stabilita' di `viSnapshotAt` entro la finestra FMP cattura indirettamente
 * la stabilita' della TA — gli input TA condividono la stessa origine FMP.
 */
data class SummaryCacheKey(
    val userId: UUID?,
    val ticker: String,
    val viSnapshotAt: java.time.Instant,
    val deepSnapshotAt: java.time.Instant?,
)
