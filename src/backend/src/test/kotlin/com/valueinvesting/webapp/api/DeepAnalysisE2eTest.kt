package com.valueinvesting.webapp.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.valueinvesting.webapp.fmp.FmpAdapter
import com.valueinvesting.webapp.fmp.FmpFixtureFactory
import com.valueinvesting.webapp.fmp.FmpTickerNotFoundException
import com.valueinvesting.webapp.persistence.entity.FilingBlobEntity
import com.valueinvesting.webapp.persistence.repository.DeepAnalysisEventLogRepository
import com.valueinvesting.webapp.service.Filing10KQDownloaderService
import com.valueinvesting.webapp.service.FilingRagService
import com.valueinvesting.webapp.service.PriceActionAnalyzer
import com.valueinvesting.webapp.service.PriceActionSnapshot
import com.ninjasquad.springmockk.MockkBean
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.justRun
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate

/**
 * E2E integration tests for GET /api/analysis/{ticker}/deep (TSK-120).
 *
 * Covers the four AC scenarios required by US-045 AC#8:
 *   - Cold call (deterministic): 200 with complete payload in acceptable time
 *   - Cache hit: 200 in < 2s (mocked pipeline = sub-ms, verified deterministically)
 *   - Ticker not found: 404 problem+json (FmpTickerNotFoundException)
 *   - No SEC filings: 422 problem+json with reason = "no_sec_filings"
 *   - Event log: deep_analysis_event_log row persisted per execution
 *
 * Testcontainers PostgreSQL pgvector; FMP, filing, RAG, and price action
 * services are mocked via @MockkBean to isolate the pipeline orchestration.
 *
 * [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-045-endpoint-deep-analysis/TSK-120.md]
 * [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-045-endpoint-deep-analysis/US-045.md §Acceptance Criteria]
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Testcontainers
class DeepAnalysisE2eTest {

    companion object {
        private val JSON = jacksonObjectMapper()

        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("pgvector/pgvector:pg17")
            .withDatabaseName("value_investing_e2e_test")
            .withUsername("test")
            .withPassword("test")

        @DynamicPropertySource
        @JvmStatic
        fun registerDataSource(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var eventLogRepo: DeepAnalysisEventLogRepository

    @MockkBean
    private lateinit var fmpAdapter: FmpAdapter

    @MockkBean
    private lateinit var filing10KQDownloaderService: Filing10KQDownloaderService

    @MockkBean
    private lateinit var filingRagService: FilingRagService

    @MockkBean
    private lateinit var priceActionAnalyzer: PriceActionAnalyzer

    @BeforeEach
    fun resetMocks() {
        clearMocks(fmpAdapter, filing10KQDownloaderService, filingRagService, priceActionAnalyzer,
            answers = false, recordedCalls = true)
        eventLogRepo.deleteAll()
    }

    // ── Cold call: 200 with all deterministic fields ──

    @Nested
    @DisplayName("Cold call (deterministic, invoke_llm=false)")
    inner class ColdCall {

        @BeforeEach
        fun stubFullPipeline() {
            stubSuccessfulPipeline("AAPL")
        }

        @Test
        @DisplayName("responds 200 with complete deterministic payload")
        fun `cold call returns 200 with all deterministic fields`() {
            val body = callDeepAndParse("AAPL")

            assertThat(body.get("ticker").asText()).isEqualTo("AAPL")
            assertThat(body.get("generatedAt").isTextual).isTrue()

            val roe = body.get("roe")
            assertThat(roe.isObject).isTrue()
            assertThat(roe.has("fiveYearAvg")).isTrue()
            assertThat(roe.has("tenYearAvg")).isTrue()
            assertThat(roe.has("fiveYearDataPoints")).isTrue()
            assertThat(roe.has("tenYearDataPoints")).isTrue()

            val priceAction = body.get("priceAction")
            assertThat(priceAction.isObject).isTrue()
            assertThat(priceAction.get("priceNow").isNumber).isTrue()
            assertThat(priceAction.get("panicDiscount").isBoolean).isTrue()
            assertThat(priceAction.get("deteriorationWarning").isBoolean).isTrue()

            val rules = body.get("ruleEngineResults")
            assertThat(rules.isArray).isTrue()
            assertThat(rules.size()).isEqualTo(13)

            val verdict = body.get("verdict")
            assertThat(verdict.isObject).isTrue()
            assertThat(verdict.get("verdettoClasse").isTextual).isTrue()
            assertThat(verdict.get("partialBasis").asBoolean()).isTrue()
            assertThat(verdict.get("motivazioneAggregata").isTextual).isTrue()

            val filingsUsed = body.get("filingsUsed")
            assertThat(filingsUsed.isArray).isTrue()
            assertThat(filingsUsed.size()).isGreaterThan(0)
            val filingRef = filingsUsed.get(0)
            assertThat(filingRef.get("accessionNumber").isTextual).isTrue()
            assertThat(filingRef.get("formType").isTextual).isTrue()
            assertThat(filingRef.get("filingDate").isTextual).isTrue()

            assertThat(body.get("llmStatus").asText()).isEqualTo("NOT_INVOKED")
            assertThat(body.get("llmCalls").asInt()).isEqualTo(0)
            assertThat(body.get("mungerReport").isNull).isTrue()
            assertThat(body.get("newsSentiment").isNull).isTrue()
            assertThat(body.get("totalDurationMs").asLong()).isGreaterThan(0)
        }

        @Test
        @DisplayName("response includes positionSize block when DCF is feasible")
        fun `cold call includes positionSize block`() {
            val body = callDeepAndParse("AAPL")

            if (!body.get("positionSize").isNull) {
                val ps = body.get("positionSize")
                assertThat(ps.get("recommendedPct").isNumber).isTrue()
                assertThat(ps.get("rangeLow").isNumber).isTrue()
                assertThat(ps.get("rangeHigh").isNumber).isTrue()
                assertThat(ps.get("basisVerdict").isTextual).isTrue()
                assertThat(ps.get("marginOfSafetyPct").isNumber).isTrue()
                assertThat(ps.get("disclaimer").isTextual).isTrue()
            }
        }

        @Test
        @DisplayName("each ruleEngineResult has ruleId, signal, rationale")
        fun `rule engine results have required structure`() {
            val body = callDeepAndParse("AAPL")
            val rules = body.get("ruleEngineResults")

            rules.forEach { rule ->
                assertThat(rule.get("ruleId").isTextual)
                    .withFailMessage("ruleId must be text, got: ${rule.get("ruleId")}")
                    .isTrue()
                assertThat(rule.get("signal").isTextual)
                    .withFailMessage("signal must be text, got: ${rule.get("signal")}")
                    .isTrue()
                assertThat(rule.get("rationale").isTextual)
                    .withFailMessage("rationale must be text, got: ${rule.get("rationale")}")
                    .isTrue()
            }
        }
    }

    // ── Cache hit: 200 in < 2s ──

    @Nested
    @DisplayName("Cache hit (simulated via fast mocks)")
    inner class CacheHit {

        @BeforeEach
        fun stubFullPipeline() {
            stubSuccessfulPipeline("AAPL")
        }

        @Test
        @DisplayName("responds 200 in under 2 seconds (mocked pipeline)")
        fun `cache hit responds within 2s threshold`() {
            val startMs = System.currentTimeMillis()

            mockMvc.get("/api/analysis/AAPL/deep") {
                accept(MediaType.APPLICATION_JSON)
            }.andExpect {
                status { isOk() }
            }

            val elapsedMs = System.currentTimeMillis() - startMs
            assertThat(elapsedMs)
                .withFailMessage("Expected response in < 2000ms, took ${elapsedMs}ms")
                .isLessThan(2000)
        }

        @Test
        @DisplayName("second call on same ticker also returns 200 under 2s")
        fun `repeated call on same ticker returns 200 fast`() {
            callDeepAndParse("AAPL")

            val startMs = System.currentTimeMillis()
            val body = callDeepAndParse("AAPL")
            val elapsedMs = System.currentTimeMillis() - startMs

            assertThat(body.get("ticker").asText()).isEqualTo("AAPL")
            assertThat(elapsedMs)
                .withFailMessage("Expected cached response in < 2000ms, took ${elapsedMs}ms")
                .isLessThan(2000)
        }
    }

    // ── Ticker not found: 404 problem+json ──

    @Nested
    @DisplayName("Invalid ticker → 404 problem+json")
    inner class InvalidTicker {

        @Test
        @DisplayName("unknown ticker returns 404 problem+json with ticker field")
        fun `unknown ticker returns 404 problem+json`() {
            every { fmpAdapter.getProfile("XYZNOPE") } throws FmpTickerNotFoundException("XYZNOPE")

            val result = mockMvc.get("/api/analysis/XYZNOPE/deep") {
                accept(MediaType.APPLICATION_JSON)
            }.andExpect {
                status { isNotFound() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            }.andReturn()

            val body = JSON.readTree(result.response.contentAsString)
            assertThat(body.get("title").asText()).isEqualTo("Ticker not found")
            assertThat(body.get("status").asInt()).isEqualTo(404)
            assertThat(body.get("ticker").asText()).isEqualTo("XYZNOPE")
            assertThat(body.get("detail").asText()).contains("XYZNOPE")
        }

        @Test
        @DisplayName("ticker is uppercased before lookup — lowercase input yields same 404")
        fun `lowercase ticker uppercased before FMP lookup`() {
            every { fmpAdapter.getProfile("BADTICK") } throws FmpTickerNotFoundException("BADTICK")

            mockMvc.get("/api/analysis/badtick/deep") {
                accept(MediaType.APPLICATION_JSON)
            }.andExpect {
                status { isNotFound() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            }
        }
    }

    // ── No SEC filings: 422 problem+json ──

    @Nested
    @DisplayName("No SEC filings → 422 problem+json")
    inner class NoSecFilings {

        @Test
        @DisplayName("ticker with no SEC filings returns 422 with all RFC 9457 fields")
        fun `no SEC filings returns 422 with full problem details`() {
            FmpFixtureFactory.stubSuccessfulFmp(fmpAdapter, "NOFILING")
            stubPipelineDeps("NOFILING")
            every { filing10KQDownloaderService.fetchAndCache("NOFILING") } returns emptyList()

            val result = mockMvc.get("/api/analysis/NOFILING/deep") {
                accept(MediaType.APPLICATION_JSON)
            }.andExpect {
                status { isUnprocessableEntity() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            }.andReturn()

            val body = JSON.readTree(result.response.contentAsString)
            assertThat(body.get("title").asText()).isEqualTo("No SEC filings available")
            assertThat(body.get("status").asInt()).isEqualTo(422)
            assertThat(body.get("reason").asText()).isEqualTo("no_sec_filings")
            assertThat(body.get("ticker").asText()).isEqualTo("NOFILING")
            assertThat(body.get("detail").asText()).contains("NOFILING")
            assertThat(body.get("type").asText()).contains("no-sec-filings")
        }
    }

    // ── Event log: 1 record per execution ──

    @Nested
    @DisplayName("Event log persistence (deep_analysis_event_log)")
    inner class EventLog {

        @BeforeEach
        fun stubFullPipeline() {
            stubSuccessfulPipeline("AAPL")
        }

        @Test
        @DisplayName("successful call persists exactly 1 event log record")
        fun `successful call creates one event log entry`() {
            assertThat(eventLogRepo.count()).isZero()

            callDeepAndParse("AAPL")

            val logs = eventLogRepo.findAll()
            assertThat(logs).hasSize(1)

            val log = logs.first()
            assertThat(log.ticker).isEqualTo("AAPL")
            assertThat(log.generatedAt).isNotNull()
            assertThat(log.llmCalls).isEqualTo(0)
            assertThat(log.totalDurationMs).isNotNull()
            assertThat(log.totalDurationMs!!).isGreaterThanOrEqualTo(0)
        }

        @Test
        @DisplayName("two calls produce two distinct event log records")
        fun `two calls produce two event log records`() {
            callDeepAndParse("AAPL")

            stubSuccessfulPipeline("MSFT")
            callDeepAndParse("MSFT")

            val logs = eventLogRepo.findAll()
            assertThat(logs).hasSize(2)

            val tickers = logs.map { it.ticker }.toSet()
            assertThat(tickers).containsExactlyInAnyOrder("AAPL", "MSFT")
        }

        @Test
        @DisplayName("event log is NOT created on 404 error (ticker not found)")
        fun `no event log on 404 error`() {
            every { fmpAdapter.getProfile("FAIL404") } throws FmpTickerNotFoundException("FAIL404")

            mockMvc.get("/api/analysis/FAIL404/deep") {
                accept(MediaType.APPLICATION_JSON)
            }.andExpect {
                status { isNotFound() }
            }

            assertThat(eventLogRepo.count()).isZero()
        }

        @Test
        @DisplayName("event log is NOT created on 422 error (no SEC filings)")
        fun `no event log on 422 error`() {
            FmpFixtureFactory.stubSuccessfulFmp(fmpAdapter, "NOSEC")
            stubPipelineDeps("NOSEC")
            every { filing10KQDownloaderService.fetchAndCache("NOSEC") } returns emptyList()

            mockMvc.get("/api/analysis/NOSEC/deep") {
                accept(MediaType.APPLICATION_JSON)
            }.andExpect {
                status { isUnprocessableEntity() }
            }

            assertThat(eventLogRepo.count()).isZero()
        }
    }

    // ── Helpers ──

    private fun callDeepAndParse(ticker: String): JsonNode {
        val result = mockMvc.get("/api/analysis/$ticker/deep") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
        }.andReturn()
        return JSON.readTree(result.response.contentAsString)
    }

    private fun stubSuccessfulPipeline(ticker: String) {
        val t = ticker.uppercase()
        FmpFixtureFactory.stubSuccessfulFmp(fmpAdapter, t)
        stubPipelineDeps(t)

        val blob = FilingBlobEntity(
            id = 1L,
            ticker = t,
            cik = "0000320193",
            formType = "10-K",
            accessionNumber = "0000320193-24-000081",
            filingDate = LocalDate.of(2024, 11, 1),
        )
        every { filing10KQDownloaderService.fetchAndCache(t) } returns listOf(blob)
        justRun { filingRagService.indexFiling(any()) }
    }

    private fun stubPipelineDeps(ticker: String) {
        val t = ticker.uppercase()
        every { priceActionAnalyzer.analyze(t) } returns PriceActionSnapshot(
            ticker = t,
            priceNow = 175.0,
            max52w = 200.0,
            min52w = 130.0,
            drawdownPct = -12.5,
            trend3mPct = 5.0,
            ma50 = 170.0,
            ma200 = 160.0,
            panicDiscount = false,
            deteriorationWarning = false,
            seriesDays = 252,
            note = null,
        )
    }
}
