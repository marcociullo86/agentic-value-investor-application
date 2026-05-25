package com.valueinvesting.webapp.universe

import org.springframework.stereotype.Component

// Default no-op per InstitutionalHoldingsProvider — usato finche' TSK-127
// (InstitutionalHoldingsService) non atterra con un @Component @Primary.
//
// Ritorna emptyList(): l'orchestrator UniverseScreenerService gira end-to-end
// con la sola Layer FMP (Step 1 + dedupe + cap), senza overlay 13-F.
//
// [^src: management/kanban/EP-012-batch-top-value-picks/US-047-universe-screener-service/TSK-126.md §Strategy Ports & Adapters]
@Component
class NoopInstitutionalHoldingsProvider : InstitutionalHoldingsProvider {
    override fun thirteenFTickers(): List<UniverseCandidate> = emptyList()
}
