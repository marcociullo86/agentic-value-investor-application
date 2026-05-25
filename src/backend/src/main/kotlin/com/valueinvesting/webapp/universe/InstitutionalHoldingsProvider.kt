package com.valueinvesting.webapp.universe

// PORT astratto per il layer 13-F (Institutional Holdings) — implementazione
// concreta arriva con TSK-127 (InstitutionalHoldingsService).
//
// Pattern Ports & Adapters: UniverseScreenerService dipende dall'interfaccia,
// non dall'implementazione. Per non bloccare TSK-126 finche' TSK-127 non e'
// pronto, fornisco un default no-op `NoopInstitutionalHoldingsProvider`. Quando
// TSK-127 atterra, il suo @Component sara' annotato @Primary (o il no-op
// rimosso) per sostituire l'implementazione attiva.
//
// [^src: management/kanban/EP-012-batch-top-value-picks/US-047-universe-screener-service/TSK-126.md §Step 2]
// [^src: management/kanban/EP-012-batch-top-value-picks/US-047-universe-screener-service/TSK-127.md]
interface InstitutionalHoldingsProvider {
    /**
     * Ritorna i ticker presenti nei 13-F filing dei top value fund (Berkshire,
     * Pabrai, etc.). Lista vuota = nessun ticker rilevato o provider non attivo.
     */
    fun thirteenFTickers(): List<UniverseCandidate>
}
