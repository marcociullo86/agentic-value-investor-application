package com.valueinvesting.webapp.fmp

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.valueinvesting.webapp.config.FmpCacheProperties
import com.valueinvesting.webapp.fmp.dto.ProfileDto
import com.valueinvesting.webapp.persistence.entity.FmpFinancialSnapshot
import com.valueinvesting.webapp.persistence.entity.FmpProfileSnapshot
import com.valueinvesting.webapp.persistence.entity.Stock
import com.valueinvesting.webapp.persistence.repository.FmpFinancialSnapshotRepository
import com.valueinvesting.webapp.persistence.repository.FmpProfileSnapshotRepository
import com.valueinvesting.webapp.persistence.repository.StockRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant

// Cache-aside layer between callers (FinancialDataService, SearchService) and
// FmpAdapter.  Backed by fmp_financial_snapshot (24h TTL) and fmp_profile_snapshot
// (configurable via `fmp.cache.profile-ttl-hours`, default 1h — ADR-014).
//
// Strategy (per ADR-004 §Cache layer 24h):
//   1. Look up the latest snapshot for (ticker, endpoint).
//   2. If `now - fetched_at < TTL` → return deserialized payload (no FMP call).
//   3. Otherwise → invoke fetchFn (the FmpAdapter method), persist, return.
//
// `getStale` skips the freshness check and is used by the resilience layer
// (TSK-011) as the fallback path when FMP is unavailable (US-006 AC).
//
// JSONB conversion: payloads are stored as JSON strings on a column typed
// `@JdbcTypeCode(SqlTypes.JSON)`.  The (de)serialization uses the application
// ObjectMapper (configured by JacksonConfig) so DTO classes stay free of any
// JPA/Hibernate coupling — see also ADR-003 §JSONB rationale.
//
// [^src: design_&_architecture/decisions/ADR-004-fmp-integration.md §Cache layer 24h]
// [^src: design_&_architecture/decisions/ADR-014-fmp-profile-snapshot-ttl.md §Decisione]
// [^src: design_&_architecture/components/backend-components.md §FmpCacheService]
// [^src: management/kanban/.../TSK-010.md §Scope tecnico]
@Service
class FmpCacheService(
    private val financialSnapshotRepository: FmpFinancialSnapshotRepository,
    private val profileSnapshotRepository: FmpProfileSnapshotRepository,
    private val stockRepository: StockRepository,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
    private val fmpCacheProperties: FmpCacheProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Cache-aside read for the four "heavy" FMP statement endpoints.
     *
     * @param ticker uppercased internally; safe to pass any case.
     * @param endpoint one of income-statement / balance-sheet-statement /
     *                 cash-flow-statement / key-metrics (validated by DB CHECK).
     * @param typeRef Jackson TypeReference for the returned list element type
     *                — needed because of generic erasure on `T`.
     * @param fetchFn lambda invoked on cache miss / expired.
     */
    // noRollbackFor: when fetchFn throws FmpUnavailableException / FmpTickerNotFoundException
    // we want the OUTER transaction (e.g. AnalyzeTickerService.analyze @Transactional)
    // to keep going so it can call getStale() for the resilience fallback. Without this,
    // Spring marks the outer tx as rollback-only and commit later fails with
    // UnexpectedRollbackException.
    @Transactional(noRollbackFor = [FmpUnavailableException::class, FmpTickerNotFoundException::class])
    fun <T> getOrFetch(
        ticker: String,
        endpoint: String,
        typeRef: TypeReference<List<T>>,
        fetchFn: () -> List<T>,
    ): CachedPayload<List<T>> {
        require(ticker.isNotBlank()) { "ticker must not be blank" }
        val t = ticker.uppercase()

        val now = Instant.now(clock)
        val existing = financialSnapshotRepository
            .findFirstByTickerAndEndpointOrderByFetchedAtDesc(t, endpoint)

        if (existing != null && isFresh(existing.fetchedAt, now, FINANCIAL_TTL)) {
            log.debug("cache hit financial ticker={} endpoint={} age={}s",
                t, endpoint, Duration.between(existing.fetchedAt, now).seconds)
            return CachedPayload(
                value = objectMapper.readValue(existing.payload, typeRef),
                fetchedAt = existing.fetchedAt,
                stale = false,
            )
        }

        log.debug("cache miss financial ticker={} endpoint={} existing={}",
            t, endpoint, existing?.fetchedAt)
        val fresh = fetchFn()
        val snapshot = FmpFinancialSnapshot(
            ticker = t,
            endpoint = endpoint,
            payload = objectMapper.writeValueAsString(fresh),
            fetchedAt = now,
            isStale = false,
            staleReason = null,
        )
        financialSnapshotRepository.save(snapshot)
        return CachedPayload(value = fresh, fetchedAt = now, stale = false)
    }

    /**
     * Cache-aside read for the FMP profile endpoint (price + meta).
     * TTL from `fmp.cache.profile-ttl-hours` (default 1h, ADR-014).
     * Side effect: upserts the `stocks` row when fetching for an unknown ticker
     * (US-005 "lazy population catalogo").
     */
    @Transactional(noRollbackFor = [FmpUnavailableException::class, FmpTickerNotFoundException::class])
    fun getOrFetchProfile(
        ticker: String,
        fetchFn: () -> ProfileDto,
    ): CachedPayload<ProfileDto> {
        require(ticker.isNotBlank()) { "ticker must not be blank" }
        val t = ticker.uppercase()

        val now = Instant.now(clock)
        val existing = profileSnapshotRepository.findFirstByTickerOrderByFetchedAtDesc(t)

        if (existing != null && isFresh(existing.fetchedAt, now, fmpCacheProperties.profileTtl)) {
            log.debug("cache hit profile ticker={} age={}s",
                t, Duration.between(existing.fetchedAt, now).seconds)
            val dto = existing.rawPayload
                ?.let { objectMapper.readValue(it, ProfileDto::class.java) }
                ?: existing.toDto()
            return CachedPayload(value = dto, fetchedAt = existing.fetchedAt, stale = false)
        }

        log.debug("cache miss profile ticker={} existing={}", t, existing?.fetchedAt)
        val fresh = fetchFn()
        val snapshot = FmpProfileSnapshot(
            ticker = t,
            price = fresh.price?.let { BigDecimal.valueOf(it) },
            marketCap = fresh.marketCap?.let { BigDecimal.valueOf(it) },
            sector = fresh.sector,
            industry = fresh.industry,
            rawPayload = objectMapper.writeValueAsString(fresh),
            fetchedAt = now,
        )

        // Lazy-populate stocks catalog so the FK from snapshot tables resolves.
        upsertStock(t, fresh, now)
        profileSnapshotRepository.save(snapshot)
        return CachedPayload(value = fresh, fetchedAt = now, stale = false)
    }

    /**
     * Read-only fallback: returns the latest snapshot for (ticker, endpoint)
     * regardless of freshness.  Used by the resilience layer (TSK-011) when the
     * upstream call fails — the returned payload is marked stale=true so the
     * caller can stamp the response (US-006 AC).
     *
     * Returns null if no snapshot exists at all (genuine cold cache).
     */
    @Transactional(readOnly = true)
    fun <T> getStale(
        ticker: String,
        endpoint: String,
        typeRef: TypeReference<List<T>>,
    ): CachedPayload<List<T>>? {
        require(ticker.isNotBlank()) { "ticker must not be blank" }
        val t = ticker.uppercase()
        val existing = financialSnapshotRepository
            .findFirstByTickerAndEndpointOrderByFetchedAtDesc(t, endpoint)
            ?: return null
        return CachedPayload(
            value = objectMapper.readValue(existing.payload, typeRef),
            fetchedAt = existing.fetchedAt,
            stale = true,
        )
    }

    private fun isFresh(fetchedAt: Instant, now: Instant, ttl: Duration): Boolean =
        Duration.between(fetchedAt, now) < ttl

    private fun upsertStock(ticker: String, dto: ProfileDto, now: Instant) {
        val existing = stockRepository.findById(ticker).orElse(null)
        if (existing == null) {
            stockRepository.save(
                Stock(
                    ticker = ticker,
                    companyName = dto.companyName,
                    sector = dto.sector,
                    industry = dto.industry,
                    marketCapUsd = dto.marketCap?.let { BigDecimal.valueOf(it) },
                    lastRefreshedAt = now,
                )
            )
        } else {
            // Refresh denormalized fields without overwriting non-null with null.
            existing.companyName = dto.companyName ?: existing.companyName
            existing.sector = dto.sector ?: existing.sector
            existing.industry = dto.industry ?: existing.industry
            existing.marketCapUsd = dto.marketCap?.let { BigDecimal.valueOf(it) } ?: existing.marketCapUsd
            existing.lastRefreshedAt = now
            stockRepository.save(existing)
        }
    }

    private fun FmpProfileSnapshot.toDto(): ProfileDto = ProfileDto(
        symbol = ticker,
        price = price?.toDouble(),
        marketCap = marketCap?.toDouble(),
        sector = sector,
        industry = industry,
    )

    companion object {
        // 24h TTL for the four heavy statements (ADR-004 / US-005 AC).
        val FINANCIAL_TTL: Duration = Duration.ofHours(24)
    }
}

/**
 * Cache-aside result wrapper.  Carrying `fetchedAt` lets the caller stamp
 * `dataSnapshotAt` on the response (US-005 AC) — and `stale` discriminates
 * fresh from getStale-returned fallback (US-006).
 */
data class CachedPayload<T>(
    val value: T,
    val fetchedAt: Instant,
    val stale: Boolean,
)
