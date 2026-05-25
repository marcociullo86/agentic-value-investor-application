package com.valueinvesting.webapp.universe

import org.springframework.stereotype.Component

// Default no-op per NewsScoutProvider — usato finche' TSK-128 (NewsScoutService)
// non atterra con un @Component @Primary.
//
// Ritorna emptyList(): l'orchestrator UniverseScreenerService gira end-to-end
// con la sola Layer FMP, senza scouting news.
//
// [^src: management/kanban/EP-012-batch-top-value-picks/US-047-universe-screener-service/TSK-126.md §Strategy Ports & Adapters]
@Component
class NoopNewsScoutProvider : NewsScoutProvider {
    override fun scoutTickers(seedTickers: List<String>): List<UniverseCandidate> = emptyList()
}
