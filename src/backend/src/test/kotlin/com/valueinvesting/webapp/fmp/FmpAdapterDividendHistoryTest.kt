package com.valueinvesting.webapp.fmp

import com.valueinvesting.webapp.config.AppProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
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

// Contract test for FmpAdapterRestClient.getDividendHistory (TSK-086, TSK-083, US-037).
//
// Tests the HTTP contract of /stable/dividends?symbol={ticker} using
// MockRestServiceServer (same pattern as FmpAdapterRestClientTest — no Spring context,
// no Testcontainers, pure unit-level contract test).
//
// Covered scenarios:
//   1. Happy path: 80-record fixture (20 years × 4 quarterly payments) — verifies
//      DTO field mapping (date/dividend/adjDividend/yield/frequency/symbol) and that
//      the adapter returns them DESC by date (most recent first).
//   2. Empty body "[]" — ticker with no dividends (legitimate growth stock scenario)
//      returns emptyList() without exception.
//   3. 404 response — treated as "no dividends" per adapter error policy; returns
//      emptyList() without exception (different from fetchList() which throws
//      FmpTickerNotFoundException for 4xx — getDividendHistory uses sentinel logic).
//   4. 5xx response — throws FmpUnavailableException (same as other endpoints).
//
// Cache verification (second-call cache hit) requires a full Spring context with
// FmpCacheService + Testcontainers PostgreSQL. This is out of scope for a pure
// contract test. The cache path is covered indirectly by FmpCacheServiceTest and
// will be included in TSK-090 (E2E integration test).
//
// NOTE: ticker uppercase coercion is tested: input "aapl" must produce
// `symbol=AAPL` in the query string per getDividendHistory() contract.
//
// [^src: management/kanban/EP-010-graham-defensive-completeness/US-037-regola-continuita-dividendi-graham/TSK-086.md]
// [^src: management/kanban/EP-010-graham-defensive-completeness/US-037-regola-continuita-dividendi-graham/TSK-083.md]
class FmpAdapterDividendHistoryTest {

    private val baseUrl = "https://fmp.test/stable"
    private val apiKey = "test-key"

    private lateinit var server: MockRestServiceServer
    private lateinit var adapter: FmpAdapterRestClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        val props = AppProperties(
            fmp = AppProperties.Fmp(baseUrl = baseUrl, apiKey = apiKey, mock = true),
        )
        adapter = FmpAdapterRestClient(builder, props)
    }

    private fun fixture(name: String): String =
        ClassPathResource("fmp-fixtures/$name").inputStream
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

    // -------------------------------------------------------------------------
    // Scenario 1: Happy path — 20-year quarterly series (80 records)
    // -------------------------------------------------------------------------

    @Test
    fun `getDividendHistory maps 80-record fixture and returns list DESC by date`() {
        server.expect(
            ExpectedCount.once(),
            requestTo(org.hamcrest.Matchers.startsWith("$baseUrl/dividends")),
        )
            .andExpect(method(org.springframework.http.HttpMethod.GET))
            .andExpect(queryParam("apikey", apiKey))
            .andExpect(queryParam("symbol", "AAPL"))
            .andRespond(withSuccess(fixture("dividends-aapl-20y.json"), MediaType.APPLICATION_JSON))

        val result = adapter.getDividendHistory("AAPL")

        assertAll(
            // 20 years × 4 quarterly payments = 80 records
            { assertThat(result).hasSize(80) },
            // First record is most recent (DESC by date)
            { assertThat(result[0].date).isEqualTo("2024-11-15") },
            // Last record is oldest
            { assertThat(result[79].date).isEqualTo("2005-02-15") },
            // DTO fields mapped correctly from the first record
            { assertThat(result[0].symbol).isEqualTo("AAPL") },
            { assertThat(result[0].dividend).isEqualTo(0.25) },
            { assertThat(result[0].adjDividend).isEqualTo(0.25) },
            { assertThat(result[0].yield).isEqualTo(0.45) },
            { assertThat(result[0].frequency).isEqualTo("Quarterly") },
            // Non-null optional fields on a mid-series record
            { assertThat(result[0].recordDate).isNotNull() },
        )
        server.verify()
    }

    @Test
    fun `getDividendHistory uppercases ticker before sending to FMP`() {
        // Verify that lower-case input "aapl" results in query param symbol=AAPL
        server.expect(
            ExpectedCount.once(),
            requestTo(org.hamcrest.Matchers.startsWith("$baseUrl/dividends")),
        )
            .andExpect(queryParam("symbol", "AAPL"))   // must be uppercase
            .andRespond(withSuccess(fixture("dividends-aapl-20y.json"), MediaType.APPLICATION_JSON))

        val result = adapter.getDividendHistory("aapl")

        assertThat(result).isNotEmpty()
        server.verify()
    }

    // -------------------------------------------------------------------------
    // Scenario 2: Empty body "[]" — no dividends, no exception
    // -------------------------------------------------------------------------

    @Test
    fun `getDividendHistory returns empty list when FMP responds with empty array`() {
        server.expect(
            ExpectedCount.once(),
            requestTo(org.hamcrest.Matchers.startsWith("$baseUrl/dividends")),
        )
            .andExpect(queryParam("symbol", "BRK.A"))
            .andRespond(withSuccess(fixture("empty.json"), MediaType.APPLICATION_JSON))

        val result = adapter.getDividendHistory("BRK.A")

        assertThat(result).isEmpty()
        server.verify()
    }

    // -------------------------------------------------------------------------
    // Scenario 3: 404 response — treated as "no dividends", no exception
    // -------------------------------------------------------------------------

    @Test
    fun `getDividendHistory returns empty list on 404 (non-paying ticker policy)`() {
        // getDividendHistory deviates from fetchList: 4xx (non-429) is treated
        // as "no dividends available" rather than FmpTickerNotFoundException.
        // This is the EmptyDividendsSentinelException path in the implementation.
        server.expect(
            ExpectedCount.once(),
            requestTo(org.hamcrest.Matchers.startsWith("$baseUrl/dividends")),
        )
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        val result = adapter.getDividendHistory("UNKNOWN")

        assertThat(result).isEmpty()
        server.verify()
    }

    @Test
    fun `getDividendHistory returns empty list on any 4xx except 429`() {
        server.expect(
            ExpectedCount.once(),
            requestTo(org.hamcrest.Matchers.startsWith("$baseUrl/dividends")),
        )
            .andRespond(withStatus(HttpStatus.BAD_REQUEST))

        val result = adapter.getDividendHistory("TICKER")

        assertThat(result).isEmpty()
        server.verify()
    }

    // -------------------------------------------------------------------------
    // Scenario 4: 5xx response — throws FmpUnavailableException
    // -------------------------------------------------------------------------

    @Test
    fun `getDividendHistory throws FmpUnavailableException on 5xx`() {
        server.expect(
            ExpectedCount.once(),
            requestTo(org.hamcrest.Matchers.startsWith("$baseUrl/dividends")),
        )
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        org.assertj.core.api.Assertions.assertThatThrownBy {
            adapter.getDividendHistory("AAPL")
        }.isInstanceOf(FmpUnavailableException::class.java)
        server.verify()
    }

    @Test
    fun `getDividendHistory throws FmpUnavailableException on 429 (rate limit)`() {
        // 429 must NOT be silently swallowed to emptyList: it must propagate
        // as FmpUnavailableException so Resilience4j retry / circuit-breaker fire.
        server.expect(
            ExpectedCount.once(),
            requestTo(org.hamcrest.Matchers.startsWith("$baseUrl/dividends")),
        )
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS))

        org.assertj.core.api.Assertions.assertThatThrownBy {
            adapter.getDividendHistory("AAPL")
        }.isInstanceOf(FmpUnavailableException::class.java)
        server.verify()
    }

    // -------------------------------------------------------------------------
    // Argument validation
    // -------------------------------------------------------------------------

    @Test
    fun `getDividendHistory rejects blank ticker`() {
        org.assertj.core.api.Assertions.assertThatThrownBy {
            adapter.getDividendHistory("  ")
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    // -------------------------------------------------------------------------
    // DESC ordering — adapter sorts result regardless of FMP response order
    // -------------------------------------------------------------------------

    @Test
    fun `getDividendHistory sorts result DESC by date even if fixture is not ordered`() {
        // Build a 3-record payload intentionally in ascending (wrong) order.
        val unordered = """
            [
              {"symbol":"TEST","date":"2020-01-15","dividend":0.10,"adjDividend":0.10,"frequency":"Annual"},
              {"symbol":"TEST","date":"2024-01-15","dividend":0.20,"adjDividend":0.20,"frequency":"Annual"},
              {"symbol":"TEST","date":"2022-01-15","dividend":0.15,"adjDividend":0.15,"frequency":"Annual"}
            ]
        """.trimIndent()

        server.expect(
            ExpectedCount.once(),
            requestTo(org.hamcrest.Matchers.startsWith("$baseUrl/dividends")),
        )
            .andRespond(withSuccess(unordered, MediaType.APPLICATION_JSON))

        val result = adapter.getDividendHistory("TEST")

        assertAll(
            { assertThat(result).hasSize(3) },
            { assertThat(result[0].date).isEqualTo("2024-01-15") },
            { assertThat(result[1].date).isEqualTo("2022-01-15") },
            { assertThat(result[2].date).isEqualTo("2020-01-15") },
        )
        server.verify()
    }

    @Test
    fun `getDividendHistory places null-date records at the end of DESC list`() {
        // Records with null date should be sorted last per nullsLast comparator.
        val withNullDate = """
            [
              {"symbol":"TEST","date":"2024-01-15","dividend":0.20},
              {"symbol":"TEST","date":null,"dividend":0.10},
              {"symbol":"TEST","date":"2022-01-15","dividend":0.15}
            ]
        """.trimIndent()

        server.expect(
            ExpectedCount.once(),
            requestTo(org.hamcrest.Matchers.startsWith("$baseUrl/dividends")),
        )
            .andRespond(withSuccess(withNullDate, MediaType.APPLICATION_JSON))

        val result = adapter.getDividendHistory("TEST")

        assertAll(
            { assertThat(result).hasSize(3) },
            { assertThat(result[0].date).isEqualTo("2024-01-15") },
            { assertThat(result[1].date).isEqualTo("2022-01-15") },
            { assertThat(result[2].date).isNull() },
        )
        server.verify()
    }
}
