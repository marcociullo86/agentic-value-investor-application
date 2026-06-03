package com.valueinvesting.webapp.api

import com.valueinvesting.webapp.api.error.GlobalExceptionHandler
import com.valueinvesting.webapp.api.error.ProblemDetailsMapper
import com.valueinvesting.webapp.config.ProblemDetailMvcConfig
import com.valueinvesting.webapp.api.model.HistoricalSeries
import com.valueinvesting.webapp.api.model.HistoricalSeriesPoint
import com.valueinvesting.webapp.fmp.FmpTickerNotFoundException
import com.valueinvesting.webapp.fmp.FmpUnavailableException
import com.valueinvesting.webapp.service.HistoricalSeriesService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
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

// MockMvc WebMvcTest per HistoricalController (US-015 / TSK-023).
// Copre DoD TSK-023: 200 con punti, 404 ticker non trovato, normalizzazione
// case ticker (passthrough al service).
//
// [^src: management/kanban/EP-005-dashboard-traffic-light-moat/US-015-grafici-storici/TSK-023.md §Test JUnit 5]
@WebMvcTest(controllers = [HistoricalController::class])
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler::class, ProblemDetailsMapper::class, ProblemDetailMvcConfig::class)
@ActiveProfiles("test")
class HistoricalControllerWebMvcTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var historicalSeriesService: HistoricalSeriesService

    private val snapshotAt = Instant.parse("2026-05-22T10:00:00Z")

    // ---- Test 1 — GET /api/historical/AAPL -> 200 + body + header. ----------
    @Test
    fun `GET historical with valid ticker returns 200 with points and snapshotAt header`() {
        every { historicalSeriesService.getSeries("AAPL") } returns HistoricalSeries(
            ticker = "AAPL",
            points = listOf(
                HistoricalSeriesPoint(fiscalYear = 2023, revenue = 100.0, netIncome = 20.0, isMissing = false),
                HistoricalSeriesPoint(fiscalYear = 2024, revenue = 110.0, netIncome = 22.0, isMissing = false),
            ),
            dataSnapshotAt = snapshotAt,
        )

        mockMvc.get("/api/historical/AAPL") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            header { string("X-Data-Snapshot-At", snapshotAt.toString()) }
            header { string("Cache-Control", "no-store") }
            jsonPath("$.ticker") { value("AAPL") }
            jsonPath("$.points.length()") { value(2) }
            jsonPath("$.points[0].fiscalYear") { value(2023) }
            jsonPath("$.points[0].revenue") { value(100.0) }
            jsonPath("$.points[0].netIncome") { value(20.0) }
            jsonPath("$.points[0].isMissing") { value(false) }
            jsonPath("$.dataSnapshotAt") { value(snapshotAt.toString()) }
        }
    }

    // ---- Test 2 — ticker inesistente -> 404 ProblemDetails. ------------------
    @Test
    fun `GET historical for unknown ticker returns 404 ProblemDetails`() {
        every { historicalSeriesService.getSeries("NONEXIST") } throws
            FmpTickerNotFoundException("NONEXIST")

        mockMvc.get("/api/historical/NONEXIST") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isNotFound() }
            content { contentType("application/problem+json") }
            jsonPath("$.title") { value("Ticker not found") }
            jsonPath("$.ticker") { value("NONEXIST") }
            jsonPath("$.properties") { doesNotExist() }
        }
    }

    // ---- Test 3 — ticker lowercase -> service riceve il valore raw (uppercase
    //                  e' responsabilita' del service, US-001 convention). ----
    @Test
    fun `GET historical passes raw lowercase ticker to service for normalization`() {
        val captured = slot<String>()
        every { historicalSeriesService.getSeries(capture(captured)) } returns HistoricalSeries(
            ticker = "AAPL",
            points = emptyList(),
            dataSnapshotAt = snapshotAt,
        )

        mockMvc.get("/api/historical/aapl") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            jsonPath("$.ticker") { value("AAPL") }
        }

        // Path passthrough come SearchController: la normalizzazione e' nel service.
        assertThat(captured.captured).isEqualTo("aapl")
    }

    // ---- Test 4 — FMP unavailable -> 503 ProblemDetails. ---------------------
    @Test
    fun `GET historical when FMP unavailable returns 503`() {
        every { historicalSeriesService.getSeries("AAPL") } throws
            FmpUnavailableException("FMP unavailable")

        mockMvc.get("/api/historical/AAPL") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isServiceUnavailable() }
            content { contentType("application/problem+json") }
            jsonPath("$.title") { value("Service Unavailable") }
        }
    }

    // ---- Test 5 — empty payload (ticker valido senza storia) -> 200 + []. ----
    @Test
    fun `GET historical with no history returns 200 and empty points`() {
        every { historicalSeriesService.getSeries("NEW") } returns HistoricalSeries(
            ticker = "NEW",
            points = emptyList(),
            dataSnapshotAt = snapshotAt,
        )

        mockMvc.get("/api/historical/NEW") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            jsonPath("$.points") { isArray() }
            jsonPath("$.points.length()") { value(0) }
            header { string("X-Data-Snapshot-At", snapshotAt.toString()) }
        }
    }
}
