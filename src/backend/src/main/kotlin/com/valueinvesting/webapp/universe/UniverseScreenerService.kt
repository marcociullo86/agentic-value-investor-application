package com.valueinvesting.webapp.universe

import com.fasterxml.jackson.core.type.TypeReference
import com.valueinvesting.webapp.fmp.FmpAdapter
import com.valueinvesting.webapp.fmp.FmpCacheService
import com.valueinvesting.webapp.fmp.dto.ScreenedStockDto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

// Orchestrator del batch "Top Value Picks" (EP-012, US-047, TSK-126).
//
// PIPELINE
// --------
// Step 1 — FMP base screener: FmpAdapter.screen(exchange="NASDAQ,NYSE",
//   marketCapMoreThan=3B, country="US"), filtro SECTOR_BLACKLIST (Financials +
//   Biotechnology), mapping → UniverseCandidate(source=SCREENER).
// Step 2 — 13-F overlay: InstitutionalHoldingsProvider.thirteenFTickers()
//   (no-op finche' TSK-127 atterra). Errori del provider sono captured per
//   non bloccare l'orchestrator (best-effort).
// Step 3 — News scout: NewsScoutProvider.scoutTickers(top-N seed)
//   (no-op finche' TSK-128 atterra). Stesso pattern best-effort.
// Step 4 — Dedupe per ticker uppercase, priorita' 13F > SCREENER > NEWS_SCOUT
//   (implementata via LinkedHashMap.putIfAbsent nell'ordine richiesto).
// Step 5 — Sort by marketCap desc, cap a universe.capCandidates (default 500).
// Step 6 — Log riassuntivo: runDate + totalCandidates + breakdownBySource +
//   durationMs.
//
// CACHE — Step 1 e' wrappato in `FmpCacheService.getOrFetch` con endpoint
// "company-screener" e pseudo-ticker "ALL" come cache key. TTL target = 6h
// (universe.cache-ttl-hours) ma `getOrFetch` corrente applica TTL fisso 24h:
// limitazione documentata + log warning a startup. Future scope:
// estendere `FmpCacheService.getOrFetch` con parametro TTL opzionale.
//
// RATE LIMITER — Il TSK richiede l'uso del limiter `fmp-batch` separato da
// `fmp-online`. Attualmente esiste solo l'istanza `fmp` in FmpResilienceConfig.
// La chiamata passa attraverso `ResilientFmpAdapter` che gia' applica il
// limiter `fmp` su tutti gli endpoint. Creazione `fmp-batch` rimandata a
// future task (estensione FmpResilienceConfig).
//
// [^src: management/kanban/EP-012-batch-top-value-picks/US-047-universe-screener-service/TSK-126.md]
// [^src: wiki/runbooks/defensive-investor-checklist.md §Universe screening]
@Service
class UniverseScreenerService(
    private val fmpAdapter: FmpAdapter,
    private val fmpCacheService: FmpCacheService,
    private val holdingsProvider: InstitutionalHoldingsProvider,
    private val newsScoutProvider: NewsScoutProvider,
    private val universeProperties: UniverseProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    init {
        if (universeProperties.cacheTtlHours != 24L) {
            log.warn(
                "UniverseScreenerService: universe.cache-ttl-hours={} but FmpCacheService.getOrFetch " +
                    "applies a fixed 24h TTL (FINANCIAL_TTL). Custom TTL requires extending FmpCacheService " +
                    "(future scope). The screener will run with effective 24h TTL.",
                universeProperties.cacheTtlHours,
            )
        }
    }

    /**
     * Esegue la pipeline universe screener e ritorna la lista finale di
     * candidati (max `universe.cap-candidates`, sorted by marketCap desc).
     *
     * Note operative:
     *   - Step 2 (13F) e Step 3 (news scout) sono wrappati in `runCatching`
     *     per garantire che eventuali failure dei provider non blocchino lo
     *     screener base. In caso di errore, l'orchestrator logga warning e
     *     ritorna il solo set FMP.
     *   - Lo Step 1 NON e' wrappato: se FMP e' down, l'errore propaga al
     *     caller (caller responsabile del fallback stale-cache).
     */
    fun screen(): List<UniverseCandidate> {
        val startMs = System.currentTimeMillis()

        // Step 1 — FMP base screener, cache-aside via FmpCacheService.
        val fmpRaw: List<ScreenedStockDto> = fmpCacheService.getOrFetch(
            ticker = "ALL",
            endpoint = "company-screener",
            typeRef = object : TypeReference<List<ScreenedStockDto>>() {},
            fetchFn = {
                fmpAdapter.screen(
                    marketCapMoreThan = universeProperties.marketCapMoreThan,
                    exchange = universeProperties.exchanges,
                    country = universeProperties.country,
                    limit = universeProperties.fmpMaxResults,
                )
            },
        ).value

        // Step 1b — filtro SECTOR_BLACKLIST + mapping → UniverseCandidate.
        val fmpFiltered: List<UniverseCandidate> = fmpRaw
            .filter { dto ->
                val sector = dto.sector ?: ""
                sector !in SECTOR_BLACKLIST
            }
            .mapNotNull { dto ->
                val sym = dto.symbol
                if (sym.isNullOrBlank()) {
                    null
                } else {
                    UniverseCandidate(
                        ticker = sym,
                        source = CandidateSource.SCREENER,
                        marketCapUsd = dto.marketCap?.toLong(),
                        sector = dto.sector,
                        exchange = dto.exchangeShortName ?: dto.exchange,
                        companyName = dto.companyName,
                    )
                }
            }

        // Step 2 — 13-F overlay (best-effort).
        val thirteenF: List<UniverseCandidate> = runCatching {
            holdingsProvider.thirteenFTickers()
        }.getOrElse { ex ->
            log.warn("13-F provider failed, fallback emptyList: {}", ex.message)
            emptyList()
        }

        // Step 3 — News scout su top-N candidati FMP (best-effort).
        val newsScout: List<UniverseCandidate> = runCatching {
            val seed = fmpFiltered
                .take(universeProperties.newsScoutSeedTop)
                .map { it.ticker }
            newsScoutProvider.scoutTickers(seed)
        }.getOrElse { ex ->
            log.warn("News scout failed, fallback emptyList: {}", ex.message)
            emptyList()
        }

        // Step 4 — dedupe ordinato per priorita' (13F > SCREENER > NEWS_SCOUT).
        // LinkedHashMap.putIfAbsent: la prima occorrenza vince; iteriamo nel
        // l'ordine voluto di priorita'.
        val byTicker = LinkedHashMap<String, UniverseCandidate>()
        for (c in thirteenF) byTicker.putIfAbsent(c.ticker.uppercase(), c)
        for (c in fmpFiltered) byTicker.putIfAbsent(c.ticker.uppercase(), c)
        for (c in newsScout) byTicker.putIfAbsent(c.ticker.uppercase(), c)

        // Step 5 — sort by market cap desc + cap.
        val result = byTicker.values
            .sortedByDescending { it.marketCapUsd ?: 0L }
            .take(universeProperties.capCandidates)

        // Step 6 — log finale.
        val durationMs = System.currentTimeMillis() - startMs
        val breakdown: Map<CandidateSource, Int> = result.groupingBy { it.source }.eachCount()
        log.info(
            "UniverseScreener runDate={} totalCandidates={} breakdownBySource={} durationMs={}",
            LocalDate.now(),
            result.size,
            breakdown,
            durationMs,
        )

        return result
    }
}
