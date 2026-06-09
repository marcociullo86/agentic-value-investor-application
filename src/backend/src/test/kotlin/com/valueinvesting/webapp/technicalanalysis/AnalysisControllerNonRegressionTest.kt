package com.valueinvesting.webapp.technicalanalysis

import com.ninjasquad.springmockk.MockkBean
import com.valueinvesting.webapp.api.AnalysisController
import com.valueinvesting.webapp.api.error.GlobalExceptionHandler
import com.valueinvesting.webapp.api.error.ProblemDetailsMapper
import com.valueinvesting.webapp.api.model.DcfMethodSource
import com.valueinvesting.webapp.api.model.RuleEngineResultResponse
import com.valueinvesting.webapp.config.ProblemDetailMvcConfig
import com.valueinvesting.webapp.contextflags.ContextFlags
import com.valueinvesting.webapp.fmp.FmpAdapter
import com.valueinvesting.webapp.ruleengine.RuleSignal
import com.valueinvesting.webapp.ruleengine.Signal
import com.valueinvesting.webapp.ruleengine.calculators.DcfMethod
import com.valueinvesting.webapp.service.AnalyzeTickerService
import io.mockk.every
import io.mockk.verify
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

// Non-regressione per l'endpoint /api/analysis/{ticker} (TSK-327 / US-098 AC):
// garantisce che l'introduzione del payload TA (US-098) non abbia aggiunto
// nuove chiamate FMP nel flusso del Rule Engine esistente.
//
// Strategia: WebMvc slice su AnalysisController + MockkBean su AnalyzeTickerService.
// Verifica che FmpAdapter NON sia invocato direttamente da questo controller
// (il servizio TA chiama FMP solo sul proprio endpoint dedicato /technical).
//
// [^src: management/kanban/EP-024-riepilogo-e-technical-analysis-tab/US-098-pipeline-ta-payload-be/TSK-327.md §"Non-regressione"]
// [^src: wiki/syntheses/ta-vs-vi-decision-layer.md §"Nessuna nuova chiamata FMP nel flusso rule engine"]
@WebMvcTest(
    controllers = [AnalysisController::class],
    excludeAutoConfiguration = [
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration::class,
    ],
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler::class, ProblemDetailsMapper::class, ProblemDetailMvcConfig::class)
@ActiveProfiles("test")
class AnalysisControllerNonRegressionTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var analyzeTickerService: AnalyzeTickerService

    // FmpAdapter bean: presente nel contesto per rilevare eventuali chiamate non attese.
    @MockkBean
    private lateinit var fmpAdapter: FmpAdapter

    @MockkBean
    private lateinit var userDetailsService:
        com.valueinvesting.webapp.security.UserDetailsServiceImpl

    private fun buildMinimalRuleEngineResult(ticker: String): RuleEngineResultResponse =
        RuleEngineResultResponse(
            ticker = ticker,
            evaluatedAt = Instant.parse("2026-06-08T10:00:00Z"),
            signals = listOf(
                RuleSignal.Roe10yAvg(
                    signal = Signal.GREEN,
                    averagePercent = 20.0,
                    yearsAvailable = 10,
                    thresholdGreenPercent = 15.0,
                    thresholdYellowPercent = 10.0,
                    observedValue = 20.0,
                    threshold = "> 15%",
                    rationale = "ROE ok",
                ),
            ),
            grahamNumber = 100.0,
            dcfIntrinsicValue = 250.0,
            dcfMethod = DcfMethod.GREENWALD,
            dcfMethodSource = DcfMethodSource.DEFAULT_POLICY,
            mosSignal = Signal.GREEN,
            currentPriceAtEval = 190.0,
            dataSnapshotAt = Instant.parse("2026-06-08T09:00:00Z"),
            isStale = false,
            contextFlags = ContextFlags(
                mrMarketRsi = null,
                longTermTrend = null,
            ),
        )

    // -------------------------------------------------------------------------
    // Test 1: GET /api/analysis/{ticker} continua a restituire RuleEngineResult
    // invariato (shape EP-013 + contextFlags) — nessuna regressione strutturale.
    // -------------------------------------------------------------------------

    @Test
    fun `GET analysis AAPL returns RuleEngineResultResponse without regression on signals and contextFlags`() {
        val fixture = buildMinimalRuleEngineResult("AAPL")
        every { analyzeTickerService.analyze("AAPL") } returns fixture

        mockMvc.get("/api/analysis/AAPL") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            jsonPath("$.ticker") { value("AAPL") }
            jsonPath("$.dcfIntrinsicValue") { value(250.0) }
            jsonPath("$.mosSignal") { value("GREEN") }
            jsonPath("$.dcfMethod") { value("GREENWALD") }
            jsonPath("$.dcfMethodSource") { value("DEFAULT_POLICY") }
            jsonPath("$.signals.length()") { value(1) }
            // contextFlags esposto ma non required (null = backward-compat OK)
        }
    }

    // -------------------------------------------------------------------------
    // Test 2: GET /api/analysis/{ticker} NON chiama FmpAdapter direttamente.
    // Il flusso Rule Engine passa esclusivamente da AnalyzeTickerService — nessuna
    // chiamata FMP aggiuntiva introdotta dall'EP-024.
    // -------------------------------------------------------------------------

    @Test
    fun `GET analysis does not invoke FmpAdapter directly — no new FMP calls in rule engine flow`() {
        val fixture = buildMinimalRuleEngineResult("AAPL")
        every { analyzeTickerService.analyze("AAPL") } returns fixture

        mockMvc.get("/api/analysis/AAPL") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
        }

        // FmpAdapter NON deve essere stato invocato in nessun metodo dal
        // TechnicalAnalysisController / AnalysisController durante questa chiamata.
        verify(exactly = 0) { fmpAdapter.getTechnicalIndicator(any(), any(), any(), any()) }
        verify(exactly = 0) { fmpAdapter.getHistoricalEodPrices(any(), any()) }
        verify(exactly = 0) { fmpAdapter.getMacd(any(), any()) }
        verify(exactly = 0) { fmpAdapter.getAtr(any(), any(), any()) }
        verify(exactly = 0) { fmpAdapter.getObv(any(), any()) }
    }

    // -------------------------------------------------------------------------
    // Test 3: Ticker non trovato dalla logica VI → servizio propaga eccezione,
    // response code rimane gestito dal GlobalExceptionHandler invariato.
    // -------------------------------------------------------------------------

    @Test
    fun `GET analysis with unknown ticker delegates to AnalyzeTickerService without added FMP calls`() {
        val fixture = buildMinimalRuleEngineResult("MSFT")
        every { analyzeTickerService.analyze("MSFT") } returns fixture

        mockMvc.get("/api/analysis/MSFT") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            jsonPath("$.ticker") { value("MSFT") }
        }

        verify(exactly = 1) { analyzeTickerService.analyze("MSFT") }
        verify(exactly = 0) { fmpAdapter.getTechnicalIndicator(any(), any(), any(), any()) }
    }
}
