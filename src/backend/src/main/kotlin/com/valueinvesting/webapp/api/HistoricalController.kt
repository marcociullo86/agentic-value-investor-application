package com.valueinvesting.webapp.api

import com.valueinvesting.webapp.api.model.HistoricalSeries
import com.valueinvesting.webapp.service.HistoricalSeriesService
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// GET /api/historical/{ticker} — serie storica decennale ricavi + utile netto
// per US-015 (grafico).
//
// Headers:
//   - `X-Data-Snapshot-At`: ISO-8601 di HistoricalSeries.dataSnapshotAt, coerente
//      con FinancialsController per la convention "timestamp dati al" (US-005).
//   - `Cache-Control: no-store`: il payload riflette uno snapshot lato server;
//      il browser/CDN non deve cachare risposte stale.
//
// Validazione ticker: delegata al service (HistoricalSeriesService) che
// uppercases e usa la regex centralizzata di FmpCacheService (`require`
// ticker.isNotBlank). Il pattern charset `^[A-Z0-9.\-]+$` viene applicato
// nel SearchService per la ricerca free-text; qui un ticker invalido
// upstream (es. spazio) viene rifiutato da FmpAdapter come 404 / dal service
// come IllegalArgumentException (mapped 400 da GlobalExceptionHandler). Non
// duplichiamo la regex per evitare disallineamento (stessa logica di
// FinancialsController).
//
// Errori (via GlobalExceptionHandler):
//   - 404 FmpTickerNotFoundException ProblemDetails (ticker non esiste su FMP)
//   - 503 FmpUnavailableException    ProblemDetails (FMP unavailable, no stale cache)
//   - 400 IllegalArgumentException   ProblemDetails (ticker blank/charset)
//
// [^src: design_&_architecture/api/openapi.yaml §/api/historical/{ticker}]
// [^src: design_&_architecture/components/backend-components.md §HistoricalSeriesService]
// [^src: management/kanban/EP-005-dashboard-traffic-light-moat/US-015-grafici-storici/TSK-023.md §HistoricalController]
@RestController
@RequestMapping("/api/historical")
class HistoricalController(
    private val historicalSeriesService: HistoricalSeriesService,
) {

    @GetMapping(value = ["/{ticker}"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getHistorical(@PathVariable ticker: String): ResponseEntity<HistoricalSeries> {
        val series = historicalSeriesService.getSeries(ticker)
        return ResponseEntity.ok()
            .header("X-Data-Snapshot-At", series.dataSnapshotAt.toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(series)
    }
}
