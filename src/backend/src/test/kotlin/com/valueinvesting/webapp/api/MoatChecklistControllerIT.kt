package com.valueinvesting.webapp.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.valueinvesting.webapp.api.model.LoginRequest
import com.valueinvesting.webapp.api.model.MoatChecklistEntryRequest
import com.valueinvesting.webapp.api.model.RegisterRequest
import com.valueinvesting.webapp.api.model.AccessTokenResponse
import com.valueinvesting.webapp.persistence.repository.LoginAttemptRepository
import com.valueinvesting.webapp.persistence.repository.MoatChecklistRepository
import com.valueinvesting.webapp.persistence.repository.RefreshTokenRepository
import com.valueinvesting.webapp.persistence.repository.StockRepository
import com.valueinvesting.webapp.persistence.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class MoatChecklistControllerIT {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("pgvector/pgvector:pg17")
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
    @Autowired private lateinit var moatRepository: MoatChecklistRepository
    @Autowired private lateinit var stockRepository: StockRepository
    @Autowired private lateinit var refreshTokenRepository: RefreshTokenRepository
    @Autowired private lateinit var loginAttemptRepository: LoginAttemptRepository
    @Autowired private lateinit var userRepository: UserRepository

    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    @BeforeEach
    fun cleanup() {
        moatRepository.deleteAll()
        stockRepository.deleteAll()
        loginAttemptRepository.deleteAll()
        refreshTokenRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `GET without token returns 401`() {
        mockMvc.get("/api/moat-checklist/AAPL").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `GET with empty store returns 4 entries with null status`() {
        val token = registerAndLogin("alice@example.com")

        mockMvc.get("/api/moat-checklist/AAPL") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.ticker") { value("AAPL") }
            jsonPath("$.entries.length()") { value(4) }
            jsonPath("$.entries[0].status") { doesNotExist() }
        }
    }

    @Test
    fun `POST saves entry and subsequent GET reflects it`() {
        val token = registerAndLogin("bob@example.com")

        mockMvc.post("/api/moat-checklist/MSFT") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                MoatChecklistEntryRequest(
                    moatType = "NETWORK_EFFECT",
                    status = "PRESENT",
                    note = "Marketplace dynamics",
                ),
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.moatType") { value("NETWORK_EFFECT") }
            jsonPath("$.status") { value("PRESENT") }
        }

        mockMvc.get("/api/moat-checklist/MSFT") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.entries[?(@.moatType=='NETWORK_EFFECT')].status") { value("PRESENT") }
            jsonPath("$.entries[?(@.moatType=='NETWORK_EFFECT')].note") {
                value("Marketplace dynamics")
            }
        }
    }

    @Test
    fun `POST twice on the same moat type updates the row instead of duplicating`() {
        val token = registerAndLogin("carol@example.com")

        post(token, "GOOG", "COST_ADVANTAGE", "PARTIAL", "v1")
        post(token, "GOOG", "COST_ADVANTAGE", "PRESENT", "v2")

        val rows = moatRepository.findAll()
        assertThat(rows).hasSize(1)
        assertThat(rows[0].status).isEqualTo("PRESENT")
        assertThat(rows[0].note).isEqualTo("v2")
    }

    @Test
    fun `POST with invalid moat type returns 400`() {
        val token = registerAndLogin("dan@example.com")

        mockMvc.post("/api/moat-checklist/AAPL") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"moatType":"PSYCHIC_POWERS","status":"PRESENT"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.status") { value(400) }
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
        return objectMapper.readValue(response.contentAsString, AccessTokenResponse::class.java).accessToken
    }

    private fun post(token: String, ticker: String, moatType: String, status: String, note: String?) {
        mockMvc.post("/api/moat-checklist/$ticker") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                MoatChecklistEntryRequest(moatType, status, note),
            )
        }.andExpect { status { isOk() } }
    }
}
