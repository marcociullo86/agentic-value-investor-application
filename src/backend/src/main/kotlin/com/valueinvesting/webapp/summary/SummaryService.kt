package com.valueinvesting.webapp.summary

import com.valueinvesting.webapp.api.model.RuleEngineResultResponse
import com.valueinvesting.webapp.api.model.SummaryRationale
import com.valueinvesting.webapp.api.model.SummaryVerdictResponse
import com.valueinvesting.webapp.api.model.TechnicalAnalysisResponse
import com.valueinvesting.webapp.service.AnalyzeTickerService
import com.valueinvesting.webapp.service.DeepAnalysisRunService
import com.valueinvesting.webapp.technicalanalysis.TechnicalAnalysisService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Orchestratore del Riepilogo cross-dominio (EP-024 Fase 2, US-103, TSK-338).
 *
 * Responsabilita':
 *   1. Legge i 3 layer SENZA RICALCOLARLI (US-103 §Endpoint):
 *      - VI: [AnalyzeTickerService.analyze] (rule engine + MoS + DCF + contextFlags).
 *      - Deep: [DeepAnalysisRunService.getLatestAnalysis] (Munger cascade, lazy:
 *        `NOT_INDEXED` non blocca).
 *      - TA: [TechnicalAnalysisService.analyze] (entry-timing + stop + sizing —
 *        Fase 1 EP-024).
 *   2. Riduce ai 4 verdetti tipati [ViVerdict] / [DeepVerdict] / `EntryTimingVerdict`
 *      / `Signal` (MoS) via i 3 reducer pure-function.
 *   3. Applica il gate VI hardcoded ([SummaryVerdictAggregator.aggregate]) per
 *      ottenere il `summaryVerdict` deterministico.
 *   4. Compone una [SummaryVerdictResponse] base **senza** rationale LLM ne'
 *      `wikiCitations` (riempiti da TSK-339): `rationale` qui contiene solo
 *      `decisionPath` deterministico + `*Summary` fallback testuali; le
 *      citazioni sono lista vuota; `warningAntiCopart` calcolato gia' qui
 *      perche' funzione di gating, non di LLM.
 *
 * **Mai LLM in questo servizio** (TSK-338): il `summaryVerdict` e la struttura
 * della response sono completamente deterministici. Il rationale narrativo +
 * RAG e' attaccato a valle dal `SummaryEnrichmentService` (TSK-339), che la
 * facade `SummaryFacade` orchestra dietro al controller (TSK-340).
 *
 * Pattern: stesso stile di [TechnicalAnalysisService] (orchestrator no-LLM su
 * advisors pure-function). Robustezza: chiamate FMP/Deep fallate → degrade
 * controllato (`taVerdict = null`, `deepAnalysisStatus = NOT_AVAILABLE`); la
 * pipeline Summary non puo' rompersi per un guasto downstream (US-103 AC).
 *
 * Kdoc cita [[ta-vs-vi-decision-layer]] §"La regola sequenziale" e
 * [[munger-inversion-rag]] §Cascade Logica (US-103 AC §"Javadoc/kdoc").
 *
 * [^src: management/kanban/EP-024-.../US-103-.../TSK-338.md]
 * [^src: design_&_architecture/decisions/ADR-030-decision-layer-vi-ta-summary.md §3, §5]
 * [^src: wiki/syntheses/ta-vs-vi-decision-layer.md §"La regola sequenziale"]
 * [^src: wiki/concepts/munger-inversion-rag.md §Cascade Logica]
 */
@Service
class SummaryService(
    private val analyzeTickerService: AnalyzeTickerService,
    private val deepAnalysisRunService: DeepAnalysisRunService,
    private val technicalAnalysisService: TechnicalAnalysisService,
    private val viVerdictAggregator: ViVerdictAggregator,
    private val deepVerdictReducer: DeepVerdictReducer,
    private val summaryVerdictAggregator: SummaryVerdictAggregator,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Componi il payload Summary deterministico. La [SummaryVerdictResponse]
     * ritornata e' gia' completa di verdetti tipati + `reentryCondition` +
     * `warningAntiCopart` + `decisionPath`; manca solo l'arricchimento LLM
     * (rationale narrativo + wikiCitations), aggiunto dal `SummaryFacade`
     * (TSK-339+340) via `withRationale(...)`.
     */
    fun composeDeterministic(ticker: String): DeterministicSummary {
        require(ticker.isNotBlank()) { "ticker must not be blank" }
        val t = ticker.uppercase()

        // --- VI (sempre richiesto: il gate VI e' primario) -----------------
        // Eventuali eccezioni qui (FMP indisponibile) propagano: senza VI il
        // Summary non e' calcolabile per design (US-103 §"Endpoint" — "orchestra,
        // non duplica" implica che il VI snapshot DEVE esistere).
        val viResult: RuleEngineResultResponse = analyzeTickerService.analyze(t)

        // --- Deep (lazy: NOT_INDEXED non blocca) ---------------------------
        val deepRead = readDeepVerdict(t)
        val deepStatus = deepRead.status
        val deepVerdict = deepRead.verdict
        val deepSnapshotAt = deepRead.snapshotAt

        // --- TA (best-effort: errore → taVerdict = null) -------------------
        val taResponse: TechnicalAnalysisResponse? = readTechnicalAnalysis(t)
        val taVerdict = taResponse?.entryTimingAdvisor?.verdict
        val reentryCondition = taResponse?.entryTimingAdvisor?.reentryCondition

        // --- Riduzione VI verdict ------------------------------------------
        val viAgg = viVerdictAggregator.aggregate(viResult.signals)

        // --- Gate VI hardcoded ---------------------------------------------
        val summaryVerdict = summaryVerdictAggregator.aggregate(
            SummaryVerdictAggregator.Input(
                viVerdict = viAgg.verdict,
                mosSignal = viResult.mosSignal,
                deepVerdict = deepVerdict,
                taVerdict = taVerdict,
            ),
        )

        // --- decisionPath testuale (sempre deterministico) -----------------
        val decisionPath = buildDecisionPath(viAgg.verdict, deepVerdict, taVerdict, summaryVerdict)

        // --- warningAntiCopart (condizioni esatte US-103 §"Output") --------
        val warningAntiCopart = computeWarningAntiCopart(viAgg.verdict, taVerdict, summaryVerdict)

        // --- Fallback *Summary deterministici (sostituiti dall'LLM in TSK-339)
        val fallbackRationale = SummaryRationale(
            viSummary = buildViFallbackSummary(viAgg, viResult),
            deepSummary = if (deepStatus == DeepAnalysisStatus.AVAILABLE)
                buildDeepFallbackSummary(deepVerdict) else null,
            taSummary = if (taVerdict != null) buildTaFallbackSummary(taVerdict, taResponse) else null,
            decisionPath = decisionPath,
        )

        // reentryCondition esposta SOLO quando WAIT_FOR_SETUP + TA WAIT
        // (US-103 §"Acceptance Criteria"): la condizione tecnica e' parte del
        // contratto solo nel caso COPART.
        val effectiveReentry = if (summaryVerdict == SummaryVerdict.WAIT_FOR_SETUP) reentryCondition else null

        val base = SummaryVerdictResponse(
            ticker = t,
            evaluatedAt = Instant.now(),
            summaryVerdict = summaryVerdict,
            viVerdict = viAgg.verdict,
            deepAnalysisStatus = deepStatus,
            deepVerdict = deepVerdict,
            taVerdict = taVerdict,
            rationale = fallbackRationale,
            reentryCondition = effectiveReentry,
            wikiCitations = emptyList(),
            warningAntiCopart = warningAntiCopart,
        )

        return DeterministicSummary(
            response = base,
            viAggregation = viAgg,
            viResult = viResult,
            taResponse = taResponse,
            deepSnapshotAt = deepSnapshotAt,
        )
    }

    /**
     * Esito strutturato della composizione deterministica: la [response] base
     * + i contesti tipizzati che servono al `SummaryEnrichmentService` (TSK-339)
     * per costruire il prompt LLM senza ri-leggere VI/TA/Deep, + il
     * `deepSnapshotAt` per la cache key di TSK-340 (`completedAt` della run
     * SUCCESS, null se NOT_INDEXED/NOT_AVAILABLE).
     */
    data class DeterministicSummary(
        val response: SummaryVerdictResponse,
        val viAggregation: ViVerdictAggregator.Result,
        val viResult: RuleEngineResultResponse,
        val taResponse: TechnicalAnalysisResponse?,
        val deepSnapshotAt: java.time.Instant?,
    )

    // ------------------------------------------------------------------------
    // Lettura layer downstream — robustezza
    // ------------------------------------------------------------------------

    /** Read result del Deep latest + snapshot timestamp per la cache key. */
    private data class DeepRead(
        val status: DeepAnalysisStatus,
        val verdict: DeepVerdict?,
        /** completedAt della run SUCCESS; null per NOT_INDEXED/NOT_AVAILABLE. */
        val snapshotAt: java.time.Instant?,
    )

    private fun readDeepVerdict(ticker: String): DeepRead {
        val latest = runCatching { deepAnalysisRunService.getLatestAnalysis(ticker) }
            .onFailure { log.warn("DeepAnalysisRunService.getLatestAnalysis({}) failed in Summary: {}", ticker, it.message) }
            .getOrNull() ?: return DeepRead(DeepAnalysisStatus.NOT_AVAILABLE, null, null)

        return when (latest.status) {
            "NONE" -> DeepRead(DeepAnalysisStatus.NOT_INDEXED, null, null)
            "SUCCESS" -> {
                val result = latest.result
                if (result == null) {
                    DeepRead(DeepAnalysisStatus.NOT_AVAILABLE, null, null)
                } else {
                    val verdetto = result.verdict.verdettoClasse
                    val rischio = result.mungerReport?.livelloRischio ?: result.verdict.livelloRischio
                    DeepRead(
                        status = DeepAnalysisStatus.AVAILABLE,
                        verdict = deepVerdictReducer.reduce(verdetto, rischio),
                        // Preferiamo completedAt (rilevante per "snapshot del Deep
                        // run"); fallback a generatedAt del payload se assente.
                        snapshotAt = latest.completedAt ?: result.generatedAt,
                    )
                }
            }
            else -> DeepRead(DeepAnalysisStatus.NOT_AVAILABLE, null, null)
        }
    }

    private fun readTechnicalAnalysis(ticker: String): TechnicalAnalysisResponse? =
        runCatching { technicalAnalysisService.analyze(ticker) }
            .onFailure { log.warn("TechnicalAnalysisService.analyze({}) failed in Summary: {}", ticker, it.message) }
            .getOrNull()

    // ------------------------------------------------------------------------
    // Helpers deterministici (fallback testuali + decisionPath + warning)
    // ------------------------------------------------------------------------

    private fun buildDecisionPath(
        viVerdict: ViVerdict,
        deepVerdict: DeepVerdict?,
        taVerdict: com.valueinvesting.webapp.api.model.EntryTimingVerdict?,
        summary: SummaryVerdict,
    ): String {
        val parts = mutableListOf<String>()
        parts += when (viVerdict) {
            ViVerdict.GREEN_DOMINANT -> "VI gate passed (GREEN dominante)"
            ViVerdict.YELLOW_DOMINANT -> "VI gate ambiguo (YELLOW dominante)"
            ViVerdict.RED_DOMINANT -> "VI gate failed (RED dominante)"
            ViVerdict.INDETERMINATE_DOMINANT -> "VI dati insufficienti"
        }
        parts += when (deepVerdict) {
            DeepVerdict.OK -> "Deep: OK"
            DeepVerdict.WATCHLIST -> "Deep: WATCHLIST"
            DeepVerdict.RISCHIO_ESTREMO -> "Deep: RISCHIO_ESTREMO (override)"
            null -> "Deep: non disponibile"
        }
        parts += when (taVerdict) {
            null -> "TA: non disponibile"
            else -> "TA: $taVerdict"
        }
        parts += "Verdetto finale: $summary"
        return parts.joinToString(" → ") + "."
    }

    private fun computeWarningAntiCopart(
        viVerdict: ViVerdict,
        taVerdict: com.valueinvesting.webapp.api.model.EntryTimingVerdict?,
        summary: SummaryVerdict,
    ): String? {
        val antiCopartGate = viVerdict == ViVerdict.GREEN_DOMINANT &&
            (taVerdict == com.valueinvesting.webapp.api.model.EntryTimingVerdict.WAIT ||
                taVerdict == com.valueinvesting.webapp.api.model.EntryTimingVerdict.ENTRY_UNFAVORABLE) &&
            summary == SummaryVerdict.WAIT_FOR_SETUP
        return if (antiCopartGate) WARNING_ANTI_COPART_TEXT else null
    }

    private fun buildViFallbackSummary(
        viAgg: ViVerdictAggregator.Result,
        viResult: RuleEngineResultResponse,
    ): String {
        val verdettoStr = when (viAgg.verdict) {
            ViVerdict.GREEN_DOMINANT -> "Verdetto fondamentale positivo"
            ViVerdict.YELLOW_DOMINANT -> "Verdetto fondamentale ambiguo"
            ViVerdict.RED_DOMINANT -> "Verdetto fondamentale negativo"
            ViVerdict.INDETERMINATE_DOMINANT -> "Dati fondamentali insufficienti"
        }
        val mosStr = "MoS ${viResult.mosSignal}"
        val ratio = if (viAgg.decisionalAvailable > 0) {
            "${viAgg.greenCount}/${viAgg.decisionalAvailable} ruleId decisionali GREEN"
        } else {
            "decisionali insufficienti"
        }
        return "$verdettoStr: $ratio, $mosStr."
    }

    private fun buildDeepFallbackSummary(deepVerdict: DeepVerdict?): String = when (deepVerdict) {
        DeepVerdict.OK -> "Deep Analysis: nessun red flag Munger."
        DeepVerdict.WATCHLIST -> "Deep Analysis: WATCHLIST, attendere conferme aggiuntive."
        DeepVerdict.RISCHIO_ESTREMO -> "Deep Analysis: RISCHIO_ESTREMO Munger — override del gate VI."
        null -> "Deep Analysis non indicizzata."
    }

    private fun buildTaFallbackSummary(
        taVerdict: com.valueinvesting.webapp.api.model.EntryTimingVerdict,
        taResponse: TechnicalAnalysisResponse?,
    ): String {
        val trendStr = taResponse?.trend?.classification?.let { " — trend $it" } ?: ""
        return "Technical Analysis (entry-timing): $taVerdict$trendStr."
    }

    companion object {
        // Testo del warning anti-COPART (US-103 §"Output" — coerente con la
        // motivazione dell'epica, [[ta-vs-vi-decision-layer]] §"La regola
        // sequenziale" + [[ta-stop-placement-position-sizing]] §"Il caso
        // motivante: COPART"). Lingua = italiano coerente con UI VI.
        const val WARNING_ANTI_COPART_TEXT: String =
            "Verdetto fondamentale positivo ma timing tecnico sfavorevole. Acquistare ora " +
                "rischia uno stop loss prematuro su una tesi VI corretta — situazione COPART. " +
                "Attendere il setup tecnico migliore."
    }
}
