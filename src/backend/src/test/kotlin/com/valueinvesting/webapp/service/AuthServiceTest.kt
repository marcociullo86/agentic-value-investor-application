package com.valueinvesting.webapp.service

import com.valueinvesting.webapp.config.AppProperties
import com.valueinvesting.webapp.persistence.entity.RefreshToken
import com.valueinvesting.webapp.persistence.entity.User
import com.valueinvesting.webapp.persistence.repository.RefreshTokenRepository
import com.valueinvesting.webapp.persistence.repository.UserRepository
import com.valueinvesting.webapp.security.IssuedToken
import com.valueinvesting.webapp.security.JwtService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID

/**
 * Unit-level coverage for [AuthService.refresh] — the sliding-TTL + absolute
 * cap algorithm introduced by ADR-010 §3 / TSK-041. The happy path on the
 * full stack lives in [com.valueinvesting.webapp.api.AuthControllerIT]; this
 * class isolates the time-arithmetic on a fixed [Clock] so the boundary
 * conditions (expiry, cap) are deterministic.
 */
class AuthServiceTest {

    private val now: Instant = Instant.parse("2026-05-22T10:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    private lateinit var userRepository: UserRepository
    private lateinit var refreshTokenRepository: RefreshTokenRepository
    private lateinit var passwordEncoder: PasswordEncoder
    private lateinit var jwtService: JwtService
    private lateinit var appProperties: AppProperties
    private lateinit var compromisedPasswordGuard: CompromisedPasswordGuard
    private lateinit var mfaService: MfaService
    private lateinit var bruteForceProtectionService: BruteForceProtectionService
    private lateinit var service: AuthService

    @BeforeEach
    fun setup() {
        userRepository = mockk(relaxed = true)
        refreshTokenRepository = mockk(relaxed = true)
        passwordEncoder = mockk(relaxed = true)
        jwtService = mockk()
        compromisedPasswordGuard = mockk(relaxed = true)
        mfaService = mockk(relaxed = true)
        bruteForceProtectionService = mockk(relaxed = true)
        appProperties = AppProperties(
            jwt = AppProperties.Jwt(
                signingSecret = "test-secret-test-secret-test-secret-test-secret-test-secret",
                accessTtlMinutes = 15,
                refreshSlidingTtlDays = 7,
                refreshAbsoluteCapDays = 30,
            ),
        )
        service = AuthService(
            userRepository,
            refreshTokenRepository,
            passwordEncoder,
            jwtService,
            appProperties,
            compromisedPasswordGuard,
            mfaService,
            bruteForceProtectionService,
            clock,
        )

        every { jwtService.issueAccessToken(any(), any()) } returns IssuedToken(
            token = "access.jwt.token",
            issuedAt = now,
            expiresAt = now.plusSeconds(900),
            expiresInSeconds = 900,
        )
    }

    @Test
    fun `refresh within sliding TTL emits new pair with first_issued_at preserved`() {
        val userId = UUID.randomUUID()
        val user = User(id = userId, email = "alice@example.com", passwordHash = "h", createdAt = now.minusSeconds(86400))
        val originalLogin = now.minusSeconds(2 * 86_400) // 2 days ago
        val refresh = RefreshToken(
            userId = userId,
            tokenValue = "old-refresh",
            expiresAt = now.plusSeconds(5 * 86_400), // still valid (5 days left of sliding TTL)
            firstIssuedAt = originalLogin,
        )
        every { refreshTokenRepository.findByTokenValue("old-refresh") } returns refresh
        every { userRepository.findById(userId) } returns Optional.of(user)
        val newTokenSlot = slot<RefreshToken>()
        every { refreshTokenRepository.save(capture(newTokenSlot)) } answers { newTokenSlot.captured }

        service.refresh("old-refresh")

        // The slot captures the *last* save() — the newly-issued refresh from
        // issueTokenPair. It must preserve the chain's first_issued_at and
        // slide expires_at forward by refreshSlidingTtlDays from `now`.
        // The original refresh is mutated in-place with revokedAt = now.
        val newRefresh = newTokenSlot.captured
        assertThat(refresh.revokedAt).isEqualTo(now)
        assertThat(newRefresh.tokenValue).isNotEqualTo("old-refresh")
        assertThat(newRefresh.firstIssuedAt).isEqualTo(originalLogin)
        assertThat(newRefresh.expiresAt).isEqualTo(now.plusSeconds(7L * 86_400))
    }

    // TSK-041 anti-enum (iter-2): the runtime [message] is uniform
    // ([InvalidRefreshTokenException.CLIENT_DETAIL]) across every cause; the
    // differentiating signal lives in [reason], which never reaches the wire.
    // Tests assert against `reason` so a regression that leaks cause-specific
    // text into `message` is caught.

    @Test
    fun `refresh fails with 401 invalid-refresh when sliding TTL expired`() {
        val userId = UUID.randomUUID()
        val refresh = RefreshToken(
            userId = userId,
            tokenValue = "stale-refresh",
            expiresAt = now.minusSeconds(1), // just expired
            firstIssuedAt = now.minusSeconds(10 * 86_400),
        )
        every { refreshTokenRepository.findByTokenValue("stale-refresh") } returns refresh

        assertThatThrownBy { service.refresh("stale-refresh") }
            .isInstanceOf(InvalidRefreshTokenException::class.java)
            .hasMessage(InvalidRefreshTokenException.CLIENT_DETAIL)
            .extracting { (it as InvalidRefreshTokenException).reason }
            .isEqualTo("sliding_expired")
    }

    @Test
    fun `refresh fails with 401 invalid-refresh when absolute 30d cap reached`() {
        val userId = UUID.randomUUID()
        val refresh = RefreshToken(
            userId = userId,
            tokenValue = "capped-refresh",
            // Sliding TTL still valid (issued 1 hour ago after some rotation chain)…
            expiresAt = now.plusSeconds(6 * 86_400),
            // …but the chain was born 31 days ago → cap reached.
            firstIssuedAt = now.minusSeconds(31L * 86_400),
        )
        every { refreshTokenRepository.findByTokenValue("capped-refresh") } returns refresh

        assertThatThrownBy { service.refresh("capped-refresh") }
            .isInstanceOf(InvalidRefreshTokenException::class.java)
            .hasMessage(InvalidRefreshTokenException.CLIENT_DETAIL)
            .extracting { (it as InvalidRefreshTokenException).reason }
            .isEqualTo("absolute_cap")
    }

    @Test
    fun `refresh fails with 401 invalid-refresh when token already revoked`() {
        val userId = UUID.randomUUID()
        val refresh = RefreshToken(
            userId = userId,
            tokenValue = "revoked-refresh",
            expiresAt = now.plusSeconds(86_400),
            firstIssuedAt = now.minusSeconds(86_400),
            revokedAt = now.minusSeconds(60),
        )
        every { refreshTokenRepository.findByTokenValue("revoked-refresh") } returns refresh

        assertThatThrownBy { service.refresh("revoked-refresh") }
            .isInstanceOf(InvalidRefreshTokenException::class.java)
            .hasMessage(InvalidRefreshTokenException.CLIENT_DETAIL)
            .extracting { (it as InvalidRefreshTokenException).reason }
            .isEqualTo("revoked")
    }

    @Test
    fun `refresh fails with 401 invalid-refresh when token unknown`() {
        every { refreshTokenRepository.findByTokenValue("ghost") } returns null

        assertThatThrownBy { service.refresh("ghost") }
            .isInstanceOf(InvalidRefreshTokenException::class.java)
            .hasMessage(InvalidRefreshTokenException.CLIENT_DETAIL)
            .extracting { (it as InvalidRefreshTokenException).reason }
            .isEqualTo("not_found")
    }
}
