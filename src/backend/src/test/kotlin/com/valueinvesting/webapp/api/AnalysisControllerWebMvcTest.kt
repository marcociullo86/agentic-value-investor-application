package com.valueinvesting.webapp.api

import com.valueinvesting.webapp.api.error.GlobalExceptionHandler
import com.valueinvesting.webapp.api.error.ProblemDetailsMapper
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
import org.springframework.http.HttpHeaders
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
@Import(GlobalExceptionHandler::class, ProblemDetailsMapper::class)
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
            signals = (1..7).map { i ->
                RuleSignal(
                    ruleId = "rule.$i",
                    signal = Signal.GREEN,
                    observedValue = 1.0,
                    threshold = "> 0",
                    rationale = "ok",
                )
            },
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
            header { string(HttpHeaders.VARY, "Origin, Authorization") }
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
