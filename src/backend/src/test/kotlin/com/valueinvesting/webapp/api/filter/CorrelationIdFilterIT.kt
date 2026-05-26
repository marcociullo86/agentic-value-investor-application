package com.valueinvesting.webapp.api.filter

import com.valueinvesting.webapp.api.SearchController
import com.valueinvesting.webapp.api.error.GlobalExceptionHandler
import com.valueinvesting.webapp.api.error.ProblemDetailsMapper
import com.valueinvesting.webapp.api.model.SearchResultList
import com.valueinvesting.webapp.config.ProblemDetailMvcConfig
import com.valueinvesting.webapp.fmp.FmpTickerNotFoundException
import com.valueinvesting.webapp.fmp.FmpUnavailableException
import com.valueinvesting.webapp.service.SearchService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.get
import java.util.concurrent.CompletableFuture

// Integration tests for CorrelationIdFilter: generation, propagation,
// log MDC presence, concurrency isolation, ProblemDetail enrichment.
// [^src: management/kanban/EP-014-logging-strutturato-observability/US-059-correlation-id-middleware/TSK-174.md §Scenari]
@WebMvcTest(
    controllers = [SearchController::class],
    excludeAutoConfiguration = [
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration::class,
        org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration::class,
    ],
)
@AutoConfigureMockMvc(addFilters = true)
@Import(
    GlobalExceptionHandler::class,
    ProblemDetailsMapper::class,
    ProblemDetailMvcConfig::class,
    CorrelationIdFilter::class,
    RequestIdFilter::class,
)
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension::class)
class CorrelationIdFilterIT {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var searchService: SearchService

    companion object {
        private val UUID_V4_REGEX =
            Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    }

    @Test
    fun `request without header generates UUID v4 correlation id in response`() {
        every { searchService.search("AAPL") } returns SearchResultList(items = emptyList())

        val result: MvcResult = mockMvc.get("/api/search?query=AAPL") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            header { exists("X-Correlation-Id") }
        }.andReturn()

        val correlationId = result.response.getHeader("X-Correlation-Id")!!
        assertThat(correlationId).matches(UUID_V4_REGEX.pattern)
    }

    @Test
    fun `request with header propagates same value in response`() {
        every { searchService.search("AAPL") } returns SearchResultList(items = emptyList())

        mockMvc.get("/api/search?query=AAPL") {
            accept(MediaType.APPLICATION_JSON)
            header("X-Correlation-Id", "my-custom-id-123")
        }.andExpect {
            status { isOk() }
            header { string("X-Correlation-Id", "my-custom-id-123") }
        }
    }

    @Test
    fun `correlation id appears in log output during request`(output: CapturedOutput) {
        every { searchService.validateTicker("LOGTEST") } throws
            FmpUnavailableException("service down")

        mockMvc.get("/api/search/LOGTEST") {
            accept(MediaType.APPLICATION_JSON)
            header("X-Correlation-Id", "trace-for-log-check")
        }.andExpect {
            status { isServiceUnavailable() }
        }

        assertThat(output.all).contains("trace-for-log-check")
    }

    @Test
    fun `concurrent requests receive distinct correlation ids`() {
        every { searchService.search("AAPL") } returns SearchResultList(items = emptyList())
        every { searchService.search("MSFT") } returns SearchResultList(items = emptyList())

        val future1 = CompletableFuture.supplyAsync {
            mockMvc.get("/api/search?query=AAPL") {
                accept(MediaType.APPLICATION_JSON)
            }.andReturn().response.getHeader("X-Correlation-Id")!!
        }

        val future2 = CompletableFuture.supplyAsync {
            mockMvc.get("/api/search?query=MSFT") {
                accept(MediaType.APPLICATION_JSON)
            }.andReturn().response.getHeader("X-Correlation-Id")!!
        }

        val id1 = future1.get()
        val id2 = future2.get()

        assertThat(id1).matches(UUID_V4_REGEX.pattern)
        assertThat(id2).matches(UUID_V4_REGEX.pattern)
        assertThat(id1).isNotEqualTo(id2)
    }

    @Test
    fun `error response ProblemDetail contains correlationId matching response header`() {
        every { searchService.validateTicker("NONEXIST") } throws
            FmpTickerNotFoundException("NONEXIST")

        val result: MvcResult = mockMvc.get("/api/search/NONEXIST") {
            accept(MediaType.APPLICATION_JSON)
            header("X-Correlation-Id", "error-trace-999")
        }.andExpect {
            status { isNotFound() }
            content { contentType("application/problem+json") }
            jsonPath("$.correlationId") { value("error-trace-999") }
        }.andReturn()

        val headerValue = result.response.getHeader("X-Correlation-Id")
        assertThat(headerValue).isEqualTo("error-trace-999")
    }
}
