package com.valueinvesting.webapp.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.valueinvesting.webapp.api.model.AccessTokenResponse
import com.valueinvesting.webapp.api.model.LoginRequest
import com.valueinvesting.webapp.api.model.MoatChecklistEntryRequest
import com.valueinvesting.webapp.api.model.RegisterRequest
import com.valueinvesting.webapp.api.model.WatchlistItemRequest
import com.valueinvesting.webapp.persistence.repository.LoginAttemptRepository
import com.valueinvesting.webapp.persistence.repository.MoatChecklistRepository
import com.valueinvesting.webapp.persistence.repository.RefreshTokenRepository
import com.valueinvesting.webapp.persistence.repository.StockRepository
import com.valueinvesting.webapp.persistence.repository.UserRepository
import com.valueinvesting.webapp.persistence.repository.WatchlistItemRepository
import com.valueinvesting.webapp.persistence.repository.WatchlistRepository
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

/**
 * US-079 — defense-in-depth QA (TSK-219 audit + TSK-220 AC verification, ADR-025 §1).
 *
 * Verifies 401/403 on protected endpoints, server-side validation rejection,
 * and per-user data isolation for user-scoped resources (watchlist, moat checklist).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class DefenseInDepthIT {

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
    @Autowired private lateinit var watchlistItemRepository: WatchlistItemRepository
    @Autowired private lateinit var watchlistRepository: WatchlistRepository
    @Autowired private lateinit var moatChecklistRepository: MoatChecklistRepository
    @Autowired private lateinit var stockRepository: StockRepository
    @Autowired private lateinit var refreshTokenRepository: RefreshTokenRepository
    @Autowired private lateinit var loginAttemptRepository: LoginAttemptRepository
    @Autowired private lateinit var userRepository: UserRepository

    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    @BeforeEach
    fun cleanup() {
        moatChecklistRepository.deleteAll()
        watchlistItemRepository.deleteAll()
        watchlistRepository.deleteAll()
        stockRepository.deleteAll()
        loginAttemptRepository.deleteAll()
        refreshTokenRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `watchlist GET without token returns 401`() {
        mockMvc.get("/api/watchlist").andExpect {
            status { isUnauthorized() }
            jsonPath("$.status") { value(401) }
        }
    }

    @Test
    fun `dcf-overrides GET without token returns 401`() {
        mockMvc.get("/api/dcf-overrides/AAPL").andExpect {
            status { isUnauthorized() }
            jsonPath("$.status") { value(401) }
        }
    }

    @Test
    fun `admin endpoint without token returns 401`() {
        mockMvc.get("/admin/llm-cost").andExpect {
            status { isUnauthorized() }
            jsonPath("$.status") { value(401) }
        }
    }

    @Test
    fun `admin endpoint with regular user token returns 403`() {
        val token = registerAndLogin("user@example.com")

        mockMvc.get("/admin/llm-cost") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.status") { value(403) }
        }
    }

    @Test
    fun `crafted invalid register payload returns 400`() {
        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"not-an-email","password":"short"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.status") { value(400) }
        }
    }

    @Test
    fun `crafted invalid moat checklist payload returns 400`() {
        val token = registerAndLogin("alice@example.com")

        mockMvc.post("/api/moat-checklist/AAPL") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"moatType":"NOT_A_MOAT","status":"PRESENT"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.status") { value(400) }
        }
    }

    @Test
    fun `crafted invalid watchlist ticker returns 400`() {
        val token = registerAndLogin("alice@example.com")

        mockMvc.post("/api/watchlist/items") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(WatchlistItemRequest("INVALID TICKER!!"))
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.status") { value(400) }
        }
    }

    @Test
    fun `user A moat checklist entries are not visible to user B`() {
        val tokenA = registerAndLogin("alice@example.com")
        val tokenB = registerAndLogin("bob@example.com")

        mockMvc.post("/api/moat-checklist/AAPL") {
            header("Authorization", "Bearer $tokenA")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                MoatChecklistEntryRequest(
                    moatType = "NETWORK_EFFECT",
                    status = "PRESENT",
                    note = "User A only",
                ),
            )
        }.andExpect { status { isOk() } }

        mockMvc.get("/api/moat-checklist/AAPL") {
            header("Authorization", "Bearer $tokenB")
        }.andExpect {
            status { isOk() }
            jsonPath("$.entries[?(@.moatType == 'NETWORK_EFFECT' && @.status == 'PRESENT')]") { isEmpty() }
        }
    }

    @Test
    fun `user A watchlist items are not visible to user B`() {
        val tokenA = registerAndLogin("alice@example.com")
        val tokenB = registerAndLogin("bob@example.com")

        mockMvc.post("/api/watchlist/items") {
            header("Authorization", "Bearer $tokenA")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(WatchlistItemRequest("AAPL"))
        }.andExpect { status { isOk() } }

        mockMvc.get("/api/watchlist") {
            header("Authorization", "Bearer $tokenB")
        }.andExpect {
            status { isOk() }
            jsonPath("$.items.length()") { value(0) }
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
}
