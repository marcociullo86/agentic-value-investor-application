package com.valueinvesting.webapp.service

import com.valueinvesting.webapp.domain.GicsSector
import com.valueinvesting.webapp.domain.MarketCapBand

// Criteri di input per SearchService.screen — mappa 1:1 sui query params del
// controller. Tutti i campi sono opzionali (nessun filtro = full universe FMP
// limitato a `limit`). Il controller costruisce questa struttura dopo la
// validazione (limit ∈ [1, 200], enum parsing) — il service riceve dati già
// puliti.
//
// [^src: design_&_architecture/api/openapi.yaml §/api/screener parameters]
// [^src: management/kanban/EP-001-ricerca-e-screening/US-002-screener-parametrico/TSK-005.md §Scope tecnico]
data class ScreenerCriteria(
    val marketCapBands: List<MarketCapBand> = emptyList(),
    val sectors: List<GicsSector> = emptyList(),
    val excludeHardToPredict: Boolean = false,
    val limit: Int = 50,
    val cursor: String? = null,
)
