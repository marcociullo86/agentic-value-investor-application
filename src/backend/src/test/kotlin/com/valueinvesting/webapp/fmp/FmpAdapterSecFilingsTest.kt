package com.valueinvesting.webapp.fmp

import com.valueinvesting.webapp.config.AppProperties
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
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

// Contract test for FmpAdapterRestClient.getSecFilings (TSK-094, US-039, EP-011).
//
// Tests the HTTP contract of /stable/sec-filings-search/symbol using
// MockRestServiceServer (same pattern as FmpAdapterDividendHistoryTest).
//
// REGRESSIONE (root cause "No SEC filings for ticker=TTD"):
//   1. L'endpoint richiede `from`/`to` OBBLIGATORI (assenti → 400 BAD_REQUEST).
//   2. L'endpoint NON filtra per formType lato server e ritorna TUTTI i filing
//      DESC per data: con un limit basso (10) i 10-K/10-Q (rari) venivano esclusi
//      dalla pagina, dominata dai Form-4/8-K più recenti → risultato vuoto.
//
// Fix verificato: una chiamata per ciascun formType (formType=10-K, formType=10-Q),
// finestra di 15 mesi, page-limit ampio (1000), filtro client-side autoritativo.
class FmpAdapterSecFilingsTest {

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

    // Payload misto come lo restituisce realmente FMP (formType ignorato lato
    // server): contiene 10-K, 10-Q e rumore (8-K, Form 4). Il client deve isolare
    // solo il form type richiesto da ciascuna chiamata.
    private val mixedPayload = """
        [
          {"symbol":"TTD","formType":"8-K","filingDate":"2026-05-29 00:00:00",
           "finalLink":"https://www.sec.gov/Archives/edgar/data/1671933/0001/8k.htm"},
          {"symbol":"TTD","formType":"4","filingDate":"2026-05-27 00:00:00",
           "finalLink":"https://www.sec.gov/Archives/edgar/data/1671933/0002/f4.htm"},
          {"symbol":"TTD","formType":"10-Q","filingDate":"2026-05-07 00:00:00",
           "finalLink":"https://www.sec.gov/Archives/edgar/data/1671933/000167193326000054/q1.htm"},
          {"symbol":"TTD","formType":"10-K","filingDate":"2026-02-27 00:00:00",
           "finalLink":"https://www.sec.gov/Archives/edgar/data/1671933/000167193326000014/k.htm"},
          {"symbol":"TTD","formType":"10-Q","filingDate":"2025-11-06 00:00:00",
           "finalLink":"https://www.sec.gov/Archives/edgar/data/1671933/000167193325000144/q3.htm"}
        ]
    """.trimIndent()

    // -------------------------------------------------------------------------
    // REGRESSIONE: una chiamata per formType, con from/to/page obbligatori
    // -------------------------------------------------------------------------

    @Test
    fun `getSecFilings issues one call per form type with mandatory window params`() {
        val to = LocalDate.now()
        val from = to.minusMonths(15)
        val iso = DateTimeFormatter.ISO_LOCAL_DATE

        // Chiamata 1: formType=10-K
        server.expect(
            ExpectedCount.once(),
            requestTo(org.hamcrest.Matchers.startsWith("$baseUrl/sec-filings-search/symbol")),
        )
            .andExpect(method(org.springframework.http.HttpMethod.GET))
            .andExpect(queryParam("apikey", apiKey))
            .andExpect(queryParam("symbol", "TTD"))
            .andExpect(queryParam("formType", "10-K"))
            .andExpect(queryParam("from", from.format(iso)))
            .andExpect(queryParam("to", to.format(iso)))
            .andExpect(queryParam("page", "0"))
            .andRespond(withSuccess(mixedPayload, MediaType.APPLICATION_JSON))

        // Chiamata 2: formType=10-Q
        server.expect(
            ExpectedCount.once(),
            requestTo(org.hamcrest.Matchers.startsWith("$baseUrl/sec-filings-search/symbol")),
        )
            .andExpect(queryParam("formType", "10-Q"))
            .andExpect(queryParam("from", from.format(iso)))
            .andExpect(queryParam("to", to.format(iso)))
            .andRespond(withSuccess(mixedPayload, MediaType.APPLICATION_JSON))

        val result = adapter.getSecFilings("ttd")

        assertAll(
            // 1×10-K (dalla call 10-K) + 2×10-Q (dalla call 10-Q), niente 8-K/Form-4.
            { assertThat(result).hasSize(3) },
            { assertThat(result.map { it.formType }).containsExactly("10-Q", "10-K", "10-Q") },
            // Ordinati DESC per filingDate (merge tra i due set).
            { assertThat(result[0].filingDate).isEqualTo("2026-05-07 00:00:00") },
            { assertThat(result[1].filingDate).isEqualTo("2026-02-27 00:00:00") },
            { assertThat(result[2].filingDate).isEqualTo("2025-11-06 00:00:00") },
        )
        server.verify()
    }

    @Test
    fun `getSecFilings honours custom lookbackMonths window`() {
        val to = LocalDate.now()
        val from = to.minusMonths(24)
        val iso = DateTimeFormatter.ISO_LOCAL_DATE

        server.expect(
            ExpectedCount.once(),
            requestTo(org.hamcrest.Matchers.startsWith("$baseUrl/sec-filings-search/symbol")),
        )
            .andExpect(queryParam("formType", "10-K"))
            .andExpect(queryParam("from", from.format(iso)))
            .andExpect(queryParam("to", to.format(iso)))
            .andRespond(withSuccess(mixedPayload, MediaType.APPLICATION_JSON))

        adapter.getSecFilings("TTD", formTypes = listOf("10-K"), lookbackMonths = 24)
        server.verify()
    }

    // -------------------------------------------------------------------------
    // Client-side form-type filtering (FMP ignora formType lato server)
    // -------------------------------------------------------------------------

    @Test
    fun `getSecFilings keeps only the requested form type from each call`() {
        // Una sola call (formTypes=["10-K"]): dal payload misto deve restare il 10-K.
        server.expect(
            ExpectedCount.once(),
            requestTo(org.hamcrest.Matchers.startsWith("$baseUrl/sec-filings-search/symbol")),
        )
            .andExpect(queryParam("formType", "10-K"))
            .andRespond(withSuccess(mixedPayload, MediaType.APPLICATION_JSON))

        val result = adapter.getSecFilings("TTD", formTypes = listOf("10-K"))

        assertAll(
            { assertThat(result).hasSize(1) },
            { assertThat(result[0].formType).isEqualTo("10-K") },
        )
        server.verify()
    }

    @Test
    fun `getSecFilings caps total results at limit across form types`() {
        server.expect(
            ExpectedCount.once(),
            requestTo(org.hamcrest.Matchers.startsWith("$baseUrl/sec-filings-search/symbol")),
        )
            .andExpect(queryParam("formType", "10-Q"))
            .andRespond(withSuccess(mixedPayload, MediaType.APPLICATION_JSON))

        // Payload ha 2 10-Q; limit=1 → solo il più recente.
        val result = adapter.getSecFilings("TTD", formTypes = listOf("10-Q"), limit = 1)

        assertAll(
            { assertThat(result).hasSize(1) },
            { assertThat(result[0].filingDate).isEqualTo("2026-05-07 00:00:00") },
        )
        server.verify()
    }

    // -------------------------------------------------------------------------
    // Error policy (singolo form type per isolare la singola chiamata)
    // -------------------------------------------------------------------------

    @Test
    fun `getSecFilings returns empty list on 400 (no filings in window)`() {
        server.expect(
            ExpectedCount.once(),
            requestTo(org.hamcrest.Matchers.startsWith("$baseUrl/sec-filings-search/symbol")),
        )
            .andRespond(withStatus(HttpStatus.BAD_REQUEST))

        val result = adapter.getSecFilings("TTD", formTypes = listOf("10-K"))

        assertThat(result).isEmpty()
        server.verify()
    }

    @Test
    fun `getSecFilings returns empty list on empty array`() {
        server.expect(
            ExpectedCount.once(),
            requestTo(org.hamcrest.Matchers.startsWith("$baseUrl/sec-filings-search/symbol")),
        )
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON))

        val result = adapter.getSecFilings("TTD", formTypes = listOf("10-K"))

        assertThat(result).isEmpty()
        server.verify()
    }

    @Test
    fun `getSecFilings throws FmpUnavailableException on 429`() {
        server.expect(
            ExpectedCount.once(),
            requestTo(org.hamcrest.Matchers.startsWith("$baseUrl/sec-filings-search/symbol")),
        )
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS))

        assertThatThrownBy { adapter.getSecFilings("TTD", formTypes = listOf("10-K")) }
            .isInstanceOf(FmpUnavailableException::class.java)
        server.verify()
    }

    @Test
    fun `getSecFilings throws FmpUnavailableException on 5xx`() {
        server.expect(
            ExpectedCount.once(),
            requestTo(org.hamcrest.Matchers.startsWith("$baseUrl/sec-filings-search/symbol")),
        )
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        assertThatThrownBy { adapter.getSecFilings("TTD", formTypes = listOf("10-K")) }
            .isInstanceOf(FmpUnavailableException::class.java)
        server.verify()
    }

    // -------------------------------------------------------------------------
    // Argument validation
    // -------------------------------------------------------------------------

    @Test
    fun `getSecFilings rejects blank ticker`() {
        assertThatThrownBy { adapter.getSecFilings("  ") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `getSecFilings rejects non-positive limit`() {
        assertThatThrownBy { adapter.getSecFilings("TTD", limit = 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `getSecFilings rejects non-positive lookbackMonths`() {
        assertThatThrownBy { adapter.getSecFilings("TTD", lookbackMonths = 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
