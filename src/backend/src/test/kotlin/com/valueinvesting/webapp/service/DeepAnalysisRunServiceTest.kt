package com.valueinvesting.webapp.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.valueinvesting.webapp.api.model.DeepAnalysisResponse
import com.valueinvesting.webapp.api.model.IngestSummary
import com.valueinvesting.webapp.api.model.PriceActionBlock
import com.valueinvesting.webapp.api.model.RoeBlock
import com.valueinvesting.webapp.api.model.VerdictBlock
import com.valueinvesting.webapp.persistence.entity.DeepAnalysisRunEntity
import com.valueinvesting.webapp.persistence.repository.DeepAnalysisRunRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class DeepAnalysisRunServiceTest {

    private val repo: DeepAnalysisRunRepository = mockk(relaxed = true)
    private val executor: DeepAnalysisRunExecutor = mockk(relaxed = true)
    private val objectMapper = ObjectMapper().findAndRegisterModules()

    private lateinit var service: DeepAnalysisRunService

    @BeforeEach
    fun setUp() {
        // Default: nessuna run pre-esistente per qualunque combinazione.
        every { repo.findFirstByTickerAndStatusOrderByRequestedAtDesc(any(), any()) } returns null
        every { repo.findFirstByTickerAndKindAndStatusOrderByRequestedAtDesc(any(), any(), any()) } returns null
        every { repo.findFirstByTickerOrderByRequestedAtDesc(any()) } returns null
        every { repo.findFirstByTickerAndKindOrderByRequestedAtDesc(any(), any()) } returns null
        // save echo-pattern (mantiene l'id app-side già inizializzato).
        every { repo.save(any<DeepAnalysisRunEntity>()) } answers { firstArg() }
        service = DeepAnalysisRunService(repo, executor, objectMapper)
    }

    // ── enqueueAnalysis ────────────────────────────────────────────────────

    @Test
    fun `enqueueAnalysis creates new run kind ANALYSIS and invokes async executor`() {
        val saved = slot<DeepAnalysisRunEntity>()
        every { repo.save(capture(saved)) } answers { firstArg() }

        val response = service.enqueueAnalysis("aapl", invokeLlm = true)

        assertThat(response.ticker).isEqualTo("AAPL")
        assertThat(response.kind).isEqualTo("ANALYSIS")
        assertThat(response.status).isEqualTo("RUNNING")
        assertThat(response.invokeLlm).isTrue()
        assertThat(response.runId).isNotBlank()

        assertThat(saved.captured.ticker).isEqualTo("AAPL")
        assertThat(saved.captured.kind).isEqualTo("ANALYSIS")
        assertThat(saved.captured.status).isEqualTo("RUNNING")
        assertThat(saved.captured.invokeLlm).isTrue()
        assertThat(saved.captured.requestedAt).isBeforeOrEqualTo(Instant.now())

        verify(exactly = 1) { executor.execute(saved.captured.id) }
    }

    @Test
    fun `enqueueAnalysis deduplicates only on RUNNING ANALYSIS for same ticker`() {
        val existingId = UUID.randomUUID()
        val existing = DeepAnalysisRunEntity(
            id = existingId,
            ticker = "MSFT",
            kind = "ANALYSIS",
            status = "RUNNING",
            invokeLlm = false,
            requestedAt = Instant.now().minusSeconds(30),
        )
        every {
            repo.findFirstByTickerAndKindAndStatusOrderByRequestedAtDesc("MSFT", "ANALYSIS", "RUNNING")
        } returns existing

        val response = service.enqueueAnalysis("msft", invokeLlm = true)

        assertThat(response.runId).isEqualTo(existingId.toString())
        assertThat(response.kind).isEqualTo("ANALYSIS")
        assertThat(response.status).isEqualTo("RUNNING")
        assertThat(response.ticker).isEqualTo("MSFT")
        // L'invokeLlm riflette la run esistente, NON il parametro request:
        // il dedupe non rilancia, quindi il flag invokeLlm originale vince.
        assertThat(response.invokeLlm).isFalse()

        verify(exactly = 0) { repo.save(any<DeepAnalysisRunEntity>()) }
        verify(exactly = 0) { executor.execute(any()) }
    }

    @Test
    fun `enqueueAnalysis NON deduplica se l'unica RUNNING e di kind INGEST`() {
        // Una run INGEST in corso non deve bloccare l'avvio di una ANALYSIS:
        // sono operazioni indipendenti. Il dedupe è per-kind.
        val existingIngest = DeepAnalysisRunEntity(
            id = UUID.randomUUID(),
            ticker = "AAPL",
            kind = "INGEST",
            status = "RUNNING",
            invokeLlm = false,
            requestedAt = Instant.now().minusSeconds(5),
        )
        // findByTickerAndKindAndStatus per ANALYSIS RUNNING → null
        every {
            repo.findFirstByTickerAndKindAndStatusOrderByRequestedAtDesc("AAPL", "ANALYSIS", "RUNNING")
        } returns null
        // (mai chiesto INGEST in questa chiamata, ma esiste comunque nello stato)
        every {
            repo.findFirstByTickerAndKindAndStatusOrderByRequestedAtDesc("AAPL", "INGEST", "RUNNING")
        } returns existingIngest

        val response = service.enqueueAnalysis("AAPL", invokeLlm = false)

        assertThat(response.kind).isEqualTo("ANALYSIS")
        verify(exactly = 1) { repo.save(any<DeepAnalysisRunEntity>()) }
        verify(exactly = 1) { executor.execute(any()) }
    }

    // ── enqueueIngest ──────────────────────────────────────────────────────

    @Test
    fun `enqueueIngest creates new run kind INGEST con invokeLlm sempre false`() {
        val saved = slot<DeepAnalysisRunEntity>()
        every { repo.save(capture(saved)) } answers { firstArg() }

        val response = service.enqueueIngest("aapl")

        assertThat(response.ticker).isEqualTo("AAPL")
        assertThat(response.kind).isEqualTo("INGEST")
        assertThat(response.status).isEqualTo("RUNNING")
        // INGEST non chiama mai LLM — il flag è hard-coded a false per costruzione.
        assertThat(response.invokeLlm).isFalse()

        assertThat(saved.captured.kind).isEqualTo("INGEST")
        assertThat(saved.captured.invokeLlm).isFalse()

        verify(exactly = 1) { executor.execute(saved.captured.id) }
    }

    @Test
    fun `enqueueIngest deduplicates only on RUNNING INGEST for same ticker`() {
        val existingId = UUID.randomUUID()
        val existing = DeepAnalysisRunEntity(
            id = existingId,
            ticker = "AAPL",
            kind = "INGEST",
            status = "RUNNING",
            invokeLlm = false,
            requestedAt = Instant.now().minusSeconds(10),
        )
        every {
            repo.findFirstByTickerAndKindAndStatusOrderByRequestedAtDesc("AAPL", "INGEST", "RUNNING")
        } returns existing

        val response = service.enqueueIngest("aapl")

        assertThat(response.runId).isEqualTo(existingId.toString())
        assertThat(response.kind).isEqualTo("INGEST")
        verify(exactly = 0) { repo.save(any<DeepAnalysisRunEntity>()) }
        verify(exactly = 0) { executor.execute(any()) }
    }

    @Test
    fun `enqueueIngest NON deduplica se l'unica RUNNING e di kind ANALYSIS`() {
        val existingAnalysis = DeepAnalysisRunEntity(
            id = UUID.randomUUID(),
            ticker = "AAPL",
            kind = "ANALYSIS",
            status = "RUNNING",
            invokeLlm = true,
            requestedAt = Instant.now().minusSeconds(5),
        )
        every {
            repo.findFirstByTickerAndKindAndStatusOrderByRequestedAtDesc("AAPL", "INGEST", "RUNNING")
        } returns null
        every {
            repo.findFirstByTickerAndKindAndStatusOrderByRequestedAtDesc("AAPL", "ANALYSIS", "RUNNING")
        } returns existingAnalysis

        val response = service.enqueueIngest("AAPL")

        assertThat(response.kind).isEqualTo("INGEST")
        verify(exactly = 1) { repo.save(any<DeepAnalysisRunEntity>()) }
        verify(exactly = 1) { executor.execute(any()) }
    }

    // ── getLatestAnalysis ──────────────────────────────────────────────────

    @Test
    fun `getLatestAnalysis returns NONE when no ANALYSIS run exists for ticker`() {
        every { repo.findFirstByTickerAndKindOrderByRequestedAtDesc("UNK", "ANALYSIS") } returns null

        val latest = service.getLatestAnalysis("unk")

        assertThat(latest.ticker).isEqualTo("UNK")
        assertThat(latest.status).isEqualTo("NONE")
        assertThat(latest.runId).isNull()
        assertThat(latest.result).isNull()
        assertThat(latest.error).isNull()
    }

    @Test
    fun `getLatestAnalysis deserializes result_json into DeepAnalysisResponse on SUCCESS`() {
        val payload = sampleDeepAnalysisResponse("AAPL")
        val json = objectMapper.writeValueAsString(payload)
        val entity = DeepAnalysisRunEntity(
            id = UUID.randomUUID(),
            ticker = "AAPL",
            kind = "ANALYSIS",
            status = "SUCCESS",
            invokeLlm = true,
            requestedAt = Instant.parse("2026-05-29T10:00:00Z"),
            startedAt = Instant.parse("2026-05-29T10:00:01Z"),
            completedAt = Instant.parse("2026-05-29T10:05:00Z"),
            resultJson = json,
        )
        every { repo.findFirstByTickerAndKindOrderByRequestedAtDesc("AAPL", "ANALYSIS") } returns entity

        val latest = service.getLatestAnalysis("aapl")

        assertThat(latest.status).isEqualTo("SUCCESS")
        assertThat(latest.runId).isEqualTo(entity.id.toString())
        assertThat(latest.error).isNull()
        assertThat(latest.result).isNotNull()
        assertThat(latest.result!!.ticker).isEqualTo("AAPL")
        assertThat(latest.result!!.verdict.verdettoClasse).isEqualTo(VerdictClass.APPROVATO)
        assertThat(latest.completedAt).isEqualTo(Instant.parse("2026-05-29T10:05:00Z"))
    }

    @Test
    fun `getLatestAnalysis exposes error info on FAILED status`() {
        val entity = DeepAnalysisRunEntity(
            id = UUID.randomUUID(),
            ticker = "AAPL",
            kind = "ANALYSIS",
            status = "FAILED",
            invokeLlm = true,
            requestedAt = Instant.parse("2026-05-29T10:00:00Z"),
            completedAt = Instant.parse("2026-05-29T10:00:30Z"),
            errorReason = "not_indexed",
            errorMessage = "Filings not indexed for ticker 'AAPL'. Run INGEST before requesting LLM analysis.",
        )
        every { repo.findFirstByTickerAndKindOrderByRequestedAtDesc("AAPL", "ANALYSIS") } returns entity

        val latest = service.getLatestAnalysis("AAPL")

        assertThat(latest.status).isEqualTo("FAILED")
        assertThat(latest.result).isNull()
        assertThat(latest.error).isNotNull()
        assertThat(latest.error!!.reason).isEqualTo("not_indexed")
        assertThat(latest.error!!.message).contains("not indexed")
    }

    @Test
    fun `getLatestAnalysis downgrades SUCCESS with unparseable JSON to FAILED internal_error`() {
        val entity = DeepAnalysisRunEntity(
            id = UUID.randomUUID(),
            ticker = "AAPL",
            kind = "ANALYSIS",
            status = "SUCCESS",
            invokeLlm = false,
            requestedAt = Instant.now(),
            completedAt = Instant.now(),
            resultJson = "{not a valid DeepAnalysisResponse json}",
        )
        every { repo.findFirstByTickerAndKindOrderByRequestedAtDesc("AAPL", "ANALYSIS") } returns entity

        val latest = service.getLatestAnalysis("AAPL")

        assertThat(latest.status).isEqualTo("FAILED")
        assertThat(latest.result).isNull()
        assertThat(latest.error).isNotNull()
        assertThat(latest.error!!.reason).isEqualTo("internal_error")
    }

    // ── getLatestIngest ────────────────────────────────────────────────────

    @Test
    fun `getLatestIngest returns NONE when no INGEST run exists for ticker`() {
        every { repo.findFirstByTickerAndKindOrderByRequestedAtDesc("UNK", "INGEST") } returns null

        val latest = service.getLatestIngest("unk")

        assertThat(latest.ticker).isEqualTo("UNK")
        assertThat(latest.status).isEqualTo("NONE")
        assertThat(latest.runId).isNull()
        assertThat(latest.summary).isNull()
        assertThat(latest.error).isNull()
    }

    @Test
    fun `getLatestIngest deserializes summary on SUCCESS`() {
        val summary = IngestSummary(
            filingsTotal = 3,
            chunksIndexed = 42,
            chunksSkipped = 1,
            indexedAt = Instant.parse("2026-05-29T11:00:00Z"),
        )
        val json = objectMapper.writeValueAsString(summary)
        val entity = DeepAnalysisRunEntity(
            id = UUID.randomUUID(),
            ticker = "AAPL",
            kind = "INGEST",
            status = "SUCCESS",
            invokeLlm = false,
            requestedAt = Instant.parse("2026-05-29T10:55:00Z"),
            completedAt = Instant.parse("2026-05-29T11:00:00Z"),
            resultJson = json,
        )
        every { repo.findFirstByTickerAndKindOrderByRequestedAtDesc("AAPL", "INGEST") } returns entity

        val latest = service.getLatestIngest("aapl")

        assertThat(latest.status).isEqualTo("SUCCESS")
        assertThat(latest.summary).isNotNull()
        assertThat(latest.summary!!.filingsTotal).isEqualTo(3)
        assertThat(latest.summary!!.chunksIndexed).isEqualTo(42)
        assertThat(latest.summary!!.chunksSkipped).isEqualTo(1)
        assertThat(latest.error).isNull()
    }

    @Test
    fun `getLatestIngest exposes error info on FAILED status`() {
        val entity = DeepAnalysisRunEntity(
            id = UUID.randomUUID(),
            ticker = "NOFIL",
            kind = "INGEST",
            status = "FAILED",
            invokeLlm = false,
            requestedAt = Instant.now(),
            completedAt = Instant.now(),
            errorReason = "no_sec_filings",
            errorMessage = "No SEC filings available for ticker: NOFIL",
        )
        every { repo.findFirstByTickerAndKindOrderByRequestedAtDesc("NOFIL", "INGEST") } returns entity

        val latest = service.getLatestIngest("nofil")

        assertThat(latest.status).isEqualTo("FAILED")
        assertThat(latest.summary).isNull()
        assertThat(latest.error).isNotNull()
        assertThat(latest.error!!.reason).isEqualTo("no_sec_filings")
    }

    @Test
    fun `getLatestIngest downgrades SUCCESS with unparseable JSON to FAILED internal_error`() {
        val entity = DeepAnalysisRunEntity(
            id = UUID.randomUUID(),
            ticker = "AAPL",
            kind = "INGEST",
            status = "SUCCESS",
            invokeLlm = false,
            requestedAt = Instant.now(),
            completedAt = Instant.now(),
            resultJson = "{garbage}",
        )
        every { repo.findFirstByTickerAndKindOrderByRequestedAtDesc("AAPL", "INGEST") } returns entity

        val latest = service.getLatestIngest("AAPL")

        assertThat(latest.status).isEqualTo("FAILED")
        assertThat(latest.summary).isNull()
        assertThat(latest.error!!.reason).isEqualTo("internal_error")
    }

    @Test
    fun `getLatestAnalysis ignora le run INGEST anche se piu recenti`() {
        // Filtra-per-kind: una INGEST appena conclusa NON deve apparire come
        // ultima ANALYSIS — il repo è chiamato con kind=ANALYSIS, mockato a null.
        every { repo.findFirstByTickerAndKindOrderByRequestedAtDesc("AAPL", "ANALYSIS") } returns null
        // (la presenza dell'INGEST non viene letta dal getLatestAnalysis, ma
        // il test esiste per documentare l'invariante: kind filter strict.)

        val latest = service.getLatestAnalysis("AAPL")

        assertThat(latest.status).isEqualTo("NONE")
        // Verifichiamo che NON sia stata chiamata la query no-kind legacy.
        verify(exactly = 0) { repo.findFirstByTickerOrderByRequestedAtDesc(any()) }
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
            llmStatus = "NOT_INVOKED",
            llmCalls = 0,
            totalDurationMs = 1234L,
        )
    }
}
