package com.valueinvesting.webapp.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.valueinvesting.webapp.llm.AnthropicClient
import com.valueinvesting.webapp.llm.LlmException
import com.valueinvesting.webapp.llm.LlmRequest
import com.valueinvesting.webapp.llm.LlmResponse
import com.valueinvesting.webapp.persistence.entity.DeepAnalysisReportEntity
import com.valueinvesting.webapp.persistence.entity.FilingBlobEntity
import com.valueinvesting.webapp.persistence.repository.DeepAnalysisReportRepository
import com.valueinvesting.webapp.persistence.repository.FilingBlobRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class MungerInversionAnalyzerTest {

    private val filingRagService: FilingRagService = mockk()
    private val anthropicClient: AnthropicClient = mockk()
    private val filingBlobRepository: FilingBlobRepository = mockk()
    private val reportRepository: DeepAnalysisReportRepository = mockk()
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    private lateinit var analyzer: MungerInversionAnalyzer

    @BeforeEach
    fun setUp() {
        analyzer = MungerInversionAnalyzer(
            filingRagService = filingRagService,
            anthropicClient = anthropicClient,
            filingBlobRepository = filingBlobRepository,
            reportRepository = reportRepository,
            objectMapper = objectMapper,
        )
    }

    private fun filingBlob(accession: String, formType: String = "10-K"): FilingBlobEntity {
        return FilingBlobEntity().apply {
            id = accession.hashCode().toLong()
            ticker = "AAPL"
            cik = "0000320193"
            this.formType = formType
            accessionNumber = accession
            filingDate = LocalDate.of(2025, 12, 1)
            fetchedAt = Instant.now()
            expiresAt = Instant.now().plus(180, ChronoUnit.DAYS)
        }
    }

    private fun chunkResult(index: Int, content: String = "Filing content for chunk $index"): FilingChunkResult {
        return FilingChunkResult(
            chunkIndex = index,
            content = content,
            distance = 0.1 * index,
            filingBlobId = 100L,
        )
    }

    private fun llmResponse(content: String): LlmResponse {
        return LlmResponse(
            content = content,
            inputTokens = 500,
            outputTokens = 200,
            stopReason = "end_turn",
            model = "claude-opus-4-8",
        )
    }

    private val queryResponseJson = """
        {"items": [
            {"testo": "High debt levels pose bankruptcy risk", "chunk_index": 2},
            {"testo": "Revenue concentration on single product", "chunk_index": 5}
        ]}
    """.trimIndent()

    private val synthesisResponseJson = """
        {
            "livello_rischio": "RISCHIO_MODERATO",
            "rischi_principali": [
                {"testo": "Significant debt levels", "chunk_index": 2},
                {"testo": "Revenue concentration risk", "chunk_index": 5}
            ],
            "punti_di_forza": [
                {"testo": "Strong brand moat", "chunk_index": 1}
            ],
            "segnali_recenti_10q": [
                {"testo": "Margin compression in latest quarter", "chunk_index": 7}
            ]
        }
    """.trimIndent()

    @Nested
    inner class AnalyzeHappyPath {

        @Test
        fun `analyze produces MungerInversionReport with all fields populated`() {
            val filings = listOf(
                filingBlob("ACC-001", "10-K"),
                filingBlob("ACC-002", "10-Q"),
            )
            every { filingBlobRepository.findByTickerAndExpiresAtAfterOrderByFilingDateDesc(eq("AAPL"), any()) } returns filings
            every { reportRepository.findByTickerAndFilingComboHashAndExpiresAtAfter(eq("AAPL"), any(), any()) } returns null
            every { filingRagService.similaritySearch(any(), eq("AAPL"), eq(8)) } returns
                listOf(chunkResult(1), chunkResult(2), chunkResult(5), chunkResult(7))
            every { anthropicClient.complete(any<LlmRequest>()) } returns llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(synthesisResponseJson)
            val entitySlot = slot<DeepAnalysisReportEntity>()
            every { reportRepository.save(capture(entitySlot)) } answers { entitySlot.captured }

            val report = analyzer.analyze("AAPL")

            assertThat(report.ticker).isEqualTo("AAPL")
            assertThat(report.livelloRischio).isEqualTo(LivelloRischio.RISCHIO_MODERATO)
            assertThat(report.rischiPrincipali).isNotEmpty
            assertThat(report.rischiPrincipali.first().chunkIndex).isIn(1, 2, 5, 7)
            assertThat(report.puntiDiForza).isNotEmpty
            assertThat(report.segnaliRecenti10Q).isNotEmpty
            assertThat(report.llmCallsCount).isEqualTo(11)
        }

        @Test
        fun `livelloRischio is one of the 4 canonical values`() {
            val filings = listOf(filingBlob("ACC-001"))
            every { filingBlobRepository.findByTickerAndExpiresAtAfterOrderByFilingDateDesc(eq("AAPL"), any()) } returns filings
            every { reportRepository.findByTickerAndFilingComboHashAndExpiresAtAfter(eq("AAPL"), any(), any()) } returns null
            every { filingRagService.similaritySearch(any(), eq("AAPL"), eq(8)) } returns listOf(chunkResult(1))
            every { anthropicClient.complete(any<LlmRequest>()) } returns llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(synthesisResponseJson)
            every { reportRepository.save(any()) } answers { firstArg() }

            val report = analyzer.analyze("AAPL")

            assertThat(report.livelloRischio).isIn(
                LivelloRischio.RISCHIO_BASSO,
                LivelloRischio.RISCHIO_MODERATO,
                LivelloRischio.RISCHIO_ALTO,
                LivelloRischio.RISCHIO_ESTREMO,
            )
        }

        @Test
        fun `each risk cites chunkIndex from source filing`() {
            val filings = listOf(filingBlob("ACC-001"))
            every { filingBlobRepository.findByTickerAndExpiresAtAfterOrderByFilingDateDesc(eq("AAPL"), any()) } returns filings
            every { reportRepository.findByTickerAndFilingComboHashAndExpiresAtAfter(eq("AAPL"), any(), any()) } returns null
            every { filingRagService.similaritySearch(any(), eq("AAPL"), eq(8)) } returns
                listOf(chunkResult(2), chunkResult(5))
            every { anthropicClient.complete(any<LlmRequest>()) } returns llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(synthesisResponseJson)
            every { reportRepository.save(any()) } answers { firstArg() }

            val report = analyzer.analyze("AAPL")

            report.rischiPrincipali.forEach { risk ->
                assertThat(risk.chunkIndex).isGreaterThanOrEqualTo(0)
            }
            report.puntiDiForza.forEach { strength ->
                assertThat(strength.chunkIndex).isGreaterThanOrEqualTo(0)
            }
            report.segnaliRecenti10Q.forEach { signal ->
                assertThat(signal.chunkIndex).isGreaterThanOrEqualTo(0)
            }
        }
    }

    @Nested
    inner class CacheBehavior {

        @Test
        fun `second call with same filing combo returns cached report without LLM calls`() {
            val filings = listOf(filingBlob("ACC-001"), filingBlob("ACC-002"))
            val comboHash = analyzer.computeFilingComboHash(filings.map { it.accessionNumber })
            val cachedReport = MungerInversionReport(
                ticker = "AAPL",
                livelloRischio = LivelloRischio.RISCHIO_BASSO,
                rischiPrincipali = listOf(InversionRisk("cached risk", 1)),
                puntiDiForza = listOf(InversionStrength("cached strength", 2)),
                segnaliRecenti10Q = emptyList(),
                filingComboHash = comboHash,
                llmCallsCount = 11,
            )
            val cachedEntity = DeepAnalysisReportEntity(
                id = 1L,
                ticker = "AAPL",
                filingComboHash = comboHash,
                reportJson = objectMapper.writeValueAsString(cachedReport),
                livelloRischio = "RISCHIO_BASSO",
                generatedAt = Instant.now(),
                expiresAt = Instant.now().plus(90, ChronoUnit.DAYS),
                llmCallsCount = 11,
            )
            every { filingBlobRepository.findByTickerAndExpiresAtAfterOrderByFilingDateDesc(eq("AAPL"), any()) } returns filings
            every { reportRepository.findByTickerAndFilingComboHashAndExpiresAtAfter(eq("AAPL"), eq(comboHash), any()) } returns cachedEntity

            val report = analyzer.analyze("AAPL")

            assertThat(report.ticker).isEqualTo("AAPL")
            assertThat(report.livelloRischio).isEqualTo(LivelloRischio.RISCHIO_BASSO)
            assertThat(report.rischiPrincipali).hasSize(1)

            verify(exactly = 0) { anthropicClient.complete(any<LlmRequest>()) }
            verify(exactly = 0) { filingRagService.similaritySearch(any(), any(), any()) }
        }
    }

    @Nested
    inner class ErrorHandling {

        @Test
        fun `LLM unavailable propagates EmbeddingServiceUnavailableException`() {
            val filings = listOf(filingBlob("ACC-001"))
            every { filingBlobRepository.findByTickerAndExpiresAtAfterOrderByFilingDateDesc(eq("AAPL"), any()) } returns filings
            every { reportRepository.findByTickerAndFilingComboHashAndExpiresAtAfter(eq("AAPL"), any(), any()) } returns null
            every { filingRagService.similaritySearch(any(), eq("AAPL"), eq(8)) } returns listOf(chunkResult(1))
            every { anthropicClient.complete(any<LlmRequest>()) } throws LlmException.ServerError(503)

            assertThatThrownBy { analyzer.analyze("AAPL") }
                .isInstanceOf(EmbeddingServiceUnavailableException::class.java)
                .hasMessageContaining("LLM unavailable")
        }

        @Test
        fun `no filings found throws IllegalStateException`() {
            every { filingBlobRepository.findByTickerAndExpiresAtAfterOrderByFilingDateDesc(eq("AAPL"), any()) } returns emptyList()

            assertThatThrownBy { analyzer.analyze("AAPL") }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("No indexed filings")
        }
    }

    @Nested
    inner class FilingComboHash {

        @Test
        fun `same accession numbers in different order produce same hash`() {
            val hash1 = analyzer.computeFilingComboHash(listOf("ACC-001", "ACC-002", "ACC-003"))
            val hash2 = analyzer.computeFilingComboHash(listOf("ACC-003", "ACC-001", "ACC-002"))

            assertThat(hash1).isEqualTo(hash2)
        }

        @Test
        fun `different accession numbers produce different hash`() {
            val hash1 = analyzer.computeFilingComboHash(listOf("ACC-001", "ACC-002"))
            val hash2 = analyzer.computeFilingComboHash(listOf("ACC-001", "ACC-003"))

            assertThat(hash1).isNotEqualTo(hash2)
        }

        @Test
        fun `hash is 64-char hex string`() {
            val hash = analyzer.computeFilingComboHash(listOf("ACC-001"))

            assertThat(hash).hasSize(64)
            assertThat(hash).matches("[0-9a-f]{64}")
        }
    }

    @Nested
    inner class OutputParsing {

        @Test
        fun `parseQueryResponse extracts items with valid chunk indices`() {
            val chunks = listOf(chunkResult(2), chunkResult(5), chunkResult(7))
            val json = """{"items": [{"testo": "risk A", "chunk_index": 2}, {"testo": "risk B", "chunk_index": 5}]}"""

            val result = analyzer.parseQueryResponse(json, chunks)

            assertThat(result).hasSize(2)
            assertThat(result[0].testo).isEqualTo("risk A")
            assertThat(result[0].chunkIndex).isEqualTo(2)
            assertThat(result[1].testo).isEqualTo("risk B")
            assertThat(result[1].chunkIndex).isEqualTo(5)
        }

        @Test
        fun `parseQueryResponse handles malformed JSON gracefully`() {
            val chunks = listOf(chunkResult(1))
            val malformed = "This is not JSON at all"

            val result = analyzer.parseQueryResponse(malformed, chunks)

            assertThat(result).isEmpty()
        }

        @Test
        fun `parseQueryResponse handles JSON wrapped in markdown code block`() {
            val chunks = listOf(chunkResult(3))
            val wrapped = """
                ```json
                {"items": [{"testo": "risk from code block", "chunk_index": 3}]}
                ```
            """.trimIndent()

            val result = analyzer.parseQueryResponse(wrapped, chunks)

            assertThat(result).hasSize(1)
            assertThat(result[0].testo).isEqualTo("risk from code block")
        }

        @Test
        fun `parseSynthesisResponse produces valid report`() {
            val report = analyzer.parseSynthesisResponse(
                synthesisResponseJson,
                "AAPL",
                "abc123",
                11,
            )

            assertThat(report.livelloRischio).isEqualTo(LivelloRischio.RISCHIO_MODERATO)
            assertThat(report.rischiPrincipali).hasSize(2)
            assertThat(report.puntiDiForza).hasSize(1)
            assertThat(report.segnaliRecenti10Q).hasSize(1)
            assertThat(report.filingComboHash).isEqualTo("abc123")
            assertThat(report.llmCallsCount).isEqualTo(11)
        }

        @Test
        fun `parseSynthesisResponse defaults unknown livello_rischio to RISCHIO_ALTO`() {
            val json = """
                {
                    "livello_rischio": "UNKNOWN_VALUE",
                    "rischi_principali": [],
                    "punti_di_forza": [],
                    "segnali_recenti_10q": []
                }
            """.trimIndent()

            val report = analyzer.parseSynthesisResponse(json, "AAPL", "hash", 11)

            assertThat(report.livelloRischio).isEqualTo(LivelloRischio.RISCHIO_ALTO)
        }

        @Test
        fun `parseSynthesisResponse populates sintesi when present (US-089)`() {
            val json = """
                {
                    "livello_rischio": "RISCHIO_MODERATO",
                    "sintesi": "Moat solido ma valutazione ricca: rischio moderato.",
                    "rischi_principali": [],
                    "punti_di_forza": [],
                    "segnali_recenti_10q": []
                }
            """.trimIndent()

            val report = analyzer.parseSynthesisResponse(json, "AAPL", "hash", 11)

            assertThat(report.sintesi).isEqualTo("Moat solido ma valutazione ricca: rischio moderato.")
        }

        @Test
        fun `parseSynthesisResponse sets sintesi to null when absent (retrocompat)`() {
            val json = """
                {
                    "livello_rischio": "RISCHIO_BASSO",
                    "rischi_principali": [],
                    "punti_di_forza": [],
                    "segnali_recenti_10q": []
                }
            """.trimIndent()

            val report = analyzer.parseSynthesisResponse(json, "AAPL", "hash", 11)

            assertThat(report.sintesi).isNull()
        }
    }

    @Nested
    inner class TickerNormalization {

        @Test
        fun `analyze normalizes ticker to uppercase`() {
            val filings = listOf(filingBlob("ACC-001"))
            every { filingBlobRepository.findByTickerAndExpiresAtAfterOrderByFilingDateDesc(eq("AAPL"), any()) } returns filings
            every { reportRepository.findByTickerAndFilingComboHashAndExpiresAtAfter(eq("AAPL"), any(), any()) } returns null
            every { filingRagService.similaritySearch(any(), eq("AAPL"), eq(8)) } returns listOf(chunkResult(1))
            every { anthropicClient.complete(any<LlmRequest>()) } returns llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(synthesisResponseJson)
            every { reportRepository.save(any()) } answers { firstArg() }

            val report = analyzer.analyze("aapl")

            assertThat(report.ticker).isEqualTo("AAPL")
        }
    }

    @Nested
    @DisplayName("TSK-107: Golden response fixture")
    inner class GoldenResponseFixture {

        private val goldenSynthesisJson: String =
            javaClass.classLoader.getResource("fixtures/munger-golden-response.json")!!.readText()

        @Test
        fun `golden fixture produces report with RISCHIO_MODERATO`() {
            val filings = listOf(
                filingBlob("ACC-001", "10-K"),
                filingBlob("ACC-002", "10-Q"),
            )
            every { filingBlobRepository.findByTickerAndExpiresAtAfterOrderByFilingDateDesc(eq("AAPL"), any()) } returns filings
            every { reportRepository.findByTickerAndFilingComboHashAndExpiresAtAfter(eq("AAPL"), any(), any()) } returns null
            every { filingRagService.similaritySearch(any(), eq("AAPL"), eq(8)) } returns
                listOf(chunkResult(1), chunkResult(2), chunkResult(3), chunkResult(5), chunkResult(7), chunkResult(8))
            every { anthropicClient.complete(any<LlmRequest>()) } returns llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(goldenSynthesisJson)
            val entitySlot = slot<DeepAnalysisReportEntity>()
            every { reportRepository.save(capture(entitySlot)) } answers { entitySlot.captured }

            val report = analyzer.analyze("AAPL")

            assertThat(report.livelloRischio).isEqualTo(LivelloRischio.RISCHIO_MODERATO)
        }

        @Test
        fun `golden fixture produces report with exactly 3 rischiPrincipali each with chunkIndex`() {
            val filings = listOf(
                filingBlob("ACC-001", "10-K"),
                filingBlob("ACC-002", "10-Q"),
            )
            every { filingBlobRepository.findByTickerAndExpiresAtAfterOrderByFilingDateDesc(eq("AAPL"), any()) } returns filings
            every { reportRepository.findByTickerAndFilingComboHashAndExpiresAtAfter(eq("AAPL"), any(), any()) } returns null
            every { filingRagService.similaritySearch(any(), eq("AAPL"), eq(8)) } returns
                listOf(chunkResult(1), chunkResult(2), chunkResult(3), chunkResult(5), chunkResult(7), chunkResult(8))
            every { anthropicClient.complete(any<LlmRequest>()) } returns llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(goldenSynthesisJson)
            every { reportRepository.save(any()) } answers { firstArg() }

            val report = analyzer.analyze("AAPL")

            assertThat(report.rischiPrincipali).hasSize(3)
            report.rischiPrincipali.forEach { risk ->
                assertThat(risk.chunkIndex).isGreaterThanOrEqualTo(0)
                assertThat(risk.testo).isNotBlank()
            }
        }

        @Test
        fun `golden fixture produces report with at least 2 puntiDiForza`() {
            val filings = listOf(
                filingBlob("ACC-001", "10-K"),
                filingBlob("ACC-002", "10-Q"),
            )
            every { filingBlobRepository.findByTickerAndExpiresAtAfterOrderByFilingDateDesc(eq("AAPL"), any()) } returns filings
            every { reportRepository.findByTickerAndFilingComboHashAndExpiresAtAfter(eq("AAPL"), any(), any()) } returns null
            every { filingRagService.similaritySearch(any(), eq("AAPL"), eq(8)) } returns
                listOf(chunkResult(1), chunkResult(2), chunkResult(3), chunkResult(5), chunkResult(7), chunkResult(8))
            every { anthropicClient.complete(any<LlmRequest>()) } returns llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(goldenSynthesisJson)
            every { reportRepository.save(any()) } answers { firstArg() }

            val report = analyzer.analyze("AAPL")

            assertThat(report.puntiDiForza).hasSizeGreaterThanOrEqualTo(2)
            report.puntiDiForza.forEach { strength ->
                assertThat(strength.chunkIndex).isGreaterThanOrEqualTo(0)
                assertThat(strength.testo).isNotBlank()
            }
        }

        @Test
        fun `golden fixture produces report with at least 1 segnaleRecente10Q`() {
            val filings = listOf(
                filingBlob("ACC-001", "10-K"),
                filingBlob("ACC-002", "10-Q"),
            )
            every { filingBlobRepository.findByTickerAndExpiresAtAfterOrderByFilingDateDesc(eq("AAPL"), any()) } returns filings
            every { reportRepository.findByTickerAndFilingComboHashAndExpiresAtAfter(eq("AAPL"), any(), any()) } returns null
            every { filingRagService.similaritySearch(any(), eq("AAPL"), eq(8)) } returns
                listOf(chunkResult(1), chunkResult(2), chunkResult(3), chunkResult(5), chunkResult(7), chunkResult(8))
            every { anthropicClient.complete(any<LlmRequest>()) } returns llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(goldenSynthesisJson)
            every { reportRepository.save(any()) } answers { firstArg() }

            val report = analyzer.analyze("AAPL")

            assertThat(report.segnaliRecenti10Q).hasSizeGreaterThanOrEqualTo(1)
            report.segnaliRecenti10Q.forEach { signal ->
                assertThat(signal.chunkIndex).isGreaterThanOrEqualTo(0)
                assertThat(signal.testo).isNotBlank()
            }
        }
    }

    @Nested
    @DisplayName("TSK-107: Malformed LLM synthesis JSON")
    inner class MalformedSynthesisJson {

        @Test
        fun `malformed synthesis JSON throws LlmException InvalidRequest`() {
            val filings = listOf(filingBlob("ACC-001"))
            val malformedSynthesis = "This is definitely not valid JSON {{{broken"
            every { filingBlobRepository.findByTickerAndExpiresAtAfterOrderByFilingDateDesc(eq("AAPL"), any()) } returns filings
            every { reportRepository.findByTickerAndFilingComboHashAndExpiresAtAfter(eq("AAPL"), any(), any()) } returns null
            every { filingRagService.similaritySearch(any(), eq("AAPL"), eq(8)) } returns listOf(chunkResult(1))
            every { anthropicClient.complete(any<LlmRequest>()) } returns llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(malformedSynthesis)
            every { reportRepository.save(any()) } answers { firstArg() }

            assertThatThrownBy { analyzer.analyze("AAPL") }
                .isInstanceOf(LlmException.InvalidRequest::class.java)
                .hasMessageContaining("Failed to parse Munger synthesis")
        }

        @Test
        fun `synthesis JSON with unrecognized fields defaults to RISCHIO_ALTO with empty lists`() {
            val filings = listOf(filingBlob("ACC-001"))
            val incompleteJson = """{"unexpected_field": "value"}"""
            every { filingBlobRepository.findByTickerAndExpiresAtAfterOrderByFilingDateDesc(eq("AAPL"), any()) } returns filings
            every { reportRepository.findByTickerAndFilingComboHashAndExpiresAtAfter(eq("AAPL"), any(), any()) } returns null
            every { filingRagService.similaritySearch(any(), eq("AAPL"), eq(8)) } returns listOf(chunkResult(1))
            every { anthropicClient.complete(any<LlmRequest>()) } returns llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(incompleteJson)
            every { reportRepository.save(any()) } answers { firstArg() }

            val report = analyzer.analyze("AAPL")

            assertThat(report.livelloRischio).isEqualTo(LivelloRischio.RISCHIO_ALTO)
            assertThat(report.rischiPrincipali).isEmpty()
            assertThat(report.puntiDiForza).isEmpty()
            assertThat(report.segnaliRecenti10Q).isEmpty()
        }

        @Test
        fun `malformed individual query responses degrade gracefully without crashing pipeline`() {
            val filings = listOf(filingBlob("ACC-001"))
            val malformedQuery = "NOT JSON AT ALL"
            every { filingBlobRepository.findByTickerAndExpiresAtAfterOrderByFilingDateDesc(eq("AAPL"), any()) } returns filings
            every { reportRepository.findByTickerAndFilingComboHashAndExpiresAtAfter(eq("AAPL"), any(), any()) } returns null
            every { filingRagService.similaritySearch(any(), eq("AAPL"), eq(8)) } returns listOf(chunkResult(1))
            every { anthropicClient.complete(any<LlmRequest>()) } returns llmResponse(malformedQuery) andThen
                llmResponse(malformedQuery) andThen
                llmResponse(malformedQuery) andThen
                llmResponse(malformedQuery) andThen
                llmResponse(malformedQuery) andThen
                llmResponse(malformedQuery) andThen
                llmResponse(malformedQuery) andThen
                llmResponse(malformedQuery) andThen
                llmResponse(malformedQuery) andThen
                llmResponse(malformedQuery) andThen
                llmResponse(synthesisResponseJson)
            every { reportRepository.save(any()) } answers { firstArg() }

            val report = analyzer.analyze("AAPL")

            assertThat(report.livelloRischio).isEqualTo(LivelloRischio.RISCHIO_MODERATO)
            assertThat(report.llmCallsCount).isEqualTo(11)
        }
    }

    @Nested
    @DisplayName("TSK-107: LLM timeout handling")
    inner class LlmTimeout {

        @Test
        fun `LLM timeout on query call propagates as EmbeddingServiceUnavailableException`() {
            val filings = listOf(filingBlob("ACC-001"))
            every { filingBlobRepository.findByTickerAndExpiresAtAfterOrderByFilingDateDesc(eq("AAPL"), any()) } returns filings
            every { reportRepository.findByTickerAndFilingComboHashAndExpiresAtAfter(eq("AAPL"), any(), any()) } returns null
            every { filingRagService.similaritySearch(any(), eq("AAPL"), eq(8)) } returns listOf(chunkResult(1))
            every { anthropicClient.complete(any<LlmRequest>()) } throws LlmException.Timeout()

            assertThatThrownBy { analyzer.analyze("AAPL") }
                .isInstanceOf(EmbeddingServiceUnavailableException::class.java)
                .hasMessageContaining("LLM unavailable")
                .hasCauseInstanceOf(LlmException.Timeout::class.java)
        }

        @Test
        fun `LLM timeout on synthesis call propagates as EmbeddingServiceUnavailableException`() {
            val filings = listOf(filingBlob("ACC-001"))
            every { filingBlobRepository.findByTickerAndExpiresAtAfterOrderByFilingDateDesc(eq("AAPL"), any()) } returns filings
            every { reportRepository.findByTickerAndFilingComboHashAndExpiresAtAfter(eq("AAPL"), any(), any()) } returns null
            every { filingRagService.similaritySearch(any(), eq("AAPL"), eq(8)) } returns listOf(chunkResult(1))
            every { anthropicClient.complete(any<LlmRequest>()) } returns llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThen
                llmResponse(queryResponseJson) andThenThrows
                LlmException.Timeout()

            assertThatThrownBy { analyzer.analyze("AAPL") }
                .isInstanceOf(EmbeddingServiceUnavailableException::class.java)
                .hasMessageContaining("LLM unavailable")
                .hasCauseInstanceOf(LlmException.Timeout::class.java)
        }

        @Test
        fun `LLM rate limited propagates as EmbeddingServiceUnavailableException`() {
            val filings = listOf(filingBlob("ACC-001"))
            every { filingBlobRepository.findByTickerAndExpiresAtAfterOrderByFilingDateDesc(eq("AAPL"), any()) } returns filings
            every { reportRepository.findByTickerAndFilingComboHashAndExpiresAtAfter(eq("AAPL"), any(), any()) } returns null
            every { filingRagService.similaritySearch(any(), eq("AAPL"), eq(8)) } returns listOf(chunkResult(1))
            every { anthropicClient.complete(any<LlmRequest>()) } throws LlmException.RateLimited(retryAfterSec = 30)

            assertThatThrownBy { analyzer.analyze("AAPL") }
                .isInstanceOf(EmbeddingServiceUnavailableException::class.java)
                .hasCauseInstanceOf(LlmException.RateLimited::class.java)
        }
    }
}
