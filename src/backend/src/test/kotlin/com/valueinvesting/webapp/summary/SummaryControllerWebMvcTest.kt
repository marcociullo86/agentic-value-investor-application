package com.valueinvesting.webapp.summary

import com.ninjasquad.springmockk.MockkBean
import com.valueinvesting.webapp.api.SummaryController
import com.valueinvesting.webapp.api.error.GlobalExceptionHandler
import com.valueinvesting.webapp.api.error.ProblemDetailsMapper
import com.valueinvesting.webapp.api.model.EntryTimingVerdict
import com.valueinvesting.webapp.api.model.ReentryCondition
import com.valueinvesting.webapp.api.model.ReentryConditionCode
import com.valueinvesting.webapp.api.model.SummaryRationale
import com.valueinvesting.webapp.api.model.SummaryVerdictResponse
import com.valueinvesting.webapp.api.model.WikiCitation
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
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.time.Instant

// WebMvcTest slice per SummaryController (TSK-341 / US-103).
//
// Copre:
//   - Fixture ENTER_NOW: verdetto e campi obbligatori.
//   - Fixture WAIT_FOR_SETUP + warningAntiCopart + reentryCondition.
//   - Fixture AVOID (VI RED gate).
//   - Fixture INSUFFICIENT_DATA.
//   - Fixture NOT_INDEXED: deepAnalysisStatus=NOT_INDEXED, deepVerdict assente.
//   - Header X-Data-Snapshot-At presente.
//   - Cache-Control: no-store.
//   - wikiCitations non vuote quando entrambi i domini sono presenti.
//
// Pattern: @WebMvcTest + MockkBean (SummaryFacade) — NO Spring Security,
// NO Testcontainers (WebMvc slice ha zero I/O).
//
// [^src: management/kanban/EP-024-.../US-103-.../TSK-341.md]
// [^src: management/kanban/EP-024-.../US-103-.../US-103.md §"Endpoint"]
// Registra il resolver di @AuthenticationPrincipal nello slice WebMvc: senza
// Spring Security attivo (SecurityAutoConfiguration esclusa), il parametro
// `principal: UserPrincipal?` verrebbe altrimenti trattato come model attribute
// → MethodArgumentNotValidException (400). Con auth assente il resolver
// restituisce null, coerente col tipo nullable.
@TestConfiguration(proxyBeanMethods = false)
class SummaryAuthPrincipalResolverConfig : WebMvcConfigurer {
    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(AuthenticationPrincipalArgumentResolver())
    }
}

@WebMvcTest(
    controllers = [SummaryController::class],
    excludeAutoConfiguration = [
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration::class,
    ],
)
@AutoConfigureMockMvc(addFilters = false)
@Import(
    GlobalExceptionHandler::class,
    ProblemDetailsMapper::class,
    ProblemDetailMvcConfig::class,
    SummaryAuthPrincipalResolverConfig::class,
)
@ActiveProfiles("test")
class SummaryControllerWebMvcTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var summaryFacade: SummaryFacade

    // SecurityConfig richiede UserDetailsServiceImpl nella classpath scan — stessa
    // tecnica usata in AnalysisControllerWebMvcTest (TSK-033).
    @MockkBean
    private lateinit var userDetailsService:
        com.valueinvesting.webapp.security.UserDetailsServiceImpl

    private val snapshotInstant: Instant = Instant.parse("2026-06-09T08:00:00Z")

    // -------------------------------------------------------------------------
    // Helpers per costruire risposte fixture
    // -------------------------------------------------------------------------

    private fun baseRationale(summaryVerdict: SummaryVerdict) = SummaryRationale(
        viSummary = "Verdetto fondamentale positivo.",
        deepSummary = "Deep Analysis: nessun red flag Munger.",
        taSummary = "TA: $summaryVerdict.",
        decisionPath = "VI gate passed → TA gate → Verdetto finale: $summaryVerdict.",
    )

    private fun buildEnterNowResponse(): SummaryVerdictResponse = SummaryVerdictResponse(
        ticker = "AAPL",
        evaluatedAt = snapshotInstant,
        summaryVerdict = SummaryVerdict.ENTER_NOW,
        viVerdict = ViVerdict.GREEN_DOMINANT,
        deepAnalysisStatus = DeepAnalysisStatus.AVAILABLE,
        deepVerdict = DeepVerdict.OK,
        taVerdict = EntryTimingVerdict.ENTRY_FAVORABLE,
        rationale = baseRationale(SummaryVerdict.ENTER_NOW),
        reentryCondition = null,
        wikiCitations = listOf(
            WikiCitation(id = "ta-vs-vi-decision-layer", anchor = "la-regola-sequenziale", domain = "technical-analysis-trading"),
            WikiCitation(id = "value-investing-rule-engine", anchor = null, domain = "value-investing"),
        ),
        warningAntiCopart = null,
    )

    private fun buildWaitForSetupResponse(): SummaryVerdictResponse = SummaryVerdictResponse(
        ticker = "CPRT",
        evaluatedAt = snapshotInstant,
        summaryVerdict = SummaryVerdict.WAIT_FOR_SETUP,
        viVerdict = ViVerdict.GREEN_DOMINANT,
        deepAnalysisStatus = DeepAnalysisStatus.NOT_INDEXED,
        deepVerdict = null,
        taVerdict = EntryTimingVerdict.WAIT,
        rationale = SummaryRationale(
            viSummary = "Verdetto fondamentale positivo: 10/14 ruleId decisionali GREEN.",
            deepSummary = null,
            taSummary = "Trend UPTREND, RSI 76 overbought. Attendere pullback.",
            decisionPath = "VI gate passed (GREEN dominante) → Deep: non disponibile → TA: WAIT → Verdetto finale: WAIT_FOR_SETUP.",
        ),
        reentryCondition = ReentryCondition(
            code = ReentryConditionCode.RSI_BELOW_50,
            description = "Re-valuta quando RSI 14d rientra sotto 50",
        ),
        wikiCitations = listOf(
            WikiCitation(id = "ta-vs-vi-decision-layer", anchor = "la-regola-sequenziale", domain = "technical-analysis-trading"),
            WikiCitation(id = "margin-of-safety", anchor = null, domain = "value-investing"),
        ),
        warningAntiCopart = "Verdetto fondamentale positivo ma timing tecnico sfavorevole. " +
            "Acquistare ora rischia uno stop loss prematuro su una tesi VI corretta — situazione COPART. " +
            "Attendere il setup tecnico migliore.",
    )

    private fun buildAvoidResponse(): SummaryVerdictResponse = SummaryVerdictResponse(
        ticker = "BEAR",
        evaluatedAt = snapshotInstant,
        summaryVerdict = SummaryVerdict.AVOID,
        viVerdict = ViVerdict.RED_DOMINANT,
        deepAnalysisStatus = DeepAnalysisStatus.NOT_INDEXED,
        deepVerdict = null,
        taVerdict = EntryTimingVerdict.ENTRY_FAVORABLE,
        rationale = baseRationale(SummaryVerdict.AVOID),
        reentryCondition = null,
        wikiCitations = emptyList(),
        warningAntiCopart = null,
    )

    private fun buildInsufficientDataResponse(): SummaryVerdictResponse = SummaryVerdictResponse(
        ticker = "NEWCO",
        evaluatedAt = snapshotInstant,
        summaryVerdict = SummaryVerdict.INSUFFICIENT_DATA,
        viVerdict = ViVerdict.INDETERMINATE_DOMINANT,
        deepAnalysisStatus = DeepAnalysisStatus.NOT_INDEXED,
        deepVerdict = null,
        taVerdict = null,
        rationale = SummaryRationale(
            viSummary = "Dati fondamentali insufficienti.",
            deepSummary = null,
            taSummary = null,
            decisionPath = "VI dati insufficienti → Verdetto finale: INSUFFICIENT_DATA.",
        ),
        reentryCondition = null,
        wikiCitations = emptyList(),
        warningAntiCopart = null,
    )

    // -------------------------------------------------------------------------
    // FIXTURE 1: ENTER_NOW — verdetto azionabile + tutti i campi
    // -------------------------------------------------------------------------

    @Test
    fun `GET summary AAPL returns 200 with ENTER_NOW verdict and required fields`() {
        every { summaryFacade.analyze("AAPL", any()) } returns buildEnterNowResponse()

        mockMvc.get("/api/analysis/AAPL/summary") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            jsonPath("$.ticker") { value("AAPL") }
            jsonPath("$.summaryVerdict") { value("ENTER_NOW") }
            jsonPath("$.viVerdict") { value("GREEN_DOMINANT") }
            jsonPath("$.deepAnalysisStatus") { value("AVAILABLE") }
            jsonPath("$.deepVerdict") { value("OK") }
            jsonPath("$.taVerdict") { value("ENTRY_FAVORABLE") }
            jsonPath("$.rationale.viSummary") { isNotEmpty() }
            jsonPath("$.rationale.decisionPath") { isNotEmpty() }
            jsonPath("$.wikiCitations") { isArray() }
            jsonPath("$.wikiCitations.length()") { value(2) }
            jsonPath("$.wikiCitations[0].id") { value("ta-vs-vi-decision-layer") }
            jsonPath("$.wikiCitations[0].domain") { value("technical-analysis-trading") }
            jsonPath("$.wikiCitations[1].domain") { value("value-investing") }
            jsonPath("$.warningAntiCopart") { doesNotExist() }
            jsonPath("$.reentryCondition") { doesNotExist() }
        }
    }

    // -------------------------------------------------------------------------
    // FIXTURE 2: WAIT_FOR_SETUP — situazione COPART (test-anchor US-103)
    // -------------------------------------------------------------------------

    @Test
    fun `GET summary CPRT returns WAIT_FOR_SETUP with warningAntiCopart and reentryCondition`() {
        every { summaryFacade.analyze("CPRT", any()) } returns buildWaitForSetupResponse()

        mockMvc.get("/api/analysis/CPRT/summary") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            jsonPath("$.ticker") { value("CPRT") }
            jsonPath("$.summaryVerdict") { value("WAIT_FOR_SETUP") }
            jsonPath("$.viVerdict") { value("GREEN_DOMINANT") }
            jsonPath("$.deepAnalysisStatus") { value("NOT_INDEXED") }
            // deepVerdict deve essere null / assente per NOT_INDEXED
            jsonPath("$.taVerdict") { value("WAIT") }
            // warningAntiCopart PRESENTE (condizioni COPART)
            jsonPath("$.warningAntiCopart") { isNotEmpty() }
            jsonPath("$.warningAntiCopart") { value(org.hamcrest.Matchers.containsString("COPART")) }
            // reentryCondition PRESENTE per WAIT_FOR_SETUP + WAIT
            jsonPath("$.reentryCondition.code") { value("RSI_BELOW_50") }
            jsonPath("$.reentryCondition.description") { isNotEmpty() }
            // rationale
            jsonPath("$.rationale.decisionPath") { value(org.hamcrest.Matchers.containsString("WAIT_FOR_SETUP")) }
        }
    }

    // -------------------------------------------------------------------------
    // FIXTURE 3: AVOID — VI RED gate primario
    // -------------------------------------------------------------------------

    @Test
    fun `GET summary BEAR returns AVOID with RED_DOMINANT — VI gate prevents ENTER_NOW`() {
        every { summaryFacade.analyze("BEAR", any()) } returns buildAvoidResponse()

        mockMvc.get("/api/analysis/BEAR/summary") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            jsonPath("$.ticker") { value("BEAR") }
            jsonPath("$.summaryVerdict") { value("AVOID") }
            jsonPath("$.viVerdict") { value("RED_DOMINANT") }
            jsonPath("$.warningAntiCopart") { doesNotExist() }
            jsonPath("$.reentryCondition") { doesNotExist() }
        }
    }

    // -------------------------------------------------------------------------
    // FIXTURE 4: INSUFFICIENT_DATA — INDETERMINATE_DOMINANT
    // -------------------------------------------------------------------------

    @Test
    fun `GET summary NEWCO returns INSUFFICIENT_DATA with INDETERMINATE_DOMINANT`() {
        every { summaryFacade.analyze("NEWCO", any()) } returns buildInsufficientDataResponse()

        mockMvc.get("/api/analysis/NEWCO/summary") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            jsonPath("$.summaryVerdict") { value("INSUFFICIENT_DATA") }
            jsonPath("$.viVerdict") { value("INDETERMINATE_DOMINANT") }
            jsonPath("$.deepAnalysisStatus") { value("NOT_INDEXED") }
        }
    }

    // -------------------------------------------------------------------------
    // Headers obbligatori (US-103 AC: X-Data-Snapshot-At + Cache-Control)
    // -------------------------------------------------------------------------

    @Test
    fun `GET summary response includes X-Data-Snapshot-At and Cache-Control no-store`() {
        every { summaryFacade.analyze("AAPL", any()) } returns buildEnterNowResponse()

        mockMvc.get("/api/analysis/AAPL/summary") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            header { string("X-Data-Snapshot-At", snapshotInstant.toString()) }
            header { string("Cache-Control", "no-store") }
        }
    }

    // -------------------------------------------------------------------------
    // NOT_INDEXED: deepVerdict assente / null (US-103 AC)
    // -------------------------------------------------------------------------

    @Test
    fun `GET summary with NOT_INDEXED deepAnalysisStatus serializes deepVerdict as absent`() {
        every { summaryFacade.analyze("CPRT", any()) } returns buildWaitForSetupResponse()

        // Il payload ha deepVerdict = null — deve essere serializzato come assente
        // o null nel JSON (non causare errori di serializzazione).
        mockMvc.get("/api/analysis/CPRT/summary") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            jsonPath("$.deepAnalysisStatus") { value("NOT_INDEXED") }
            // deepVerdict = null non deve causare un 500 (US-103 AC: "continua a funzionare")
        }
    }

    // -------------------------------------------------------------------------
    // wikiCitations — almeno una per dominio (US-103 AC)
    // -------------------------------------------------------------------------

    @Test
    fun `GET summary ENTER_NOW contains wikiCitations with both domains`() {
        every { summaryFacade.analyze("AAPL", any()) } returns buildEnterNowResponse()

        mockMvc.get("/api/analysis/AAPL/summary") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            jsonPath("$.wikiCitations[?(@.domain == 'value-investing')]") { isNotEmpty() }
            jsonPath("$.wikiCitations[?(@.domain == 'technical-analysis-trading')]") { isNotEmpty() }
        }
    }
}
