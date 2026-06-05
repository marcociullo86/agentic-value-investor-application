package com.valueinvesting.webapp.api

import com.valueinvesting.webapp.api.error.GlobalExceptionHandler
import com.valueinvesting.webapp.api.error.ProblemDetailsMapper
import com.valueinvesting.webapp.config.ProblemDetailMvcConfig
import com.valueinvesting.webapp.api.model.DcfMethodSource
import com.valueinvesting.webapp.api.model.RuleEngineResultResponse
import com.valueinvesting.webapp.ruleengine.RuleSignal
import com.valueinvesting.webapp.ruleengine.Signal
import com.valueinvesting.webapp.ruleengine.calculators.DcfMethod
import com.valueinvesting.webapp.service.AnalyzeTickerService
import com.ninjasquad.springmockk.MockkBean
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

@WebMvcTest(
    controllers = [AnalysisController::class],
    excludeAutoConfiguration = [
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration::class,
    ],
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler::class, ProblemDetailsMapper::class, ProblemDetailMvcConfig::class)
@ActiveProfiles("test")
class AnalysisControllerWebMvcTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var analyzeTickerService: AnalyzeTickerService

    // SecurityConfig (TSK-033) is picked up by classpath scan; mock its
    // sole constructor arg (UserDetailsServiceImpl) so the WebMvc slice
    // can build the context without wiring UserRepository.
    @MockkBean
    private lateinit var userDetailsService:
        com.valueinvesting.webapp.security.UserDetailsServiceImpl

    @Test
    fun `GET analysis returns RuleEngineResult with seven signals`() {
        val fixture = RuleEngineResultResponse(
            ticker = "AAPL",
            evaluatedAt = Instant.parse("2024-06-01T12:00:00Z"),
            // TSK-312 (EP-021): RuleSignal e' una sealed interface con sotto-tipi
            // vincolati ai ruleId canonici (ADR-028 §1). I fixture qui usano i
            // primi 7 ruleId Buffett-quality (EP-003), costruiti via sotto-tipo
            // tipato diretto. Legacy fields ancora valorizzati (R+1/R+2).
            signals = listOf(
                RuleSignal.Roe10yAvg(
                    signal = Signal.GREEN,
                    averagePercent = 1.0,
                    yearsAvailable = 10,
                    thresholdGreenPercent = 15.0,
                    thresholdYellowPercent = 10.0,
                    observedValue = 1.0,
                    threshold = "> 0",
                    rationale = "ok",
                ),
                RuleSignal.Roic10yAvg(
                    signal = Signal.GREEN,
                    averagePercent = 1.0,
                    yearsAvailable = 10,
                    thresholdGreenPercent = 12.0,
                    thresholdYellowPercent = 8.0,
                    observedValue = 1.0,
                    threshold = "> 0",
                    rationale = "ok",
                ),
                RuleSignal.GrossMargin10yAvg(
                    signal = Signal.GREEN,
                    averagePercent = 1.0,
                    thresholdGreenPercent = 40.0,
                    thresholdYellowPercent = 30.0,
                    observedValue = 1.0,
                    threshold = "> 0",
                    rationale = "ok",
                ),
                RuleSignal.NetMargin10yAvg(
                    signal = Signal.GREEN,
                    averagePercent = 1.0,
                    thresholdGreenPercent = 10.0,
                    observedValue = 1.0,
                    threshold = "> 0",
                    rationale = "ok",
                ),
                RuleSignal.CurrentRatioLatest(
                    signal = Signal.GREEN,
                    ratioLatest = 1.0,
                    thresholdGreen = 2.0,
                    thresholdYellow = 1.5,
                    observedValue = 1.0,
                    threshold = "> 0",
                    rationale = "ok",
                ),
                RuleSignal.DebtToIncomeLatest(
                    signal = Signal.GREEN,
                    ratioLatest = 1.0,
                    thresholdGreen = 4.0,
                    thresholdYellow = 5.0,
                    netIncomePositive = true,
                    observedValue = 1.0,
                    threshold = "> 0",
                    rationale = "ok",
                ),
                RuleSignal.CapexIntensity10yAvg(
                    signal = Signal.GREEN,
                    averagePercent = 1.0,
                    thresholdGreenPercent = 25.0,
                    thresholdYellowPercent = 30.0,
                    observedValue = 1.0,
                    threshold = "> 0",
                    rationale = "ok",
                ),
            ),
            grahamNumber = 47.43,
            dcfIntrinsicValue = 150.0,
            dcfMethod = DcfMethod.GREENWALD,
            dcfMethodSource = DcfMethodSource.DEFAULT_POLICY,
            mosSignal = Signal.GREEN,
            currentPriceAtEval = 100.0,
            dataSnapshotAt = Instant.parse("2024-06-01T10:00:00Z"),
            isStale = false,
        )
        every { analyzeTickerService.analyze("AAPL") } returns fixture

        mockMvc.get("/api/analysis/AAPL") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            header { string("X-Data-Snapshot-At", fixture.dataSnapshotAt.toString()) }
            header { string("X-Data-Stale", "false") }
            jsonPath("$.ticker") { value("AAPL") }
            jsonPath("$.dcfMethodSource") { value("DEFAULT_POLICY") }
            jsonPath("$.signals.length()") { value(7) }
            jsonPath("$.grahamNumber") { value(47.43) }
            jsonPath("$.dcfIntrinsicValue") { value(150.0) }
            jsonPath("$.dcfMethod") { value("GREENWALD") }
            jsonPath("$.mosSignal") { value("GREEN") }
        }
    }
}
