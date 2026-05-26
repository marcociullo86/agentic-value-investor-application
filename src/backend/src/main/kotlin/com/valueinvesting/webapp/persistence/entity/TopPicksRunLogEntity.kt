package com.valueinvesting.webapp.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// JPA entity per `top_picks_run_log` (V023, TSK-133).
// Audit trail di ogni esecuzione di TopValuePicksJob — 1 row per run.
// `status` vincolato a CHECK lato DB (STARTED|COMPLETED|FAILED|ABORTED).
//
// PK UUID generata application-side (init di `id` con UUID.randomUUID()) per
// evitare round-trip `RETURNING gen_random_uuid()` lato Postgres e ridurre
// latency del primo save (record creato a inizio run con status=STARTED).
//
// [^src: src/backend/src/main/resources/db/migration/V023__top_picks_run_log.sql]
// [^src: management/kanban/EP-012-batch-top-value-picks/US-048-job-notturno-top-picks/TSK-131.md]
@Entity
@Table(name = "top_picks_run_log")
data class TopPicksRunLogEntity(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID = UUID.randomUUID(),

    @Column(name = "run_date", nullable = false)
    var runDate: LocalDate = LocalDate.now(),

    @Column(name = "started_at", nullable = false)
    var startedAt: Instant = Instant.now(),

    @Column(name = "finished_at")
    var finishedAt: Instant? = null,

    @Column(name = "duration_seconds")
    var durationSeconds: Long? = null,

    @Column(name = "tickers_processed", nullable = false)
    var tickersProcessed: Int = 0,

    @Column(name = "tickers_failed", nullable = false)
    var tickersFailed: Int = 0,

    @Column(name = "top30_count", nullable = false)
    var top30Count: Int = 0,

    @Column(name = "top30_tickers", columnDefinition = "text")
    var top30Tickers: String? = null,

    @Column(name = "status", length = 20, nullable = false)
    var status: String = "STARTED",

    @Column(name = "error_message", columnDefinition = "text")
    var errorMessage: String? = null,
)
