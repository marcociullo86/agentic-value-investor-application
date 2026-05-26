package com.valueinvesting.webapp.job

import com.valueinvesting.webapp.api.model.PositionSizeBlock
import com.valueinvesting.webapp.api.model.PriceActionBlock
import com.valueinvesting.webapp.api.model.RoeBlock
import com.valueinvesting.webapp.api.model.VerdictBlock
import com.valueinvesting.webapp.api.model.DeepAnalysisResponse
import com.valueinvesting.webapp.persistence.entity.TopPicksRunLogEntity
import com.valueinvesting.webapp.persistence.entity.TopValuePickEntity
import com.valueinvesting.webapp.persistence.entity.TopValuePickId
import com.valueinvesting.webapp.persistence.repository.TopPicksRunLogRepository
import com.valueinvesting.webapp.persistence.repository.TopValuePickRepository
import com.valueinvesting.webapp.service.DeepAnalysisService
import com.valueinvesting.webapp.service.LivelloRischio
import com.valueinvesting.webapp.service.SentimentClass
import com.valueinvesting.webapp.service.VerdictClass
import com.valueinvesting.webapp.universe.CandidateSource
import com.valueinvesting.webapp.universe.UniverseCandidate
import com.valueinvesting.webapp.universe.UniverseScreenerService
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Unit tests for [TopValuePicksJob] — US-048, TSK-134.
 *
 * No Spring context — pure MockK + JUnit 5 + AssertJ.
 * All collaborators (UniverseScreenerService, DeepAnalysisService,
 * TopValuePickRepository, TopPicksRunLogRepository) are mocked.
 *
 * Covered ACs (from US-048 + task description):
 *  1. Idempotenza rerun: doppio run stesso giorno → upsert OK, no duplicati.
 *  2. Error per singolo ticker: 1/10 throw → batch continua, tickersFailed=1.
 *  3. Top-N filter: 50 candidati → solo top 30 salvati.
 *  4. Verdict filter: solo APPROVATO/APPROVATO_PANIC_BUY/WATCHLIST salvati.
 *  5. Ordinamento DESC by MoS: top 30 ordinati per marginOfSafety desc.
 *  6. Run log status COMPLETED su success case.
 *  7. Run log status FAILED: universeScreenerService.screen() throws.
 *
 * [^src: management/kanban/EP-012-batch-top-value-picks/US-048-job-notturno-top-picks/TSK-134.md]
 * [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/job/TopValuePicksJob.kt]
 */
@DisplayName("TopValuePicksJob — US-048 / TSK-134")
class TopValuePicksJobTest {

    private val universeScreenerService = mockk<UniverseScreenerService>()
    private val deepAnalysisService = mockk<DeepAnalysisService>()
    private val topValuePickRepository = mockk<TopValuePickRepository>()
    private val runLogRepository = mockk<TopPicksRunLogRepository>()
    private val properties = TopPicksProperties(
        enabled = true,
        topN = 30,
        zone = "UTC",
    )

    private val job = TopValuePicksJob(
        universeScreenerService,
        deepAnalysisService,
        topValuePickRepository,
        runLogRepository,
        properties,
    )

    // Captured entities saved by topValuePickRepository.saveAll
    private val savedEntitiesSlot = slot<List<TopValuePickEntity>>()

    // Captured run-log entities
    private val runLogSlot = slot<TopPicksRunLogEntity>()

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        // Default stub: no existing entities for any run date (first run)
        every { topValuePickRepository.findByRunDateOrderByRankPositionAsc(any()) } returns emptyList()
        every { topValuePickRepository.deleteAllById(any()) } just Runs
        every { topValuePickRepository.saveAll(capture(savedEntitiesSlot)) } answers {
            firstArg<List<TopValuePickEntity>>()
        }
        // runLogRepository stubs: return the entity passed in (simulate id assignment)
        every { runLogRepository.save(any()) } answers { firstArg() }
    }

    // -------------------------------------------------------------------------
    // AC-1 — Idempotenza rerun: stesso run_date → upsert (delete + insert)
    // -------------------------------------------------------------------------
    @Test
    fun `idempotency rerun - second run on same date deletes existing then reinserts`() {
        val today = LocalDate.now()
        val candidates = buildCandidates(5)
        every { universeScreenerService.screen() } returns candidates

        // Stub analysis responses with verdicts that pass filter
        candidates.forEach { cand ->
            every { deepAnalysisService.analyze(cand.ticker, invokeLlm = false) } returns
                buildApprovatoResponse(cand.ticker, mosPct = 25.0)
        }

        // First run: no existing rows
        every { topValuePickRepository.findByRunDateOrderByRankPositionAsc(today) } returns emptyList()
        job.run()

        val firstRunSaved = savedEntitiesSlot.captured.toList()
        assertThat(firstRunSaved).hasSize(5)

        // Second run: simulate existing rows from first run
        val existingEntities = firstRunSaved.mapIndexed { idx, e ->
            e.copy(rankPosition = idx + 1)
        }
        every { topValuePickRepository.findByRunDateOrderByRankPositionAsc(today) } returns existingEntities

        job.run()

        // Verify delete was called with the existing PKs
        verify {
            topValuePickRepository.deleteAllById(
                match { ids ->
                    ids.toList().size == existingEntities.size
                },
            )
        }
        // Verify saveAll was called again with same count (not doubled)
        assertThat(savedEntitiesSlot.captured).hasSize(5)
    }

    // -------------------------------------------------------------------------
    // AC-2 — Error per singolo ticker: 1/10 throw → batch continua
    // -------------------------------------------------------------------------
    @Test
    fun `single ticker error - batch continues and tickersFailed=1 in run log`() {
        val candidates = buildCandidates(10)
        every { universeScreenerService.screen() } returns candidates

        // Ticker index 3 throws; all others succeed
        candidates.forEachIndexed { idx, cand ->
            if (idx == 3) {
                every { deepAnalysisService.analyze(cand.ticker, invokeLlm = false) } throws
                    RuntimeException("FMP timeout for ${cand.ticker}")
            } else {
                every { deepAnalysisService.analyze(cand.ticker, invokeLlm = false) } returns
                    buildApprovatoResponse(cand.ticker, mosPct = (50.0 - idx))
            }
        }

        val capturedLogs = mutableListOf<TopPicksRunLogEntity>()
        every { runLogRepository.save(capture(capturedLogs)) } answers { firstArg() }

        job.run()

        // Find the final save (status = COMPLETED), not the initial STARTED
        val completedLog = capturedLogs.lastOrNull()
        assertThat(completedLog).isNotNull
        assertThat(completedLog!!.status).isEqualTo("COMPLETED")
        assertThat(completedLog.tickersFailed).isEqualTo(1)
        assertThat(completedLog.tickersProcessed).isEqualTo(9)
    }

    // -------------------------------------------------------------------------
    // AC-3 — Top-N filter: 50 candidati → solo top 30 salvati
    // -------------------------------------------------------------------------
    @Test
    fun `top-N filter - 50 candidates yield at most 30 saved picks`() {
        val candidates = buildCandidates(50)
        every { universeScreenerService.screen() } returns candidates

        candidates.forEachIndexed { idx, cand ->
            every { deepAnalysisService.analyze(cand.ticker, invokeLlm = false) } returns
                buildApprovatoResponse(cand.ticker, mosPct = (100.0 - idx))
        }

        job.run()

        assertThat(savedEntitiesSlot.captured).hasSize(30)
    }

    // -------------------------------------------------------------------------
    // AC-4 — Verdict filter: SCARTATO/INDETERMINATO esclusi
    //          (Note: these are not VerdictClass enum values — simulated via
    //           BOCCIATO_NUMERICO which is also excluded by the job filter)
    // -------------------------------------------------------------------------
    @Test
    fun `verdict filter - bocciato candidates are excluded from saved picks`() {
        val approvato = buildCandidate("GOOD1")
        val panicBuy = buildCandidate("GOOD2")
        val watchlist = buildCandidate("GOOD3")
        val bocciato = buildCandidate("BAD1")
        val bocciatoQual = buildCandidate("BAD2")

        every { universeScreenerService.screen() } returns listOf(
            approvato, panicBuy, watchlist, bocciato, bocciatoQual,
        )

        every { deepAnalysisService.analyze("GOOD1", invokeLlm = false) } returns
            buildDeepResponse("GOOD1", VerdictClass.APPROVATO, mosPct = 30.0)
        every { deepAnalysisService.analyze("GOOD2", invokeLlm = false) } returns
            buildDeepResponse("GOOD2", VerdictClass.APPROVATO_PANIC_BUY, mosPct = 55.0)
        every { deepAnalysisService.analyze("GOOD3", invokeLlm = false) } returns
            buildDeepResponse("GOOD3", VerdictClass.WATCHLIST, mosPct = 12.0)
        every { deepAnalysisService.analyze("BAD1", invokeLlm = false) } returns
            buildDeepResponse("BAD1", VerdictClass.BOCCIATO_NUMERICO, mosPct = -10.0)
        every { deepAnalysisService.analyze("BAD2", invokeLlm = false) } returns
            buildDeepResponse("BAD2", VerdictClass.BOCCIATO_VALUE_TRAP, mosPct = -20.0)

        job.run()

        val saved = savedEntitiesSlot.captured
        assertThat(saved).hasSize(3)
        assertThat(saved.map { it.ticker }).containsExactlyInAnyOrder("GOOD1", "GOOD2", "GOOD3")
        assertThat(saved.map { it.verdettoClasse }).doesNotContain(
            VerdictClass.BOCCIATO_NUMERICO.name,
            VerdictClass.BOCCIATO_VALUE_TRAP.name,
        )
    }

    // -------------------------------------------------------------------------
    // AC-5 — Ordinamento DESC by MoS: saved entities have rankPosition 1=highest MoS
    // -------------------------------------------------------------------------
    @Test
    fun `sorting by MoS DESC - rank 1 has highest margin of safety`() {
        val candidates = buildCandidates(5)
        every { universeScreenerService.screen() } returns candidates

        // Assign MoS in ascending order: ticker0=10%, ticker1=20%, etc.
        candidates.forEachIndexed { idx, cand ->
            every { deepAnalysisService.analyze(cand.ticker, invokeLlm = false) } returns
                buildApprovatoResponse(cand.ticker, mosPct = (10.0 + idx * 10.0))
        }

        job.run()

        val saved = savedEntitiesSlot.captured
        assertThat(saved).hasSize(5)
        // rankPosition 1 should correspond to highest MoS (ticker4 = 50%)
        val rank1 = saved.first { it.rankPosition == 1 }
        assertThat(rank1.ticker).isEqualTo("TICKER_4")
        // Verify descending MoS order
        val mosList = saved.sortedBy { it.rankPosition }.map { it.marginOfSafety!! }
        assertThat(mosList).isSortedAccordingTo(Comparator.reverseOrder())
    }

    // -------------------------------------------------------------------------
    // AC-6 — Run log status COMPLETED on success
    // -------------------------------------------------------------------------
    @Test
    fun `run log status COMPLETED on successful run`() {
        val candidates = buildCandidates(3)
        every { universeScreenerService.screen() } returns candidates
        candidates.forEach { cand ->
            every { deepAnalysisService.analyze(cand.ticker, invokeLlm = false) } returns
                buildApprovatoResponse(cand.ticker, mosPct = 30.0)
        }

        val capturedLogs = mutableListOf<TopPicksRunLogEntity>()
        every { runLogRepository.save(capture(capturedLogs)) } answers { firstArg() }

        job.run()

        val completedLog = capturedLogs.last()
        assertThat(completedLog.status).isEqualTo("COMPLETED")
        assertThat(completedLog.errorMessage).isNull()
        assertThat(completedLog.top30Count).isEqualTo(3)
        assertThat(completedLog.finishedAt).isNotNull()
    }

    // -------------------------------------------------------------------------
    // AC-7 — Run log status FAILED when universeScreenerService.screen() throws
    // -------------------------------------------------------------------------
    @Test
    fun `run log status FAILED when screen() throws exception`() {
        every { universeScreenerService.screen() } throws RuntimeException("FMP unavailable")

        val capturedLogs = mutableListOf<TopPicksRunLogEntity>()
        every { runLogRepository.save(capture(capturedLogs)) } answers { firstArg() }

        job.run()

        val failedLog = capturedLogs.last()
        assertThat(failedLog.status).isEqualTo("FAILED")
        assertThat(failedLog.errorMessage).contains("FMP unavailable")
        assertThat(failedLog.finishedAt).isNotNull()
    }

    // -------------------------------------------------------------------------
    // AC-8 — fmpBatchRateLimiter: bean-level config — skip unit verification
    //         (tested via FmpResilienceConfigTest; not testable without Spring context)
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun buildCandidates(count: Int): List<UniverseCandidate> =
        (0 until count).map { buildCandidate("TICKER_$it") }

    private fun buildCandidate(ticker: String): UniverseCandidate =
        UniverseCandidate(
            ticker = ticker,
            source = CandidateSource.SCREENER,
            marketCapUsd = 10_000_000_000L,
            sector = "Technology",
            companyName = "Company $ticker",
        )

    private fun buildApprovatoResponse(ticker: String, mosPct: Double): DeepAnalysisResponse =
        buildDeepResponse(ticker, VerdictClass.APPROVATO, mosPct)

    private fun buildDeepResponse(
        ticker: String,
        verdictClass: VerdictClass,
        mosPct: Double,
    ): DeepAnalysisResponse {
        val verdictBlock = VerdictBlock(
            verdettoClasse = verdictClass,
            positionSizePct = if (verdictClass in listOf(
                    VerdictClass.APPROVATO,
                    VerdictClass.APPROVATO_PANIC_BUY,
                    VerdictClass.WATCHLIST,
                )
            ) 5.0 else 0.0,
            partialBasis = false,
            motivazioneAggregata = "Test verdict for $ticker",
            ruleCountGreen = 8,
            ruleCountYellow = 3,
            ruleCountRed = 2,
            livelloRischio = LivelloRischio.RISCHIO_BASSO,
            newsSentimentDominante = SentimentClass.NEUTRAL,
        )
        val positionSize = if (verdictClass in listOf(
                VerdictClass.APPROVATO,
                VerdictClass.APPROVATO_PANIC_BUY,
                VerdictClass.WATCHLIST,
            )
        ) {
            PositionSizeBlock(
                recommendedPct = 5.0,
                rangeLow = 2.0,
                rangeHigh = 8.0,
                basisVerdict = verdictClass,
                marginOfSafetyPct = mosPct,
                disclaimer = "Test disclaimer",
            )
        } else null

        return DeepAnalysisResponse(
            ticker = ticker,
            generatedAt = Instant.now(),
            roe = RoeBlock(fiveYearAvg = 0.15, tenYearAvg = 0.14, fiveYearDataPoints = 5, tenYearDataPoints = 10),
            priceAction = PriceActionBlock(
                priceNow = 100.0,
                max52w = 120.0,
                min52w = 80.0,
                drawdownPct = -0.167,
                trend3mPct = 0.05,
                ma50 = 95.0,
                ma200 = 90.0,
                panicDiscount = false,
                deteriorationWarning = false,
                seriesDays = 252,
            ),
            ruleEngineResults = emptyList(),
            verdict = verdictBlock,
            positionSize = positionSize,
            filingsUsed = emptyList(),
            mungerReport = null,
            newsSentiment = null,
            llmStatus = "NOT_INVOKED",
            llmCalls = 0,
            totalDurationMs = 100L,
        )
    }
}
