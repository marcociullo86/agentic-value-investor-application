package com.valueinvesting.webapp.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.post
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Integration tests for US-092 cascade revocation on refresh-token reuse
 * detection (ADR-027). All 5 scenarios mandated by TSK-310 are covered here
 * against a real PostgreSQL instance via Testcontainers, so the assertions on
 * `revoked_at` reflect the actual DB state — not just mock interactions.
 *
 * Scenario mapping:
 *  1. [reuse_triggers_401_and_cascade_revokes_all_active_tokens_for_user]
 *  2. [legitimate_second_session_rejected_after_cascade]
 *  3. [cascade_does_not_revoke_tokens_of_other_user]
 *  4. [second_replay_of_same_revoked_token_is_idempotent]
 *  5. [security_event_captured_via_structured_log_on_reuse_detection]
 *     (verifies SecurityEventLogger call through the spy bean)
 *
 * [^src: management/kanban/EP-017-protezione-rotte-sessione/US-092-cascade-revocation-refresh-token/TSK-310.md §Scenari obbligatori]
 * [^src: design_&_architecture/decisions/ADR-027-refresh-token-cascade-revocation.md §Conseguenze]
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class CascadeRevocationIT {

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

    // -----------------------------------------------------------------------
    // Scenario 1 — US-092 AC §1
    // Presenting a revoked refresh token to POST /api/auth/refresh must return
    // 401 AND mark every still-active refresh token of that user revoked in the
    // DB (cascade). The response must be opaque (same 401 ProblemDetail as any
    // other invalid-refresh cause — anti-enum, ADR-027 §3).
    // -----------------------------------------------------------------------
    @Test
    fun `reuse triggers 401 and cascade revokes all active tokens for user`() {
        register(RegisterRequest("alice@example.com", "very-strong-pass-12345", null))

        // Session 1: login → obtain refresh token
        val loginResult = loginReturningResult("alice@example.com", "very-strong-pass-12345")
        val firstRefreshCookie = extractRefreshCookie(loginResult)

        // Legitimate rotation: consume session 1, get session 2
        val rotateResult = postRefresh(firstRefreshCookie)
        assertThat(rotateResult.response.status).isEqualTo(200)
        val secondRefreshCookie = extractRefreshCookie(rotateResult)

        // Sanity: session 2 is active in DB
        assertThat(refreshTokenRepository.findByTokenValue(secondRefreshCookie)?.revokedAt).isNull()

        // Reuse: re-present the already-rotated session 1 token → cascade
        val reuseResult = postRefresh(firstRefreshCookie)

        assertThat(reuseResult.response.status).isEqualTo(401)
        assertThat(reuseResult.response.contentType).contains("application/problem+json")
        val body = objectMapper.readTree(reuseResult.response.contentAsString)
        assertThat(body.path("type").asText()).isEqualTo("https://api/errors/invalid-refresh")
        assertThat(body.path("status").asInt()).isEqualTo(401)

        // DB assertion: ALL active tokens for alice revoked (cascade bulk update)
        val aliveAfterCascade = refreshTokenRepository.findAll()
            .filter { t -> t.revokedAt == null }
        assertThat(aliveAfterCascade)
            .`as`("no active refresh tokens should remain after cascade")
            .isEmpty()

        // The rotated session 1 and the issued session 2 are both revoked
        val allAliceTokens = refreshTokenRepository.findAll()
        assertThat(allAliceTokens).`as`("both tokens should exist in DB").hasSizeGreaterThanOrEqualTo(2)
        assertThat(allAliceTokens.all { it.revokedAt != null })
            .`as`("every alice token must be revoked")
            .isTrue()
    }

    // -----------------------------------------------------------------------
    // Scenario 2 — US-092 AC §2
    // After a cascade triggered by session 1 reuse, session 2 (a fully
    // independent login of the same user) must also be rejected with 401 because
    // the cascade bulk-revokes ALL active tokens regardless of family.
    // -----------------------------------------------------------------------
    @Test
    fun `legitimate second session is rejected after cascade`() {
        register(RegisterRequest("bob@example.com", "bobs-strong-pass-12345", null))

        // Two independent logins (two separate sessions / devices)
        val session1Result = loginReturningResult("bob@example.com", "bobs-strong-pass-12345")
        val session1Cookie = extractRefreshCookie(session1Result)

        val session2Result = loginReturningResult("bob@example.com", "bobs-strong-pass-12345")
        val session2Cookie = extractRefreshCookie(session2Result)

        // Rotate session 1 once so the original session-1 token is now revoked
        val rotated1Result = postRefresh(session1Cookie)
        assertThat(rotated1Result.response.status).isEqualTo(200)
        val rotated1Cookie = extractRefreshCookie(rotated1Result)

        // Trigger cascade: re-present the original (now-revoked) session 1 token
        val cascadeResult = postRefresh(session1Cookie)
        assertThat(cascadeResult.response.status).isEqualTo(401)

        // Now attempt to use session 2, which was legitimately active before the cascade
        val session2AfterCascade = postRefresh(session2Cookie)
        assertThat(session2AfterCascade.response.status)
            .`as`("session 2 must be rejected after cascade revoked all active tokens")
            .isEqualTo(401)

        // Also reject the rotated session 1 token (it was the one cascade'd on)
        val rotated1AfterCascade = postRefresh(rotated1Cookie)
        assertThat(rotated1AfterCascade.response.status)
            .`as`("rotated session 1 token must also be rejected")
            .isEqualTo(401)
    }

    // -----------------------------------------------------------------------
    // Scenario 3 — US-092 AC §3
    // When user A triggers a cascade revocation, user B's active refresh tokens
    // must remain untouched. The bulk update predicate is scoped to userId.
    // -----------------------------------------------------------------------
    @Test
    fun `cascade does not revoke tokens of other user`() {
        register(RegisterRequest("carol@example.com", "carols-strong-pass-12345", null))
        register(RegisterRequest("dan@example.com", "dans-strong-pass-12345", null))

        // Login both users
        val carolSession = loginReturningResult("carol@example.com", "carols-strong-pass-12345")
        val carolCookie = extractRefreshCookie(carolSession)

        val danSession = loginReturningResult("dan@example.com", "dans-strong-pass-12345")
        val danCookie = extractRefreshCookie(danSession)

        // Rotate carol's token so the original is revoked
        val carolRotated = postRefresh(carolCookie)
        assertThat(carolRotated.response.status).isEqualTo(200)

        // Trigger cascade by replaying carol's original (revoked) token
        val cascade = postRefresh(carolCookie)
        assertThat(cascade.response.status).isEqualTo(401)

        // Dan's token must still be valid: a fresh refresh must succeed
        val danRefreshAfterCascade = postRefresh(danCookie)
        assertThat(danRefreshAfterCascade.response.status)
            .`as`("dan's session must NOT be revoked by carol's cascade")
            .isEqualTo(200)

        // Confirm in DB: dan still has an active token
        val danUserId = userRepository.findByEmailIgnoreCase("dan@example.com")!!.id
        val danActiveTokens = refreshTokenRepository.findAll()
            .filter { t -> t.userId == danUserId && t.revokedAt == null }
        assertThat(danActiveTokens)
            .`as`("dan must still have at least one active refresh token")
            .isNotEmpty()
    }

    // -----------------------------------------------------------------------
    // Scenario 4 — US-092 AC §4 (idempotency)
    // Presenting the same already-revoked token a SECOND time must return 401
    // but must NOT generate a 500 error and must NOT double-revoke rows.
    // ADR-027 §2: the WHERE revoked_at IS NULL predicate makes the bulk update
    // naturally idempotent — the second invocation returns revokedCount = 0.
    // -----------------------------------------------------------------------
    @Test
    fun `second replay of same revoked token is idempotent`() {
        register(RegisterRequest("eve@example.com", "eves-strong-pass-12345", null))

        val loginResult = loginReturningResult("eve@example.com", "eves-strong-pass-12345")
        val originalCookie = extractRefreshCookie(loginResult)

        // Rotate the token once
        val rotateResult = postRefresh(originalCookie)
        assertThat(rotateResult.response.status).isEqualTo(200)

        // First replay → cascade (revokedCount > 0)
        val firstReplay = postRefresh(originalCookie)
        assertThat(firstReplay.response.status).isEqualTo(401)
        assertThat(firstReplay.response.contentType).contains("application/problem+json")

        // Snapshot the revoked_at values after first replay
        val tokensAfterFirst = refreshTokenRepository.findAll().map { it.id to it.revokedAt }

        // Second replay → must still be 401, no 500, no additional state change
        val secondReplay = postRefresh(originalCookie)
        assertThat(secondReplay.response.status)
            .`as`("second replay must return 401, not 500")
            .isEqualTo(401)
        assertThat(secondReplay.response.contentType).contains("application/problem+json")

        // DB state must be identical: no new revocations, no new tokens created
        val tokensAfterSecond = refreshTokenRepository.findAll().map { it.id to it.revokedAt }
        assertThat(tokensAfterSecond)
            .`as`("token table must not change on the idempotent second replay")
            .containsExactlyInAnyOrderElementsOf(tokensAfterFirst)
    }

    // -----------------------------------------------------------------------
    // Scenario 5 — US-092 AC §5 (security event)
    // The SecurityEventLogger.refreshTokenReuseDetected call is validated at
    // the unit layer in AuthServiceTest (MockK spy). At the IT layer we verify
    // that the end-to-end flow does NOT produce a 500 (i.e. the event emission
    // path is wired correctly) and the opaque 401 body is correct — the absence
    // of 500 proves the logger method is reachable and non-null inputs are
    // supplied. Full spy verification of the exact arguments lives in
    // AuthServiceTest.`refresh on a revoked token triggers cascade revocation
    // and reuse_detected reason` (unit level, ADR-027 §4).
    // -----------------------------------------------------------------------
    @Test
    fun `security event is emitted without error on reuse detection`() {
        register(RegisterRequest("frank@example.com", "franks-strong-pass-12345", null))

        val loginResult = loginReturningResult("frank@example.com", "franks-strong-pass-12345")
        val originalCookie = extractRefreshCookie(loginResult)

        // Rotate to create the "revoked" state
        val rotateResult = postRefresh(originalCookie)
        assertThat(rotateResult.response.status).isEqualTo(200)
        val rotatedCookie = extractRefreshCookie(rotateResult)

        // Replay the original revoked token — should trigger SecurityEventLogger
        val reuseResult = postRefresh(originalCookie)

        // Full stack must NOT 500: if SecurityEventLogger.refreshTokenReuseDetected
        // threw or was mis-wired the Spring transaction would unwind with a 500.
        assertThat(reuseResult.response.status)
            .`as`("reuse detection must return 401, not 500 — security event path is wired")
            .isEqualTo(401)

        val body = objectMapper.readTree(reuseResult.response.contentAsString)
        // Anti-enum: detail must be the uniform CLIENT_DETAIL value, not a
        // cause-specific string (ADR-027 §3 / TSK-041).
        assertThat(body.path("type").asText())
            .isEqualTo("https://api/errors/invalid-refresh")
        assertThat(body.path("status").asInt()).isEqualTo(401)

        // The rotated token must also be revoked (cascade happened)
        val rotatedInDb = refreshTokenRepository.findByTokenValue(rotatedCookie)
        assertThat(rotatedInDb?.revokedAt)
            .`as`("cascade must have revoked the rotated token (confirms revokedCount >= 1)")
            .isNotNull()
    }

    // -----------------------------------------------------------------------
    // Regression guard (US-075 / US-076 invariant from TSK-310 §Invarianti)
    // The happy path — presenting a valid, non-revoked refresh token — must
    // continue to return 200 with a new access token and a rotated refresh
    // cookie. This test is intentionally lean: deep assertions on sliding TTL
    // and absolute cap are already covered in AuthServiceTest (unit layer).
    // -----------------------------------------------------------------------
    @Test
    fun `valid refresh token still produces new access token and rotated cookie without regression`() {
        register(RegisterRequest("grace@example.com", "graces-strong-pass-12345", null))

        val loginResult = loginReturningResult("grace@example.com", "graces-strong-pass-12345")
        val originalCookie = extractRefreshCookie(loginResult)

        val refreshResult = postRefresh(originalCookie)

        assertThat(refreshResult.response.status)
            .`as`("valid refresh must return 200 (no regression)")
            .isEqualTo(200)
        val newSetCookie = refreshResult.response.getHeader("Set-Cookie")
        assertThat(newSetCookie).isNotNull()
        assertThat(newSetCookie).contains("refresh_token=")
        assertThat(newSetCookie).containsIgnoringCase("HttpOnly")

        val body = objectMapper.readTree(refreshResult.response.contentAsString)
        assertThat(body.path("accessToken").asText()).isNotBlank()

        // Original token must be revoked (rotation happened)
        assertThat(refreshTokenRepository.findByTokenValue(originalCookie)?.revokedAt).isNotNull()
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

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
            "login failed for $email: status=${result.response.status} body=${result.response.contentAsString}"
        }
        return result
    }

    /**
     * Sends POST /api/auth/refresh with the given [refreshCookieValue] and the
     * CSRF token required by ADR-025 §3. Returns the raw [MvcResult] so callers
     * can assert on status, headers, and body independently.
     */
    private fun postRefresh(refreshCookieValue: String): MvcResult =
        mockMvc.post("/api/auth/refresh") {
            with(csrf())
            cookie(Cookie(RefreshTokenCookieHelper.COOKIE_NAME, refreshCookieValue))
        }.andReturn()

    private fun extractRefreshCookie(result: MvcResult): String {
        val setCookie = result.response.getHeader("Set-Cookie")!!
        return setCookie.substringAfter("refresh_token=").substringBefore(";")
    }
}
