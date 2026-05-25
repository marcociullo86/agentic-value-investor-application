package com.valueinvesting.webapp.api

import com.valueinvesting.webapp.api.model.DeepAnalysisResponse
import com.valueinvesting.webapp.service.DeepAnalysisService
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/analysis")
class DeepAnalysisController(
    private val deepAnalysisService: DeepAnalysisService,
) {

    @GetMapping("/{ticker}/deep", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getDeepAnalysis(
        @PathVariable ticker: String,
        @RequestParam(name = "invoke_llm", defaultValue = "false") invokeLlm: Boolean,
    ): ResponseEntity<DeepAnalysisResponse> {
        val result = deepAnalysisService.analyze(ticker, invokeLlm)
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(result)
    }
}
