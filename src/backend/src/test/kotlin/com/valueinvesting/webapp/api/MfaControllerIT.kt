package com.valueinvesting.webapp.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.valueinvesting.webapp.api.model.AccessTokenResponse
import com.valueinvesting.webapp.api.model.LoginRequest
import com.valueinvesting.webapp.api.model.MfaChallengeRequest
import com.valueinvesting.webapp.api.model.MfaDisableRequest
import com.valueinvesting.webapp.api.model.MfaEnrollmentResponse
import com.valueinvesting.webapp.api.model.MfaRecoveryRequest
import com.valueinvesting.webapp.api.model.MfaVerifyRequest
import com.valueinvesting.webapp.api.model.RegisterRequest
import com.valueinvesting.webapp.persistence.repository.LoginAttemptRepository
import com.valueinvesting.webapp.persistence.repository.MfaSecretRepository
import com.valueinvesting.webapp.persistence.repository.RefreshTokenRepository
import com.valueinvesting.webapp.persistence.repository.UserRepository
import dev.samstevens.totp.code.DefaultCodeGenerator
import dev.samstevens.totp.code.HashingAlgorithm
import dev.samstevens.totp.time.SystemTimeProvider
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
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.post
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Integration tests for [MfaController] (TSK-228 / US-081 / ADR-025 §4) covering
 * the end-to-end MFA lifecycle on a real PostgreSQL via Testcontainers:
 *
 *  1. enroll → verify → activated
 *  2. login with MFA enabled → mfaRequired=true + mfaToken (no access/refresh)
 *  3. challenge with TOTP → access token + refresh cookie
 *  4. recovery with one-time code → access token; second use rejected
 *  5. disable with password confirmation; without password → 401
 *  6. invalid TOTP / mfaToken / pre-activation rejected with RFC 9457 ProblemDetail
 *
 * The TOTP code is computed with the same library/period as the server.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class MfaControllerIT {

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

        private const val EMAIL = "mfa-user@example.com"
        private const val PASSWORD = "mfa-very-strong-password-1234"
        private const val TOTP_PERIOD_SECONDS: Long = 30
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    private lateinit var mfaSecretRepository: MfaSecretRepository

    @Autowired
    private lateinit var loginAttemptRepository: LoginAttemptRepository

    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val totpGenerator = DefaultCodeGenerator(HashingAlgorithm.SHA1, 6)
    private val systemTimeProvider = SystemTimeProvider()

    @BeforeEach
    fun cleanup() {
        loginAttemptRepository.deleteAll()
        mfaSecretRepository.deleteAll()
        refreshTokenRepository.deleteAll()
        userRepository.deleteAll()
        register()
    }

    @Test
    fun `enroll without bearer returns 401`() {
        mockMvc.post("/api/auth/mfa/enroll") { contentType = MediaType.APPLICATION_JSON }
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `enroll then verify activates MFA and login then issues mfaToken`() {
        val accessToken = loginNoMfaReturningAccessToken()
        val enrollBody = enroll(accessToken)
        verify(accessToken, currentTotpCode(enrollBody.secret))

        // After activation, plain login (email + password) must short-circuit
        // to mfaRequired=true and NOT emit access token / Set-Cookie.
        val loginResult = postLoginExpectingMfaRequired()
        val body = objectMapper.readTree(loginResult.response.contentAsString)
        assertThat(body.path("mfaRequired").asBoolean()).isTrue()
        assertThat(body.path("mfaToken").asText()).isNotBlank()
        assertThat(body.has("accessToken") && !body.get("accessToken").isNull).isFalse()
        assertThat(loginResult.response.getHeader("Set-Cookie")).isNull()
        assertThat(refreshTokenRepository.count()).isEqualTo(0)
    }

    @Test
    fun `challenge with TOTP returns access token and refresh cookie`() {
        val accessToken = loginNoMfaReturningAccessToken()
        val enrollBody = enroll(accessToken)
        verify(accessToken, currentTotpCode(enrollBody.secret))

        val mfaToken = mfaTokenFromLogin()
        val challengeResult = mockMvc.post("/api/auth/mfa/challenge") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                MfaChallengeRequest(mfaToken, currentTotpCode(enrollBody.secret)),
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { exists() }
            jsonPath("$.expiresInSeconds") { value(15 * 60) }
        }.andReturn()

        val setCookie = challengeResult.response.getHeader("Set-Cookie")
        assertThat(setCookie).isNotNull()
        assertThat(setCookie).contains("refresh_token=")
        assertThat(setCookie).containsIgnoringCase("HttpOnly")
        assertThat(setCookie).contains("Path=/api/auth")
    }

    @Test
    fun `challenge with wrong TOTP returns 400 invalid-totp-code`() {
        val accessToken = loginNoMfaReturningAccessToken()
        val enrollBody = enroll(accessToken)
        verify(accessToken, currentTotpCode(enrollBody.secret))
        val mfaToken = mfaTokenFromLogin()

        mockMvc.post("/api/auth/mfa/challenge") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(MfaChallengeRequest(mfaToken, "000000"))
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.type") { value("https://api/errors/invalid-totp-code") }
        }
    }

    @Test
    fun `challenge with access token in mfaToken slot returns 401`() {
        val accessToken = loginNoMfaReturningAccessToken()
        val enrollBody = enroll(accessToken)
        verify(accessToken, currentTotpCode(enrollBody.secret))

        // Replay the access token (purpose=access) as if it were the mfaToken
        // → JwtService.parseMfaChallengeToken must reject so an access token
        // can never bypass MFA.
        mockMvc.post("/api/auth/mfa/challenge") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                MfaChallengeRequest(accessToken, currentTotpCode(enrollBody.secret)),
            )
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `recovery code is single-use`() {
        val accessToken = loginNoMfaReturningAccessToken()
        val enrollBody = enroll(accessToken)
        verify(accessToken, currentTotpCode(enrollBody.secret))

        val recoveryCode = enrollBody.recoveryCodes.first()
        val firstMfaToken = mfaTokenFromLogin()
        mockMvc.post("/api/auth/mfa/recovery") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                MfaRecoveryRequest(firstMfaToken, recoveryCode),
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { exists() }
        }

        val secondMfaToken = mfaTokenFromLogin()
        mockMvc.post("/api/auth/mfa/recovery") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                MfaRecoveryRequest(secondMfaToken, recoveryCode),
            )
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.type") { value("https://api/errors/invalid-recovery-code") }
        }
    }

    @Test
    fun `disable with wrong password returns 401`() {
        val accessToken = loginNoMfaReturningAccessToken()
        val enrollBody = enroll(accessToken)
        verify(accessToken, currentTotpCode(enrollBody.secret))

        mockMvc.delete("/api/auth/mfa") {
            header("Authorization", "Bearer $accessToken")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(MfaDisableRequest("wrong-password-1234567890"))
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.type") { value("https://api/errors/invalid-credentials") }
        }
    }

    @Test
    fun `disable with correct password removes MFA and login goes back to direct token issuance`() {
        val accessToken = loginNoMfaReturningAccessToken()
        val enrollBody = enroll(accessToken)
        verify(accessToken, currentTotpCode(enrollBody.secret))

        mockMvc.delete("/api/auth/mfa") {
            header("Authorization", "Bearer $accessToken")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(MfaDisableRequest(PASSWORD))
        }.andExpect { status { isNoContent() } }

        val user = userRepository.findByEmailIgnoreCase(EMAIL)!!
        assertThat(mfaSecretRepository.findByUserId(user.id)).isNull()

        // Plain login again issues a token pair (no MFA short-circuit).
        loginAttemptRepository.deleteAll()
        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(LoginRequest(EMAIL, PASSWORD))
        }.andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { exists() }
            jsonPath("$.mfaRequired") { value(false) }
        }
    }

    @Test
    fun `verify with wrong code returns 400 and MFA stays disabled`() {
        val accessToken = loginNoMfaReturningAccessToken()
        enroll(accessToken)

        mockMvc.post("/api/auth/mfa/verify") {
            header("Authorization", "Bearer $accessToken")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(MfaVerifyRequest("000000"))
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.type") { value("https://api/errors/invalid-totp-code") }
        }

        val user = userRepository.findByEmailIgnoreCase(EMAIL)!!
        assertThat(mfaSecretRepository.findByUserId(user.id)?.enabled).isFalse()
    }

    @Test
    fun `enroll twice without verify reissues fresh material`() {
        val accessToken = loginNoMfaReturningAccessToken()
        val first = enroll(accessToken)
        val second = enroll(accessToken)
        assertThat(second.secret).isNotEqualTo(first.secret)
        assertThat(second.recoveryCodes).isNotEqualTo(first.recoveryCodes)
    }

    @Test
    fun `enroll after activation returns 409 mfa-already-enabled`() {
        val accessToken = loginNoMfaReturningAccessToken()
        val first = enroll(accessToken)
        verify(accessToken, currentTotpCode(first.secret))

        mockMvc.post("/api/auth/mfa/enroll") {
            header("Authorization", "Bearer $accessToken")
            contentType = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isConflict() }
            jsonPath("$.type") { value("https://api/errors/mfa-already-enabled") }
        }
    }

    @Test
    fun `disable when MFA was not enabled returns 409 mfa-not-enabled`() {
        val accessToken = loginNoMfaReturningAccessToken()
        mockMvc.delete("/api/auth/mfa") {
            header("Authorization", "Bearer $accessToken")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(MfaDisableRequest(PASSWORD))
        }.andExpect {
            status { isConflict() }
            jsonPath("$.type") { value("https://api/errors/mfa-not-enabled") }
        }
    }

    private fun register() {
        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(RegisterRequest(EMAIL, PASSWORD, "MFA User"))
        }.andExpect { status { isCreated() } }
    }

    // Test profile rate-limits LOGIN to 2 req per account in 5 min, and several
    // tests below need to call /login multiple times (no-MFA → enroll → MFA-required
    // → recovery → etc.). Reset the audit counter before each login so the rate
    // limiter stays out of the way; rate-limit semantics are validated separately.
    private fun loginNoMfaReturningAccessToken(): String {
        loginAttemptRepository.deleteAll()
        val result = mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(LoginRequest(EMAIL, PASSWORD))
        }.andReturn()
        check(result.response.status == 200) {
            "login failed: ${result.response.contentAsString}"
        }
        return objectMapper.readValue(result.response.contentAsString, AccessTokenResponse::class.java).accessToken
    }

    private fun postLoginExpectingMfaRequired(): MvcResult {
        loginAttemptRepository.deleteAll()
        return mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(LoginRequest(EMAIL, PASSWORD))
        }.andExpect { status { isOk() } }.andReturn()
    }

    private fun mfaTokenFromLogin(): String {
        val result = postLoginExpectingMfaRequired()
        val body: JsonNode = objectMapper.readTree(result.response.contentAsString)
        check(body.path("mfaRequired").asBoolean(false)) {
            "expected mfaRequired=true, got ${result.response.contentAsString}"
        }
        return body.get("mfaToken").asText()
    }

    private fun enroll(accessToken: String): MfaEnrollmentResponse {
        val result = mockMvc.post("/api/auth/mfa/enroll") {
            header("Authorization", "Bearer $accessToken")
            contentType = MediaType.APPLICATION_JSON
        }.andExpect { status { isOk() } }.andReturn()
        return objectMapper.readValue(result.response.contentAsString, MfaEnrollmentResponse::class.java)
    }

    private fun verify(accessToken: String, totpCode: String) {
        mockMvc.post("/api/auth/mfa/verify") {
            header("Authorization", "Bearer $accessToken")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(MfaVerifyRequest(totpCode))
        }.andExpect { status { isNoContent() } }
    }

    /**
     * Mirrors [TotpService] period arithmetic: counter = unix-seconds / 30s.
     * The `dev.samstevens.totp` library expects the bucket index, not raw seconds.
     */
    private fun currentTotpCode(secret: String): String {
        val counter = systemTimeProvider.time / TOTP_PERIOD_SECONDS
        return totpGenerator.generate(secret, counter)
    }
}
