package com.valueinvesting.webapp.api

import com.valueinvesting.webapp.api.model.SummaryVerdictResponse
import com.valueinvesting.webapp.security.UserPrincipal
import com.valueinvesting.webapp.summary.SummaryFacade
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// GET /api/analysis/{ticker}/summary — Riepilogo cross-dominio VI+TA (EP-024,
// US-103, TSK-340).
//
// Auth-aware: il Vary header (Authorization) e' applicato a tutto il subtree
// /api/analysis/* da [AnalysisVaryHeaderFilter] (ADR-011). La cache (TSK-340)
// e' per-user via SummaryCacheKey.userId (passato dal SecurityContext).
//
// Verdetto deterministico (gate VI hardcoded, ADR-030 §3+§5) — l'LLM (1 sola
// call, TSK-339) tocca solo il rationale narrativo, MAI il `summaryVerdict`.
//
// [^src: management/kanban/EP-024-.../US-103-.../TSK-340.md]
// [^src: design_&_architecture/decisions/ADR-030-decision-layer-vi-ta-summary.md §3, §4, §5]
// [^src: wiki/concepts/analysis-api-pipeline.md]
// [^src: wiki/concepts/value-investing-rule-engine.md]
// [^src: wiki/syntheses/ta-vs-vi-decision-layer.md §"La regola sequenziale"]
@RestController
@RequestMapping("/api/analysis")
@Tag(name = "analysis", description = "Riepilogo cross-dominio VI+TA (EP-024 Fase 2)")
class SummaryController(
    private val summaryFacade: SummaryFacade,
) {

    @GetMapping(
        value = ["/{ticker}/summary"],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    @Operation(
        summary = "Riepilogo cross-dominio VI+TA (US-103)",
        description = """
Aggrega verdetto VI (rule engine + MoS + intrinsic value) + Deep Analysis
(Munger inversion) + Technical Analysis (entry-timing + stop + sizing) in una
raccomandazione tipata `ENTER_NOW / WAIT_FOR_SETUP / AVOID / INSUFFICIENT_DATA`.

Gate VI primario HARDCODED (ADR-030 §3+§5):
- viVerdict = RED_DOMINANT  → MAI ENTER_NOW (sempre AVOID).
- deepVerdict = RISCHIO_ESTREMO → MAI ENTER_NOW (override Munger).
- viVerdict = INDETERMINATE_DOMINANT → INSUFFICIENT_DATA.

L'LLM (Claude Opus, 1 sola call) genera solo il `rationale.viSummary/deepSummary/
taSummary`. Il `summaryVerdict` NON e' MAI prodotto dall'LLM. Logging gated
(EP-020) + LlmBudgetGuard (EP-011 TSK-156).

Caching per-user su `(userId, ticker, viSnapshot, taSnapshot, deepSnapshot)`
TTL 24h o invalidazione su nuovo snapshot.

`warningAntiCopart` presente SOLO quando viVerdict = GREEN_DOMINANT AND
taVerdict ∈ {WAIT, ENTRY_UNFAVORABLE} AND summaryVerdict = WAIT_FOR_SETUP
(situazione COPART: VI positivo, TA sfavorevole).
""",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Payload Summary",
                content = [Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = Schema(implementation = SummaryVerdictResponse::class),
                )],
            ),
            ApiResponse(responseCode = "404", description = "Ticker non trovato (FMP)"),
            ApiResponse(responseCode = "503", description = "FMP indisponibile"),
        ],
    )
    fun getSummary(
        @PathVariable ticker: String,
        @AuthenticationPrincipal principal: UserPrincipal?,
    ): ResponseEntity<SummaryVerdictResponse> {
        val payload = summaryFacade.analyze(ticker, principal?.userId)
        return ResponseEntity.ok()
            .header("X-Data-Snapshot-At", payload.evaluatedAt.toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(payload)
    }
}
