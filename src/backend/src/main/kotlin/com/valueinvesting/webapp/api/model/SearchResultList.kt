package com.valueinvesting.webapp.api.model

// Risposta JSON di GET /api/search?query=...
//
// Shape allineata STRETTAMENTE a openapi.yaml §components/schemas/SearchResultList:
//   items: SearchResultItem[]   (required, può essere [])
//
// Nota: lo schema OpenAPI corrente NON contiene il campo `query` nella
// response, anche se l'echo della query normalizzata sarebbe utile al client
// per debug / verifica casing. Per restare conformi al contratto pubblicato
// (ADR-007: openapi.yaml = source of truth) NON aggiungiamo qui il campo:
// eventuale enrichment dev'essere preceduto da update OpenAPI + gate Arch.
//
// `items` riusa lo schema SearchResultItem condiviso con GET /api/screener.
//
// [^src: design_&_architecture/api/openapi.yaml §components/schemas/SearchResultList]
data class SearchResultList(
    val items: List<SearchResultItem>,
)
