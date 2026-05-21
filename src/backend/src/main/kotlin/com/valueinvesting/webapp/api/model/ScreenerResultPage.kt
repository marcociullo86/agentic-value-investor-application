package com.valueinvesting.webapp.api.model

// Risposta JSON di GET /api/screener. Shape allineata a
// openapi.yaml §components/schemas/ScreenerResultPage:
//   items: SearchResultItem[]   (required, può essere [])
//   nextCursor: string | null   (per paginazione opaca; null = ultima pagina)
//
// `items` riusa lo schema SearchResultItem condiviso con GET /api/search.
//
// [^src: design_&_architecture/api/openapi.yaml §components/schemas/ScreenerResultPage]
data class ScreenerResultPage(
    val items: List<SearchResultItem>,
    val nextCursor: String? = null,
)

// SearchResultItem: ticker + companyName obbligatori, sector e marketCapUsd
// nullable (FMP può omettere). Allineato a openapi.yaml §components/schemas.
//
// Nota: campi `price` / `currency` previsti dallo spec TSK-005 sono stati
// volutamente esclusi qui per restare strettamente conformi al contratto
// OpenAPI già pubblicato (US-001 e US-002 condividono SearchResultItem). Un
// eventuale enrichment richiede prima un aggiornamento dell'openapi.yaml +
// gate Arch — gap potenziale tracciato nel report di handoff.
//
// [^src: design_&_architecture/api/openapi.yaml §components/schemas/SearchResultItem]
data class SearchResultItem(
    val ticker: String,
    val companyName: String,
    val sector: String? = null,
    val marketCapUsd: Double? = null,
)
