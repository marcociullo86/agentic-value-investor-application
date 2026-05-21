package com.valueinvesting.webapp.api

import com.valueinvesting.webapp.api.error.GlobalExceptionHandler
import com.valueinvesting.webapp.api.error.ProblemDetailsMapper
import com.valueinvesting.webapp.api.model.ScreenerResultPage
import com.valueinvesting.webapp.api.model.SearchResultItem
import com.valueinvesting.webapp.service.ScreenerCriteria
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

@WebMvcTest(controllers = [ScreenerController::class])
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler::class, ProblemDetailsMapper::class)
@ActiveProfiles("test")
class ScreenerControllerWebMvcTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var searchService: SearchService

    // DoD #1: filtri completi → 200 + items non vuota.
    @Test
    fun `GET screener with marketCap and sector returns 200 with items`() {
        val capturedCriteria = slot<ScreenerCriteria>()
        every { searchService.screen(capture(capturedCriteria)) } returns ScreenerResultPage(
            items = listOf(
                SearchResultItem(
                    ticker = "AAPL",
                    companyName = "Apple Inc.",
                    sector = "Technology",
                    marketCapUsd = 3_000_000_000_000.0,
                ),
                SearchResultItem(
                    ticker = "MSFT",
                    companyName = "Microsoft Corporation",
                    sector = "Technology",
                    marketCapUsd = 2_800_000_000_000.0,
                ),
            ),
            nextCursor = null,
        )

        mockMvc.get("/api/screener?marketCap=LARGE&sector=INFORMATION_TECHNOLOGY") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            jsonPath("$.items.length()") { value(2) }
            jsonPath("$.items[0].ticker") { value("AAPL") }
            jsonPath("$.items[0].sector") { value("Technology") }
            jsonPath("$.nextCursor") { doesNotExist() }
        }

        verify { searchService.screen(any()) }
        // limite default 50 propagato
        assert(capturedCriteria.captured.limit == 50)
        assert(capturedCriteria.captured.marketCapBands.size == 1)
        assert(capturedCriteria.captured.sectors.size == 1)
    }

    // DoD #2: nessun filtro → 200 + criteria con limit=50 default e liste vuote.
    @Test
    fun `GET screener with no filters returns 200 and defaults limit to 50`() {
        val capturedCriteria = slot<ScreenerCriteria>()
        every { searchService.screen(capture(capturedCriteria)) } returns ScreenerResultPage(
            items = emptyList(),
            nextCursor = null,
        )

        mockMvc.get("/api/screener") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            jsonPath("$.items.length()") { value(0) }
        }

        assert(capturedCriteria.captured.limit == 50)
        assert(capturedCriteria.captured.marketCapBands.isEmpty())
        assert(capturedCriteria.captured.sectors.isEmpty())
        assert(!capturedCriteria.captured.excludeHardToPredict)
        assert(capturedCriteria.captured.cursor == null)
    }

    // DoD #3: filtro senza match → 200 + items: [] (NON 404).
    @Test
    fun `GET screener with no match returns 200 and empty items`() {
        every { searchService.screen(any()) } returns ScreenerResultPage(
            items = emptyList(),
            nextCursor = null,
        )

        mockMvc.get("/api/screener?marketCap=MICRO&sector=UTILITIES") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            jsonPath("$.items").isArray
            jsonPath("$.items.length()") { value(0) }
        }
    }

    // Enum non valido sul marketCap → 400 ProblemDetails (type-mismatch).
    @Test
    fun `GET screener with invalid marketCap enum returns 400`() {
        mockMvc.get("/api/screener?marketCap=NANO") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isBadRequest() }
            content { contentType("application/problem+json") }
            jsonPath("$.title") { value("Bad Request") }
            jsonPath("$.parameter") { value("marketCap") }
            jsonPath("$.rejectedValue") { value("NANO") }
        }
    }

    // limit > 200 → 400 (constraint violation).
    @Test
    fun `GET screener with limit over 200 returns 400`() {
        mockMvc.get("/api/screener?limit=300") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    // limit < 1 → 400.
    @Test
    fun `GET screener with limit zero returns 400`() {
        mockMvc.get("/api/screener?limit=0") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    // excludeHardToPredict=true viene propagato al service.
    @Test
    fun `GET screener with excludeHardToPredict propagates flag to service`() {
        val capturedCriteria = slot<ScreenerCriteria>()
        every { searchService.screen(capture(capturedCriteria)) } returns ScreenerResultPage(
            items = emptyList(),
            nextCursor = null,
        )

        mockMvc.get("/api/screener?excludeHardToPredict=true") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
        }

        assert(capturedCriteria.captured.excludeHardToPredict)
    }
}
