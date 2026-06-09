package com.valueinvesting.webapp.api

import com.valueinvesting.webapp.api.model.BacktestResponse
import com.valueinvesting.webapp.backtest.BacktestEngine
import com.valueinvesting.webapp.backtest.BacktestService
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

// GET /api/analysis/{ticker}/backtest — backtest per-ticker della strategia
// EP-024 vs baselines VI_ONLY + BUY_AND_HOLD (EP-024 Fase 3, US-105, TSK-348).
//
// Verifica storica del round-trip "compra a sconto + nel momento giusto →
// rivendi": misura se il layer di timing TA aggiunge soldi rispetto al
// "comprare appena il titolo era a sconto" (timingEdgePct).
//
// Disciplina point-in-time OBBLIGATORIA (US-105 §"Ricostruzione point-in-time"):
//   - Fondamentali filtrati per `filingDate`/`acceptedDate` ≤ t.
//   - Indicatori TA calcolati solo su EOD ≤ t.
//   - Verdetto a t: mapping deterministico US-103 / ADR-030, gate VI primario
//     hardcoded.
//
// Vincoli (US-105 §"Vincoli di scope"):
//   - Solo single ticker (no universo / Top Picks — survivorship bias).
//   - Calcolo deterministico pure-function Kotlin, no LLM.
//   - `equity` opzionale, MAI persistita server-side.
//   - `caveats` SEMPRE presente (lookAheadResidual, singleTicker, notPortfolioPerformance).
//   - Edge `INSUFFICIENT_HISTORY` esplicito, mai finestra parziale silenziosa.
//
// Caching: per-chiave `(ticker, years, horizonMonths)` (Caffeine, TTL 24h,
// `equity` ESCLUSA — coerente con la sua semantica non-persistita).
//
// [^src: management/kanban/EP-024-.../US-105-.../TSK-348.md]
// [^src: design_&_architecture/decisions/ADR-030-decision-layer-vi-ta-summary.md §1]
// [^src: wiki/syntheses/ta-vs-vi-decision-layer.md]
// [^src: wiki/concepts/analysis-api-pipeline.md]
@RestController
@RequestMapping("/api/analysis")
@Tag(name = "analysis", description = "Backtest per-ticker (EP-024 Fase 3)")
class BacktestController(
    private val backtestService: BacktestService,
) {

    @GetMapping(
        value = ["/{ticker}/backtest"],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    @Operation(
        summary = "Backtest per-ticker (EP-024 US-105)",
        description = """
Ricostruisce il verdetto EP-024 (ENTER_NOW / WAIT_FOR_SETUP / AVOID) su punti
storici con disciplina point-in-time (filingDate per i fondamentali, EOD ≤ t
per gli indicatori TA) e simula il round-trip completo (entry → exit alla prima
tra VI_TARGET / STOP_HIT / HORIZON). Confronta contro 2 baseline:

  - VI_ONLY — entra ad ogni `t` con gate VI positivo, ignorando il timing TA
    (risponde a "comprare appena era a sconto bastava?").
  - BUY_AND_HOLD — trade unico sulla finestra.

`timingEdgePct` = avgReturnPct(EP024) − avgReturnPct(VI_ONLY) e' la metrica
chiave che giustifica (o smentisce) il layer di timing. Label: POSITIVE_EDGE
(> +2pp), NEUTRAL (entro ±2pp), NEGATIVE_EDGE (< -2pp).

Storico insufficiente → status = INSUFFICIENT_HISTORY con
`insufficientHistoryReason` esplicito; NESSUN backtest parziale silenzioso.

`caveats` SEMPRE presente: lookAheadResidual (FMP serve fondamentali
ristrutturati), singleTicker, notPortfolioPerformance.

`equity` opzionale (default 50000): non e' MAI persistita server-side e NON
e' parte della chiave di cache. E' un metadato del DTO per il FE che voglia
derivare il dollar amount lato client. Il backtest stesso opera in % di
rendimento, non in valore assoluto.

Caching: per-chiave (ticker, years, horizonMonths), TTL 24h.
""",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Payload backtest (status = OK o INSUFFICIENT_HISTORY)",
                content = [Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = Schema(implementation = BacktestResponse::class),
                )],
            ),
            ApiResponse(responseCode = "400", description = "Parametri non validi (es. horizonMonths fuori da {1,3,6,12})"),
            ApiResponse(responseCode = "404", description = "Ticker non trovato (FMP)"),
            ApiResponse(responseCode = "503", description = "FMP indisponibile"),
        ],
    )
    fun getBacktest(
        @PathVariable ticker: String,
        @RequestParam(name = "years", required = false, defaultValue = "5") years: Int,
        @RequestParam(name = "horizonMonths", required = false, defaultValue = "6") horizonMonths: Int,
        @Suppress("UNUSED_PARAMETER")
        @RequestParam(name = "equity", required = false) equity: Double?,
    ): ResponseEntity<BacktestResponse> {
        // `equity` deliberatamente non passata al servizio: il calcolo del
        // backtest e' percentuale (returnPct, totalReturnPct, timingEdgePct).
        // Il parametro e' accettato per coerenza di contratto con /technical
        // e per consentire al FE di esporre il valore assoluto, ma NON entra
        // nel calcolo BE ne nella chiave di cache (US-105 §"Vincoli di scope":
        // equity mai persistita).
        validate(years, horizonMonths)
        val payload = backtestService.backtest(ticker, years, horizonMonths)
        return ResponseEntity.ok()
            .header("X-Data-Snapshot-At", payload.evaluatedAt.toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(payload)
    }

    /**
     * Valida i query params PRIMA di toccare il servizio: clamps deterministici
     * coerenti con BacktestEngine.MIN_YEARS / MAX_YEARS / ALLOWED_HORIZONS.
     */
    private fun validate(years: Int, horizonMonths: Int) {
        require(years in BacktestEngine.MIN_YEARS..BacktestEngine.MAX_YEARS) {
            "years must be in [${BacktestEngine.MIN_YEARS}..${BacktestEngine.MAX_YEARS}], got $years"
        }
        require(horizonMonths in BacktestEngine.ALLOWED_HORIZONS) {
            "horizonMonths must be one of ${BacktestEngine.ALLOWED_HORIZONS}, got $horizonMonths"
        }
    }
}
