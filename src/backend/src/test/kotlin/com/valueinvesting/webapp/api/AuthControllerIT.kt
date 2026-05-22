package com.valueinvesting.webapp.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.valueinvesting.webapp.api.model.LoginRequest
import com.valueinvesting.webapp.api.model.RefreshRequest
import com.valueinvesting.webapp.api.model.RegisterRequest
import com.valueinvesting.webapp.api.model.TokenPairResponse
import com.valueinvesting.webapp.persistence.repository.RefreshTokenRepository
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
import org.springframework.test.web.servlet.post
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Integration tests for AuthController (TSK-033). Verifies the full JWT-based
 * registration/login/refresh/logout lifecycle on a real PostgreSQL via
 * Testcontainers — no mocks.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AuthControllerIT {

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

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    @BeforeEach
    fun cleanup() {
        refreshTokenRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `register returns 201 with user profile`() {
        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                RegisterRequest("alice@example.com", "very-strong-password-123", "Alice"),
            )
        }.andExpect {
            status { isCreated() }
            jsonPath("$.email") { value("alice@example.com") }
            jsonPath("$.displayName") { value("Alice") }
            jsonPath("$.id") { exists() }
        }

        assertThat(userRepository.findByEmailIgnoreCase("ALICE@example.com")).isNotNull
    }

    @Test
    fun `register duplicate email returns 409 ProblemDetails`() {
        val request = RegisterRequest("bob@example.com", "another-strong-pass-12345", null)
        register(request)

        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isConflict() }
            jsonPath("$.status") { value(409) }
            jsonPath("$.title") { value("Email already registered") }
        }
    }

    @Test
    fun `login with valid credentials returns access plus refresh token`() {
        register(RegisterRequest("carol@example.com", "yet-another-strong-pass-12345", null))

        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                LoginRequest("carol@example.com", "yet-another-strong-pass-12345"),
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { exists() }
            jsonPath("$.refreshToken") { exists() }
            jsonPath("$.expiresInSeconds") { value(15 * 60) }
        }
    }

    @Test
    fun `login with wrong password returns 401`() {
        register(RegisterRequest("dan@example.com", "correct-password-12345678", null))

        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                LoginRequest("dan@example.com", "wrong-password-1234567890"),
            )
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.status") { value(401) }
        }
    }

    @Test
    fun `refresh exchanges valid refresh token for new pair`() {
        register(RegisterRequest("eve@example.com", "eves-strong-password-12345", null))
        val tokenPair = login("eve@example.com", "eves-strong-password-12345")

        mockMvc.post("/api/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(RefreshRequest(tokenPair.refreshToken))
        }.andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { exists() }
            jsonPath("$.refreshToken") { exists() }
        }

        // Old refresh token should now be revoked (rotation).
        val rotated = refreshTokenRepository.findByTokenValue(tokenPair.refreshToken)
        assertThat(rotated?.revokedAt).isNotNull()
    }

    @Test
    fun `refresh with invalid token returns 401`() {
        mockMvc.post("/api/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(RefreshRequest("not-a-real-token"))
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    private fun register(request: RegisterRequest) {
        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect { status { isCreated() } }
    }

    private fun login(email: String, password: String): TokenPairResponse {
        val response = mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(LoginRequest(email, password))
        }.andReturn().response
        check(response.status == 200) { "login failed: ${response.contentAsString}" }
        return objectMapper.readValue(response.contentAsString, TokenPairResponse::class.java)
    }
}
