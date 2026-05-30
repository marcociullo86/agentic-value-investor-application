package com.valueinvesting.webapp.api

import com.valueinvesting.webapp.api.model.DeepAnalysisResponse
import com.valueinvesting.webapp.api.model.DeepAnalysisRunStatusResponse
import com.valueinvesting.webapp.api.model.IngestStatusResponse
import com.valueinvesting.webapp.api.model.LatestDeepAnalysisResponse
import com.valueinvesting.webapp.service.DeepAnalysisRunService
import com.valueinvesting.webapp.service.DeepAnalysisService
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/analysis")
class DeepAnalysisController(
    private val deepAnalysisService: DeepAnalysisService,
    private val deepAnalysisRunService: DeepAnalysisRunService,
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

    // POST /api/analysis/{ticker}/deep/runs — enqueue async deep ANALYSIS run.
    // Ritorna 202 Accepted con run-id + status (RUNNING o, in caso di dedupe,
    // status della run ANALYSIS RUNNING già esistente). Il client deve poi
    // pollare GET /{ticker}/deep/latest per recuperare il risultato persistito.
    // Post EP-011 split (V028) questo endpoint NON scarica/indicizza filing:
    // l'ANALYSIS-with-LLM presuppone un INGEST precedente (POST /deep/ingest).
    @PostMapping("/{ticker}/deep/runs", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun enqueueDeepAnalysis(
        @PathVariable ticker: String,
        @RequestParam(name = "invoke_llm", defaultValue = "false") invokeLlm: Boolean,
    ): ResponseEntity<DeepAnalysisRunStatusResponse> {
        val status = deepAnalysisRunService.enqueueAnalysis(ticker, invokeLlm)
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(status)
    }

    // GET /api/analysis/{ticker}/deep/latest — ultima run ANALYSIS persistita.
    // status=NONE quando non esiste alcuna run ANALYSIS per il ticker.
    // Le run INGEST sono filtrate fuori — vedi GET /deep/ingest/latest.
    @GetMapping("/{ticker}/deep/latest", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getLatestDeepAnalysis(
        @PathVariable ticker: String,
    ): ResponseEntity<LatestDeepAnalysisResponse> {
        val latest = deepAnalysisRunService.getLatestAnalysis(ticker)
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(latest)
    }

    // POST /api/analysis/{ticker}/deep/ingest — enqueue async INGEST run
    // (EP-011 split V028). Scarica filing 10-K/10-Q e indicizza embedding
    // pgvector, idempotente sui blob già indicizzati. Ritorna 202 Accepted.
    // Da chiamare PRIMA di un POST /deep/runs?invoke_llm=true.
    @PostMapping("/{ticker}/deep/ingest", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun enqueueDeepAnalysisIngest(
        @PathVariable ticker: String,
    ): ResponseEntity<DeepAnalysisRunStatusResponse> {
        val status = deepAnalysisRunService.enqueueIngest(ticker)
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(status)
    }

    // GET /api/analysis/{ticker}/deep/ingest/latest — stato dell'ultima run
    // INGEST + summary (filings/chunks/skipped) su SUCCESS.
    @GetMapping("/{ticker}/deep/ingest/latest", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getLatestDeepAnalysisIngest(
        @PathVariable ticker: String,
    ): ResponseEntity<IngestStatusResponse> {
        val latest = deepAnalysisRunService.getLatestIngest(ticker)
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(latest)
    }
}
