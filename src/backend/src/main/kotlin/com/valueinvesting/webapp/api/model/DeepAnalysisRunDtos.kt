package com.valueinvesting.webapp.api.model

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

// DTOs per gli endpoint asincroni di deep analysis post-split EP-011 (V028).
// Esistono due famiglie di run che condividono la stessa tabella
// deep_analysis_run e lo stesso executor, distinte dal campo `kind`:
//
//   INGEST   — download filing + indicizzazione embedding (idempotente).
//              Endpoint: POST /deep/ingest, GET /deep/ingest/latest.
//              Risultato: IngestSummary (counts + indexedAt).
//   ANALYSIS — verdetto deterministico + (opt) Munger LLM che riusa gli
//              embedding già prodotti da un INGEST precedente.
//              Endpoint: POST /deep/runs, GET /deep/latest.
//              Risultato: DeepAnalysisResponse.
//
// DeepAnalysisRunStatusResponse — restituito 202 Accepted da entrambi i POST
// enqueue (INGEST o ANALYSIS). `kind` consente al FE di distinguere la
// natura della run senza chiamare il GET corrispondente.
//
// LatestDeepAnalysisResponse — payload del GET /deep/latest (kind=ANALYSIS).
//
// IngestStatusResponse — payload del GET /deep/ingest/latest (kind=INGEST).
// status=NONE quando nessuna run INGEST esiste per il ticker.

@Schema(name = "DeepAnalysisRunStatusResponse")
data class DeepAnalysisRunStatusResponse(
    val runId: String,
    val ticker: String,
    @Schema(description = "INGEST | ANALYSIS")
    val kind: String,
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
    @Schema(description = "Reason code aligned with GlobalExceptionHandler (not_found, no_sec_filings, not_indexed, llm_unavailable, embedding_unavailable, internal_error)")
    val reason: String,
    @Schema(nullable = true)
    val message: String?,
)

// Sintesi del lavoro fatto da una run di tipo INGEST. Serializzata in
// result_json al successo per consentire al GET /deep/ingest/latest di
// restituire i counts senza ri-eseguire alcuna query — è puro reporting.
//
//   filingsTotal     — quanti FilingBlob restituiti da fetchAndCache
//   chunksIndexed    — somma di chunk effettivamente embedded (nuovi)
//   chunksSkipped    — numero di filing per cui indexFiling ha skippato
//                      (countByFilingBlobId>0, embedding ricalcolato evitato)
//   indexedAt        — istante di completamento ingest (≈ now() del SUCCESS)
@Schema(name = "IngestSummary")
data class IngestSummary(
    val filingsTotal: Int,
    val chunksIndexed: Int,
    @Schema(description = "Numero di filing già indicizzati che sono stati saltati")
    val chunksSkipped: Int,
    @Schema(nullable = true)
    val indexedAt: Instant?,
)

@Schema(name = "IngestStatusResponse")
data class IngestStatusResponse(
    val ticker: String,
    @Schema(description = "RUNNING | SUCCESS | FAILED | NONE")
    val status: String,
    @Schema(description = "Run id; null when status=NONE", nullable = true)
    val runId: String?,
    @Schema(nullable = true)
    val requestedAt: Instant?,
    @Schema(nullable = true)
    val completedAt: Instant?,
    @Schema(description = "Ingest summary; populated only on SUCCESS", nullable = true)
    val summary: IngestSummary?,
    @Schema(description = "Error info; populated only on FAILED", nullable = true)
    val error: RunError?,
)
