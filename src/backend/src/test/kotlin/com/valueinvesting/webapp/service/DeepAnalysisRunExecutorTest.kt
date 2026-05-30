package com.valueinvesting.webapp.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.valueinvesting.webapp.api.model.DeepAnalysisResponse
import com.valueinvesting.webapp.api.model.IngestSummary
import com.valueinvesting.webapp.api.model.PriceActionBlock
import com.valueinvesting.webapp.api.model.RoeBlock
import com.valueinvesting.webapp.api.model.VerdictBlock
import com.valueinvesting.webapp.fmp.FmpTickerNotFoundException
import com.valueinvesting.webapp.persistence.entity.DeepAnalysisRunEntity
import com.valueinvesting.webapp.persistence.repository.DeepAnalysisRunRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
import java.util.UUID

class DeepAnalysisRunExecutorTest {

    private val deepAnalysisService: DeepAnalysisService = mockk()
    private val repo: DeepAnalysisRunRepository = mockk(relaxed = true)
    private val objectMapper = ObjectMapper().findAndRegisterModules()

    private lateinit var executor: DeepAnalysisRunExecutor

    @BeforeEach
    fun setUp() {
        every { repo.save(any<DeepAnalysisRunEntity>()) } answers { firstArg() }
        executor = DeepAnalysisRunExecutor(deepAnalysisService, repo, objectMapper)
    }

    // ── ANALYSIS dispatch ───────────────────────────────────────────────

    @Test
    fun `execute ANALYSIS SUCCESS path persists status SUCCESS with serialized DeepAnalysisResponse`() {
        val runId = UUID.randomUUID()
        val initial = DeepAnalysisRunEntity(
            id = runId,
            ticker = "AAPL",
            kind = "ANALYSIS",
            status = "RUNNING",
            invokeLlm = true,
            requestedAt = Instant.now(),
        )
        every { repo.findById(runId) } returns Optional.of(initial)

        val response = sampleDeepAnalysisResponse("AAPL")
        every { deepAnalysisService.analyze("AAPL", true) } returns response

        // Cattura snapshot in-place ad ogni save: l'executor muta l'entity
        // tra una save e l'altra (markRunning → analyze → markSuccess), e un
        // singolo `slot` o capture-by-reference catturerebbe sempre lo stato
        // finale. Salviamo una copia immediata via `answers`.
        val savedSnapshots = mutableListOf<DeepAnalysisRunEntity>()
        every { repo.save(any<DeepAnalysisRunEntity>()) } answers {
            val e = firstArg<DeepAnalysisRunEntity>()
            savedSnapshots.add(e.copy())
            e
        }

        executor.execute(runId)

        val statuses = savedSnapshots.map { it.status }
        assertThat(statuses).contains("RUNNING", "SUCCESS")

        val finalSave = savedSnapshots.last { it.status == "SUCCESS" }
        assertThat(finalSave.resultJson).isNotNull()
        assertThat(finalSave.errorReason).isNull()
        assertThat(finalSave.completedAt).isNotNull()

        // Il JSON persistito deve essere deserializzabile nel DTO originale.
        val roundtrip = objectMapper.readValue(finalSave.resultJson, DeepAnalysisResponse::class.java)
        assertThat(roundtrip.ticker).isEqualTo("AAPL")
        assertThat(roundtrip.verdict.verdettoClasse).isEqualTo(VerdictClass.APPROVATO)

        verify(exactly = 1) { deepAnalysisService.analyze("AAPL", true) }
        verify(exactly = 0) { deepAnalysisService.ingest(any()) }
    }

    // ── INGEST dispatch ─────────────────────────────────────────────────

    @Test
    fun `execute INGEST SUCCESS path chiama ingest e serializza IngestSummary in result_json`() {
        val runId = UUID.randomUUID()
        val initial = DeepAnalysisRunEntity(
            id = runId,
            ticker = "AAPL",
            kind = "INGEST",
            status = "RUNNING",
            invokeLlm = false,
            requestedAt = Instant.now(),
        )
        every { repo.findById(runId) } returns Optional.of(initial)

        val summary = IngestSummary(
            filingsTotal = 4,
            chunksIndexed = 120,
            chunksSkipped = 1,
            indexedAt = Instant.parse("2026-05-29T11:00:00Z"),
        )
        every { deepAnalysisService.ingest("AAPL") } returns summary

        val savedSnapshots = mutableListOf<DeepAnalysisRunEntity>()
        every { repo.save(any<DeepAnalysisRunEntity>()) } answers {
            val e = firstArg<DeepAnalysisRunEntity>()
            savedSnapshots.add(e.copy())
            e
        }

        executor.execute(runId)

        val finalSave = savedSnapshots.last { it.status == "SUCCESS" }
        assertThat(finalSave.resultJson).isNotNull()
        assertThat(finalSave.errorReason).isNull()
        assertThat(finalSave.completedAt).isNotNull()

        val roundtrip = objectMapper.readValue(finalSave.resultJson, IngestSummary::class.java)
        assertThat(roundtrip.filingsTotal).isEqualTo(4)
        assertThat(roundtrip.chunksIndexed).isEqualTo(120)
        assertThat(roundtrip.chunksSkipped).isEqualTo(1)

        // Il dispatch deve essere per kind: ANALYSIS branch non chiamato.
        verify(exactly = 1) { deepAnalysisService.ingest("AAPL") }
        verify(exactly = 0) { deepAnalysisService.analyze(any(), any()) }
    }

    @Test
    fun `execute INGEST FAILED on NoSecFilingsException sets reason no_sec_filings`() {
        val runId = UUID.randomUUID()
        val initial = DeepAnalysisRunEntity(
            id = runId,
            ticker = "NOFIL",
            kind = "INGEST",
            status = "RUNNING",
            invokeLlm = false,
            requestedAt = Instant.now(),
        )
        every { repo.findById(runId) } returns Optional.of(initial)
        every { deepAnalysisService.ingest("NOFIL") } throws NoSecFilingsException(ticker = "NOFIL")

        val savedSlots = mutableListOf<DeepAnalysisRunEntity>()
        every { repo.save(capture(savedSlots)) } answers { firstArg() }

        executor.execute(runId)

        val finalSave = savedSlots.last { it.status == "FAILED" }
        assertThat(finalSave.errorReason).isEqualTo("no_sec_filings")
        assertThat(finalSave.errorMessage).contains("NOFIL")
    }

    // ── ANALYSIS error paths ────────────────────────────────────────────

    @Test
    fun `execute ANALYSIS FAILED path on FmpTickerNotFoundException sets reason not_found`() {
        val runId = UUID.randomUUID()
        val initial = DeepAnalysisRunEntity(
            id = runId,
            ticker = "NOPE",
            kind = "ANALYSIS",
            status = "RUNNING",
            invokeLlm = false,
            requestedAt = Instant.now(),
        )
        every { repo.findById(runId) } returns Optional.of(initial)
        every { deepAnalysisService.analyze("NOPE", false) } throws
            FmpTickerNotFoundException(ticker = "NOPE")

        val savedSlots = mutableListOf<DeepAnalysisRunEntity>()
        every { repo.save(capture(savedSlots)) } answers { firstArg() }

        executor.execute(runId)

        val finalSave = savedSlots.last { it.status == "FAILED" }
        assertThat(finalSave.errorReason).isEqualTo("not_found")
    }

    @Test
    fun `execute ANALYSIS FAILED path on FilingsNotIndexedException sets reason not_indexed`() {
        // Post EP-011 split (V028): se l'utente lancia ANALYSIS-with-LLM
        // senza INGEST precedente, il service alza FilingsNotIndexedException;
        // l'executor deve mapparla alla reason `not_indexed`, distinta da
        // `no_sec_filings` (che indica assenza di filing presso SEC).
        val runId = UUID.randomUUID()
        val initial = DeepAnalysisRunEntity(
            id = runId,
            ticker = "AAPL",
            kind = "ANALYSIS",
            status = "RUNNING",
            invokeLlm = true,
            requestedAt = Instant.now(),
        )
        every { repo.findById(runId) } returns Optional.of(initial)
        every { deepAnalysisService.analyze("AAPL", true) } throws
            FilingsNotIndexedException(ticker = "AAPL")

        val savedSlots = mutableListOf<DeepAnalysisRunEntity>()
        every { repo.save(capture(savedSlots)) } answers { firstArg() }

        executor.execute(runId)

        val finalSave = savedSlots.last { it.status == "FAILED" }
        assertThat(finalSave.errorReason).isEqualTo("not_indexed")
        assertThat(finalSave.errorMessage).contains("AAPL")
    }

    @Test
    fun `execute ANALYSIS FAILED path on LlmUnavailableException sets reason llm_unavailable`() {
        val runId = UUID.randomUUID()
        val initial = DeepAnalysisRunEntity(
            id = runId,
            ticker = "AAPL",
            kind = "ANALYSIS",
            status = "RUNNING",
            invokeLlm = true,
            requestedAt = Instant.now(),
        )
        every { repo.findById(runId) } returns Optional.of(initial)
        every { deepAnalysisService.analyze("AAPL", true) } throws
            LlmUnavailableException(ticker = "AAPL")

        val savedSlots = mutableListOf<DeepAnalysisRunEntity>()
        every { repo.save(capture(savedSlots)) } answers { firstArg() }

        executor.execute(runId)

        val finalSave = savedSlots.last { it.status == "FAILED" }
        assertThat(finalSave.errorReason).isEqualTo("llm_unavailable")
    }

    @Test
    fun `execute ANALYSIS FAILED path on EmbeddingServiceUnavailableException sets reason embedding_unavailable`() {
        val runId = UUID.randomUUID()
        val initial = DeepAnalysisRunEntity(
            id = runId,
            ticker = "AAPL",
            kind = "ANALYSIS",
            status = "RUNNING",
            invokeLlm = false,
            requestedAt = Instant.now(),
        )
        every { repo.findById(runId) } returns Optional.of(initial)
        every { deepAnalysisService.analyze("AAPL", false) } throws
            EmbeddingServiceUnavailableException("Sidecar unavailable")

        val savedSlots = mutableListOf<DeepAnalysisRunEntity>()
        every { repo.save(capture(savedSlots)) } answers { firstArg() }

        executor.execute(runId)

        val finalSave = savedSlots.last { it.status == "FAILED" }
        assertThat(finalSave.errorReason).isEqualTo("embedding_unavailable")
    }

    @Test
    fun `execute ANALYSIS FAILED path on generic Exception sets reason internal_error`() {
        val runId = UUID.randomUUID()
        val initial = DeepAnalysisRunEntity(
            id = runId,
            ticker = "AAPL",
            kind = "ANALYSIS",
            status = "RUNNING",
            invokeLlm = false,
            requestedAt = Instant.now(),
        )
        every { repo.findById(runId) } returns Optional.of(initial)
        every { deepAnalysisService.analyze("AAPL", false) } throws
            IllegalStateException("boom")

        val savedSlots = mutableListOf<DeepAnalysisRunEntity>()
        every { repo.save(capture(savedSlots)) } answers { firstArg() }

        executor.execute(runId)

        val finalSave = savedSlots.last { it.status == "FAILED" }
        assertThat(finalSave.errorReason).isEqualTo("internal_error")
        assertThat(finalSave.errorMessage).isEqualTo("boom")
    }

    @Test
    fun `execute is no-op when run not found`() {
        val runId = UUID.randomUUID()
        every { repo.findById(runId) } returns Optional.empty()

        executor.execute(runId)

        verify(exactly = 0) { deepAnalysisService.analyze(any(), any()) }
        verify(exactly = 0) { deepAnalysisService.ingest(any()) }
        verify(exactly = 0) { repo.save(any<DeepAnalysisRunEntity>()) }
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private fun sampleDeepAnalysisResponse(ticker: String): DeepAnalysisResponse {
        return DeepAnalysisResponse(
            ticker = ticker,
            generatedAt = Instant.parse("2026-05-29T10:05:00Z"),
            roe = RoeBlock(
                fiveYearAvg = 0.25,
                tenYearAvg = 0.22,
                fiveYearDataPoints = 5,
                tenYearDataPoints = 10,
            ),
            priceAction = PriceActionBlock(
                priceNow = 200.0,
                max52w = 220.0,
                min52w = 150.0,
                drawdownPct = -9.09,
                trend3mPct = 5.0,
                ma50 = 195.0,
                ma200 = 180.0,
                panicDiscount = false,
                deteriorationWarning = false,
                seriesDays = 252,
            ),
            ruleEngineResults = emptyList(),
            verdict = VerdictBlock(
                verdettoClasse = VerdictClass.APPROVATO,
                positionSizePct = 5.0,
                partialBasis = true,
                motivazioneAggregata = "Test verdict",
                ruleCountGreen = 10,
                ruleCountYellow = 2,
                ruleCountRed = 1,
                livelloRischio = LivelloRischio.RISCHIO_BASSO,
                newsSentimentDominante = SentimentClass.NEUTRAL,
            ),
            positionSize = null,
            filingsUsed = emptyList(),
            mungerReport = null,
            newsSentiment = null,
            llmStatus = "INVOKED",
            llmCalls = 10,
            totalDurationMs = 60000L,
        )
    }
}
