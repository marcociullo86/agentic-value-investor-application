package com.valueinvesting.webapp.api

import com.valueinvesting.webapp.api.model.SearchResultList
import com.valueinvesting.webapp.api.model.StockProfile
import com.valueinvesting.webapp.service.SearchService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// GET /api/search?query={q}        — US-001 ricerca free-text
// GET /api/search/{ticker}         — US-001 validazione esistenza ticker
//
// Validazione query/ticker: TUTTO delegato a SearchService.normalize* (require).
//   Razionale: il service è già la single source of truth per la regola di
//   normalizzazione (uppercase prima di FMP) e il charset/length bound. Niente
//   @RequestParam validation lato controller così evitiamo doppia regola che
//   potrebbe disallinearsi. IllegalArgumentException → 400 ProblemDetails via
//   GlobalExceptionHandler.handleIllegalArgument (TSK-011).
//
// Lista vuota → 200 con `items: []` (NON 404 — coerente con OpenAPI:
//   "Lista risultati (anche vuota se nessun match)" §/api/search).
// Ticker inesistente su /api/search/{ticker} → 404 RFC 9457 via
//   FmpTickerNotFoundException → GlobalExceptionHandler.
//
// [^src: design_&_architecture/api/openapi.yaml §/api/search]
// [^src: design_&_architecture/api/openapi.yaml §/api/search/{ticker}]
// [^src: design_&_architecture/decisions/ADR-007-api-contract.md §Status codes]
@RestController
@RequestMapping("/api/search")
class SearchController(
    private val searchService: SearchService,
) {

    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun search(
        @RequestParam(required = true) query: String,
    ): ResponseEntity<SearchResultList> {
        val result = searchService.search(query)
        return ResponseEntity.ok(result)
    }

    @GetMapping("/{ticker}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun validateTicker(
        @PathVariable ticker: String,
    ): ResponseEntity<StockProfile> {
        val profile = searchService.validateTicker(ticker)
        return ResponseEntity.ok(profile)
    }
}
