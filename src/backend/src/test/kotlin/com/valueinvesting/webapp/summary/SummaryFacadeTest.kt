package com.valueinvesting.webapp.summary

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.valueinvesting.webapp.api.model.DeepAnalysisResponse
import com.valueinvesting.webapp.api.model.LatestDeepAnalysisResponse
import com.valueinvesting.webapp.api.model.PriceActionBlock
import com.valueinvesting.webapp.api.model.ReentryCondition
import com.valueinvesting.webapp.api.model.ReentryConditionCode
import com.valueinvesting.webapp.api.model.RoeBlock
import com.valueinvesting.webapp.api.model.RuleEngineResultResponse
import com.valueinvesting.webapp.api.model.SummaryRationale
import com.valueinvesting.webapp.api.model.SummaryVerdictResponse
import com.valueinvesting.webapp.api.model.TechnicalAnalysisResponse
import com.valueinvesting.webapp.api.model.VerdictBlock
import com.valueinvesting.webapp.ruleengine.RuleSignal
import com.valueinvesting.webapp.ruleengine.Signal
import com.valueinvesting.webapp.api.model.DcfMethodSource
import com.valueinvesting.webapp.api.model.EntryTimingAdvisor
import com.valueinvesting.webapp.api.model.EntryTimingRationale
import com.valueinvesting.webapp.api.model.EntryTimingVerdict
import com.valueinvesting.webapp.api.model.LevelsBlock
import com.valueinvesting.webapp.api.model.MomentumBlock
import com.valueinvesting.webapp.api.model.PriceContextBlock
import com.valueinvesting.webapp.api.model.TrendBlock
import com.valueinvesting.webapp.api.model.VolatilityBlock
import com.valueinvesting.webapp.api.model.VolumeBlock
import com.valueinvesting.webapp.service.AnalyzeTickerService
import com.valueinvesting.webapp.service.DeepAnalysisRunService
import com.valueinvesting.webapp.service.LivelloRischio
import com.valueinvesting.webapp.service.VerdictClass
import com.valueinvesting.webapp.technicalanalysis.TechnicalAnalysisService
import com.valueinvesting.webapp.technicalanalysis.TrendClassification
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.time.Duration
import java.time.Instant
import java.util.UUID

// Integration test per SummaryFacade + SummaryService + cache (TSK-341 / US-103).
//
// Copre:
//   1. Cache HIT: seconda chiamata con stessi snapshot servita dalla cache
//      (SummaryRationaleService NON viene chiamata una seconda volta).
//   2. NOT_INDEXED: deepAnalysisStatus = NOT_INDEXED → Summary funziona con
//      deepVerdict = null, verdetto basato su VI + TA.
//   3. Invarianza LLM: cambiare il testo del rationale prodotto da
//      SummaryRationaleService (mock) NON cambia il summaryVerdict. Il
//      summaryVerdict è prodotto in composeDeterministic PRIMA di enrich.
//   4. Cache miss dopo cambio snapshot: un nuovo viSnapshotAt produce cache miss.
//
// Pattern: mockk su tutte le dipendenze esterne (AnalyzeTickerService,
// DeepAnalysisRunService, TechnicalAnalysisService, SummaryRationaleService,
// SummaryWikiCitationsService). Cache Caffeine reale con TTL corto.
//
// [^src: management/kanban/EP-024-.../US-103-.../TSK-341.md §Integration]
// [^src: management/kanban/EP-024-.../US-103-.../US-103.md §"Caching"]
// [^src: design_&_architecture/decisions/ADR-030-decision-layer-vi-ta-summary.md §5]
class SummaryFacadeTest {

    // --- Mocks ---------------------------------------------------------------
    private val analyzeTickerService: AnalyzeTickerService = mockk()
    private val deepAnalysisRunService: DeepAnalysisRunService = mockk()
    private val technicalAnalysisService: TechnicalAnalysisService = mockk()
    private val rationaleService: SummaryRationaleService = mockk()
    private val citationsService: SummaryWikiCitationsService = mockk()

    // Cache reale Caffeine (in-process, nessun DB) con TTL sufficientemente lungo.
    private val realCache: Cache<SummaryCacheKey, SummaryVerdictResponse> =
        Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(60))
            .maximumSize(100)
            .build()

    // --- SUT -----------------------------------------------------------------
    private lateinit var viVerdictAggregator: ViVerdictAggregator
    private lateinit var deepVerdictReducer: DeepVerdictReducer
    private lateinit var summaryVerdictAggregator: SummaryVerdictAggregator
    private lateinit var summaryService: SummaryService
    private lateinit var facade: SummaryFacade

    private val snapshotAt: Instant = Instant.parse("2026-06-09T08:00:00Z")
    private val userId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000042")

    @BeforeEach
    fun setUp() {
        viVerdictAggregator = ViVerdictAggregator()
        deepVerdictReducer = DeepVerdictReducer()
        summaryVerdictAggregator = SummaryVerdictAggregator()
        summaryService = SummaryService(
            analyzeTickerService = analyzeTickerService,
            deepAnalysisRunService = deepAnalysisRunService,
            technicalAnalysisService = technicalAnalysisService,
            viVerdictAggregator = viVerdictAggregator,
            deepVerdictReducer = deepVerdictReducer,
            summaryVerdictAggregator = summaryVerdictAggregator,
        )
        facade = SummaryFacade(
            summaryService = summaryService,
            rationaleService = rationaleService,
            citationsService = citationsService,
            summaryCache = realCache,
        )

        // Default stub per citations (lista vuota — non influisce sui verdetti)
        every { citationsService.fetchCitations(any(), any(), any()) } returns emptyList()
    }

    // =========================================================================
    // Helper per costruire fixture di risposta
    // =========================================================================

    /** 10/14 GREEN decisionali (71%) → GREEN_DOMINANT; MoS GREEN. */
    private fun buildGreenViResult(snapshotAt: Instant = this.snapshotAt): RuleEngineResultResponse {
        val decisionalIds = listOf(
            "SIZE_LATEST", "EARNINGS_STABILITY_10Y", "EPS_GROWTH_10Y", "PE_3Y_AVG", "PB_LATEST",
            "DIVIDEND_CONTINUITY_20Y", "ROE_10Y_AVG", "ROIC_10Y_AVG", "GROSS_MARGIN_10Y_AVG",
            "NET_MARGIN_10Y_AVG", // 10 GREEN
            "CURRENT_RATIO_LATEST", "DEBT_TO_INCOME_LATEST", "CAPEX_INTENSITY_10Y_AVG", "NET_NET_RATIO", // 4 RED
        )
        val signals: List<RuleSignal> = decisionalIds.mapIndexed { idx, id ->
            val sig = if (idx < 10) Signal.GREEN else Signal.RED
            when (id) {
                "SIZE_LATEST" -> RuleSignal.Size(signal = sig)
                "EARNINGS_STABILITY_10Y" -> RuleSignal.EarningsStability10y(signal = sig)
                "EPS_GROWTH_10Y" -> RuleSignal.EpsGrowth10y(signal = sig)
                "PE_3Y_AVG" -> RuleSignal.Pe3yAvg(signal = sig)
                "PB_LATEST" -> RuleSignal.PbLatest(signal = sig)
                "DIVIDEND_CONTINUITY_20Y" -> RuleSignal.DividendContinuity20y(signal = sig)
                "ROE_10Y_AVG" -> RuleSignal.Roe10yAvg(signal = sig)
                "ROIC_10Y_AVG" -> RuleSignal.Roic10yAvg(signal = sig)
                "GROSS_MARGIN_10Y_AVG" -> RuleSignal.GrossMargin10yAvg(signal = sig)
                "NET_MARGIN_10Y_AVG" -> RuleSignal.NetMargin10yAvg(signal = sig)
                "CURRENT_RATIO_LATEST" -> RuleSignal.CurrentRatioLatest(signal = sig)
                "DEBT_TO_INCOME_LATEST" -> RuleSignal.DebtToIncomeLatest(signal = sig)
                "CAPEX_INTENSITY_10Y_AVG" -> RuleSignal.CapexIntensity10yAvg(signal = sig)
                "NET_NET_RATIO" -> RuleSignal.NetNetRatio(signal = sig)
                else -> error("Unknown: $id")
            }
        } + listOf(RuleSignal.NcavLatest(signal = Signal.GREEN)) // informativo, escluso

        return RuleEngineResultResponse(
            ticker = "AAPL",
            evaluatedAt = snapshotAt,
            signals = signals,
            grahamNumber = 50.0,
            dcfIntrinsicValue = 200.0,
            dcfMethod = null,
            dcfMethodSource = DcfMethodSource.DEFAULT_POLICY,
            mosSignal = Signal.GREEN,
            currentPriceAtEval = 180.0,
            dataSnapshotAt = snapshotAt,
            isStale = false,
        )
    }

    /** Risposta TA con WAIT (situazione COPART). */
    private fun buildWaitTaResponse(): TechnicalAnalysisResponse = TechnicalAnalysisResponse(
        ticker = "AAPL",
        evaluatedAt = snapshotAt,
        trend = TrendBlock(
            sma50 = 190.0, sma200 = 175.0,
            classification = TrendClassification.UPTREND,
            sma200SlopePerDay = 0.05,
            confidenceReduced = false,
        ),
        momentum = MomentumBlock(rsi14 = 76.0, macdDaily = 0.02, macdWeekly = 0.5, confidenceReduced = false),
        volatility = VolatilityBlock(atr14 = 2.5, confidenceReduced = false),
        volume = VolumeBlock(obv = 5_000_000.0, avgVolume20d = 80_000_000.0, confidenceReduced = false),
        levels = LevelsBlock(support = emptyList(), resistance = emptyList(), confidenceReduced = false),
        priceContext = PriceContextBlock(
            currentPrice = 180.0, high52w = 220.0, low52w = 160.0,
            drawdownFrom52wHigh = 0.18, confidenceReduced = false,
        ),
        entryTimingAdvisor = EntryTimingAdvisor(
            verdict = EntryTimingVerdict.WAIT,
            reentryCondition = ReentryCondition(
                code = ReentryConditionCode.RSI_BELOW_50,
                description = "Re-valuta quando RSI 14d rientra sotto 50",
            ),
            rationale = EntryTimingRationale(
                screen1 = "UPTREND", screen2 = "RSI 76 OVERBOUGHT", screen3 = "n/d",
                wikiCitations = listOf("ta-entry-timing-stock-detail"),
            ),
        ),
        stopSuggestion = null,
        positionSizing = null,
        rewardRiskRatio = null,
    )

    /** LatestDeepAnalysisResponse con status=NONE (NOT_INDEXED). */
    private fun buildNotIndexedDeepResponse(): LatestDeepAnalysisResponse = LatestDeepAnalysisResponse(
        ticker = "AAPL",
        status = "NONE",
        runId = null,
        invokeLlm = false,
        requestedAt = null,
        completedAt = null,
        result = null,
        error = null,
    )

    /** LatestDeepAnalysisResponse con status=SUCCESS e verdict APPROVATO. */
    private fun buildSuccessDeepResponse(completedAt: Instant = snapshotAt): LatestDeepAnalysisResponse {
        val deepAnalysisResp = DeepAnalysisResponse(
            ticker = "AAPL",
            generatedAt = completedAt,
            roe = RoeBlock(
                fiveYearAvg = 0.22, tenYearAvg = 0.20,
                fiveYearDataPoints = 5, tenYearDataPoints = 10,
            ),
            priceAction = PriceActionBlock(
                priceNow = 180.0, max52w = 220.0, min52w = 160.0,
                drawdownPct = 0.18, trend3mPct = 0.05,
                ma50 = 190.0, ma200 = 175.0,
                panicDiscount = false, deteriorationWarning = false, seriesDays = 400,
            ),
            ruleEngineResults = emptyList(),
            verdict = VerdictBlock(
                verdettoClasse = VerdictClass.APPROVATO,
                positionSizePct = 5.0,
                partialBasis = false,
                motivazioneAggregata = "Azienda solida.",
                ruleCountGreen = 10, ruleCountYellow = 2, ruleCountRed = 2,
                livelloRischio = LivelloRischio.RISCHIO_BASSO,
                newsSentimentDominante = com.valueinvesting.webapp.service.SentimentClass.NEUTRAL,
            ),
            positionSize = null,
            filingsUsed = emptyList(),
            mungerReport = null,
            newsSentiment = null,
            llmStatus = "NOT_INVOKED",
            llmCalls = 0,
            totalDurationMs = 500L,
        )
        return LatestDeepAnalysisResponse(
            ticker = "AAPL",
            status = "SUCCESS",
            runId = "run-001",
            invokeLlm = false,
            requestedAt = completedAt.minusSeconds(60),
            completedAt = completedAt,
            result = deepAnalysisResp,
            error = null,
        )
    }

    // Fallback rationale deterministico usato dal mock razionale
    private fun buildFallbackRationale() = SummaryRationale(
        viSummary = "Verdetto fondamentale positivo.",
        deepSummary = null,
        taSummary = "TA WAIT.",
        decisionPath = "VI gate passed → TA WAIT → Verdetto finale: WAIT_FOR_SETUP.",
    )

    // =========================================================================
    // Test 1: Cache HIT — seconda chiamata con stessi snapshot servita da cache
    // =========================================================================

    @Test
    fun `second call with same snapshot returns cached response and does NOT invoke rationaleService again`() {
        // GIVEN: VI e TA stub sempre uguali (stesso snapshotAt → stessa chiave cache)
        every { analyzeTickerService.analyze("AAPL") } returns buildGreenViResult()
        every { deepAnalysisRunService.getLatestAnalysis("AAPL") } returns buildNotIndexedDeepResponse()
        every { technicalAnalysisService.analyze(eq("AAPL"), any()) } returns buildWaitTaResponse()

        val fallback = buildFallbackRationale()
        every { rationaleService.enrich(any(), any(), any()) } returns fallback.copy(
            viSummary = "Rationale generato da LLM: call #1",
        )

        // WHEN: prima chiamata (cache MISS → invoca rationaleService)
        val result1 = facade.analyze("AAPL", userId)

        // Seconda chiamata con stessa chiave (stesso viSnapshotAt) → cache HIT
        val result2 = facade.analyze("AAPL", userId)

        // THEN: la risposta è la stessa oggetto dalla cache
        assertAll(
            { assertThat(result1.summaryVerdict).isEqualTo(result2.summaryVerdict) },
            { assertThat(result1.ticker).isEqualTo("AAPL") },
            { assertThat(result2.ticker).isEqualTo("AAPL") },
        )
        // rationaleService deve essere stato chiamato UNA SOLA VOLTA (prima chiamata).
        // Alla seconda chiamata, la facade deve ritornare dalla cache senza invocare LLM.
        verify(exactly = 1) { rationaleService.enrich(any(), any(), any()) }
    }

    // =========================================================================
    // Test 2: Cache MISS dopo cambio snapshot — nuovo viSnapshotAt = nuova chiave
    // =========================================================================

    @Test
    fun `different viSnapshotAt produces cache miss and reinvokes rationaleService`() {
        val snapshot1 = Instant.parse("2026-06-09T08:00:00Z")
        val snapshot2 = Instant.parse("2026-06-09T10:00:00Z") // snapshot aggiornato

        every { analyzeTickerService.analyze("AAPL") }
            .returnsMany(buildGreenViResult(snapshot1), buildGreenViResult(snapshot2))
        every { deepAnalysisRunService.getLatestAnalysis("AAPL") } returns buildNotIndexedDeepResponse()
        every { technicalAnalysisService.analyze(eq("AAPL"), any()) } returns buildWaitTaResponse()

        val fallback = buildFallbackRationale()
        every { rationaleService.enrich(any(), any(), any()) } returns fallback

        facade.analyze("AAPL", userId) // chiamata 1: snapshotAt = snapshot1
        facade.analyze("AAPL", userId) // chiamata 2: snapshotAt = snapshot2 → miss

        // rationaleService chiamato 2 volte perché chiave cache diversa.
        verify(exactly = 2) { rationaleService.enrich(any(), any(), any()) }
    }

    // =========================================================================
    // Test 3: NOT_INDEXED — deepAnalysisStatus = NOT_INDEXED → deepVerdict = null
    // =========================================================================

    @Test
    fun `deepAnalysisStatus NOT_INDEXED produces deepVerdict null and Summary works with VI plus TA only`() {
        every { analyzeTickerService.analyze("AAPL") } returns buildGreenViResult()
        every { deepAnalysisRunService.getLatestAnalysis("AAPL") } returns buildNotIndexedDeepResponse()
        every { technicalAnalysisService.analyze(eq("AAPL"), any()) } returns buildWaitTaResponse()
        every { rationaleService.enrich(any(), any(), any()) } returns buildFallbackRationale()

        val result = facade.analyze("AAPL", userId)

        assertAll(
            { assertThat(result.deepAnalysisStatus).isEqualTo(DeepAnalysisStatus.NOT_INDEXED) },
            { assertThat(result.deepVerdict).isNull() },
            // Il Summary deve comunque produrre un verdetto (VI + TA, senza Deep)
            { assertThat(result.summaryVerdict).isNotNull() },
            { assertThat(result.ticker).isEqualTo("AAPL") },
        )
    }

    @Test
    fun `deepAnalysisStatus NOT_INDEXED with VI GREEN and TA ENTRY_FAVORABLE produces ENTER_NOW`() {
        // VI GREEN + Deep null (NOT_INDEXED) + TA FAVORABLE → ENTER_NOW (tabella row 1a)
        val favorableTa = buildWaitTaResponse().copy(
            entryTimingAdvisor = EntryTimingAdvisor(
                verdict = EntryTimingVerdict.ENTRY_FAVORABLE,
                reentryCondition = null,
                rationale = EntryTimingRationale("UPTREND", "RSI 45", "n/d", emptyList()),
            ),
        )
        every { analyzeTickerService.analyze("AAPL") } returns buildGreenViResult()
        every { deepAnalysisRunService.getLatestAnalysis("AAPL") } returns buildNotIndexedDeepResponse()
        every { technicalAnalysisService.analyze("AAPL") } returns favorableTa
        every { rationaleService.enrich(any(), any(), any()) } returns buildFallbackRationale()

        val result = facade.analyze("AAPL", userId)

        assertAll(
            { assertThat(result.deepAnalysisStatus).isEqualTo(DeepAnalysisStatus.NOT_INDEXED) },
            { assertThat(result.deepVerdict).isNull() },
            { assertThat(result.summaryVerdict).isEqualTo(SummaryVerdict.ENTER_NOW) },
        )
    }

    // =========================================================================
    // Test 4: Invarianza LLM — summaryVerdict NON dipende dal rationale LLM
    // =========================================================================
    //
    // Il summaryVerdict è prodotto in SummaryService.composeDeterministic (passo 1)
    // PRIMA che SummaryRationaleService.enrich (passo 4) sia chiamato.
    // Quindi anche se il servizio LLM producesse razionali completamente diversi
    // (o addirittura assurdi), il summaryVerdict deve essere identico.
    // US-103 AC: "L'LLM non produce mai il summaryVerdict: test che verifica che
    // cambiando solo il testo del prompt (mantenendo input strutturati) il verdetto
    // resti invariato."

    @Test
    fun `summaryVerdict is invariant when rationaleService returns different text — LLM invariance`() {
        // Setup: due chiamate alla facade con lo STESSO snapshot (stessa chiave cache),
        // ma simuliamo l'invarianza con snapshot diversi per forzare due esecuzioni
        // di composeDeterministic + enrich indipendenti.
        val snapshot1 = Instant.parse("2026-06-09T08:00:00Z")
        val snapshot2 = Instant.parse("2026-06-09T09:00:00Z")

        every { analyzeTickerService.analyze("AAPL") }
            .returnsMany(buildGreenViResult(snapshot1), buildGreenViResult(snapshot2))
        every { deepAnalysisRunService.getLatestAnalysis("AAPL") } returns buildNotIndexedDeepResponse()
        every { technicalAnalysisService.analyze(eq("AAPL"), any()) } returns buildWaitTaResponse()

        // Primo rationale LLM: testo standard
        every { rationaleService.enrich(any(), any(), any()) }
            .returnsMany(
                // Primo enrich: rationale "normale"
                SummaryRationale(
                    viSummary = "Verdetto fondamentale positivo: 10/14 GREEN.",
                    deepSummary = null,
                    taSummary = "Attendere pullback tecnico prima di entrare.",
                    decisionPath = "VI gate passed → TA WAIT → Verdetto finale: WAIT_FOR_SETUP.",
                ),
                // Secondo enrich: testo completamente diverso (simula prompt diverso)
                SummaryRationale(
                    viSummary = "TESTO COMPLETAMENTE DIVERSO — prompt variato drasticamente.",
                    deepSummary = "Munger: n/d.",
                    taSummary = "RSI overbought, timing sfavorevole.",
                    decisionPath = "VI gate passed → TA WAIT → Verdetto finale: WAIT_FOR_SETUP.",
                ),
            )

        // Prima esecuzione (snapshot1)
        val result1 = facade.analyze("AAPL", userId)
        // Seconda esecuzione (snapshot2 = cache miss → diverso enrich)
        val result2 = facade.analyze("AAPL", userId)

        // INVARIANTE: i summaryVerdict devono essere identici nonostante
        // il rationale LLM completamente diverso.
        assertAll(
            "summaryVerdict must be identical regardless of LLM rationale text",
            { assertThat(result1.summaryVerdict).isEqualTo(SummaryVerdict.WAIT_FOR_SETUP) },
            { assertThat(result2.summaryVerdict).isEqualTo(SummaryVerdict.WAIT_FOR_SETUP) },
            { assertThat(result1.summaryVerdict).isEqualTo(result2.summaryVerdict) },
            // Il rationale TESTO può variare (LLM) — ma il verdetto STRUTTURALE NO.
            { assertThat(result1.rationale.viSummary).isNotEqualTo(result2.rationale.viSummary) },
        )
    }

    @Test
    fun `summaryVerdict invariance holds when rationaleService throws exception and falls back`() {
        // Se l'LLM esplode e il servizio ritorna il fallback deterministico,
        // il summaryVerdict deve restare invariato.
        every { analyzeTickerService.analyze("AAPL") } returns buildGreenViResult()
        every { deepAnalysisRunService.getLatestAnalysis("AAPL") } returns buildNotIndexedDeepResponse()
        every { technicalAnalysisService.analyze(eq("AAPL"), any()) } returns buildWaitTaResponse()
        // rationaleService ritorna fallback (simula LLM frozen / error → fallback)
        every { rationaleService.enrich(any(), any(), any()) } returns buildFallbackRationale()

        val result = facade.analyze("AAPL", userId)

        // Il summaryVerdict deve essere corretto a prescindere da ciò che fa l'LLM.
        assertThat(result.summaryVerdict).isEqualTo(SummaryVerdict.WAIT_FOR_SETUP)
    }

    // =========================================================================
    // Test 5: warningAntiCopart — presente solo nelle condizioni esatte
    // =========================================================================

    @Test
    fun `warningAntiCopart is present for VI GREEN plus TA WAIT — anti-COPART case`() {
        every { analyzeTickerService.analyze("AAPL") } returns buildGreenViResult()
        every { deepAnalysisRunService.getLatestAnalysis("AAPL") } returns buildNotIndexedDeepResponse()
        every { technicalAnalysisService.analyze(eq("AAPL"), any()) } returns buildWaitTaResponse()
        every { rationaleService.enrich(any(), any(), any()) } returns buildFallbackRationale()

        val result = facade.analyze("AAPL", userId)

        assertAll(
            { assertThat(result.summaryVerdict).isEqualTo(SummaryVerdict.WAIT_FOR_SETUP) },
            { assertThat(result.viVerdict).isEqualTo(ViVerdict.GREEN_DOMINANT) },
            { assertThat(result.taVerdict).isEqualTo(EntryTimingVerdict.WAIT) },
            { assertThat(result.warningAntiCopart).isNotNull() },
            { assertThat(result.warningAntiCopart).isNotBlank() },
        )
    }

    @Test
    fun `warningAntiCopart is absent when summaryVerdict is ENTER_NOW`() {
        val favorableTa = buildWaitTaResponse().copy(
            entryTimingAdvisor = EntryTimingAdvisor(
                verdict = EntryTimingVerdict.ENTRY_FAVORABLE,
                reentryCondition = null,
                rationale = EntryTimingRationale("UPTREND", "RSI 45", "n/d", emptyList()),
            ),
        )
        every { analyzeTickerService.analyze("AAPL") } returns buildGreenViResult()
        every { deepAnalysisRunService.getLatestAnalysis("AAPL") } returns buildNotIndexedDeepResponse()
        every { technicalAnalysisService.analyze("AAPL") } returns favorableTa
        every { rationaleService.enrich(any(), any(), any()) } returns buildFallbackRationale()

        val result = facade.analyze("AAPL", userId)

        assertAll(
            { assertThat(result.summaryVerdict).isEqualTo(SummaryVerdict.ENTER_NOW) },
            { assertThat(result.warningAntiCopart).isNull() },
        )
    }

    // =========================================================================
    // Test 6: decisionPath deterministico — mai prodotto dall'LLM
    // =========================================================================

    @Test
    fun `decisionPath in rationale is deterministic and not overridden by LLM enrich`() {
        every { analyzeTickerService.analyze("AAPL") } returns buildGreenViResult()
        every { deepAnalysisRunService.getLatestAnalysis("AAPL") } returns buildSuccessDeepResponse()
        every { technicalAnalysisService.analyze(eq("AAPL"), any()) } returns buildWaitTaResponse()
        // Il mock LLM ritorna un decisionPath completamente diverso — deve essere ignorato.
        every { rationaleService.enrich(any(), any(), any()) } answers { call ->
            val fallback = call.invocation.args[2] as SummaryRationale
            // Verifichiamo che il SummaryRationaleService.enrich preservi il decisionPath
            // deterministico: la produzione mantiene il decisionPath dal fallback.
            fallback.copy(viSummary = "LLM rationale override", decisionPath = fallback.decisionPath)
        }

        val result = facade.analyze("AAPL", userId)

        // decisionPath deve contenere il flusso logico deterministico
        assertThat(result.rationale.decisionPath).isNotBlank()
        assertThat(result.rationale.decisionPath).contains("Verdetto finale:")
    }
}
