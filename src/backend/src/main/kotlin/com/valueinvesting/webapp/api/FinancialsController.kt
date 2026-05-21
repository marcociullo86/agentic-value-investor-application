package com.valueinvesting.webapp.api

import com.valueinvesting.webapp.service.FinancialDataService
import com.valueinvesting.webapp.service.FinancialDataset
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// Endpoint diagnostico per US-004: espone il FinancialDataset raw assemblato.
// Headers X-Data-Snapshot-At / X-Data-Stale come da openapi.yaml §/api/financials/{ticker}.
// [^src: design_&_architecture/api/openapi.yaml §/api/financials/{ticker}]
// [^src: design_&_architecture/components/backend-components.md §FinancialsController]
@RestController
@RequestMapping("/api/financials")
class FinancialsController(
    private val financialDataService: FinancialDataService,
) {

    @GetMapping(value = ["/{ticker}"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getFinancials(@PathVariable ticker: String): ResponseEntity<FinancialDataset> {
        val dataset = financialDataService.getFinancialDataset(ticker)
        return ResponseEntity.ok()
            .header("X-Data-Snapshot-At", dataset.dataSnapshotAt.toString())
            .header("X-Data-Stale", dataset.isStale.toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(dataset)
    }
}
