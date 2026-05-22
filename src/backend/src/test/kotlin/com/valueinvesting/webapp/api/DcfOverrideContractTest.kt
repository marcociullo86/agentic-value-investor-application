package com.valueinvesting.webapp.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.valueinvesting.webapp.api.model.DcfOverrideRequest
import com.valueinvesting.webapp.api.model.LoginRequest
import com.valueinvesting.webapp.api.model.RegisterRequest
import com.valueinvesting.webapp.api.model.TokenPairResponse
import com.valueinvesting.webapp.fmp.FmpAdapter
import com.valueinvesting.webapp.fmp.FmpFixtureFactory
import com.valueinvesting.webapp.persistence.repository.DcfMethodOverrideRepository
import com.valueinvesting.webapp.persistence.repository.RefreshTokenRepository
import com.valueinvesting.webapp.persistence.repository.UserRepository
import com.ninjasquad.springmockk.MockkBean
import io.mockk.clearMocks
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
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
import org.springframework.test.web.servlet.post
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Contract tests for US-020 / ADR-011 (TSK-047).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Tag("contract")
class DcfOverrideContractTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("value_investing_test")
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

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var dcfOverrideRepository: DcfMethodOverrideRepository
    @Autowired private lateinit var refreshTokenRepository: RefreshTokenRepository
    @Autowired private lateinit var userRepository: UserRepository

    @MockkBean
    private lateinit var fmpAdapter: FmpAdapter

    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    @BeforeEach
    fun reset() {
        clearMocks(fmpAdapter, answers = false, recordedCalls = true)
        dcfOverrideRepository.deleteAll()
        refreshTokenRepository.deleteAll()
        userRepository.deleteAll()
        FmpFixtureFactory.stubSuccessfulFmp(fmpAdapter, "AAPL")
    }

    @Test
    fun `anonymous analysis returns DEFAULT_POLICY and Vary Authorization`() {
        mockMvc.get("/api/analysis/AAPL") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            header { string("Vary", "Origin, Authorization") }
            jsonPath("$.dcfMethodSource") { value("DEFAULT_POLICY") }
        }
    }

    @Test
    fun `authenticated user without override returns DEFAULT_POLICY`() {
        val token = registerAndLogin("no-override@example.com")

        mockMvc.get("/api/analysis/AAPL") {
            header("Authorization", "Bearer $token")
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            jsonPath("$.dcfMethodSource") { value("DEFAULT_POLICY") }
        }
    }

    @Test
    fun `authenticated user with override returns USER_OVERRIDE`() {
        val token = registerAndLogin("with-override@example.com")
        postOverride(token, "AAPL", "GREENWALD")

        mockMvc.get("/api/analysis/AAPL") {
            header("Authorization", "Bearer $token")
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            jsonPath("$.dcfMethodSource") { value("USER_OVERRIDE") }
            jsonPath("$.dcfMethod") { value("GREENWALD") }
        }
    }

    @Test
    fun `POST override with insufficient PPE history returns 422 ProblemDetail`() {
        FmpFixtureFactory.stubLowPpeHistory(fmpAdapter, "LOWPPE")
        val token = registerAndLogin("feasibility@example.com")

        mockMvc.post("/api/dcf-overrides") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                DcfOverrideRequest(ticker = "LOWPPE", forcedMethod = "GREENWALD"),
            )
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.status") { value(422) }
            jsonPath("$.type") { value("https://api/errors/dcf-method-unfeasible") }
            jsonPath("$.reason") { value("PPE_RATIO_HISTORY_INSUFFICIENT") }
            jsonPath("$.availableYears") { exists() }
            jsonPath("$.requiredYears") { value(5) }
            jsonPath("$.properties").doesNotExist()
        }
    }

    @Test
    fun `GET override returns 200 when present and 404 when absent`() {
        val token = registerAndLogin("getter@example.com")
        postOverride(token, "AAPL", "FCF_FALLBACK")

        mockMvc.get("/api/dcf-overrides/AAPL") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.forcedMethod") { value("FCF_FALLBACK") }
        }

        mockMvc.get("/api/dcf-overrides/MSFT") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.type") { value("https://api/errors/dcf-override-not-found") }
        }

        mockMvc.get("/api/dcf-overrides/AAPL").andExpect {
            status { isUnauthorized() }
        }
    }

    private fun registerAndLogin(email: String, password: String = "very-strong-pass-12345"): String {
        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(RegisterRequest(email, password, null))
        }.andExpect { status { isCreated() } }
        val response = mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(LoginRequest(email, password))
        }.andReturn().response
        check(response.status == 200) { "login failed: ${response.contentAsString}" }
        return objectMapper.readValue(response.contentAsString, TokenPairResponse::class.java).accessToken
    }

    private fun postOverride(token: String, ticker: String, method: String) {
        mockMvc.post("/api/dcf-overrides") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                DcfOverrideRequest(ticker = ticker, forcedMethod = method),
            )
        }.andExpect { status { isCreated() } }
    }
}
