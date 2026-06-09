package com.valueinvesting.webapp.technicalanalysis

import com.ninjasquad.springmockk.MockkBean
import com.valueinvesting.webapp.api.TechnicalAnalysisController
import com.valueinvesting.webapp.api.error.GlobalExceptionHandler
import com.valueinvesting.webapp.api.error.ProblemDetailsMapper
import com.valueinvesting.webapp.api.model.EntryTimingAdvisor
import com.valueinvesting.webapp.api.model.EntryTimingRationale
import com.valueinvesting.webapp.api.model.EntryTimingVerdict
import com.valueinvesting.webapp.api.model.LevelsBlock
import com.valueinvesting.webapp.api.model.MomentumBlock
import com.valueinvesting.webapp.api.model.PositionSizing
import com.valueinvesting.webapp.api.model.PositionSizingWarning
import com.valueinvesting.webapp.api.model.PriceContextBlock
import com.valueinvesting.webapp.api.model.RewardRiskLabel
import com.valueinvesting.webapp.api.model.RewardRiskRatio
import com.valueinvesting.webapp.api.model.SixPercentRule
import com.valueinvesting.webapp.api.model.StopSuggestion
import com.valueinvesting.webapp.api.model.StopType
import com.valueinvesting.webapp.api.model.TechnicalAnalysisResponse
import com.valueinvesting.webapp.api.model.TrendBlock
import com.valueinvesting.webapp.api.model.TwoPercentRule
import com.valueinvesting.webapp.api.model.VolatilityBlock
import com.valueinvesting.webapp.api.model.VolumeBlock
import com.valueinvesting.webapp.config.ProblemDetailMvcConfig
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.Instant

// WebMvcTest slice per TechnicalAnalysisController (TSK-327 / US-098).
//
// Copre:
//   - Fixture AAPL uptrend: 6 blocchi valorizzati, trend=UPTREND, advisor presenti.
//   - Fixture downtrend: trend=DOWNTREND, entryTimingAdvisor.verdict=ENTRY_UNFAVORABLE.
//   - Fixture storico corto: trend=INDETERMINATE, confidenceReduced=true.
//   - Header `X-Data-Snapshot-At` presente.
//   - Cache-Control: no-store.
//
// Pattern: @WebMvcTest + MockkBean (TechnicalAnalysisService) — NO Spring Security,
// NO Testcontainers (WebMvc slice ha zero I/O).
//
// [^src: management/kanban/EP-024-riepilogo-e-technical-analysis-tab/US-098-pipeline-ta-payload-be/TSK-327.md]
// [^src: wiki/syntheses/ta-entry-timing-stock-detail.md]
@WebMvcTest(
    controllers = [TechnicalAnalysisController::class],
    excludeAutoConfiguration = [
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration::class,
    ],
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler::class, ProblemDetailsMapper::class, ProblemDetailMvcConfig::class)
@ActiveProfiles("test")
class TechnicalAnalysisControllerWebMvcTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var technicalAnalysisService: TechnicalAnalysisService

    // SecurityConfig richiede UserDetailsServiceImpl nella classpath scan — stessa
    // tecnica usata in AnalysisControllerWebMvcTest (TSK-033).
    @MockkBean
    private lateinit var userDetailsService:
        com.valueinvesting.webapp.security.UserDetailsServiceImpl

    // -------------------------------------------------------------------------
    // Helpers per costruire risposte fixture
    // -------------------------------------------------------------------------

    private val snapshotInstant: Instant = Instant.parse("2026-06-08T10:00:00Z")

    private fun buildUptrendResponse(): TechnicalAnalysisResponse = TechnicalAnalysisResponse(
        ticker = "AAPL",
        evaluatedAt = snapshotInstant,
        trend = TrendBlock(
            sma50 = 190.0,
            sma200 = 175.0,
            classification = TrendClassification.UPTREND,
            sma200SlopePerDay = 0.05,
            confidenceReduced = false,
        ),
        momentum = MomentumBlock(
            rsi14 = 55.0,
            macdDaily = 0.8,
            macdWeekly = 1.2,
            confidenceReduced = false,
        ),
        volatility = VolatilityBlock(atr14 = 2.5, confidenceReduced = false),
        volume = VolumeBlock(obv = 5_000_000.0, avgVolume20d = 80_000_000.0, confidenceReduced = false),
        levels = LevelsBlock(support = emptyList(), resistance = emptyList(), confidenceReduced = false),
        priceContext = PriceContextBlock(
            currentPrice = 195.0,
            high52w = 220.0,
            low52w = 160.0,
            drawdownFrom52wHigh = 0.113,
            confidenceReduced = false,
        ),
        entryTimingAdvisor = EntryTimingAdvisor(
            verdict = EntryTimingVerdict.ENTRY_FAVORABLE,
            reentryCondition = null,
            rationale = EntryTimingRationale(
                screen1 = "Screen 1 (trend di lungo): UPTREND",
                screen2 = "Screen 2 (oscillatore): RSI14 55.0",
                screen3 = "Screen 3: support n/d",
                wikiCitations = listOf("ta-entry-timing-stock-detail#screen-1"),
            ),
        ),
        stopSuggestion = StopSuggestion(
            type = StopType.SMA200_BASED,
            stopPrice = 174.1,
            stopDistance = 20.9,
            stopDistancePct = 10.7,
            anchorReference = "SMA200@175.00",
            rationale = "Stop sotto SMA200 con buffer 0.5%.",
        ),
        positionSizing = PositionSizing(
            twoPercentRule = TwoPercentRule(
                equity = 50_000.0,
                maxRiskAllowed = 1_000.0,
                stopDistance = 20.9,
                sharesRecommended = 47L,
                positionValueRecommended = 9_165.0,
                positionPctEquity = 0.183,
                warning = null,
            ),
            sixPercentRule = SixPercentRule(
                maxAggregateRiskPerMonth = 3_000.0,
                disclaimer = "Conferma di NON superare il 6% aggregato.",
            ),
        ),
        rewardRiskRatio = RewardRiskRatio(
            upside = 55.0,
            downside = 20.9,
            value = 2.63,
            label = RewardRiskLabel.ACCEPTABLE,
            rationale = "Accettabile: 2.6:1.",
        ),
    )

    private fun buildDowntrendResponse(): TechnicalAnalysisResponse = TechnicalAnalysisResponse(
        ticker = "BEAR",
        evaluatedAt = snapshotInstant,
        trend = TrendBlock(
            sma50 = 90.0,
            sma200 = 110.0,
            classification = TrendClassification.DOWNTREND,
            sma200SlopePerDay = -0.08,
            confidenceReduced = false,
        ),
        momentum = MomentumBlock(rsi14 = 35.0, macdDaily = -0.6, macdWeekly = -1.0, confidenceReduced = false),
        volatility = VolatilityBlock(atr14 = 3.0, confidenceReduced = false),
        volume = VolumeBlock(obv = -1_000_000.0, avgVolume20d = 30_000_000.0, confidenceReduced = false),
        levels = LevelsBlock(support = emptyList(), resistance = emptyList(), confidenceReduced = false),
        priceContext = PriceContextBlock(
            currentPrice = 85.0,
            high52w = 130.0,
            low52w = 80.0,
            drawdownFrom52wHigh = 0.346,
            confidenceReduced = false,
        ),
        entryTimingAdvisor = EntryTimingAdvisor(
            verdict = EntryTimingVerdict.ENTRY_UNFAVORABLE,
            reentryCondition = null,
            rationale = EntryTimingRationale(
                screen1 = "Screen 1: DOWNTREND",
                screen2 = "Screen 2: RSI14 35.0",
                screen3 = "Screen 3: n/d",
                wikiCitations = listOf("ta-entry-timing-stock-detail#screen-1"),
            ),
        ),
        stopSuggestion = null,
        positionSizing = null,
        rewardRiskRatio = null,
    )

    private fun buildShortHistoryResponse(): TechnicalAnalysisResponse = TechnicalAnalysisResponse(
        ticker = "NEWIPO",
        evaluatedAt = snapshotInstant,
        trend = TrendBlock(
            sma50 = null,
            sma200 = null,
            classification = TrendClassification.INDETERMINATE,
            sma200SlopePerDay = null,
            confidenceReduced = true,
        ),
        momentum = MomentumBlock(rsi14 = 48.0, macdDaily = 0.1, macdWeekly = null, confidenceReduced = true),
        volatility = VolatilityBlock(atr14 = 1.2, confidenceReduced = false),
        volume = VolumeBlock(obv = 500_000.0, avgVolume20d = 10_000_000.0, confidenceReduced = false),
        levels = LevelsBlock(support = emptyList(), resistance = emptyList(), confidenceReduced = true),
        priceContext = PriceContextBlock(
            currentPrice = 25.0,
            high52w = 30.0,
            low52w = 20.0,
            drawdownFrom52wHigh = 0.167,
            confidenceReduced = true,
        ),
        entryTimingAdvisor = EntryTimingAdvisor(
            verdict = EntryTimingVerdict.INDETERMINATE,
            reentryCondition = null,
            rationale = EntryTimingRationale(
                screen1 = "Trend INDETERMINATE: storico EOD insufficiente (< 200 sedute).",
                screen2 = "Screen 2 non valutabile in assenza di trend di lungo.",
                screen3 = "Screen 3 non valutabile in assenza di trend di lungo.",
                wikiCitations = listOf("elder-triple-screen-impulse-system", "moving-averages-ta"),
            ),
        ),
        stopSuggestion = null,
        positionSizing = null,
        rewardRiskRatio = null,
    )

    // -------------------------------------------------------------------------
    // FIXTURE 1: AAPL uptrend — 6 blocchi + advisor presenti (US-098 AC)
    // -------------------------------------------------------------------------

    @Test
    fun `GET technical AAPL returns 200 with UPTREND classification and all 6 blocks`() {
        every { technicalAnalysisService.analyze("AAPL", any()) } returns buildUptrendResponse()

        mockMvc.get("/api/analysis/AAPL/technical") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            jsonPath("$.ticker") { value("AAPL") }
            jsonPath("$.trend.classification") { value("UPTREND") }
            jsonPath("$.trend.confidenceReduced") { value(false) }
            jsonPath("$.trend.sma50") { value(190.0) }
            jsonPath("$.trend.sma200") { value(175.0) }
            jsonPath("$.momentum.rsi14") { value(55.0) }
            jsonPath("$.momentum.macdDaily") { value(0.8) }
            jsonPath("$.momentum.macdWeekly") { value(1.2) }
            jsonPath("$.volatility.atr14") { value(2.5) }
            jsonPath("$.volume.obv") { value(5_000_000.0) }
            jsonPath("$.levels") { isNotEmpty() }
            jsonPath("$.priceContext.currentPrice") { value(195.0) }
            jsonPath("$.priceContext.high52w") { value(220.0) }
            jsonPath("$.priceContext.drawdownFrom52wHigh") { value(0.113) }
            jsonPath("$.entryTimingAdvisor.verdict") { value("ENTRY_FAVORABLE") }
            jsonPath("$.entryTimingAdvisor.viGate") { value("this_advisor_assumes_vi_verdict_positive") }
            jsonPath("$.stopSuggestion.type") { value("SMA200_BASED") }
            jsonPath("$.positionSizing.twoPercentRule.equity") { value(50_000.0) }
            jsonPath("$.positionSizing.sixPercentRule.maxAggregateRiskPerMonth") { value(3_000.0) }
            jsonPath("$.rewardRiskRatio.label") { value("ACCEPTABLE") }
        }
    }

    @Test
    fun `GET technical AAPL response includes X-Data-Snapshot-At and Cache-Control no-store`() {
        every { technicalAnalysisService.analyze("AAPL", any()) } returns buildUptrendResponse()

        mockMvc.get("/api/analysis/AAPL/technical") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            header { string("X-Data-Snapshot-At", snapshotInstant.toString()) }
            header { string("Cache-Control", "no-store") }
        }
    }

    // -------------------------------------------------------------------------
    // FIXTURE 2: titolo in downtrend conclamato (US-098 AC)
    // -------------------------------------------------------------------------

    @Test
    fun `GET technical BEAR returns DOWNTREND classification and ENTRY_UNFAVORABLE verdict`() {
        every { technicalAnalysisService.analyze("BEAR", any()) } returns buildDowntrendResponse()

        mockMvc.get("/api/analysis/BEAR/technical") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            jsonPath("$.ticker") { value("BEAR") }
            jsonPath("$.trend.classification") { value("DOWNTREND") }
            jsonPath("$.trend.sma200SlopePerDay") { value(-0.08) }
            jsonPath("$.entryTimingAdvisor.verdict") { value("ENTRY_UNFAVORABLE") }
            jsonPath("$.priceContext.drawdownFrom52wHigh") { value(0.346) }
        }
    }

    // -------------------------------------------------------------------------
    // FIXTURE 3: storico corto — INDETERMINATE + confidenceReduced=true (US-098 AC)
    // -------------------------------------------------------------------------

    @Test
    fun `GET technical NEWIPO returns INDETERMINATE trend and confidenceReduced true`() {
        every { technicalAnalysisService.analyze("NEWIPO", any()) } returns buildShortHistoryResponse()

        mockMvc.get("/api/analysis/NEWIPO/technical") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            jsonPath("$.ticker") { value("NEWIPO") }
            jsonPath("$.trend.classification") { value("INDETERMINATE") }
            jsonPath("$.trend.confidenceReduced") { value(true) }
            jsonPath("$.priceContext.confidenceReduced") { value(true) }
            jsonPath("$.entryTimingAdvisor.verdict") { value("INDETERMINATE") }
            jsonPath("$.entryTimingAdvisor.reentryCondition") { doesNotExist() }
        }
    }

    // -------------------------------------------------------------------------
    // Equity param forwarding (US-100 AC: equity mai persistito — arriva via QP)
    // -------------------------------------------------------------------------

    @Test
    fun `GET technical with equity query param forwards value to service`() {
        every { technicalAnalysisService.analyze("AAPL", 100_000.0) } returns buildUptrendResponse()

        mockMvc.get("/api/analysis/AAPL/technical?equity=100000.0") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
        }
    }
}
