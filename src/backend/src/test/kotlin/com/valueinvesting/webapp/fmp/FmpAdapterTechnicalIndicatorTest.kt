package com.valueinvesting.webapp.fmp

import com.valueinvesting.webapp.config.AppProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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

// Contract test for FmpAdapterRestClient.getTechnicalIndicator (TSK-164, US-056/057, EP-013).
//
// Tests the HTTP contract of
//   /technical-indicators/{indicator}?symbol={ticker}&periodLength={n}&timeframe={tf}
// using MockRestServiceServer (standalone, no Spring context, no Testcontainers).
//
// Error-policy under test (mirrors getDividendHistory sentinel pattern):
//   - 4xx non-429  → emptyList()  (ticker IPO recente, EmptyTechnicalIndicatorSentinelException)
//   - 429          → FmpUnavailableException(429)
//   - 5xx          → FmpUnavailableException(status)
//
// Whitelist enforced: only "rsi" and "sma" are allowed (case-sensitive).
//
// [^src: management/kanban/EP-013-mr-market-context-flags/US-056-rsi-mr-market-context-flag/TSK-164.md]
// [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/fmp/FmpAdapterRestClient.kt §getTechnicalIndicator]
class FmpAdapterTechnicalIndicatorTest {

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
    // Test 1: RSI happy path — 30-record fixture, mapping + natural order preserved
    // -------------------------------------------------------------------------

    @Test
    fun `getTechnicalIndicator RSI maps 30-record fixture and returns list in FMP order`() {
        server.expect(
            ExpectedCount.once(),
            requestTo(org.hamcrest.Matchers.startsWith("$baseUrl/technical-indicators/rsi")),
        )
            .andExpect(method(org.springframework.http.HttpMethod.GET))
            .andExpect(queryParam("apikey", apiKey))
            .andExpect(queryParam("symbol", "AAPL"))
            .andExpect(queryParam("periodLength", "14"))
            .andExpect(queryParam("timeframe", "1day"))
            .andRespond(withSuccess(fixture("technical-rsi-aapl-30.json"), MediaType.APPLICATION_JSON))

        val result = adapter.getTechnicalIndicator(
            ticker = "AAPL",
            indicator = "rsi",
            periodLength = 14,
            timeframe = "1day",
        )

        assertAll(
            // 30 records returned as-is (FMP order preserved — no sorting in adapter)
            { assertThat(result).hasSize(30) },
            // First record = most recent in fixture (2026-05-25)
            { assertThat(result[0].date).isEqualTo("2026-05-25 16:00:00") },
            // DTO fields mapped correctly
            { assertThat(result[0].value).isEqualTo(20.0) },
            { assertThat(result[0].close).isEqualTo(182.0) },
            { assertThat(result[0].volume).isEqualTo(50_000_000L) },
            // Last record = oldest
            { assertThat(result[29].date).isEqualTo("2026-04-26 16:00:00") },
            { assertThat(result[29].value).isEqualTo(63.5) },
        )
        server.verify()
    }

    // -------------------------------------------------------------------------
    // Test 2: SMA happy path — 200-record fixture, mapping OK
    // -------------------------------------------------------------------------

    @Test
    fun `getTechnicalIndicator SMA maps 200-record fixture correctly`() {
        server.expect(
            ExpectedCount.once(),
            requestTo(org.hamcrest.Matchers.startsWith("$baseUrl/technical-indicators/sma")),
        )
            .andExpect(queryParam("symbol", "AAPL"))
            .andExpect(queryParam("periodLength", "200"))
            .andRespond(withSuccess(fixture("technical-sma-aapl-200.json"), MediaType.APPLICATION_JSON))

        val result = adapter.getTechnicalIndicator(
            ticker = "AAPL",
            indicator = "sma",
            periodLength = 200,
            timeframe = "1day",
        )

        assertAll(
            { assertThat(result).hasSize(200) },
            // First record = most recent in fixture (2026-05-25), SMA value
            { assertThat(result[0].date).isEqualTo("2026-05-25 16:00:00") },
            { assertThat(result[0].value).isEqualTo(170.0) },
            // Last record = oldest
            { assertThat(result[199].date).isEqualTo("2025-11-07 16:00:00") },
            { assertThat(result[199].value).isEqualTo(189.9) },
        )
        server.verify()
    }

    // -------------------------------------------------------------------------
    // Test 3: Whitelist enforcement — "macd" not allowed → IllegalArgumentException
    // -------------------------------------------------------------------------

    @Test
    fun `getTechnicalIndicator rejects indicator not in whitelist (macd)`() {
        // No HTTP call expected — IAE thrown before network access
        assertThatThrownBy {
            adapter.getTechnicalIndicator(
                ticker = "AAPL",
                indicator = "macd",
                periodLength = 14,
                timeframe = "1day",
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("macd")
        server.verify() // no calls expected
    }

    // -------------------------------------------------------------------------
    // Test 4: Whitelist enforcement (case-sensitive) — "RSI" not allowed
    // -------------------------------------------------------------------------

    @Test
    fun `getTechnicalIndicator whitelist is case-sensitive — RSI uppercase rejects`() {
        // Whitelist contains "rsi" (lowercase) only. "RSI" (uppercase) is a different key.
        assertThatThrownBy {
            adapter.getTechnicalIndicator(
                ticker = "AAPL",
                indicator = "RSI",
                periodLength = 14,
                timeframe = "1day",
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
        server.verify()
    }

    // -------------------------------------------------------------------------
    // Test 5: Empty body "[]" → emptyList() (no exception)
    // -------------------------------------------------------------------------

    @Test
    fun `getTechnicalIndicator returns empty list when FMP responds with empty array`() {
        server.expect(
            ExpectedCount.once(),
            requestTo(org.hamcrest.Matchers.startsWith("$baseUrl/technical-indicators/rsi")),
        )
            .andRespond(withSuccess(fixture("empty.json"), MediaType.APPLICATION_JSON))

        val result = adapter.getTechnicalIndicator(
            ticker = "NEWIPO",
            indicator = "rsi",
            periodLength = 14,
            timeframe = "1day",
        )

        assertThat(result).isEmpty()
        server.verify()
    }

    // -------------------------------------------------------------------------
    // Test 6: 404 → emptyList (sentinel: ticker IPO < periodLength days)
    // -------------------------------------------------------------------------

    @Test
    fun `getTechnicalIndicator returns empty list on 404 (IPO ticker policy)`() {
        server.expect(
            ExpectedCount.once(),
            requestTo(org.hamcrest.Matchers.startsWith("$baseUrl/technical-indicators/rsi")),
        )
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        val result = adapter.getTechnicalIndicator(
            ticker = "NEWCO",
            indicator = "rsi",
            periodLength = 14,
            timeframe = "1day",
        )

        assertThat(result).isEmpty()
        server.verify()
    }

    // -------------------------------------------------------------------------
    // Test 7: 400 generic 4xx (non-429) → emptyList (sentinel)
    // -------------------------------------------------------------------------

    @Test
    fun `getTechnicalIndicator returns empty list on generic 4xx non-429`() {
        server.expect(
            ExpectedCount.once(),
            requestTo(org.hamcrest.Matchers.startsWith("$baseUrl/technical-indicators/rsi")),
        )
            .andRespond(withStatus(HttpStatus.BAD_REQUEST))

        val result = adapter.getTechnicalIndicator(
            ticker = "AAPL",
            indicator = "rsi",
            periodLength = 14,
            timeframe = "1day",
        )

        assertThat(result).isEmpty()
        server.verify()
    }

    // -------------------------------------------------------------------------
    // Test 8: 429 → FmpUnavailableException(429) — must NOT be swallowed
    // -------------------------------------------------------------------------

    @Test
    fun `getTechnicalIndicator throws FmpUnavailableException on 429 (rate limit)`() {
        server.expect(
            ExpectedCount.once(),
            requestTo(org.hamcrest.Matchers.startsWith("$baseUrl/technical-indicators/rsi")),
        )
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS))

        assertThatThrownBy {
            adapter.getTechnicalIndicator(
                ticker = "AAPL",
                indicator = "rsi",
                periodLength = 14,
                timeframe = "1day",
            )
        }.isInstanceOf(FmpUnavailableException::class.java)
        server.verify()
    }

    // -------------------------------------------------------------------------
    // Test 9: 500 → FmpUnavailableException(500)
    // -------------------------------------------------------------------------

    @Test
    fun `getTechnicalIndicator throws FmpUnavailableException on 5xx`() {
        server.expect(
            ExpectedCount.once(),
            requestTo(org.hamcrest.Matchers.startsWith("$baseUrl/technical-indicators/sma")),
        )
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        assertThatThrownBy {
            adapter.getTechnicalIndicator(
                ticker = "AAPL",
                indicator = "sma",
                periodLength = 200,
                timeframe = "1day",
            )
        }.isInstanceOf(FmpUnavailableException::class.java)
        server.verify()
    }

    // -------------------------------------------------------------------------
    // Test 10: Blank ticker → IllegalArgumentException (pre-HTTP guard)
    // -------------------------------------------------------------------------

    @Test
    fun `getTechnicalIndicator rejects blank ticker`() {
        assertThatThrownBy {
            adapter.getTechnicalIndicator(
                ticker = "   ",
                indicator = "rsi",
                periodLength = 14,
                timeframe = "1day",
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
        server.verify()
    }
}
