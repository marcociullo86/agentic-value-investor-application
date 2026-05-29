package com.valueinvesting.webapp.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.valueinvesting.webapp.api.model.DeepAnalysisResponse
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
        // Default: nessuna run pre-esistente.
        every { repo.findFirstByTickerAndStatusOrderByRequestedAtDesc(any(), any()) } returns null
        every { repo.findFirstByTickerOrderByRequestedAtDesc(any()) } returns null
        // save echo-pattern (mantiene l'id app-side già inizializzato).
        every { repo.save(any<DeepAnalysisRunEntity>()) } answers { firstArg() }
        service = DeepAnalysisRunService(repo, executor, objectMapper)
    }

    @Test
    fun `enqueue creates new run and invokes async executor when no RUNNING exists`() {
        val saved = slot<DeepAnalysisRunEntity>()
        every { repo.save(capture(saved)) } answers { firstArg() }

        val response = service.enqueue("aapl", invokeLlm = true)

        assertThat(response.ticker).isEqualTo("AAPL")
        assertThat(response.status).isEqualTo("RUNNING")
        assertThat(response.invokeLlm).isTrue()
        assertThat(response.runId).isNotBlank()

        // Persistito con ticker uppercase + status RUNNING + invokeLlm propagato.
        assertThat(saved.captured.ticker).isEqualTo("AAPL")
        assertThat(saved.captured.status).isEqualTo("RUNNING")
        assertThat(saved.captured.invokeLlm).isTrue()
        assertThat(saved.captured.requestedAt).isBeforeOrEqualTo(Instant.now())

        verify(exactly = 1) { executor.execute(saved.captured.id) }
    }

    @Test
    fun `enqueue deduplicates when a RUNNING run already exists for ticker`() {
        val existingId = UUID.randomUUID()
        val existing = DeepAnalysisRunEntity(
            id = existingId,
            ticker = "MSFT",
            status = "RUNNING",
            invokeLlm = false,
            requestedAt = Instant.now().minusSeconds(30),
        )
        every {
            repo.findFirstByTickerAndStatusOrderByRequestedAtDesc("MSFT", "RUNNING")
        } returns existing

        val response = service.enqueue("msft", invokeLlm = true)

        // Stesso runId della pre-esistente; nessuna save né executor.execute.
        assertThat(response.runId).isEqualTo(existingId.toString())
        assertThat(response.status).isEqualTo("RUNNING")
        assertThat(response.ticker).isEqualTo("MSFT")
        // L'invokeLlm riflette la run esistente, NON il parametro request:
        // il dedupe non rilancia, quindi il flag invokeLlm originale vince.
        assertThat(response.invokeLlm).isFalse()

        verify(exactly = 0) { repo.save(any<DeepAnalysisRunEntity>()) }
        verify(exactly = 0) { executor.execute(any()) }
    }

    @Test
    fun `getLatest returns NONE when no run exists for ticker`() {
        every { repo.findFirstByTickerOrderByRequestedAtDesc("UNK") } returns null

        val latest = service.getLatest("unk")

        assertThat(latest.ticker).isEqualTo("UNK")
        assertThat(latest.status).isEqualTo("NONE")
        assertThat(latest.runId).isNull()
        assertThat(latest.result).isNull()
        assertThat(latest.error).isNull()
    }

    @Test
    fun `getLatest deserializes result_json into DeepAnalysisResponse on SUCCESS`() {
        val payload = sampleDeepAnalysisResponse("AAPL")
        val json = objectMapper.writeValueAsString(payload)
        val entity = DeepAnalysisRunEntity(
            id = UUID.randomUUID(),
            ticker = "AAPL",
            status = "SUCCESS",
            invokeLlm = true,
            requestedAt = Instant.parse("2026-05-29T10:00:00Z"),
            startedAt = Instant.parse("2026-05-29T10:00:01Z"),
            completedAt = Instant.parse("2026-05-29T10:05:00Z"),
            resultJson = json,
        )
        every { repo.findFirstByTickerOrderByRequestedAtDesc("AAPL") } returns entity

        val latest = service.getLatest("aapl")

        assertThat(latest.status).isEqualTo("SUCCESS")
        assertThat(latest.runId).isEqualTo(entity.id.toString())
        assertThat(latest.error).isNull()
        assertThat(latest.result).isNotNull()
        assertThat(latest.result!!.ticker).isEqualTo("AAPL")
        assertThat(latest.result!!.verdict.verdettoClasse).isEqualTo(VerdictClass.APPROVATO)
        assertThat(latest.completedAt).isEqualTo(Instant.parse("2026-05-29T10:05:00Z"))
    }

    @Test
    fun `getLatest exposes error info on FAILED status`() {
        val entity = DeepAnalysisRunEntity(
            id = UUID.randomUUID(),
            ticker = "AAPL",
            status = "FAILED",
            invokeLlm = false,
            requestedAt = Instant.parse("2026-05-29T10:00:00Z"),
            completedAt = Instant.parse("2026-05-29T10:00:30Z"),
            errorReason = "no_sec_filings",
            errorMessage = "No SEC filings available for ticker: AAPL",
        )
        every { repo.findFirstByTickerOrderByRequestedAtDesc("AAPL") } returns entity

        val latest = service.getLatest("AAPL")

        assertThat(latest.status).isEqualTo("FAILED")
        assertThat(latest.result).isNull()
        assertThat(latest.error).isNotNull()
        assertThat(latest.error!!.reason).isEqualTo("no_sec_filings")
        assertThat(latest.error!!.message).contains("No SEC filings")
    }

    @Test
    fun `getLatest downgrades SUCCESS with unparseable JSON to FAILED internal_error`() {
        val entity = DeepAnalysisRunEntity(
            id = UUID.randomUUID(),
            ticker = "AAPL",
            status = "SUCCESS",
            invokeLlm = false,
            requestedAt = Instant.now(),
            completedAt = Instant.now(),
            resultJson = "{not a valid DeepAnalysisResponse json}",
        )
        every { repo.findFirstByTickerOrderByRequestedAtDesc("AAPL") } returns entity

        val latest = service.getLatest("AAPL")

        assertThat(latest.status).isEqualTo("FAILED")
        assertThat(latest.result).isNull()
        assertThat(latest.error).isNotNull()
        assertThat(latest.error!!.reason).isEqualTo("internal_error")
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
