package com.valueinvesting.webapp.api

import com.valueinvesting.webapp.api.model.TechnicalAnalysisResponse
import com.valueinvesting.webapp.technicalanalysis.TechnicalAnalysisService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// GET /api/analysis/{ticker}/technical — payload Technical Analysis (EP-024, US-098..US-100).
//
// Endpoint auth-aware coerente con /api/analysis/{ticker}: Vary header (Authorization)
// applicato dal filtro AnalysisVaryHeaderFilter (ADR-011, EP-006/EP-017).
//
// Cache-aside FMP 24h applicata a monte dal ResilientFmpAdapter (vedi
// TechnicalAnalysisService.fetchOrEmpty). Errori FMP downstream → ProblemDetail
// standard via GlobalExceptionHandler (EP-014/EP-015).
//
// L'equity per il position sizing arriva via query param `?equity=...` e NON
// e' mai persistito server-side (US-100 §"Separazione di responsabilita'").
//
// [^src: management/kanban/EP-024-riepilogo-e-technical-analysis-tab/US-098-pipeline-ta-payload-be/TSK-326.md]
// [^src: wiki/syntheses/ta-entry-timing-stock-detail.md]
// [^src: wiki/concepts/analysis-api-pipeline.md]
@RestController
@RequestMapping("/api/analysis")
@Tag(name = "analysis", description = "Technical Analysis tab payload (EP-024)")
class TechnicalAnalysisController(
    private val technicalAnalysisService: TechnicalAnalysisService,
) {

    @GetMapping(
        value = ["/{ticker}/technical"],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    @Operation(
        summary = "Payload Technical Analysis (US-098..US-100)",
        description = """
Restituisce il payload Technical Analysis per il ticker: 6 blocchi indicatori
(trend deterministico SMA50/200, momentum RSI14 + MACD daily/weekly, volatilita'
ATR14, volume OBV, livelli structural support/resistance, priceContext 52w) +
3 advisor (entry-timing Triple-Screen Elder, stopSuggestion Murphy/Elder,
positionSizing 2%/6% Rule Elder, rewardRiskRatio vs DCF intrinsic value).

LAYER ADVISORY di timing: NON sostituisce il verdetto VI. Il gate VI primario
e' applicato dal Riepilogo (US-103) — non da questo endpoint.

Equity per il position sizing: parametro `equity` query (default 50000). Mai
persistito server-side.
""",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Payload TA",
                content = [Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = Schema(implementation = TechnicalAnalysisResponse::class),
                )],
            ),
            ApiResponse(responseCode = "404", description = "Ticker non trovato (FMP)"),
            ApiResponse(responseCode = "503", description = "FMP indisponibile"),
        ],
    )
    fun getTechnicalAnalysis(
        @PathVariable ticker: String,
        @RequestParam(name = "equity", required = false, defaultValue = "50000.0") equity: Double,
    ): ResponseEntity<TechnicalAnalysisResponse> {
        val payload = technicalAnalysisService.analyze(ticker, equity)
        return ResponseEntity.ok()
            .header("X-Data-Snapshot-At", payload.evaluatedAt.toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(payload)
    }
}
