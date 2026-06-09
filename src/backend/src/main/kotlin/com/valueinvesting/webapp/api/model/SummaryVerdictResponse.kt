package com.valueinvesting.webapp.api.model

import com.valueinvesting.webapp.summary.DeepAnalysisStatus
import com.valueinvesting.webapp.summary.DeepVerdict
import com.valueinvesting.webapp.summary.SummaryVerdict
import com.valueinvesting.webapp.summary.ViVerdict
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

// DTO Summary cross-dominio — payload del nuovo endpoint
// GET /api/analysis/{ticker}/summary (EP-024 Fase 2, US-103, TSK-340).
//
// Verdetto deterministico (gate VI hardcoded, ADR-030 §3+§5): `summaryVerdict`,
// `viVerdict`, `deepVerdict`, `taVerdict` sono SEMPRE prodotti da pure-function
// Kotlin. L'LLM (1 sola call, TSK-339) genera solo i 3 campi narrativi
// `rationale.viSummary/deepSummary/taSummary` partendo dai dati strutturati.
//
// `wikiCitations` arrivano dalla similarity search RAG sul corpus_kind=WIKI
// (US-103 §"Citazioni RAG cross-dominio", ADR-030 §2).
//
// `warningAntiCopart` e' presente SOLO nelle condizioni esatte specificate da
// US-103 §"Output" (viVerdict GREEN_DOMINANT + taVerdict WAIT/ENTRY_UNFAVORABLE
// + summaryVerdict WAIT_FOR_SETUP); in tutti gli altri casi e' null.
//
// Stile coerente con TechnicalAnalysisResponse (EP-024 Fase 1, US-098): @Schema
// su data class, enum tipati, kdoc esteso.
//
// [^src: management/kanban/EP-024-.../US-103-aggregatore-riepilogo-cross-dominio-be/US-103.md §"Output"]
// [^src: design_&_architecture/decisions/ADR-030-decision-layer-vi-ta-summary.md §3, §5]
// [^src: wiki/syntheses/ta-vs-vi-decision-layer.md §"La regola sequenziale"]
// [^src: wiki/concepts/munger-inversion-rag.md §Cascade Logica]
@Schema(
    name = "SummaryVerdictResponse",
    description = """
Payload del Riepilogo cross-dominio VI + Deep + TA (EP-024 Fase 2, US-103).

Verdetti tipati (`summaryVerdict`, `viVerdict`, `deepVerdict`, `taVerdict`):
prodotti da pure-function Kotlin con tabella di mapping hardcoded (ADR-030 §3+§5).
L'LLM (1 sola call) genera solo i 3 campi narrativi del `rationale`.

Gate VI primario hardcoded: un titolo con `viVerdict = RED_DOMINANT` non puo'
MAI diventare `ENTER_NOW`, indipendentemente da TA. Munger `RISCHIO_ESTREMO`
overrides il gate VI → AVOID.

`warningAntiCopart` presente SOLO quando `viVerdict = GREEN_DOMINANT` AND
`taVerdict ∈ {WAIT, ENTRY_UNFAVORABLE}` AND `summaryVerdict = WAIT_FOR_SETUP`.
""",
)
data class SummaryVerdictResponse(
    val ticker: String,
    val evaluatedAt: Instant,

    /** Verdetto sintetico azionabile — deterministico, MAI LLM. */
    val summaryVerdict: SummaryVerdict,

    /** Verdetto VI aggregato (proporzionale sui ruleId decisionali disponibili — ADR-030 §3). */
    val viVerdict: ViVerdict,

    /** Stato della Deep Analysis: AVAILABLE | NOT_INDEXED | NOT_AVAILABLE (non blocca il Summary). */
    val deepAnalysisStatus: DeepAnalysisStatus,

    /**
     * Verdetto Deep ridotto a 3 segnali (OK / WATCHLIST / RISCHIO_ESTREMO).
     * Null quando `deepAnalysisStatus != AVAILABLE` (US-103 AC).
     */
    @field:Schema(nullable = true)
    val deepVerdict: DeepVerdict?,

    /**
     * Verdetto Triple-Screen TA (US-099). Null quando la TA non e' calcolabile
     * (FMP indisponibile o dati insufficienti — il payload TA degrada ma il
     * Summary continua con un verdetto basato su VI + Deep).
     */
    @field:Schema(nullable = true)
    val taVerdict: EntryTimingVerdict?,

    val rationale: SummaryRationale,

    /**
     * Condizione tecnica di re-entry quando `summaryVerdict = WAIT_FOR_SETUP`
     * AND `taVerdict ∈ {WAIT}` — propagata dall'`EntryTimingAdvisor` (US-099).
     * Null in tutti gli altri casi.
     */
    @field:Schema(nullable = true)
    val reentryCondition: ReentryCondition?,

    /** Citazioni RAG cross-dominio (US-103 §"Citazioni RAG cross-dominio"). */
    val wikiCitations: List<WikiCitation>,

    /**
     * Warning anti-COPART. Presente SOLO quando viVerdict = GREEN_DOMINANT AND
     * taVerdict ∈ {WAIT, ENTRY_UNFAVORABLE} AND summaryVerdict = WAIT_FOR_SETUP
     * (US-103 §"Output"). In tutti gli altri casi null.
     */
    @field:Schema(nullable = true)
    val warningAntiCopart: String?,
)

/**
 * Rationale narrativo prodotto dall'LLM (1 sola call, US-103 §"Citazioni RAG"
 * — TSK-339). Il `decisionPath` e' un riassunto testuale del gate VI applicato
 * (es. "VI gate passed. TA gate: WAIT. Verdetto finale: WAIT_FOR_SETUP."),
 * **deterministico**: NON viene mai prodotto dall'LLM.
 *
 * I 3 campi `viSummary` / `deepSummary` / `taSummary` sono generati dall'LLM
 * partendo dai dati strutturati; quando la chiamata LLM e' frozen / budget
 * esaurito / errore, sono popolati con un fallback deterministico generato in
 * Kotlin (US-103 AC: la pipeline Summary non puo' rompersi per un guasto LLM).
 */
@Schema(name = "SummaryRationale", description = "Rationale narrativo del Riepilogo (LLM per i 3 *Summary, deterministico per decisionPath).")
data class SummaryRationale(
    @field:Schema(description = "Sintesi narrativa del verdetto VI (LLM o fallback deterministico).")
    val viSummary: String,
    @field:Schema(description = "Sintesi narrativa della Deep Analysis (LLM o fallback). Null se deepAnalysisStatus != AVAILABLE.", nullable = true)
    val deepSummary: String?,
    @field:Schema(description = "Sintesi narrativa del verdetto TA (LLM o fallback). Null se taVerdict = null.", nullable = true)
    val taSummary: String?,
    @field:Schema(description = "Riassunto testuale deterministico del gate applicato. Sempre presente.")
    val decisionPath: String,
)

/**
 * Singola citazione wiki cross-dominio (US-103 §"Output"). `id` = slug della
 * pagina (= `wiki_source_id` in `filing_chunks`); `anchor` opzionale; `domain`
 * = `value-investing` o `technical-analysis-trading`.
 */
@Schema(name = "WikiCitation", description = "Citazione di una pagina wiki (id = slug, anchor opzionale, dominio tipato).")
data class WikiCitation(
    @field:Schema(example = "ta-vs-vi-decision-layer")
    val id: String,
    @field:Schema(description = "Ancora opzionale al paragrafo specifico della pagina wiki.", nullable = true)
    val anchor: String?,
    @field:Schema(description = "Dominio della pagina wiki: value-investing | technical-analysis-trading.")
    val domain: String,
)
