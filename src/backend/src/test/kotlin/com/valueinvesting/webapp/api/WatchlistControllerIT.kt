package com.valueinvesting.webapp.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.valueinvesting.webapp.api.model.LoginRequest
import com.valueinvesting.webapp.api.model.RegisterRequest
import com.valueinvesting.webapp.api.model.AccessTokenResponse
import com.valueinvesting.webapp.api.model.WatchlistItemRequest
import com.valueinvesting.webapp.persistence.repository.RefreshTokenRepository
import com.valueinvesting.webapp.persistence.repository.StockRepository
import com.valueinvesting.webapp.persistence.repository.UserRepository
import com.valueinvesting.webapp.persistence.repository.WatchlistItemRepository
import com.valueinvesting.webapp.persistence.repository.WatchlistRepository
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
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Integration tests for WatchlistController (TSK-029). Verifies idempotency
 * of POST, 404 on missing DELETE, persistence across login sessions.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class WatchlistControllerIT {

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
    @Autowired private lateinit var stockRepository: StockRepository
    @Autowired private lateinit var refreshTokenRepository: RefreshTokenRepository
    @Autowired private lateinit var userRepository: UserRepository

    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    @BeforeEach
    fun cleanup() {
        watchlistItemRepository.deleteAll()
        watchlistRepository.deleteAll()
        stockRepository.deleteAll()
        refreshTokenRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `GET watchlist without token returns 401`() {
        mockMvc.get("/api/watchlist").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `GET watchlist on first call creates and returns empty default watchlist`() {
        val token = registerAndLogin("alice@example.com")

        mockMvc.get("/api/watchlist") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.isDefault") { value(true) }
            jsonPath("$.items.length()") { value(0) }
            jsonPath("$.id") { exists() }
        }
    }

    @Test
    fun `POST item twice with same ticker is idempotent`() {
        val token = registerAndLogin("bob@example.com")
        addItem(token, "AAPL")
        addItem(token, "AAPL")

        val watchlists = watchlistRepository.findAll()
        assertThat(watchlists).hasSize(1)
        val items = watchlistItemRepository.findByWatchlistIdOrderByAddedAtDesc(watchlists[0].id)
        assertThat(items).hasSize(1)
        assertThat(items[0].ticker).isEqualTo("AAPL")
    }

    @Test
    fun `POST item lower-case ticker normalizes to upper-case`() {
        val token = registerAndLogin("carol@example.com")
        addItem(token, "msft")

        mockMvc.get("/api/watchlist") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.items[0].ticker") { value("MSFT") }
        }
    }

    @Test
    fun `DELETE existing ticker returns 204 and second DELETE returns 404`() {
        val token = registerAndLogin("dan@example.com")
        addItem(token, "GOOG")

        mockMvc.delete("/api/watchlist/items/GOOG") {
            header("Authorization", "Bearer $token")
        }.andExpect { status { isNoContent() } }

        mockMvc.delete("/api/watchlist/items/GOOG") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.status") { value(404) }
            jsonPath("$.title") { value("Ticker not in watchlist") }
        }
    }

    @Test
    fun `watchlist persists across new login sessions for same user`() {
        registerAndLogin("eve@example.com").let { token -> addItem(token, "NVDA") }
        val freshToken = login("eve@example.com")

        mockMvc.get("/api/watchlist") {
            header("Authorization", "Bearer $freshToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.items.length()") { value(1) }
            jsonPath("$.items[0].ticker") { value("NVDA") }
        }
    }

    private fun registerAndLogin(email: String, password: String = "very-strong-pass-12345"): String {
        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(RegisterRequest(email, password, null))
        }.andExpect { status { isCreated() } }
        return login(email, password)
    }

    private fun login(email: String, password: String = "very-strong-pass-12345"): String {
        val response = mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(LoginRequest(email, password))
        }.andReturn().response
        check(response.status == 200) { "login failed: ${response.contentAsString}" }
        return objectMapper.readValue(response.contentAsString, AccessTokenResponse::class.java).accessToken
    }

    private fun addItem(token: String, ticker: String) {
        mockMvc.post("/api/watchlist/items") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(WatchlistItemRequest(ticker))
        }.andExpect { status { isOk() } }
    }
}
