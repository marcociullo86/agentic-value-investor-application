package com.valueinvesting.webapp.api

import com.valueinvesting.webapp.api.model.RuleEngineResultResponse
import com.valueinvesting.webapp.service.AnalyzeTickerService
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/analysis")
class AnalysisController(
    private val analyzeTickerService: AnalyzeTickerService,
) {

    @GetMapping("/{ticker}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getAnalysis(@PathVariable ticker: String): ResponseEntity<RuleEngineResultResponse> {
        val result = analyzeTickerService.analyze(ticker)
        return ResponseEntity.ok()
            .header("X-Data-Snapshot-At", result.dataSnapshotAt.toString())
            .header("X-Data-Stale", result.isStale.toString())
            .header(HttpHeaders.VARY, "Origin, Authorization")
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(result)
    }
}
