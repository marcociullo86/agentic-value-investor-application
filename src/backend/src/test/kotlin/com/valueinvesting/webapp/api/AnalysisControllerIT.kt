package com.valueinvesting.webapp.api

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
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.Instant

@WebMvcTest(controllers = [AnalysisController::class])
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class AnalysisControllerIT {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var analyzeTickerService: AnalyzeTickerService

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
            jsonPath("$.signals.length()") { value(7) }
            jsonPath("$.grahamNumber") { value(47.43) }
            jsonPath("$.dcfIntrinsicValue") { value(150.0) }
            jsonPath("$.dcfMethod") { value("GREENWALD") }
            jsonPath("$.mosSignal") { value("GREEN") }
        }
    }
}
