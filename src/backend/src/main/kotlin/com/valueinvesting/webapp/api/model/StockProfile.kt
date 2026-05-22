package com.valueinvesting.webapp.api.model

// Risposta JSON di GET /api/search/{ticker}. Shape allineata strettamente a
// openapi.yaml §components/schemas/StockProfile:
//   ticker, companyName              required
//   sector, industry, marketCapUsd,
//   currentPrice, dataSnapshotAt     nullable
//
// `dataSnapshotAt` riflette il `fetchedAt` del FmpProfileSnapshot riusato dalla
// cache (US-005 AC "timestamp dati al"). Per il caso US-001 il caller —
// SearchService.validateTicker — popola da CachedPayload.fetchedAt restituito
// dal FmpCacheService.getOrFetchProfile (TSK-010).
//
// Nota di non-riuso: NON riusiamo `SearchResultItem` per /api/search/{ticker}
// perché lo schema OpenAPI espone campi differenti (industry/currentPrice/
// dataSnapshotAt presenti qui, marketCapUsd presente in entrambi ma con
// semantica di "profilo singolo" qui vs "candidato lista" nello SearchResultItem).
//
// [^src: design_&_architecture/api/openapi.yaml §components/schemas/StockProfile]
// [^src: design_&_architecture/decisions/ADR-007-api-contract.md §Status codes]
data class StockProfile(
    val ticker: String,
    val companyName: String,
    val sector: String? = null,
    val industry: String? = null,
    val marketCapUsd: Double? = null,
    val currentPrice: Double? = null,
    val dataSnapshotAt: java.time.Instant? = null,
)
