package com.valueinvesting.webapp.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.valueinvesting.webapp.api.model.AccessTokenResponse
import com.valueinvesting.webapp.api.model.LoginRequest
import com.valueinvesting.webapp.api.model.RegisterRequest
import com.valueinvesting.webapp.persistence.repository.LoginAttemptRepository
import com.valueinvesting.webapp.persistence.repository.RefreshTokenRepository
import com.valueinvesting.webapp.persistence.repository.UserRepository
import jakarta.servlet.http.Cookie
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.post
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Integration tests for AuthController (TSK-033, TSK-209). Verifies the full
 * JWT-based registration/login/refresh/logout lifecycle on a real PostgreSQL
 * via Testcontainers — no mocks.
 *
 * TSK-209 (ADR-024 §3): refresh token migrated from body JSON to httpOnly
 * cookie. Tests verify Set-Cookie headers and cookie-based refresh/logout.
 * TSK-223 (ADR-025 §3): CSRF required on refresh/logout via X-CSRF-Token header.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AuthControllerIT {

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

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    private lateinit var loginAttemptRepository: LoginAttemptRepository

    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    @BeforeEach
    fun cleanup() {
        loginAttemptRepository.deleteAll()
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
            content { contentType("application/problem+json") }
            jsonPath("$.status") { value(409) }
            jsonPath("$.type") { value("https://api/errors/email-already-registered") }
            jsonPath("$.title") { value("Email already registered") }
            jsonPath("$.detail") { value("Email already registered: bob@example.com") }
        }
    }

    @Test
    fun `login returns accessToken in body and refresh token in httpOnly cookie`() {
        register(RegisterRequest("carol@example.com", "yet-another-strong-pass-12345", null))

        val result = mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                LoginRequest("carol@example.com", "yet-another-strong-pass-12345"),
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { exists() }
            jsonPath("$.expiresInSeconds") { value(15 * 60) }
            jsonPath("$.refreshToken") { doesNotExist() }
        }.andReturn()

        val setCookie = result.response.getHeader("Set-Cookie")
        assertThat(setCookie).isNotNull()
        assertThat(setCookie).contains("refresh_token=")
        assertThat(setCookie).containsIgnoringCase("HttpOnly")
        assertThat(setCookie).containsIgnoringCase("SameSite=Strict")
        assertThat(setCookie).contains("Path=/api/auth")
        assertThat(setCookie).contains("Max-Age=604800")
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
    fun `refresh reads cookie and returns new access token plus rotated cookie`() {
        register(RegisterRequest("eve@example.com", "eves-strong-password-12345", null))
        val loginResult = loginReturningResult("eve@example.com", "eves-strong-password-12345")
        val refreshCookie = extractRefreshCookie(loginResult)

        val refreshResult = mockMvc.post("/api/auth/refresh") {
            with(csrf())
            cookie(Cookie(RefreshTokenCookieHelper.COOKIE_NAME, refreshCookie))
        }.andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { exists() }
            jsonPath("$.expiresInSeconds") { exists() }
            jsonPath("$.refreshToken") { doesNotExist() }
        }.andReturn()

        val newSetCookie = refreshResult.response.getHeader("Set-Cookie")
        assertThat(newSetCookie).isNotNull()
        assertThat(newSetCookie).contains("refresh_token=")
        assertThat(newSetCookie).containsIgnoringCase("HttpOnly")

        val oldToken = refreshTokenRepository.findByTokenValue(refreshCookie)
        assertThat(oldToken?.revokedAt).isNotNull()
    }

    @Test
    fun `refresh without cookie returns 400`() {
        mockMvc.post("/api/auth/refresh") {
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `refresh without CSRF token returns 403`() {
        register(RegisterRequest("csrf-eve@example.com", "eves-strong-password-12345", null))
        val loginResult = loginReturningResult("csrf-eve@example.com", "eves-strong-password-12345")
        val refreshCookie = extractRefreshCookie(loginResult)

        mockMvc.post("/api/auth/refresh") {
            cookie(Cookie(RefreshTokenCookieHelper.COOKIE_NAME, refreshCookie))
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.status") { value(403) }
        }
    }

    @Test
    fun `logout without CSRF token returns 403`() {
        register(RegisterRequest("csrf-frank@example.com", "franks-strong-password-12345", null))
        val loginResult = loginReturningResult("csrf-frank@example.com", "franks-strong-password-12345")
        val refreshCookie = extractRefreshCookie(loginResult)
        val accessToken = extractAccessToken(loginResult)

        mockMvc.post("/api/auth/logout") {
            header("Authorization", "Bearer $accessToken")
            cookie(Cookie(RefreshTokenCookieHelper.COOKIE_NAME, refreshCookie))
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.status") { value(403) }
        }
    }

    @Test
    fun `logout revokes token and clears cookie with 204`() {
        register(RegisterRequest("frank@example.com", "franks-strong-password-12345", null))
        val loginResult = loginReturningResult("frank@example.com", "franks-strong-password-12345")
        val refreshCookie = extractRefreshCookie(loginResult)
        val accessToken = extractAccessToken(loginResult)

        val logoutResult = mockMvc.post("/api/auth/logout") {
            with(csrf())
            header("Authorization", "Bearer $accessToken")
            cookie(Cookie(RefreshTokenCookieHelper.COOKIE_NAME, refreshCookie))
        }.andExpect {
            status { isNoContent() }
        }.andReturn()

        val deleteCookie = logoutResult.response.getHeader("Set-Cookie")
        assertThat(deleteCookie).isNotNull()
        assertThat(deleteCookie).contains("Max-Age=0")

        val revoked = refreshTokenRepository.findByTokenValue(refreshCookie)
        assertThat(revoked?.revokedAt).isNotNull()
    }

    private fun register(request: RegisterRequest) {
        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect { status { isCreated() } }
    }

    private fun loginReturningResult(email: String, password: String): MvcResult {
        val result = mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(LoginRequest(email, password))
        }.andReturn()
        check(result.response.status == 200) {
            "login failed: ${result.response.contentAsString}"
        }
        return result
    }

    private fun extractRefreshCookie(result: MvcResult): String {
        val setCookie = result.response.getHeader("Set-Cookie")!!
        return setCookie.substringAfter("refresh_token=").substringBefore(";")
    }

    private fun extractAccessToken(result: MvcResult): String {
        val body = objectMapper.readValue(result.response.contentAsString, AccessTokenResponse::class.java)
        return body.accessToken
    }
}
