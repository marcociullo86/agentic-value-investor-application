package com.valueinvesting.webapp.api

import com.valueinvesting.webapp.api.model.ScreenerResultPage
import com.valueinvesting.webapp.domain.GicsSector
import com.valueinvesting.webapp.domain.MarketCapBand
import com.valueinvesting.webapp.service.ScreenerCriteria
import com.valueinvesting.webapp.service.SearchService
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// GET /api/screener — US-002.
//
// Validazione query params:
//   - marketCap[]: enum MarketCapBand (Spring lo parse direttamente; valori non
//                  validi → MethodArgumentTypeMismatchException → 400 via
//                  GlobalExceptionHandler IllegalArgumentException handler).
//   - sector[]:    enum GicsSector (idem).
//   - limit:       Int 1..200 via jakarta.validation @Min/@Max + @Validated.
//                  Violazione → ConstraintViolationException → 400.
//
// Lista vuota → 200 con `items: []` (NON 404 — vedi DoD).
// Errori upstream FMP → 503 ProblemDetails via GlobalExceptionHandler.
//
// [^src: design_&_architecture/api/openapi.yaml §/api/screener]
// [^src: management/kanban/EP-001-ricerca-e-screening/US-002-screener-parametrico/TSK-005.md §ScreenerController]
@RestController
@RequestMapping("/api/screener")
@Validated
class ScreenerController(
    private val searchService: SearchService,
) {

    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun screen(
        @RequestParam(required = false) marketCap: List<MarketCapBand>?,
        @RequestParam(required = false) sector: List<GicsSector>?,
        @RequestParam(required = false, defaultValue = "false") excludeHardToPredict: Boolean,
        @RequestParam(required = false, defaultValue = "50")
        @Min(value = 1, message = "limit must be >= 1")
        @Max(value = 200, message = "limit must be <= 200")
        limit: Int,
        @RequestParam(required = false) cursor: String?,
    ): ResponseEntity<ScreenerResultPage> {
        val criteria = ScreenerCriteria(
            marketCapBands = marketCap.orEmpty(),
            sectors = sector.orEmpty(),
            excludeHardToPredict = excludeHardToPredict,
            limit = limit,
            cursor = cursor,
        )
        val page = searchService.screen(criteria)
        return ResponseEntity.ok(page)
    }
}
