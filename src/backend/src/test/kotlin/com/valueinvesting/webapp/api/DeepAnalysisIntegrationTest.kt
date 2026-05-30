package com.valueinvesting.webapp.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.valueinvesting.webapp.fmp.FmpAdapter
import com.valueinvesting.webapp.fmp.FmpFixtureLoader
import com.valueinvesting.webapp.fmp.dto.BalanceSheetDto
import com.valueinvesting.webapp.fmp.dto.IncomeStatementDto
import com.valueinvesting.webapp.persistence.entity.FilingBlobEntity
import com.valueinvesting.webapp.persistence.repository.FilingBlobRepository
import com.valueinvesting.webapp.persistence.repository.FilingChunkRepository
import com.valueinvesting.webapp.service.Filing10KQDownloaderService
import com.valueinvesting.webapp.service.FilingRagService
import com.valueinvesting.webapp.service.IndexResult
import com.valueinvesting.webapp.service.PriceActionAnalyzer
import com.valueinvesting.webapp.service.PriceActionSnapshot
import com.ninjasquad.springmockk.MockkBean
import io.mockk.clearMocks
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
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
 * Integration tests for GET /api/analysis/{ticker}/deep — full pipeline
 * verification including ROE dual lookback and deterministic blocks (TSK-118).
 *
 * Tests the full pipeline from controller through DeepAnalysisService →
 * RoeCalculator → response DTO serialization with controlled FMP fixtures.
 * Filing, RAG, and price action services are mocked to isolate FMP data flow.
 *
 * [^src: design_&_architecture/decisions/ADR-020-roe-lookback-policy-deep-analysis.md §Specifica payload Deep Analysis]
 * [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-045-endpoint-deep-analysis/TSK-118.md]
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Testcontainers
class DeepAnalysisIntegrationTest {

    companion object {
        private val JSON: ObjectMapper = jacksonObjectMapper()
        private val EPS = Offset.offset(1e-6)

        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("pgvector/pgvector:pg17")
            .withDatabaseName("value_investing_deep_test")
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

    @MockkBean
    private lateinit var fmpAdapter: FmpAdapter

    @MockkBean
    private lateinit var filing10KQDownloaderService: Filing10KQDownloaderService

    @MockkBean
    private lateinit var filingRagService: FilingRagService

    // Post EP-011 split (V028): analyze() legge i blob già in cache da
    // FilingBlobRepository (per `filingsUsed`) e — sul ramo LLM — il numero
    // di chunk indicizzati. Mockati per evitare di popolare la cache filing
    // a livello DB in test che si concentrano su ROE/verdetto.
    @MockkBean
    private lateinit var filingBlobRepository: FilingBlobRepository

    @MockkBean
    private lateinit var filingChunkRepository: FilingChunkRepository

    @MockkBean
    private lateinit var priceActionAnalyzer: PriceActionAnalyzer

    @BeforeEach
    fun resetMocks() {
        clearMocks(fmpAdapter, filing10KQDownloaderService, filingRagService,
            filingBlobRepository, filingChunkRepository, priceActionAnalyzer,
            answers = false, recordedCalls = true)
    }

    // ── Full data (10-year history) ──

    @Nested
    @DisplayName("Full 10-year data (stable ROE company)")
    inner class FullData {

        @BeforeEach
        fun stubFullHistory() {
            stubWithControlledRoe(
                ticker = "STABLE",
                incomeCount = 10,
                netIncomePerYear = 100.0,
                equityPerYear = 500.0,
            )
        }

        @Test
        fun `fiveYearAvg computed from 5 most recent years`() {
            val roe = callDeepRoeBlock("STABLE")

            assertThat(roe.get("fiveYearAvg").asDouble()).isCloseTo(0.20, EPS)
            assertThat(roe.get("fiveYearDataPoints").asInt()).isEqualTo(5)
        }

        @Test
        fun `tenYearAvg computed from all 10 years`() {
            val roe = callDeepRoeBlock("STABLE")

            assertThat(roe.get("tenYearAvg").asDouble()).isCloseTo(0.20, EPS)
            assertThat(roe.get("tenYearDataPoints").asInt()).isEqualTo(10)
        }

        @Test
        fun `response includes verdict and priceAction blocks`() {
            val body = callDeepAndParse("STABLE")

            assertThat(body.has("verdict")).isTrue()
            assertThat(body.get("verdict").has("verdettoClasse")).isTrue()
            assertThat(body.has("priceAction")).isTrue()
            assertThat(body.get("priceAction").get("panicDiscount").asBoolean()).isFalse()
        }
    }

    // ── IPO recente (< 5 years) ──

    @Nested
    @DisplayName("IPO recent: only 3 years of data")
    inner class IpoRecent {

        @BeforeEach
        fun stubShortHistory() {
            stubWithControlledRoe(
                ticker = "IPO3Y",
                incomeCount = 3,
                netIncomePerYear = 50.0,
                equityPerYear = 200.0,
            )
        }

        @Test
        fun `fiveYearAvg uses available 3 years, dataPoints is 3`() {
            val roe = callDeepRoeBlock("IPO3Y")

            assertThat(roe.get("fiveYearAvg").asDouble()).isCloseTo(0.25, EPS)
            assertThat(roe.get("fiveYearDataPoints").asInt()).isEqualTo(3)
        }

        @Test
        fun `tenYearAvg also uses 3 available years`() {
            val roe = callDeepRoeBlock("IPO3Y")

            assertThat(roe.get("tenYearAvg").asDouble()).isCloseTo(0.25, EPS)
            assertThat(roe.get("tenYearDataPoints").asInt()).isEqualTo(3)
        }
    }

    // ── Equity ≤ 0 in some years ──

    @Nested
    @DisplayName("Years with equity ≤ 0 excluded from computation")
    inner class NegativeEquity {

        @Test
        fun `years with zero equity excluded - fewer dataPoints`() {
            val incomes = listOf(
                IncomeStatementDto(netIncome = 100.0),
                IncomeStatementDto(netIncome = 80.0),
                IncomeStatementDto(netIncome = 60.0),
                IncomeStatementDto(netIncome = 40.0),
                IncomeStatementDto(netIncome = 20.0),
            )
            val balances = listOf(
                BalanceSheetDto(totalStockholdersEquity = 500.0),
                BalanceSheetDto(totalStockholdersEquity = 0.0),
                BalanceSheetDto(totalStockholdersEquity = 400.0),
                BalanceSheetDto(totalStockholdersEquity = -100.0),
                BalanceSheetDto(totalStockholdersEquity = 300.0),
            )
            stubWithExplicitData("NEGEQ", incomes, balances)

            val roe = callDeepRoeBlock("NEGEQ")

            assertThat(roe.get("fiveYearDataPoints").asInt()).isEqualTo(3)
            val expectedAvg = (100.0 / 500 + 60.0 / 400 + 20.0 / 300) / 3
            assertThat(roe.get("fiveYearAvg").asDouble()).isCloseTo(expectedAvg, EPS)
        }

        @Test
        fun `all equity lte zero returns null fiveYearAvg`() {
            val incomes = listOf(
                IncomeStatementDto(netIncome = 100.0),
                IncomeStatementDto(netIncome = 80.0),
                IncomeStatementDto(netIncome = 60.0),
            )
            val balances = listOf(
                BalanceSheetDto(totalStockholdersEquity = -100.0),
                BalanceSheetDto(totalStockholdersEquity = 0.0),
                BalanceSheetDto(totalStockholdersEquity = -50.0),
            )
            stubWithExplicitData("ALLEQ0", incomes, balances)

            val roe = callDeepRoeBlock("ALLEQ0")

            assertThat(roe.get("fiveYearAvg").isNull).isTrue()
            assertThat(roe.get("fiveYearDataPoints").asInt()).isEqualTo(0)
        }
    }

    // ── Divergence scenario (5y vs 10y) ──

    @Nested
    @DisplayName("Divergence between 5y and 10y ROE (turnaround company)")
    inner class Divergence {

        @Test
        fun `divergent ROE - 5y significantly higher than 10y`() {
            val incomes = (1..10).map { i ->
                val netIncome = if (i <= 5) 150.0 else 50.0
                IncomeStatementDto(netIncome = netIncome)
            }
            val balances = (1..10).map { BalanceSheetDto(totalStockholdersEquity = 500.0) }
            stubWithExplicitData("TURN", incomes, balances)

            val roe = callDeepRoeBlock("TURN")

            assertThat(roe.get("fiveYearAvg").asDouble()).isCloseTo(0.30, EPS)
            assertThat(roe.get("fiveYearDataPoints").asInt()).isEqualTo(5)

            val expectedTenYearAvg = (5 * 0.30 + 5 * 0.10) / 10
            assertThat(roe.get("tenYearAvg").asDouble()).isCloseTo(expectedTenYearAvg, EPS)
            assertThat(roe.get("tenYearDataPoints").asInt()).isEqualTo(10)
        }
    }

    // ── Error handling ──

    @Nested
    @DisplayName("Error scenarios (post EP-011 split V028)")
    inner class Errors {

        // Pre-split (TSK-118): analyze() chiamava fetchAndCache e lanciava
        // NoSecFilingsException quando vuoto → 422 reason=no_sec_filings.
        // Post-split (V028): analyze() NON scarica più filing; il ramo
        // deterministico produce verdetto anche con filingsUsed vuoto, e il
        // ramo LLM senza chunk indicizzati lancia FilingsNotIndexedException
        // → 409 reason=not_indexed. Manteniamo i due casi a documentare
        // entrambe le invarianti.

        @Test
        fun `deterministic branch ritorna 200 anche con cache filing vuota`() {
            stubWithControlledRoe("NOSEC", 5, 100.0, 500.0)
            // Override post-controllata: cache vuota → filingsUsed=[]
            every { filingBlobRepository.findByTickerOrderByFilingDateDesc("NOSEC") } returns emptyList()

            mockMvc.get("/api/analysis/NOSEC/deep") {
                accept(MediaType.APPLICATION_JSON)
            }.andExpect {
                status { isOk() }
            }
        }

        @Test
        fun `llm branch senza chunk indicizzati ritorna 409 reason not_indexed`() {
            stubWithControlledRoe("NOIDX", 5, 100.0, 500.0)
            every { filingBlobRepository.findByTickerOrderByFilingDateDesc("NOIDX") } returns emptyList()
            every { filingChunkRepository.countByTicker("NOIDX") } returns 0L

            val result = mockMvc.get("/api/analysis/NOIDX/deep?invoke_llm=true") {
                accept(MediaType.APPLICATION_JSON)
            }.andExpect {
                status { isConflict() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            }.andReturn()

            val body = JSON.readTree(result.response.contentAsString)
            assertThat(body.get("reason")?.asText()).isEqualTo("not_indexed")
        }
    }

    // ── Golden test: Munger prompt context (via log output) ──

    @Nested
    @DisplayName("Golden test: MungerPromptContextBuilder integration with deep endpoint")
    inner class MungerGoldenTest {

        @Test
        fun `divergent ROE values produce correct prompt context in response`() {
            val incomes = (1..10).map { i ->
                val netIncome = if (i <= 5) 150.0 else 90.0
                IncomeStatementDto(netIncome = netIncome)
            }
            val balances = (1..10).map { BalanceSheetDto(totalStockholdersEquity = 500.0) }
            stubWithExplicitData("DIVG", incomes, balances)

            val roe = callDeepRoeBlock("DIVG")

            assertThat(roe.get("fiveYearAvg").asDouble()).isCloseTo(0.30, EPS)
            assertThat(roe.get("tenYearAvg").asDouble()).isCloseTo(0.24, EPS)

            val divergence = Math.abs(roe.get("fiveYearAvg").asDouble() - roe.get("tenYearAvg").asDouble())
            assertThat(divergence * 100).isGreaterThan(5.0)
        }

        @Test
        fun `IPO recent with null fiveYearAvg serialized as JSON null`() {
            val incomes = listOf(
                IncomeStatementDto(netIncome = 100.0),
                IncomeStatementDto(netIncome = 80.0),
            )
            val balances = listOf(
                BalanceSheetDto(totalStockholdersEquity = -200.0),
                BalanceSheetDto(totalStockholdersEquity = -100.0),
            )
            stubWithExplicitData("NULLROE", incomes, balances)

            val body = callDeepAndParse("NULLROE")
            val roe = body.get("roe")

            assertThat(roe.get("fiveYearAvg").isNull).isTrue()
            assertThat(roe.get("tenYearAvg").isNull).isTrue()
            assertThat(roe.get("fiveYearDataPoints").asInt()).isEqualTo(0)
            assertThat(roe.get("tenYearDataPoints").asInt()).isEqualTo(0)
        }
    }

    // ── Helpers ──

    private fun callDeepRoeBlock(ticker: String): JsonNode {
        val body = callDeepAndParse(ticker)
        return body.get("roe")
    }

    private fun callDeepAndParse(ticker: String): JsonNode {
        val result = mockMvc.get("/api/analysis/$ticker/deep") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
        }.andReturn()
        return JSON.readTree(result.response.contentAsString)
    }

    private fun stubWithControlledRoe(
        ticker: String,
        incomeCount: Int,
        netIncomePerYear: Double,
        equityPerYear: Double,
    ) {
        val incomes = (1..incomeCount).map { IncomeStatementDto(netIncome = netIncomePerYear) }
        val balances = (1..incomeCount).map { BalanceSheetDto(totalStockholdersEquity = equityPerYear) }
        stubWithExplicitData(ticker, incomes, balances)
    }

    private fun stubWithExplicitData(
        ticker: String,
        incomes: List<IncomeStatementDto>,
        balances: List<BalanceSheetDto>,
    ) {
        every { fmpAdapter.getProfile(ticker.uppercase()) } returns FmpFixtureLoader.loadProfile("AAPL")
        every { fmpAdapter.getIncomeStatement(ticker.uppercase(), any()) } returns incomes
        every { fmpAdapter.getBalanceSheet(ticker.uppercase(), any()) } returns balances
        every { fmpAdapter.getCashFlow(ticker.uppercase(), any()) } returns FmpFixtureLoader.tenYearCashFlows("AAPL")
        every { fmpAdapter.getKeyMetrics(ticker.uppercase(), any()) } returns FmpFixtureLoader.tenYearKeyMetrics("AAPL")
        every { fmpAdapter.getDividendHistory(ticker.uppercase()) } returns emptyList()
        stubPipelineDeps(ticker.uppercase())
    }

    private fun stubPipelineDeps(ticker: String) {
        val blob = FilingBlobEntity(
            id = 1L,
            ticker = ticker,
            cik = "0000320193",
            formType = "10-K",
            accessionNumber = "0000320193-24-000081",
            filingDate = LocalDate.of(2024, 11, 1),
        )
        // Post V028 split: analyze() interroga filingBlobRepository (non più
        // fetchAndCache) per popolare il blocco reporting `filingsUsed`.
        every { filingBlobRepository.findByTickerOrderByFilingDateDesc(ticker) } returns listOf(blob)
        every { filingChunkRepository.countByTicker(ticker) } returns 1L
        // fetchAndCache + indexFiling restano stubbati: vivono nel ramo INGEST
        // (non esercitato qui), li teniamo per simmetria con E2eTest.
        every { filing10KQDownloaderService.fetchAndCache(ticker) } returns listOf(blob)
        every { filingRagService.indexFiling(any(), any()) } returns IndexResult(0, true)
        every { priceActionAnalyzer.analyze(ticker) } returns PriceActionSnapshot(
            ticker = ticker,
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
