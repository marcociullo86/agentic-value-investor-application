package com.valueinvesting.webapp.universe

// PORT astratto per il layer News Scout — implementazione concreta arriva con
// TSK-128 (NewsScoutService).
//
// Stessa logica del PORT InstitutionalHoldingsProvider: default no-op per
// permettere a UniverseScreenerService di compilare/girare end-to-end con
// la sola Layer FMP fino a quando TSK-128 fornisce override.
//
// [^src: management/kanban/EP-012-batch-top-value-picks/US-047-universe-screener-service/TSK-126.md §Step 3]
// [^src: management/kanban/EP-012-batch-top-value-picks/US-047-universe-screener-service/TSK-128.md]
interface NewsScoutProvider {
    /**
     * Scansiona news recenti sui `seedTickers` (top-200 candidati post-screener)
     * e ritorna candidati supplementari (es. ticker citati frequentemente con
     * sentiment value-friendly). Lista vuota = nessun candidato extra o
     * provider non attivo.
     */
    fun scoutTickers(seedTickers: List<String>): List<UniverseCandidate>
}
