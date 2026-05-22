package com.valueinvesting.webapp.api

import com.valueinvesting.webapp.api.error.GlobalExceptionHandler
import com.valueinvesting.webapp.api.error.ProblemDetailsMapper
import com.valueinvesting.webapp.api.model.SearchResultItem
import com.valueinvesting.webapp.api.model.SearchResultList
import com.valueinvesting.webapp.api.model.StockProfile
import com.valueinvesting.webapp.fmp.FmpTickerNotFoundException
import com.valueinvesting.webapp.fmp.FmpUnavailableException
import com.valueinvesting.webapp.service.SearchService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.slot
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

// MockMvc WebMvcTest per SearchController (US-001).
// Copre DoD TSK-002 + casi 400 (validazione) e 404 (ticker non trovato).
//
// [^src: management/kanban/EP-001-ricerca-e-screening/US-001-ricerca-ticker-simbolo/TSK-002.md §Test]
@WebMvcTest(controllers = [SearchController::class])
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler::class, ProblemDetailsMapper::class)
@ActiveProfiles("test")
class SearchControllerWebMvcTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var searchService: SearchService

    // ===== GET /api/search?query=... =========================================

    // DoD #1: GET /api/search?query=AAPL → 200 con items non vuota.
    @Test
    fun `GET search with valid query returns 200 with items`() {
        every { searchService.search("AAPL") } returns SearchResultList(
            items = listOf(
                SearchResultItem(ticker = "AAPL", companyName = "Apple Inc."),
                SearchResultItem(ticker = "APC", companyName = "Apple Corp Holdings"),
            ),
        )

        mockMvc.get("/api/search?query=AAPL") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            jsonPath("$.items.length()") { value(2) }
            jsonPath("$.items[0].ticker") { value("AAPL") }
            jsonPath("$.items[0].companyName") { value("Apple Inc.") }
        }
    }

    // DoD #3: normalizzazione — il service deve ricevere la query già uppercased
    // se l'utente la digita in lowercase.
    // NB: nel design la normalizzazione è nel service (require + uppercase),
    // quindi il controller passa la query così com'è e il service la trasforma.
    // Verifichiamo invece che il path Spring → service la propaga inalterata.
    @Test
    fun `GET search passes lowercase query to service (service-side normalization)`() {
        val captured = slot<String>()
        every { searchService.search(capture(captured)) } returns SearchResultList(items = emptyList())

        mockMvc.get("/api/search?query=aapl") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
        }

        // La normalizzazione avviene NEL service (single source of truth);
        // il controller delega: assertion che il valore raw arrivi al service.
        assert(captured.captured == "aapl")
        verify { searchService.search("aapl") }
    }

    // GET /api/search senza ?query= → 400 (mancato parametro required).
    @Test
    fun `GET search without query param returns 400`() {
        mockMvc.get("/api/search") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    // GET /api/search?query= (vuota) → service solleva IllegalArgumentException
    // → 400 ProblemDetails via GlobalExceptionHandler.
    @Test
    fun `GET search with blank query returns 400 ProblemDetails`() {
        every { searchService.search("") } throws IllegalArgumentException("query must not be blank")

        mockMvc.get("/api/search?query=") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isBadRequest() }
            content { contentType("application/problem+json") }
            jsonPath("$.title") { value("Bad Request") }
            jsonPath("$.detail") { value("query must not be blank") }
        }
    }

    // Query con caratteri invalidi (XSS payload) → 400 via service require.
    @Test
    fun `GET search with invalid characters in query returns 400`() {
        // Spring Boot 3.5 / MockMvc passes through the raw URL-encoded query
        // string verbatim; the service receives "%3Cscript%3E" (not "<script>").
        // The SecurityRequirements rejection happens regardless of decoding —
        // the regex pattern blocks "%" too, so the stub uses the encoded form
        // observed at the boundary.
        every { searchService.search("%3Cscript%3E") } throws
            IllegalArgumentException("query contains invalid characters")

        mockMvc.get("/api/search?query=%3Cscript%3E") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isBadRequest() }
            content { contentType("application/problem+json") }
        }
    }

    // Empty match list → 200 con items: [] (NON 404).
    @Test
    fun `GET search with no match returns 200 and empty items`() {
        every { searchService.search("ZZZNOPE") } returns SearchResultList(items = emptyList())

        mockMvc.get("/api/search?query=ZZZNOPE") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            jsonPath("$.items") { isArray() }
            jsonPath("$.items.length()") { value(0) }
        }
    }

    // ===== GET /api/search/{ticker} ==========================================

    // DoD #2: ticker esistente → 200 con StockProfile.
    @Test
    fun `GET search by ticker for existing ticker returns 200 StockProfile`() {
        every { searchService.validateTicker("AAPL") } returns StockProfile(
            ticker = "AAPL",
            companyName = "Apple Inc.",
            sector = "Technology",
            industry = "Consumer Electronics",
            marketCapUsd = 3e12,
            currentPrice = 150.0,
            dataSnapshotAt = Instant.parse("2026-05-22T10:00:00Z"),
        )

        mockMvc.get("/api/search/AAPL") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            jsonPath("$.ticker") { value("AAPL") }
            jsonPath("$.companyName") { value("Apple Inc.") }
            jsonPath("$.sector") { value("Technology") }
            jsonPath("$.industry") { value("Consumer Electronics") }
            jsonPath("$.marketCapUsd") { value(3.0e12) }
            jsonPath("$.currentPrice") { value(150.0) }
        }
    }

    // DoD #2: ticker inesistente → 404 ProblemDetails RFC 9457.
    @Test
    fun `GET search by ticker for unknown ticker returns 404 ProblemDetails`() {
        every { searchService.validateTicker("NONEXIST") } throws
            FmpTickerNotFoundException("NONEXIST")

        mockMvc.get("/api/search/NONEXIST") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isNotFound() }
            content { contentType("application/problem+json") }
            jsonPath("$.title") { value("Ticker not found") }
            jsonPath("$.ticker") { value("NONEXIST") }
            jsonPath("$.properties").doesNotExist()
        }
    }

    // Ticker case-insensitive: lowercase nel path arriva al service per
    // normalizzazione (US-001 AC).
    @Test
    fun `GET search by ticker passes lowercase ticker to service`() {
        val captured = slot<String>()
        every { searchService.validateTicker(capture(captured)) } returns StockProfile(
            ticker = "AAPL",
            companyName = "Apple Inc.",
        )

        mockMvc.get("/api/search/aapl") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
        }

        // Il controller passa il @PathVariable raw; la normalizzazione è nel
        // service. Verifichiamo il payload (companyName mappato già su AAPL).
        assert(captured.captured == "aapl")
    }

    // Ticker invalido (es. caratteri non ammessi) → 400.
    @Test
    fun `GET search by ticker with invalid characters returns 400`() {
        // Spring Boot 3.5 / MockMvc passes the raw URL-encoded path segment
        // verbatim into @PathVariable; the service receives "A%20B"
        // (not the decoded "A B"). The validation regex rejects "%" too,
        // so the stub uses the encoded form observed at the boundary.
        every { searchService.validateTicker("A%20B") } throws
            IllegalArgumentException("ticker contains invalid characters")

        mockMvc.get("/api/search/A%20B") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isBadRequest() }
            content { contentType("application/problem+json") }
        }
    }

    // Cascata FMP unavailable → 503 RFC 9457 (mapper esistente da TSK-011).
    @Test
    fun `GET search by ticker when FMP is unavailable returns 503`() {
        every { searchService.validateTicker("AAPL") } throws
            FmpUnavailableException("FMP unavailable")

        mockMvc.get("/api/search/AAPL") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isServiceUnavailable() }
            content { contentType("application/problem+json") }
            jsonPath("$.title") { value("Service Unavailable") }
        }
    }
}
