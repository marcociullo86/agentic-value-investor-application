package com.valueinvesting.webapp.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.valueinvesting.webapp.api.model.AccessTokenResponse
import com.valueinvesting.webapp.api.model.LoginRequest
import com.valueinvesting.webapp.api.model.RegisterRequest
import com.valueinvesting.webapp.config.SecurityHeadersConfig
import com.valueinvesting.webapp.persistence.repository.LoginAttemptRepository
import com.valueinvesting.webapp.persistence.repository.RefreshTokenRepository
import com.valueinvesting.webapp.persistence.repository.UserRepository
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * TSK-224 — CSP + CSRF integration tests (US-080 AC verification).
 *
 * Covers:
 *  1. [CspOnAuthEndpoints] — Content-Security-Policy header present on all API responses,
 *     including /api/auth/* endpoints (complements SecurityHeadersIT which covers
 *     /actuator and /api/screener).
 *  2. [CsrfProtection] — POST /api/auth/refresh without X-CSRF-Token → 403.
 *     (Overlaps with AuthControllerIT "refresh without CSRF token returns 403" intentionally:
 *     US-080 DoD requires explicit traceability here.)
 *  3. [SameSiteCookies] — refresh_token cookie carries SameSite=Strict.
 *     (Overlaps with AuthStorageSecurityIT; kept for US-080 traceability.)
 *
 * [^src: management/kanban/EP-018-hardening-sicurezza-compliance/US-080-protezione-attacchi-web/US-080.md §AC]
 * [^src: design_&_architecture/decisions/ADR-025-security-hardening-pci-dss.md §2 §3]
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class CspCsrfSecurityIT {

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

        private const val EMAIL = "csp-csrf-it@example.com"
        private const val PASSWORD = "csp-csrf-strong-pass-12345"
    }

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var refreshTokenRepository: RefreshTokenRepository
    @Autowired private lateinit var loginAttemptRepository: LoginAttemptRepository

    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    @BeforeEach
    fun cleanup() {
        loginAttemptRepository.deleteAll()
        refreshTokenRepository.deleteAll()
        userRepository.deleteAll()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 1. CSP header on API responses (US-080 AC: CSP on all HTTP responses)
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("CSP header on auth API endpoints (US-080 AC#1)")
    inner class CspOnAuthEndpoints {

        @Test
        fun `POST api auth register 201 response carries Content-Security-Policy header`() {
            val result = mockMvc.post("/api/auth/register") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(
                    RegisterRequest(EMAIL, PASSWORD, "CSP IT User"),
                )
            }.andExpect { status { isCreated() } }.andReturn()

            assertThat(result.response.getHeader("Content-Security-Policy"))
                .isEqualTo(SecurityHeadersConfig.CONTENT_SECURITY_POLICY)
        }

        @Test
        fun `POST api auth login 200 response carries Content-Security-Policy header`() {
            registerUser()

            val result = loginReturningResult()

            assertThat(result.response.getHeader("Content-Security-Policy"))
                .isEqualTo(SecurityHeadersConfig.CONTENT_SECURITY_POLICY)
        }

        @Test
        fun `POST api auth login 401 error response also carries Content-Security-Policy header`() {
            val result = mockMvc.post("/api/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(
                    LoginRequest("nobody@example.com", "wrong-pass-12345"),
                )
            }.andExpect { status { isUnauthorized() } }.andReturn()

            assertThat(result.response.getHeader("Content-Security-Policy"))
                .isEqualTo(SecurityHeadersConfig.CONTENT_SECURITY_POLICY)
        }

        @Test
        fun `GET actuator health response carries Content-Security-Policy header`() {
            val result = mockMvc.get("/actuator/health").andReturn()

            assertThat(result.response.getHeader("Content-Security-Policy"))
                .isEqualTo(SecurityHeadersConfig.CONTENT_SECURITY_POLICY)
        }

        @Test
        fun `Content-Security-Policy is an HTTP response header not empty (US-080 AC#6)`() {
            val result = mockMvc.get("/actuator/health").andReturn()
            val csp = result.response.getHeader("Content-Security-Policy")

            assertThat(csp).isNotBlank()
            assertThat(csp).contains("default-src 'self'")
            assertThat(csp).contains("frame-src 'none'")
            assertThat(csp).contains("object-src 'none'")
            assertThat(csp).contains("form-action 'self'")
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2. CSRF protection (US-080 AC#3)
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("CSRF 403 on state-changing auth endpoints (US-080 AC#3)")
    inner class CsrfProtection {

        @Test
        fun `POST api auth refresh without X-CSRF-Token returns 403`() {
            registerUser()
            val loginResult = loginReturningResult()
            val refreshCookie = extractRefreshCookie(loginResult)

            mockMvc.post("/api/auth/refresh") {
                cookie(Cookie(RefreshTokenCookieHelper.COOKIE_NAME, refreshCookie))
                // No csrf() → no X-CSRF-Token header
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.status") { value(403) }
            }
        }

        @Test
        fun `POST api auth logout without X-CSRF-Token returns 403`() {
            registerUser()
            val loginResult = loginReturningResult()
            val refreshCookie = extractRefreshCookie(loginResult)
            val accessToken = extractAccessToken(loginResult)

            mockMvc.post("/api/auth/logout") {
                header("Authorization", "Bearer $accessToken")
                cookie(Cookie(RefreshTokenCookieHelper.COOKIE_NAME, refreshCookie))
                // No csrf() → no X-CSRF-Token header
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.status") { value(403) }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. SameSite=Strict cookie (US-080 AC#4)
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SameSite=Strict on session cookies (US-080 AC#4)")
    inner class SameSiteCookies {

        @Test
        fun `login Set-Cookie refresh_token has SameSite=Strict`() {
            registerUser()
            val result = loginReturningResult()
            val setCookie = result.response.getHeader("Set-Cookie")

            assertThat(setCookie).isNotNull()
            assertThat(setCookie).containsIgnoringCase("SameSite=Strict")
        }

        @Test
        fun `login Set-Cookie refresh_token has HttpOnly flag`() {
            registerUser()
            val result = loginReturningResult()
            val setCookie = result.response.getHeader("Set-Cookie")

            assertThat(setCookie).isNotNull()
            assertThat(setCookie).containsIgnoringCase("HttpOnly")
        }

        @Test
        fun `login Set-Cookie refresh_token Path is restricted to api auth`() {
            registerUser()
            val result = loginReturningResult()
            val setCookie = result.response.getHeader("Set-Cookie")

            assertThat(setCookie).isNotNull()
            assertThat(setCookie).contains("Path=/api/auth")
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun registerUser() {
        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                RegisterRequest(EMAIL, PASSWORD, "CSP IT User"),
            )
        }.andExpect { status { isCreated() } }
    }

    private fun loginReturningResult(): MvcResult {
        val result = mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(LoginRequest(EMAIL, PASSWORD))
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
        val body = objectMapper.readValue(
            result.response.contentAsString,
            AccessTokenResponse::class.java,
        )
        return body.accessToken
    }
}
