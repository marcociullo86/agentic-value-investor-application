package com.valueinvesting.webapp.fmp.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

// FMP `/api/v3/search?query=...` response item.
//
// Shape FMP (campioni reali):
//   { "symbol": "AAPL", "name": "Apple Inc.", "currency": "USD",
//     "stockExchange": "NASDAQ Global Select", "exchangeShortName": "NASDAQ" }
//
// A differenza di `/profile/{ticker}` (single-element list per ticker),
// `/search` ritorna 0..N hit per match parziale su symbol+name. Lista vuota
// è risultato legittimo (zero match) e NON deve sollevare
// FmpTickerNotFoundException — l'adapter restituisce `emptyList()`.
//
// Campi extra ignorati via @JsonIgnoreProperties per robustezza ai cambi
// di payload FMP (stessa convenzione di ProfileDto/ScreenedStockDto).
//
// [^src: management/kanban/EP-001-ricerca-e-screening/US-001-ricerca-ticker-simbolo/TSK-002.md §DTO FMP adapter]
// [^src: design_&_architecture/decisions/ADR-004-fmp-integration.md §Adapter pattern]
@JsonIgnoreProperties(ignoreUnknown = true)
data class SearchHitDto(
    val symbol: String? = null,
    val name: String? = null,
    val currency: String? = null,
    val stockExchange: String? = null,
    val exchangeShortName: String? = null,
)
