package com.valueinvesting.webapp.fmp.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

// FMP `/stable/search-symbol?query=...` response item.
//
// Shape FMP stable (campioni reali):
//   { "symbol": "AAPL", "name": "Apple Inc.", "currency": "USD",
//     "exchangeFullName": "NASDAQ Global Select", "exchange": "NASDAQ" }
//
// I campi Kotlin `stockExchange`/`exchangeShortName` sono nomi storici (v3);
// vengono popolati dai nuovi field JSON via @JsonProperty per evitare rinomi
// downstream nei consumer.
//
// A differenza di `/profile?symbol={ticker}` (single-element list per ticker),
// `/search-symbol` ritorna 0..N hit per match parziale su symbol. Lista vuota
// è risultato legittimo (zero match) e NON deve sollevare
// FmpTickerNotFoundException — l'adapter restituisce `emptyList()`.
//
// Campi extra ignorati via @JsonIgnoreProperties per robustezza ai cambi
// di payload FMP (stessa convenzione di ProfileDto/ScreenedStockDto).
//
// [^src: wiki/concepts/fmp-company-search.md]
// [^src: design_&_architecture/decisions/ADR-004-fmp-integration.md §Adapter pattern]
@JsonIgnoreProperties(ignoreUnknown = true)
data class SearchHitDto(
    val symbol: String? = null,
    val name: String? = null,
    val currency: String? = null,
    @JsonProperty("exchangeFullName") val stockExchange: String? = null,
    @JsonProperty("exchange") val exchangeShortName: String? = null,
)
