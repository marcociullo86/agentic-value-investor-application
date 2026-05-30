package com.valueinvesting.webapp.api

import com.valueinvesting.webapp.api.model.TopPicksPageResponse
import com.valueinvesting.webapp.job.TopPicksManualTrigger
import com.valueinvesting.webapp.service.TopPicksQueryService
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.LocalDate

// REST controller per il batch output Top Value Picks (EP-012, US-050).
// Endpoint pubblico (no auth) per esporre la classifica giornaliera prodotta
// dal job @Scheduled cron 02:00 UTC (TopValuePicksJob, TSK-131).
//
// Drift correction TSK frontmatter: il path canonico per i controller è
// `com.valueinvesting.webapp.api` (vedi AnalysisController, DeepAnalysisController,
// etc.) — il frontmatter del TSK puntava al package legacy
// `com.valueinvesting.controller`, sostituito qui per coerenza.
//
// Gestione errori:
//   - IllegalArgumentException viene mappata a 400 problem+json centralmente
//     da `GlobalExceptionHandler.handleIllegalArgument` (ADR-007 / ADR-012).
//     Nessun @ExceptionHandler locale: riusiamo l'advice esistente.
//
// Cache-Control:
//   - max-age=3600 + public: classifica daily, la run è fissa per la giornata
//     ed è safe servire la copia cache fino al run successivo (cron 02:00 UTC).
//
// [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/api/error/GlobalExceptionHandler.kt §handleIllegalArgument]
// [^src: management/kanban/EP-012-batch-top-value-picks/US-050-endpoint-top-picks/TSK-138.md]
@RestController
@RequestMapping("/api/top-picks")
class TopPicksController(
    private val service: TopPicksQueryService,
    private val manualTrigger: TopPicksManualTrigger,
) {

    // Manual on-demand trigger of TopValuePicksJob. Fire-and-forget: returns
    // 202 Accepted immediately while the job runs in a background thread.
    // Returns 409 Conflict if a run is already in flight (concurrency guard
    // in TopPicksManualTrigger).
    @PostMapping("/run", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun triggerRun(): ResponseEntity<Map<String, Any?>> {
        val result = manualTrigger.trigger()
        return when (result) {
            is TopPicksManualTrigger.TriggerResult.Started -> ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(
                    mapOf(
                        "status" to "started",
                        "startedAt" to result.startedAt.toString(),
                        "message" to "Top Picks batch job avviato. Risultati visibili su /top-picks al termine.",
                    ),
                )
            is TopPicksManualTrigger.TriggerResult.AlreadyRunning -> ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(
                    mapOf(
                        "status" to "already_running",
                        "startedAt" to result.startedAt?.toString(),
                        "message" to "Un run è già in corso. Riprova al termine.",
                    ),
                )
        }
    }

    // Request cooperative cancellation of the in-flight manual run. The job
    // stops at the next ticker boundary and leaves the day's existing picks
    // untouched (run log → ABORTED). Returns 202 if a cancel was registered,
    // 409 if no run is in flight.
    @PostMapping("/run/cancel", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun cancelRun(): ResponseEntity<Map<String, Any?>> {
        return when (val result = manualTrigger.requestCancel()) {
            is TopPicksManualTrigger.CancelResult.Requested -> ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(
                    mapOf(
                        "status" to "cancellation_requested",
                        "startedAt" to result.startedAt?.toString(),
                        "message" to "Richiesta di blocco inviata. Il batch si fermerà al prossimo ticker.",
                    ),
                )
            is TopPicksManualTrigger.CancelResult.NotRunning -> ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(
                    mapOf(
                        "status" to "not_running",
                        "startedAt" to null,
                        "message" to "Nessun run in corso da bloccare.",
                    ),
                )
        }
    }

    // Lightweight liveness probe for the manual run, so the UI can render the
    // right control (Lancia vs Blocca) on mount and detect when a run finishes.
    @GetMapping("/run/status", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun runStatus(): ResponseEntity<Map<String, Any?>> = ResponseEntity.ok()
        // Polled liveness probe (~every 5s): must never be served stale from a
        // browser/proxy cache, or the UI would lag the real run state.
        .cacheControl(CacheControl.noStore())
        .body(
            mapOf(
                "running" to manualTrigger.isRunning(),
                "startedAt" to manualTrigger.lastStartedAt()?.toString(),
            ),
        )

    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun list(
        @RequestParam(required = false) date: String?,
        @RequestParam(required = false) verdict: String?,
        @RequestParam(required = false) sector: String?,
        @RequestParam(name = "min_mos", required = false) minMos: Double?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "30") size: Int,
    ): ResponseEntity<TopPicksPageResponse> {
        require(size in 1..100) { "size must be 1..100" }
        require(page >= 0) { "page must be >= 0" }
        val runDate = parseAndValidateDate(date)

        val response = service.findTopPicks(
            runDate = runDate,
            verdict = verdict,
            sector = sector,
            minMos = minMos,
            page = page,
            size = size,
        )
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
            .body(response)
    }

    private fun parseAndValidateDate(date: String?): LocalDate? {
        if (date.isNullOrBlank()) return null
        val parsed = runCatching { LocalDate.parse(date) }
            .getOrElse { throw IllegalArgumentException("Invalid date format: $date (expected YYYY-MM-DD)") }
        if (parsed.isAfter(LocalDate.now())) {
            throw IllegalArgumentException("date in future: $parsed")
        }
        return parsed
    }
}
