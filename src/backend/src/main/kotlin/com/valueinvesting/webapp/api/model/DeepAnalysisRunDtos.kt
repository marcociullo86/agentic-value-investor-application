package com.valueinvesting.webapp.api.model

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

// DTOs per l'endpoint asincrono di deep analysis (POST /runs + GET /latest).
//
// DeepAnalysisRunStatusResponse — payload restituito 202 Accepted dal POST
// enqueue: il client riceve subito runId + status corrente (RUNNING o, in
// caso di dedupe, quello della run RUNNING già esistente).
//
// LatestDeepAnalysisResponse — payload del GET /latest: stato + risultato
// completo (su SUCCESS) o info di errore (su FAILED). Quando non esiste
// alcuna run per il ticker, lo status è "NONE" e result/error sono null.

@Schema(name = "DeepAnalysisRunStatusResponse")
data class DeepAnalysisRunStatusResponse(
    val runId: String,
    val ticker: String,
    @Schema(description = "RUNNING | SUCCESS | FAILED")
    val status: String,
    val invokeLlm: Boolean,
)

@Schema(name = "LatestDeepAnalysisResponse")
data class LatestDeepAnalysisResponse(
    val ticker: String,
    @Schema(description = "RUNNING | SUCCESS | FAILED | NONE")
    val status: String,
    @Schema(description = "Run id; null when status=NONE", nullable = true)
    val runId: String?,
    val invokeLlm: Boolean,
    @Schema(nullable = true)
    val requestedAt: Instant?,
    @Schema(nullable = true)
    val completedAt: Instant?,
    @Schema(description = "Full DeepAnalysisResponse; populated only on SUCCESS", nullable = true)
    val result: DeepAnalysisResponse?,
    @Schema(description = "Error info; populated only on FAILED", nullable = true)
    val error: RunError?,
)

@Schema(name = "RunError")
data class RunError(
    @Schema(description = "Reason code aligned with GlobalExceptionHandler (not_found, no_sec_filings, llm_unavailable, embedding_unavailable, internal_error)")
    val reason: String,
    @Schema(nullable = true)
    val message: String?,
)
