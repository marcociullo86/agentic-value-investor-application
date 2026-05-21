package com.valueinvesting.webapp.fmp

import com.valueinvesting.webapp.config.AppProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

// Unit test for FmpAdapterRestClient with MockRestServiceServer + JSON fixtures.
// Copre i 4 endpoint, ordering, null preservation e ticker not found.
// [^src: design_&_architecture/components/backend-components.md §Testing strategy]
class FmpAdapterRestClientTest {

    private val baseUrl = "https://fmp.test/api/v3"
    private val apiKey = "test-key"

    private lateinit var server: MockRestServiceServer
    private lateinit var adapter: FmpAdapterRestClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        // Bind a MockRestServiceServer to the *builder*, then build the adapter with it.
        server = MockRestServiceServer.bindTo(builder).build()
        val props = AppProperties(
            fmp = AppProperties.Fmp(baseUrl = baseUrl, apiKey = apiKey, mock = true),
        )
        adapter = FmpAdapterRestClient(builder, props)
    }

    private fun fixture(name: String): String =
        ClassPathResource("fmp-fixtures/$name").inputStream
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

    // --- Happy path: income statement ----------------------------------------

    @Test
    fun `getIncomeStatement returns ordered list and preserves nulls`() {
        server.expect(ExpectedCount.once(), requestTo(org.hamcrest.Matchers.startsWith("$baseUrl/income-statement/AAPL")))
            .andExpect(method(org.springframework.http.HttpMethod.GET))
            .andExpect(queryParam("apikey", apiKey))
            .andExpect(queryParam("limit", "10"))
            .andRespond(
                withSuccess(fixture("income-statement-aapl.json"), MediaType.APPLICATION_JSON),
            )

        val result = adapter.getIncomeStatement("aapl")

        assertThat(result).hasSize(2)
        // Ordering preserved from FMP (most recent first).
        assertThat(result[0].calendarYear).isEqualTo("2024")
        assertThat(result[1].calendarYear).isEqualTo("2023")
        // Null-safety: 2023 record has `researchAndDevelopmentExpenses: null` -> must stay null.
        assertThat(result[1].researchAndDevelopmentExpenses).isNull()
        // Non-null fields hydrate correctly.
        assertThat(result[0].revenue).isEqualTo(391_035_000_000.0)
        assertThat(result[0].netIncome).isEqualTo(93_736_000_000.0)
        server.verify()
    }

    // --- Happy path: balance sheet -------------------------------------------

    @Test
    fun `getBalanceSheet maps DTO fields including absent (null) ones`() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("$baseUrl/balance-sheet-statement/AAPL")))
            .andExpect(method(org.springframework.http.HttpMethod.GET))
            .andRespond(withSuccess(fixture("balance-sheet-aapl.json"), MediaType.APPLICATION_JSON))

        val result = adapter.getBalanceSheet("AAPL")

        assertThat(result).hasSize(2)
        // 2023 record omits propertyPlantEquipmentNet entirely -> Jackson should leave it null,
        // never 0.0 (US-004 AC).
        assertThat(result[1].propertyPlantEquipmentNet).isNull()
        assertThat(result[0].totalAssets).isEqualTo(364_980_000_000.0)
        server.verify()
    }

    // --- Happy path: cash flow -----------------------------------------------

    @Test
    fun `getCashFlow returns expected list`() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("$baseUrl/cash-flow-statement/AAPL")))
            .andRespond(withSuccess(fixture("cash-flow-aapl.json"), MediaType.APPLICATION_JSON))

        val result = adapter.getCashFlow("AAPL")

        assertThat(result).hasSize(2)
        assertThat(result[0].freeCashFlow).isEqualTo(108_807_000_000.0)
        assertThat(result[1].dividendsPaid).isNull() // not in 2023 fixture record
        server.verify()
    }

    // --- Happy path: key metrics ---------------------------------------------

    @Test
    fun `getKeyMetrics preserves null roic for 2023`() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("$baseUrl/key-metrics/AAPL")))
            .andRespond(withSuccess(fixture("key-metrics-aapl.json"), MediaType.APPLICATION_JSON))

        val result = adapter.getKeyMetrics("AAPL")

        assertThat(result).hasSize(2)
        assertThat(result[0].roic).isEqualTo(0.628)
        // Critical: explicit `roic: null` must remain null (NOT 0.0).
        assertThat(result[1].roic).isNull()
        server.verify()
    }

    // --- Ticker not found: empty list ----------------------------------------

    @Test
    fun `empty array from FMP triggers FmpTickerNotFoundException`() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("$baseUrl/income-statement/ZZZZ")))
            .andRespond(withSuccess(fixture("empty.json"), MediaType.APPLICATION_JSON))

        assertThatThrownBy { adapter.getIncomeStatement("ZZZZ") }
            .isInstanceOf(FmpTickerNotFoundException::class.java)
            .hasMessageContaining("ZZZZ")
        server.verify()
    }

    // --- Ticker not found: 404 from FMP --------------------------------------

    @Test
    fun `4xx response from FMP triggers FmpTickerNotFoundException`() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("$baseUrl/income-statement/ZZZZ")))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        assertThatThrownBy { adapter.getIncomeStatement("ZZZZ") }
            .isInstanceOf(FmpTickerNotFoundException::class.java)
        server.verify()
    }

    // --- 5xx -> FmpUnavailableException --------------------------------------

    @Test
    fun `5xx response from FMP triggers FmpUnavailableException`() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("$baseUrl/income-statement/AAPL")))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        assertThatThrownBy { adapter.getIncomeStatement("AAPL") }
            .isInstanceOf(FmpUnavailableException::class.java)
        server.verify()
    }

    // --- Limit ≤ 10 enforced by parameter ------------------------------------

    @Test
    fun `default limit is 10 propagated to query string`() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("limit=10")))
            .andRespond(withSuccess(fixture("income-statement-aapl.json"), MediaType.APPLICATION_JSON))

        adapter.getIncomeStatement("AAPL") // default limit
        server.verify()
    }

    @Test
    fun `custom limit overrides default`() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("limit=5")))
            .andRespond(withSuccess(fixture("income-statement-aapl.json"), MediaType.APPLICATION_JSON))

        adapter.getIncomeStatement("AAPL", limit = 5)
        server.verify()
    }

    // --- Argument validation -------------------------------------------------

    @Test
    fun `blank ticker is rejected`() {
        assertThatThrownBy { adapter.getIncomeStatement("  ") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `non-positive limit is rejected`() {
        assertThatThrownBy { adapter.getIncomeStatement("AAPL", limit = 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
