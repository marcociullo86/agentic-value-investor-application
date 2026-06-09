package com.valueinvesting.webapp.summary

import com.github.benmanes.caffeine.cache.Cache
import com.valueinvesting.webapp.api.model.SummaryVerdictResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Facade che orchestrazione (TSK-338 deterministico + TSK-339 LLM/RAG +
 * TSK-340 caching) per il Summary cross-dominio (EP-024 / US-103). Singolo
 * entry point usato dal [com.valueinvesting.webapp.api.SummaryController].
 *
 * Sequenza:
 *   1. [SummaryService.composeDeterministic] → verdetti tipati + reentryCondition
 *      + warningAntiCopart + decisionPath + rationale fallback (gia' pronto) +
 *      snapshot timestamp dei 3 layer.
 *   2. Costruzione [SummaryCacheKey] dai snapshot timestamp + userId.
 *   3. Cache lookup: HIT → ritorna il payload cacheato (LLM/RAG gia' fatti).
 *   4. MISS → [SummaryRationaleService.enrich] (1 sola call LLM gated + budget,
 *      degrada a fallback) + [SummaryWikiCitationsService.fetchCitations]
 *      (similarity search RAG, degrada a lista vuota) → put in cache.
 *
 * Invariante critica: il `summaryVerdict` e' calcolato in [1] e NON viene mai
 * piu' toccato; gli step [3] e [4] sono additive-only sul payload base.
 *
 * Invalidazione cache (US-103 §"Caching"): la chiave include i snapshot di
 * VI / TA / Deep — un nuovo snapshot (nuova run di Deep, nuova analisi VI)
 * produce una chiave nuova → cache miss naturale. TTL fisso 24h come fallback
 * (vedi [SummaryCacheConfig]).
 *
 * [^src: management/kanban/EP-024-.../US-103-.../TSK-339.md]
 * [^src: management/kanban/EP-024-.../US-103-.../TSK-340.md]
 * [^src: management/kanban/EP-024-.../US-103-.../US-103.md §"Caching"]
 */
@Service
class SummaryFacade(
    private val summaryService: SummaryService,
    private val rationaleService: SummaryRationaleService,
    private val citationsService: SummaryWikiCitationsService,
    private val summaryCache: Cache<SummaryCacheKey, SummaryVerdictResponse>,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Calcola (o serve da cache) il payload Summary completo per il ticker.
     * Mai eccezioni dai passi LLM/RAG: degradano a fallback deterministico /
     * lista vuota. La compose deterministica (`step 1`) e' sempre eseguita —
     * per costruire la cache key e per garantire freschezza del verdetto.
     *
     * @param ticker  ticker (uppercased internamente).
     * @param userId  user UUID per la cache per-user (null = anonymous). La
     *                separazione e' necessaria perche' il VI snapshot dipende
     *                dall'override DCF user-scoped (DcfOverrideRepository).
     */
    fun analyze(ticker: String, userId: UUID?): SummaryVerdictResponse {
        val det = summaryService.composeDeterministic(ticker)
        val base = det.response

        // Cache key dai snapshot timestamp (US-103 §"Caching", vedi
        // [SummaryCacheKey]). TA snapshot non incluso perche' cache-aside
        // sopra la stessa cache FMP 24h (ADR-030 §1): VI snapshot e' proxy
        // adeguato della freschezza degli input TA entro la finestra.
        val key = SummaryCacheKey(
            userId = userId,
            ticker = base.ticker,
            viSnapshotAt = det.viResult.dataSnapshotAt,
            deepSnapshotAt = det.deepSnapshotAt,
        )

        val cached = summaryCache.getIfPresent(key)
        if (cached != null) {
            log.debug("Summary cache HIT ticker={} userId={} key={}", base.ticker, userId, key)
            return cached
        }

        log.debug("Summary cache MISS ticker={} userId={} key={}", base.ticker, userId, key)

        // Step 2: LLM rationale (1 call, gated + budget; degrada a fallback).
        val enrichedRationale = rationaleService.enrich(
            ticker = base.ticker,
            det = det,
            fallback = base.rationale,
        )

        // Step 3: RAG citations (degrada a lista vuota su errore).
        val citations = citationsService.fetchCitations(
            viVerdict = base.viVerdict,
            deepVerdict = base.deepVerdict,
            taVerdict = base.taVerdict,
        )

        val enriched = base.copy(
            rationale = enrichedRationale,
            wikiCitations = citations,
        )
        summaryCache.put(key, enriched)
        return enriched
    }
}
