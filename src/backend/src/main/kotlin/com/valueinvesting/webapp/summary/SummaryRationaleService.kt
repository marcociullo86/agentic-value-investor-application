package com.valueinvesting.webapp.summary

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.valueinvesting.webapp.api.model.RuleEngineResultResponse
import com.valueinvesting.webapp.api.model.SummaryRationale
import com.valueinvesting.webapp.api.model.TechnicalAnalysisResponse
import com.valueinvesting.webapp.llm.AnthropicClient
import com.valueinvesting.webapp.llm.LlmBudgetGuard
import com.valueinvesting.webapp.llm.LlmCostCounterService
import com.valueinvesting.webapp.llm.LlmException
import com.valueinvesting.webapp.llm.LlmFrozenException
import com.valueinvesting.webapp.llm.LlmInteractionLogger
import com.valueinvesting.webapp.llm.LlmRequest
import com.valueinvesting.webapp.llm.LlmResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * Genera il rationale narrativo del Riepilogo (EP-024 / US-103 / TSK-339) con
 * **una sola call LLM** che produce i 3 campi `rationale.viSummary`,
 * `rationale.deepSummary`, `rationale.taSummary`.
 *
 * Invariante architetturale (US-103 §"Citazioni RAG cross-dominio", ADR-030 §5):
 *   - L'LLM NON produce mai il `summaryVerdict` (ne' gli altri verdetti tipati).
 *     Il prompt riceve i verdetti **deterministici** in input come fatti dati;
 *     l'LLM ha solo il compito di tradurli in linguaggio naturale.
 *   - Variare solo il testo del prompt (mantenendo i verdetti) NON cambia il
 *     `summaryVerdict`: e' garantito dal flusso (rationale e' attaccato DOPO
 *     che il gate hardcoded ha gia' calcolato `summaryVerdict` in TSK-338).
 *
 * Pattern LLM gated + budget (riuso EP-011 TSK-156 / EP-020 US-088):
 *   - [LlmBudgetGuard.checkOrThrow] pre-call → fail-fast con [LlmFrozenException].
 *   - [LlmInteractionLogger] gated via `deep-analysis.llm.log-interactions`.
 *   - [LlmCostCounterService.recordCall] post-call → contabilizzazione in
 *     `llm_cost_tracking` (telemetria failure-tolerant: non rompe la response).
 *
 * Robustezza: errori LLM (frozen, network, JSON malformato, eccezione qualsiasi)
 * → fallback testuali deterministici (i `*Summary` gia' calcolati da
 * [SummaryService.composeDeterministic]). La pipeline Summary non puo' rompersi
 * per un guasto LLM (US-103 AC).
 *
 * [^src: management/kanban/EP-024-.../US-103-.../TSK-339.md]
 * [^src: design_&_architecture/decisions/ADR-030-decision-layer-vi-ta-summary.md §5]
 * [^src: design_&_architecture/decisions/ADR-019-llm-cost-budget-telemetry.md §2,§6]
 */
@Service
class SummaryRationaleService(
    private val anthropicClient: AnthropicClient,
    private val llmBudgetGuard: LlmBudgetGuard,
    private val llmCostCounterService: LlmCostCounterService,
    private val objectMapper: ObjectMapper,
    private val llmInteractionLogger: LlmInteractionLogger = LlmInteractionLogger(),
    @Value("\${anthropic.model:claude-opus-4-8}") private val configuredModel: String = "claude-opus-4-8",
    @Value("\${summary.rationale.max-tokens:1500}") private val maxTokens: Int = 1500,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Genera il rationale narrativo (3 *Summary) per il Summary del ticker.
     * Il `decisionPath` viene preservato come passato in input (e' deterministico,
     * mai LLM).
     *
     * @param fallback rationale fallback gia' calcolato da [SummaryService]:
     *                 viene ritornato cosi' com'e' su qualsiasi errore LLM.
     */
    fun enrich(
        ticker: String,
        det: SummaryService.DeterministicSummary,
        fallback: SummaryRationale,
    ): SummaryRationale {
        // Pre-call gate: budget freeze short-circuit (ADR-019 §6).
        try {
            llmBudgetGuard.checkOrThrow()
        } catch (_: LlmFrozenException) {
            log.info("Summary rationale LLM frozen for ticker={}: returning deterministic fallback", ticker)
            return fallback
        }

        val systemPrompt = SYSTEM_PROMPT
        val userPrompt = buildUserPrompt(ticker, det)
        val request = LlmRequest(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            maxTokens = maxTokens,
        )

        val start = System.currentTimeMillis()
        val response: LlmResponse = try {
            anthropicClient.complete(request)
        } catch (ex: LlmException) {
            log.warn("Summary rationale LLM call failed for ticker={}: {}", ticker, ex.message)
            return fallback
        } catch (ex: Exception) {
            // Defensive: Resilience4j RequestNotPermitted/CallNotPermitted/altre runtime
            // → mai bloccare il Summary. Le eccezioni LLM canoniche tipizzate sono in
            // LlmException; tutto il resto degrada al fallback.
            log.warn("Summary rationale LLM unexpected error for ticker={}: {}", ticker, ex.message)
            return fallback
        }
        val latencyMs = (System.currentTimeMillis() - start).toInt()

        // Telemetria + cost tracking (best-effort, non rilancia mai).
        llmCostCounterService.recordCall(
            model = response.model.ifBlank { configuredModel },
            endpoint = TELEMETRY_ENDPOINT,
            purpose = TELEMETRY_PURPOSE,
            inputTokens = response.inputTokens,
            outputTokens = response.outputTokens,
            latencyMs = latencyMs,
            ticker = ticker,
        )
        llmInteractionLogger.log(
            "summary-rationale:$ticker", systemPrompt, userPrompt, response.content, latencyMs.toLong(),
        )

        // Parse JSON strutturato; su qualsiasi errore di parsing, fallback.
        val parsed = parseResponse(response.content)
            ?: run {
                log.warn("Summary rationale LLM produced unparseable JSON for ticker={}, using fallback", ticker)
                return fallback
            }

        return SummaryRationale(
            viSummary = parsed.viSummary.ifBlank { fallback.viSummary }.take(MAX_SUMMARY_CHARS),
            deepSummary = parsed.deepSummary.takeUnless { it.isBlank() }?.take(MAX_SUMMARY_CHARS)
                ?: fallback.deepSummary,
            taSummary = parsed.taSummary.takeUnless { it.isBlank() }?.take(MAX_SUMMARY_CHARS)
                ?: fallback.taSummary,
            // decisionPath SEMPRE deterministico (mai dall'LLM).
            decisionPath = fallback.decisionPath,
        )
    }

    private fun buildUserPrompt(ticker: String, det: SummaryService.DeterministicSummary): String {
        val resp = det.response
        val viAgg = det.viAggregation
        val viResult: RuleEngineResultResponse = det.viResult
        val taResp: TechnicalAnalysisResponse? = det.taResponse

        val priceStr = viResult.currentPriceAtEval?.let { "%.2f USD".format(it) } ?: "n/d"
        val dcfStr = viResult.dcfIntrinsicValue?.let { "%.2f USD".format(it) } ?: "n/d"
        val greenRatio = if (viAgg.decisionalAvailable > 0) {
            "${viAgg.greenCount}/${viAgg.decisionalAvailable}"
        } else "n/d"
        val trendStr = taResp?.trend?.classification?.toString() ?: "n/d"
        val rsiStr = taResp?.momentum?.rsi14?.let { "%.1f".format(it) } ?: "n/d"
        val macdDailyStr = taResp?.momentum?.macdDaily?.let { "%.2f".format(it) } ?: "n/d"

        return buildString {
            appendLine("Ticker: $ticker")
            appendLine("Prezzo corrente: $priceStr | DCF intrinsic value: $dcfStr")
            appendLine()
            appendLine("Verdetti DETERMINISTICI (gia' calcolati, NON modificarli):")
            appendLine("- summaryVerdict = ${resp.summaryVerdict}")
            appendLine("- viVerdict      = ${resp.viVerdict} ($greenRatio ruleId decisionali GREEN; MoS=${viResult.mosSignal})")
            appendLine("- deepAnalysisStatus = ${resp.deepAnalysisStatus}; deepVerdict = ${resp.deepVerdict ?: "null"}")
            appendLine("- taVerdict      = ${resp.taVerdict ?: "null"} (trend=$trendStr, RSI14=$rsiStr, MACD daily=$macdDailyStr)")
            if (resp.reentryCondition != null) {
                appendLine("- reentryCondition = ${resp.reentryCondition.code}: ${resp.reentryCondition.description}")
            }
            if (resp.warningAntiCopart != null) {
                appendLine("- warningAntiCopart attivo (caso COPART: VI positivo + TA sfavorevole).")
            }
            appendLine()
            appendLine("decisionPath testuale (deterministico, NON modificarlo): ${resp.rationale.decisionPath}")
            appendLine()
            appendLine("Scrivi le 3 sintesi narrative (italiano, max ~280 caratteri ciascuna, no markdown,")
            appendLine("no JSON fuori dallo schema richiesto, no speculazioni — solo riformulazione dei dati sopra):")
        }
    }

    private fun parseResponse(raw: String): RationaleDto? {
        return try {
            val cleaned = extractJsonBlock(raw)
            objectMapper.readValue(cleaned, RationaleDto::class.java)
        } catch (ex: Exception) {
            log.warn("Failed to parse Summary rationale LLM JSON: {}", ex.message)
            null
        }
    }

    private fun extractJsonBlock(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{")) return trimmed
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        return if (start >= 0 && end > start) trimmed.substring(start, end + 1) else trimmed
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class RationaleDto(
        val viSummary: String = "",
        val deepSummary: String = "",
        val taSummary: String = "",
    )

    companion object {
        // Tetto difensivo sui 3 *Summary (US-103: rationale strutturato, no
        // walls-of-text). Coerente con MungerInversionAnalyzer che truncate
        // a 2000 char la sintesi narrativa Munger (TSK-300 F-01).
        const val MAX_SUMMARY_CHARS: Int = 800

        const val TELEMETRY_ENDPOINT: String = "summary"
        const val TELEMETRY_PURPOSE: String = "summary-rationale"

        // System prompt SHORT, vincolante sulla forma (JSON-only) e
        // sull'invariante (NON cambiare i verdetti tipati gia' decisi).
        private val SYSTEM_PROMPT: String = """
You are a financial analyst writing the narrative rationale for a value-investing
decision summary. The verdicts (summaryVerdict, viVerdict, deepVerdict, taVerdict)
have ALREADY been computed by a deterministic Kotlin rule engine. You MUST NOT
change them, contradict them, or re-derive them — your job is ONLY to translate
the structured data into 3 short narrative sentences (Italian).

Respond ONLY with a single valid JSON object matching this schema, no markdown,
no commentary:
{
  "viSummary":   "1-2 frasi (max ~280 char) sul verdetto fondamentale VI",
  "deepSummary": "1-2 frasi (max ~280 char) sulla Deep Analysis; stringa vuota se non disponibile",
  "taSummary":   "1-2 frasi (max ~280 char) sul verdetto TA di timing; stringa vuota se non disponibile"
}

Rules:
- Ground each summary in the structured data provided; no speculation, no
  numeric values that are not in the input.
- Do NOT recommend buy/sell. The verdict is set externally.
- Italian language, plain text only.
""".trimIndent()
    }
}
