package com.valueinvesting.webapp.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.valueinvesting.webapp.api.model.AccessTokenResponse
import com.valueinvesting.webapp.api.model.LoginRequest
import com.valueinvesting.webapp.api.model.RegisterRequest
import com.valueinvesting.webapp.persistence.repository.LoginAttemptRepository
import com.valueinvesting.webapp.persistence.repository.RefreshTokenRepository
import com.valueinvesting.webapp.persistence.repository.UserRepository
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
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
import java.nio.charset.StandardCharsets

/**
 * TSK-212 — Security-focused integration tests for auth storage migration (US-075).
 *
 * Verifies AC:
 *  1. Set-Cookie: refresh_token has HttpOnly, SameSite=Strict, Path=/api/auth
 *  2. Access token JWT exp - iat <= 900 (15 min)
 *  3. Cookie-based rehydration (login → cookie → refresh → new access token)
 *  4. Rotation revocation (reuse of rotated token → 401)
 *
 * Note: `Secure` flag is config-driven (`app.jwt.cookie-secure`), set to false
 * in test profile — verified separately.
 *
 * [^src: management/kanban/EP-017-protezione-rotte-sessione/US-075-migrazione-storage-credenziali/US-075.md §AC]
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AuthStorageSecurityIT {

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

        private const val EMAIL = "security-test@example.com"
        private const val PASSWORD = "very-strong-security-test-password-123"
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    private lateinit var loginAttemptRepository: LoginAttemptRepository

    @Value("\${app.jwt.signing-secret}")
    private lateinit var jwtSigningSecret: String

    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    @BeforeEach
    fun cleanup() {
        loginAttemptRepository.deleteAll()
        refreshTokenRepository.deleteAll()
        userRepository.deleteAll()
        register()
    }

    @Nested
    @DisplayName("Set-Cookie attributes (US-075 AC#3)")
    inner class CookieAttributes {

        @Test
        fun `login Set-Cookie has HttpOnly flag`() {
            val result = loginReturningResult()
            val setCookie = result.response.getHeader("Set-Cookie")

            assertThat(setCookie).isNotNull()
            assertThat(setCookie).containsIgnoringCase("HttpOnly")
        }

        @Test
        fun `login Set-Cookie has SameSite=Strict`() {
            val result = loginReturningResult()
            val setCookie = result.response.getHeader("Set-Cookie")

            assertThat(setCookie).isNotNull()
            assertThat(setCookie).containsIgnoringCase("SameSite=Strict")
        }

        @Test
        fun `login Set-Cookie has Path restricted to auth endpoints`() {
            val result = loginReturningResult()
            val setCookie = result.response.getHeader("Set-Cookie")

            assertThat(setCookie).isNotNull()
            assertThat(setCookie).contains("Path=/api/auth")
        }

        @Test
        fun `login Set-Cookie uses cookie name refresh_token`() {
            val result = loginReturningResult()
            val setCookie = result.response.getHeader("Set-Cookie")

            assertThat(setCookie).isNotNull()
            assertThat(setCookie).startsWith("refresh_token=")
        }

        @Test
        fun `login response body does not contain refreshToken`() {
            val result = loginReturningResult()
            val body = result.response.contentAsString

            assertThat(body).doesNotContain("refreshToken")
            assertThat(body).doesNotContain("refresh_token")
        }

        @Test
        fun `login Set-Cookie Max-Age is 7 days (604800s)`() {
            val result = loginReturningResult()
            val setCookie = result.response.getHeader("Set-Cookie")

            assertThat(setCookie).isNotNull()
            assertThat(setCookie).contains("Max-Age=604800")
        }
    }

    @Nested
    @DisplayName("Access token JWT lifetime (US-075 AC#4)")
    inner class AccessTokenLifetime {

        @Test
        fun `access token exp minus iat is at most 900 seconds (15 min)`() {
            val result = loginReturningResult()
            val body = objectMapper.readValue(
                result.response.contentAsString,
                AccessTokenResponse::class.java,
            )

            val key = Keys.hmacShaKeyFor(
                jwtSigningSecret.toByteArray(StandardCharsets.UTF_8),
            )
            val claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(body.accessToken)
                .payload

            val iat = claims.issuedAt.time / 1000
            val exp = claims.expiration.time / 1000
            val delta = exp - iat

            assertThat(delta).isLessThanOrEqualTo(900)
        }

        @Test
        fun `expiresInSeconds in response body is 900`() {
            val result = loginReturningResult()
            val body = objectMapper.readValue(
                result.response.contentAsString,
                AccessTokenResponse::class.java,
            )

            assertThat(body.expiresInSeconds).isEqualTo(900)
        }
    }

    @Nested
    @DisplayName("Cookie-based rehydration (US-075 AC#5)")
    inner class Rehydration {

        @Test
        fun `refresh with valid cookie returns new access token`() {
            val loginResult = loginReturningResult()
            val refreshCookie = extractRefreshCookie(loginResult)

            mockMvc.post("/api/auth/refresh") {
                with(csrf())
                cookie(Cookie(RefreshTokenCookieHelper.COOKIE_NAME, refreshCookie))
            }.andExpect {
                status { isOk() }
                jsonPath("$.accessToken") { exists() }
                jsonPath("$.expiresInSeconds") { exists() }
            }
        }

        @Test
        fun `refresh response also sets rotated cookie`() {
            val loginResult = loginReturningResult()
            val refreshCookie = extractRefreshCookie(loginResult)

            val refreshResult = mockMvc.post("/api/auth/refresh") {
                with(csrf())
                cookie(Cookie(RefreshTokenCookieHelper.COOKIE_NAME, refreshCookie))
            }.andReturn()

            val newSetCookie = refreshResult.response.getHeader("Set-Cookie")
            assertThat(newSetCookie).isNotNull()
            assertThat(newSetCookie).startsWith("refresh_token=")
            assertThat(newSetCookie).containsIgnoringCase("HttpOnly")
            assertThat(newSetCookie).containsIgnoringCase("SameSite=Strict")
        }

        @Test
        fun `refresh response body does not expose refreshToken`() {
            val loginResult = loginReturningResult()
            val refreshCookie = extractRefreshCookie(loginResult)

            val refreshResult = mockMvc.post("/api/auth/refresh") {
                with(csrf())
                cookie(Cookie(RefreshTokenCookieHelper.COOKIE_NAME, refreshCookie))
            }.andReturn()

            val body = refreshResult.response.contentAsString
            assertThat(body).doesNotContain("refreshToken")
            assertThat(body).doesNotContain("refresh_token")
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
    }

    @Nested
    @DisplayName("Rotation revocation (US-075 AC#6)")
    inner class RotationRevocation {

        @Test
        fun `reuse of rotated refresh token returns 401`() {
            val loginResult = loginReturningResult()
            val originalCookie = extractRefreshCookie(loginResult)

            // Rotate: use the original cookie → new cookie emitted, old one revoked
            mockMvc.post("/api/auth/refresh") {
                with(csrf())
                cookie(Cookie(RefreshTokenCookieHelper.COOKIE_NAME, originalCookie))
            }.andExpect { status { isOk() } }

            // Reuse: try the original (now-revoked) cookie again
            mockMvc.post("/api/auth/refresh") {
                with(csrf())
                cookie(Cookie(RefreshTokenCookieHelper.COOKIE_NAME, originalCookie))
            }.andExpect {
                status { isUnauthorized() }
            }
        }

        @Test
        fun `old token is marked revoked in DB after rotation`() {
            val loginResult = loginReturningResult()
            val originalCookie = extractRefreshCookie(loginResult)

            mockMvc.post("/api/auth/refresh") {
                with(csrf())
                cookie(Cookie(RefreshTokenCookieHelper.COOKIE_NAME, originalCookie))
            }.andExpect { status { isOk() } }

            val revokedToken = refreshTokenRepository.findByTokenValue(originalCookie)
            assertThat(revokedToken).isNotNull()
            assertThat(revokedToken!!.revokedAt).isNotNull()
        }

        @Test
        fun `logout clears cookie via Max-Age 0 and revokes token`() {
            val loginResult = loginReturningResult()
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

        @Test
        fun `refresh after logout returns 401`() {
            val loginResult = loginReturningResult()
            val refreshCookie = extractRefreshCookie(loginResult)
            val accessToken = extractAccessToken(loginResult)

            mockMvc.post("/api/auth/logout") {
                with(csrf())
                header("Authorization", "Bearer $accessToken")
                cookie(Cookie(RefreshTokenCookieHelper.COOKIE_NAME, refreshCookie))
            }.andExpect { status { isNoContent() } }

            mockMvc.post("/api/auth/refresh") {
                with(csrf())
                cookie(Cookie(RefreshTokenCookieHelper.COOKIE_NAME, refreshCookie))
            }.andExpect {
                status { isUnauthorized() }
            }
        }
    }

    // ──────────── helpers ────────────

    private fun register() {
        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                RegisterRequest(EMAIL, PASSWORD, "Security Test"),
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
