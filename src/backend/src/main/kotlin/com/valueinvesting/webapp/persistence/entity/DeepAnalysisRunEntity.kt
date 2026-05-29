package com.valueinvesting.webapp.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

// JPA entity per `deep_analysis_run` (V027).
// Audit trail + result cache della deep analysis async pipeline — 1 row per
// run lanciato via POST /api/analysis/{ticker}/deep/runs.
//
// `status` vincolato a CHECK lato DB (RUNNING|SUCCESS|FAILED).
// `result_json` populated solo su SUCCESS (serializzato DeepAnalysisResponse).
// `error_reason` allineato alle reason di GlobalExceptionHandler (not_found,
// no_sec_filings, llm_unavailable, embedding_unavailable, internal_error).
//
// PK UUID generata application-side (init di `id` con UUID.randomUUID()) per
// evitare round-trip `RETURNING gen_random_uuid()` lato Postgres: la riga
// viene creata a inizio enqueue con status=RUNNING e l'id serve subito al
// caller (esposto al FE come run-id).
//
// [^src: src/backend/src/main/resources/db/migration/V027__deep_analysis_run.sql]
@Entity
@Table(name = "deep_analysis_run")
data class DeepAnalysisRunEntity(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID = UUID.randomUUID(),

    @Column(name = "ticker", length = 16, nullable = false)
    var ticker: String = "",

    @Column(name = "status", length = 20, nullable = false)
    var status: String = "RUNNING",

    @Column(name = "invoke_llm", nullable = false)
    var invokeLlm: Boolean = false,

    @Column(name = "requested_at", nullable = false)
    var requestedAt: Instant = Instant.now(),

    @Column(name = "started_at")
    var startedAt: Instant? = null,

    @Column(name = "completed_at")
    var completedAt: Instant? = null,

    @Column(name = "result_json", columnDefinition = "text")
    var resultJson: String? = null,

    @Column(name = "error_reason", length = 64)
    var errorReason: String? = null,

    @Column(name = "error_message", columnDefinition = "text")
    var errorMessage: String? = null,
)
