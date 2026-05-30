package com.valueinvesting.webapp.service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.valueinvesting.webapp.llm.AnthropicClient
import com.valueinvesting.webapp.llm.LlmException
import com.valueinvesting.webapp.llm.LlmInteractionLogger
import com.valueinvesting.webapp.llm.LlmRequest
import com.valueinvesting.webapp.persistence.entity.DeepAnalysisReportEntity
import com.valueinvesting.webapp.persistence.repository.DeepAnalysisReportRepository
import com.valueinvesting.webapp.persistence.repository.FilingBlobRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit

// [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-041-munger-inversion-llm/TSK-105.md]
// [^src: wiki/runbooks/sec-10k-10q-analysis-playbook.md §Step 3 — Analisi Munger-inversione]
@Service
class MungerInversionAnalyzer(
    private val filingRagService: FilingRagService,
    private val anthropicClient: AnthropicClient,
    private val filingBlobRepository: FilingBlobRepository,
    private val reportRepository: DeepAnalysisReportRepository,
    private val objectMapper: ObjectMapper,
    private val llmInteractionLogger: LlmInteractionLogger = LlmInteractionLogger(),
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val RAG_TOP_K = 8
        private const val CACHE_TTL_DAYS = 90L
        private const val MAX_TOKENS_PER_QUERY = 2000
        private const val MAX_TOKENS_SYNTHESIS = 4000
    }

    @Transactional
    fun analyze(
        ticker: String,
        roeFiveYearAvg: Double? = null,
        roeTenYearAvg: Double? = null,
    ): MungerInversionReport {
        val normalizedTicker = ticker.uppercase()

        val filings = filingBlobRepository.findByTickerAndExpiresAtAfterOrderByFilingDateDesc(
            normalizedTicker,
            Instant.now(),
        )
        if (filings.isEmpty()) {
            throw IllegalStateException("No indexed filings found for ticker $normalizedTicker")
        }

        val comboHash = computeFilingComboHash(filings.map { it.accessionNumber })

        val cached = reportRepository.findByTickerAndFilingComboHashAndExpiresAtAfter(
            normalizedTicker,
            comboHash,
            Instant.now(),
        )
        if (cached != null) {
            log.info("Cache hit for {} (hash={})", normalizedTicker, comboHash)
            return deserializeReport(cached)
        }

        log.info(
            "Cache miss for {} (hash={}), running {} Munger queries",
            normalizedTicker, comboHash, MungerQueries.ALL.size,
        )

        // ADR-020 — pre-RAG structured context block with dual-lookback ROE
        // commentary. The same block is prepended to every query prompt AND to
        // the synthesis input so the LLM can comment on a 5y/10y divergence
        // wherever it surfaces (TSK-162 wave-04b finding: builder existed but
        // was never wired into the prompts).
        val roeContext = MungerPromptContextBuilder.buildRoeContext(roeFiveYearAvg, roeTenYearAvg)

        var llmCalls = 0
        val queryResults = MungerQueries.ALL.map { query ->
            val chunks = filingRagService.similaritySearch(query, normalizedTicker, RAG_TOP_K)
            val context = buildContext(chunks)
            val response = callLlm(query, context, roeContext, normalizedTicker, MAX_TOKENS_PER_QUERY)
            llmCalls++
            parseQueryResponse(response, chunks)
        }

        val synthesisInput = buildSynthesisInput(queryResults, roeContext, normalizedTicker)
        val synthesisResponse = callLlmSynthesis(synthesisInput, normalizedTicker, MAX_TOKENS_SYNTHESIS)
        llmCalls++

        val report = parseSynthesisResponse(synthesisResponse, normalizedTicker, comboHash, llmCalls)

        persistReport(report)

        log.info(
            "Munger analysis complete for {}: livelloRischio={}, llmCalls={}",
            normalizedTicker, report.livelloRischio, report.llmCallsCount,
        )

        return report
    }

    internal fun computeFilingComboHash(accessionNumbers: List<String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val payload = accessionNumbers.sorted().joinToString("|")
        return digest.digest(payload.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun buildContext(chunks: List<FilingChunkResult>): String {
        return chunks.joinToString("\n\n") { chunk ->
            "[Chunk ${chunk.chunkIndex}] ${chunk.content}"
        }
    }

    private fun callLlm(
        query: String,
        context: String,
        roeContext: String,
        ticker: String,
        maxTokens: Int,
    ): String {
        val systemPrompt = """
            You are a financial analyst performing a Munger-style inversion analysis on SEC filings for $ticker.
            Analyze the provided filing excerpts and answer the question.
            Respond ONLY with valid JSON matching this schema:
            {"items": [{"testo": "description of risk/strength/signal", "chunk_index": N}]}
            where chunk_index is the [Chunk N] number from the context that supports your finding.
            Include 2-5 items maximum. Be specific and cite evidence from the filings.
        """.trimIndent()

        // ADR-020 — prepend dual-lookback ROE block so query-level findings can
        // weight risks/strengths against the company's 5y vs 10y profitability.
        val userPrompt = buildString {
            if (roeContext.isNotBlank()) {
                appendLine(roeContext)
                appendLine()
            }
            appendLine("Question: $query")
            appendLine()
            appendLine("Context from SEC filings for $ticker:")
            append(context)
        }

        val request = LlmRequest(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            maxTokens = maxTokens,
        )

        try {
            val start = System.currentTimeMillis()
            val content = anthropicClient.complete(request).content
            llmInteractionLogger.log(
                "munger-query:$ticker", systemPrompt, userPrompt, content, System.currentTimeMillis() - start,
            )
            return content
        } catch (ex: LlmException) {
            log.error("LLM call failed for ticker={}, query='{}': {}", ticker, query.take(60), ex.message)
            throw EmbeddingServiceUnavailableException(
                "LLM unavailable during Munger analysis for $ticker: ${ex.message}",
                ex,
            )
        }
    }

    private fun callLlmSynthesis(input: String, ticker: String, maxTokens: Int): String {
        val systemPrompt = """
            You are a financial analyst synthesizing a comprehensive Munger-style inversion report for $ticker.
            Given the individual analysis results from 10 inversion queries, produce a final report.
            Respond ONLY with valid JSON matching this schema:
            {
              "livello_rischio": "RISCHIO_BASSO|RISCHIO_MODERATO|RISCHIO_ALTO|RISCHIO_ESTREMO",
              "sintesi": "narrative synthesis (max ~1500 chars) explaining WHY this overall risk level, weighing the dominant risks against the strengths",
              "rischi_principali": [{"testo": "...", "chunk_index": N}],
              "punti_di_forza": [{"testo": "...", "chunk_index": N}],
              "segnali_recenti_10q": [{"testo": "...", "chunk_index": N}]
            }
            Rules:
            - livello_rischio must be exactly one of the four values shown above.
            - sintesi: a concise narrative (Italian) that a value investor can read to understand the verdict rationale; ground it in the filings, no speculation.
            - rischi_principali: top 5-10 most critical risks, deduplicated across queries.
            - punti_di_forza: key strengths that emerged as counterbalance to risks.
            - segnali_recenti_10q: signals specific to recent 10-Q deterioration vs 10-K.
            - Every item must include a chunk_index referencing the source filing excerpt.
        """.trimIndent()

        val request = LlmRequest(
            systemPrompt = systemPrompt,
            userPrompt = input,
            maxTokens = maxTokens,
        )

        try {
            val start = System.currentTimeMillis()
            val content = anthropicClient.complete(request).content
            llmInteractionLogger.log(
                "munger-synthesis:$ticker", systemPrompt, input, content, System.currentTimeMillis() - start,
            )
            return content
        } catch (ex: LlmException) {
            log.error("LLM synthesis failed for ticker={}: {}", ticker, ex.message)
            throw EmbeddingServiceUnavailableException(
                "LLM unavailable during Munger synthesis for $ticker: ${ex.message}",
                ex,
            )
        }
    }

    private fun buildSynthesisInput(
        queryResults: List<List<QueryItem>>,
        roeContext: String,
        ticker: String,
    ): String {
        val sb = StringBuilder()
        if (roeContext.isNotBlank()) {
            sb.appendLine(roeContext)
            sb.appendLine()
        }
        sb.appendLine("Synthesis of 10 Munger inversion queries for $ticker:")
        sb.appendLine()
        MungerQueries.ALL.forEachIndexed { idx, query ->
            sb.appendLine("--- Query ${idx + 1}: $query ---")
            val items = queryResults[idx]
            if (items.isEmpty()) {
                sb.appendLine("No findings.")
            } else {
                items.forEach { item ->
                    sb.appendLine("- [chunk ${item.chunkIndex}] ${item.testo}")
                }
            }
            sb.appendLine()
        }
        return sb.toString()
    }

    internal fun parseQueryResponse(
        rawJson: String,
        chunks: List<FilingChunkResult>,
    ): List<QueryItem> {
        val cleaned = extractJsonBlock(rawJson)
        return try {
            val parsed = objectMapper.readValue(cleaned, QueryResponseDto::class.java)
            val validChunkIndices = chunks.map { it.chunkIndex }.toSet()
            parsed.items.map { item ->
                QueryItem(
                    testo = item.testo,
                    chunkIndex = if (item.chunkIndex in validChunkIndices) item.chunkIndex else chunks.firstOrNull()?.chunkIndex ?: 0,
                )
            }
        } catch (ex: Exception) {
            log.warn("Failed to parse LLM query response, returning empty: {}", ex.message)
            emptyList()
        }
    }

    internal fun parseSynthesisResponse(
        rawJson: String,
        ticker: String,
        comboHash: String,
        llmCalls: Int,
    ): MungerInversionReport {
        val cleaned = extractJsonBlock(rawJson)
        return try {
            val dto = objectMapper.readValue(cleaned, SynthesisResponseDto::class.java)
            MungerInversionReport(
                ticker = ticker,
                livelloRischio = parseLivelloRischio(dto.livelloRischio),
                rischiPrincipali = dto.rischiPrincipali.map { InversionRisk(it.testo, it.chunkIndex) },
                puntiDiForza = dto.puntiDiForza.map { InversionStrength(it.testo, it.chunkIndex) },
                segnaliRecenti10Q = dto.segnaliRecenti10Q.map { InversionSignal(it.testo, it.chunkIndex) },
                filingComboHash = comboHash,
                llmCallsCount = llmCalls,
                sintesi = dto.sintesi.ifBlank { null },
            )
        } catch (ex: Exception) {
            log.error("Failed to parse LLM synthesis response for {}: {}", ticker, ex.message)
            throw LlmException.InvalidRequest(
                "Failed to parse Munger synthesis for $ticker: ${ex.message}",
                ex,
            )
        }
    }

    private fun parseLivelloRischio(raw: String): LivelloRischio {
        return try {
            LivelloRischio.valueOf(raw.trim().uppercase())
        } catch (_: IllegalArgumentException) {
            log.warn("Unknown livello_rischio '{}', defaulting to RISCHIO_ALTO", raw)
            LivelloRischio.RISCHIO_ALTO
        }
    }

    private fun extractJsonBlock(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{")) return trimmed

        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1)
        }
        return trimmed
    }

    private fun persistReport(report: MungerInversionReport) {
        val reportJson = objectMapper.writeValueAsString(report)
        val entity = DeepAnalysisReportEntity(
            ticker = report.ticker,
            filingComboHash = report.filingComboHash,
            reportJson = reportJson,
            livelloRischio = report.livelloRischio.name,
            generatedAt = Instant.now(),
            expiresAt = Instant.now().plus(CACHE_TTL_DAYS, ChronoUnit.DAYS),
            llmCallsCount = report.llmCallsCount,
        )
        reportRepository.save(entity)
        log.info("Persisted deep_analysis_report for {} (hash={})", report.ticker, report.filingComboHash)
    }

    private fun deserializeReport(entity: DeepAnalysisReportEntity): MungerInversionReport {
        return objectMapper.readValue(entity.reportJson, MungerInversionReport::class.java)
    }

    // --- Internal DTOs for LLM JSON parsing ---

    internal data class QueryItem(
        val testo: String,
        val chunkIndex: Int,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class QueryResponseDto(
        val items: List<QueryItemDto> = emptyList(),
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class QueryItemDto(
        val testo: String = "",
        @JsonProperty("chunk_index") val chunkIndex: Int = 0,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class SynthesisResponseDto(
        @JsonProperty("livello_rischio") val livelloRischio: String = "",
        @JsonProperty("sintesi") val sintesi: String = "",
        @JsonProperty("rischi_principali") val rischiPrincipali: List<QueryItemDto> = emptyList(),
        @JsonProperty("punti_di_forza") val puntiDiForza: List<QueryItemDto> = emptyList(),
        @JsonProperty("segnali_recenti_10q") val segnaliRecenti10Q: List<QueryItemDto> = emptyList(),
    )
}
