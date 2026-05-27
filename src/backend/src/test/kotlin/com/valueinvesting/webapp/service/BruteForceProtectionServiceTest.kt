package com.valueinvesting.webapp.service

import com.valueinvesting.webapp.client.TurnstileClient
import com.valueinvesting.webapp.config.BruteForceProperties
import com.valueinvesting.webapp.persistence.entity.LoginAttemptEntity
import com.valueinvesting.webapp.persistence.entity.User
import com.valueinvesting.webapp.persistence.repository.LoginAttemptRepository
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * Unit-level coverage for [BruteForceProtectionService] (TSK-230). All time
 * arithmetic runs against a fixed [Clock] so the boundary conditions
 * (5min/15min/30min windows, progressive-delay exponent) are deterministic.
 *
 * The progressive-delay [Thread.sleep] inside [BruteForceProtectionService.guardLogin]
 * is exercised with the threshold set to a huge number so no sleep ever fires
 * in the test branches that DON'T assert on it — keeps the suite fast.
 */
class BruteForceProtectionServiceTest {

    private val now: Instant = Instant.parse("2026-05-28T01:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    private lateinit var loginAttemptRepository: LoginAttemptRepository
    private lateinit var turnstileClient: TurnstileClient
    private lateinit var service: BruteForceProtectionService
    private lateinit var properties: BruteForceProperties

    @BeforeEach
    fun setup() {
        loginAttemptRepository = mockk(relaxed = true)
        turnstileClient = mockk()
        properties = BruteForceProperties()
        // Default-safe stubs: every counter returns 0, every "latest lockout"
        // returns null, every history list returns empty. Per-test overrides
        // shadow these to exercise the relevant code path.
        every {
            loginAttemptRepository.countByIpAddressAndFailureReasonAndAttemptedAtAfter(any(), any(), any())
        } returns 0
        every {
            loginAttemptRepository.countByAccountEmailAndFailureReasonAndAttemptedAtAfter(any(), any(), any())
        } returns 0
        every {
            loginAttemptRepository.findLatestAttemptedAtByAccountSince(any(), any(), any())
        } returns null
        every {
            loginAttemptRepository.findRecentSuccessfulIpsByAccount(any(), any())
        } returns emptyList()

        service = BruteForceProtectionService(
            loginAttemptRepository = loginAttemptRepository,
            properties = properties,
            turnstileClient = turnstileClient,
            clock = clock,
        )
    }

    // ---------------------------------------------------------------------
    // guardLogin — account lockout (423 Locked)
    // ---------------------------------------------------------------------

    @Test
    fun `guardLogin throws AccountLockedException when lockout sentinel within 30 minutes`() {
        val lockoutStart = now.minus(Duration.ofMinutes(10))
        every {
            loginAttemptRepository.findLatestAttemptedAtByAccountSince(
                accountEmail = "alice@example.com",
                failureReason = BruteForceProtectionService.REASON_ACCOUNT_LOCKED,
                since = any(),
            )
        } returns lockoutStart

        assertThatThrownBy { service.guardLogin("alice@example.com", "203.0.113.10", null) }
            .isInstanceOfSatisfying(AccountLockedException::class.java) { ex ->
                // 30 min lockout starting 10 min ago → 20 min left (1200s).
                assertThat(ex.retryAfterSeconds).isEqualTo(20 * 60L)
            }
    }

    @Test
    fun `guardLogin succeeds when lockout sentinel is older than configured duration`() {
        // findLatestAttemptedAtByAccountSince already filters by `since` so a
        // stale sentinel returns null — guard proceeds normally.
        every {
            loginAttemptRepository.findLatestAttemptedAtByAccountSince(any(), any(), any())
        } returns null

        assertThatCode { service.guardLogin("alice@example.com", "203.0.113.10", null) }
            .doesNotThrowAnyException()
    }

    // ---------------------------------------------------------------------
    // guardLogin — per-IP CAPTCHA gate
    // ---------------------------------------------------------------------

    @Test
    fun `guardLogin throws CaptchaRequiredException TOKEN_MISSING when IP failures exceed threshold and token absent`() {
        every {
            loginAttemptRepository.countByIpAddressAndFailureReasonAndAttemptedAtAfter(
                "198.51.100.5",
                BruteForceProtectionService.REASON_BAD_CREDENTIALS,
                any(),
            )
        } returns properties.ipCaptchaThreshold

        assertThatThrownBy { service.guardLogin("bob@example.com", "198.51.100.5", captchaToken = null) }
            .isInstanceOfSatisfying(CaptchaRequiredException::class.java) {
                assertThat(it.reason).isEqualTo(CaptchaRequiredException.Reason.TOKEN_MISSING)
            }
    }

    @Test
    fun `guardLogin throws CaptchaRequiredException TOKEN_INVALID when siteverify rejects token`() {
        every {
            loginAttemptRepository.countByIpAddressAndFailureReasonAndAttemptedAtAfter(any(), any(), any())
        } returns properties.ipCaptchaThreshold
        every { turnstileClient.verify("bad-token", "198.51.100.5") } returns false

        assertThatThrownBy { service.guardLogin("bob@example.com", "198.51.100.5", "bad-token") }
            .isInstanceOfSatisfying(CaptchaRequiredException::class.java) {
                assertThat(it.reason).isEqualTo(CaptchaRequiredException.Reason.TOKEN_INVALID)
            }
    }

    @Test
    fun `guardLogin proceeds when IP failures exceed threshold but captcha verifies`() {
        every {
            loginAttemptRepository.countByIpAddressAndFailureReasonAndAttemptedAtAfter(any(), any(), any())
        } returns properties.ipCaptchaThreshold
        every { turnstileClient.verify("good-token", "198.51.100.5") } returns true

        assertThatCode { service.guardLogin("bob@example.com", "198.51.100.5", "good-token") }
            .doesNotThrowAnyException()
        verify(exactly = 1) { turnstileClient.verify("good-token", "198.51.100.5") }
    }

    @Test
    fun `guardLogin does not call turnstile when IP failures below threshold`() {
        // Counter under threshold — no captcha gate; siteverify never invoked
        // regardless of whether a token was supplied.
        every {
            loginAttemptRepository.countByIpAddressAndFailureReasonAndAttemptedAtAfter(any(), any(), any())
        } returns properties.ipCaptchaThreshold - 1

        service.guardLogin("bob@example.com", "198.51.100.5", "some-token")

        verify(exactly = 0) { turnstileClient.verify(any(), any()) }
    }

    // ---------------------------------------------------------------------
    // guardLogin — progressive delay
    // ---------------------------------------------------------------------

    @Test
    fun `guardLogin applies no delay below progressive threshold`() {
        // 4 failures < 5 (default threshold). No sleep, no captcha (IP=0), no
        // lockout (latest=null). Returns instantly — assert via duration cap.
        every {
            loginAttemptRepository.countByAccountEmailAndFailureReasonAndAttemptedAtAfter(
                "alice@example.com",
                BruteForceProtectionService.REASON_BAD_CREDENTIALS,
                any(),
            )
        } returns 4

        val durationMs = measureMillis { service.guardLogin("alice@example.com", "203.0.113.10", null) }
        assertThat(durationMs).isLessThan(500)
    }

    @Test
    fun `guardLogin caps progressive delay at progressiveDelayCapSeconds`() {
        // Use a tiny cap so the sleep is observable in unit tests. Reconstruct
        // the service with a 1-second cap and threshold so we don't actually
        // sleep minutes during CI.
        properties = BruteForceProperties(progressiveDelayCapSeconds = 1, progressiveDelayThreshold = 5)
        service = BruteForceProtectionService(
            loginAttemptRepository = loginAttemptRepository,
            properties = properties,
            turnstileClient = turnstileClient,
            clock = clock,
        )
        every {
            loginAttemptRepository.countByAccountEmailAndFailureReasonAndAttemptedAtAfter(
                "alice@example.com",
                BruteForceProtectionService.REASON_BAD_CREDENTIALS,
                any(),
            )
        } returns 50L // 2^45 raw, capped to 1s

        val durationMs = measureMillis { service.guardLogin("alice@example.com", "203.0.113.10", null) }
        assertThat(durationMs).isBetween(900L, 2_000L)
    }

    // ---------------------------------------------------------------------
    // guardRegister — per-IP captcha only
    // ---------------------------------------------------------------------

    @Test
    fun `guardRegister applies captcha gate when IP threshold tripped`() {
        every {
            loginAttemptRepository.countByIpAddressAndFailureReasonAndAttemptedAtAfter(any(), any(), any())
        } returns properties.ipCaptchaThreshold

        assertThatThrownBy { service.guardRegister("198.51.100.5", null) }
            .isInstanceOf(CaptchaRequiredException::class.java)
    }

    @Test
    fun `guardRegister does not consult lockout sentinel because no account email is known`() {
        every {
            loginAttemptRepository.countByIpAddressAndFailureReasonAndAttemptedAtAfter(any(), any(), any())
        } returns 0

        service.guardRegister("198.51.100.5", null)

        verify(exactly = 0) {
            loginAttemptRepository.findLatestAttemptedAtByAccountSince(any(), any(), any())
        }
    }

    // ---------------------------------------------------------------------
    // recordLoginFailure — persistence + lockout sentinel
    // ---------------------------------------------------------------------

    @Test
    fun `recordLoginFailure persists row with normalized email and reason`() {
        val captured = slot<LoginAttemptEntity>()
        every { loginAttemptRepository.save(capture(captured)) } answers { captured.captured }

        service.recordLoginFailure(
            email = "  Alice@Example.COM ",
            ip = "203.0.113.10",
            userAgent = "ua-string",
            reason = BruteForceProtectionService.REASON_BAD_CREDENTIALS,
        )

        assertThat(captured.captured.accountEmail).isEqualTo("alice@example.com")
        assertThat(captured.captured.ipAddress).isEqualTo("203.0.113.10")
        assertThat(captured.captured.success).isFalse()
        assertThat(captured.captured.failureReason).isEqualTo("bad_credentials")
        assertThat(captured.captured.userAgent).isEqualTo("ua-string")
        assertThat(captured.captured.attemptedAt).isEqualTo(now)
    }

    @Test
    fun `recordLoginFailure inserts ACCOUNT_LOCKED sentinel when threshold crossed`() {
        // Stage: this failure pushes the 15-min window to >= 20 fails.
        every {
            loginAttemptRepository.countByAccountEmailAndFailureReasonAndAttemptedAtAfter(
                "alice@example.com",
                BruteForceProtectionService.REASON_BAD_CREDENTIALS,
                any(),
            )
        } returns properties.lockoutThreshold

        val saves = mutableListOf<LoginAttemptEntity>()
        every { loginAttemptRepository.save(capture(saves)) } answers { firstArg() }

        service.recordLoginFailure(
            email = "alice@example.com",
            ip = "203.0.113.10",
            userAgent = "ua",
            reason = BruteForceProtectionService.REASON_BAD_CREDENTIALS,
        )

        assertThat(saves).hasSize(2)
        assertThat(saves[0].failureReason).isEqualTo("bad_credentials")
        assertThat(saves[1].failureReason).isEqualTo("account_locked")
        assertThat(saves[1].accountEmail).isEqualTo("alice@example.com")
    }

    @Test
    fun `recordLoginFailure does not insert second sentinel when account already locked`() {
        // Already locked 5 min ago: lockout sentinel within 30 min window.
        every {
            loginAttemptRepository.findLatestAttemptedAtByAccountSince(
                "alice@example.com",
                BruteForceProtectionService.REASON_ACCOUNT_LOCKED,
                any(),
            )
        } returns now.minus(Duration.ofMinutes(5))
        every {
            loginAttemptRepository.countByAccountEmailAndFailureReasonAndAttemptedAtAfter(
                "alice@example.com",
                BruteForceProtectionService.REASON_BAD_CREDENTIALS,
                any(),
            )
        } returns properties.lockoutThreshold + 5
        val saves = mutableListOf<LoginAttemptEntity>()
        every { loginAttemptRepository.save(capture(saves)) } answers { firstArg() }

        service.recordLoginFailure(
            email = "alice@example.com",
            ip = "203.0.113.10",
            userAgent = null,
            reason = BruteForceProtectionService.REASON_BAD_CREDENTIALS,
        )

        // Only the bad_credentials row — no second account_locked sentinel.
        assertThat(saves).hasSize(1)
        assertThat(saves[0].failureReason).isEqualTo("bad_credentials")
    }

    @Test
    fun `recordLoginFailure for non-bad-credentials reason does not trigger lockout check`() {
        every {
            loginAttemptRepository.countByAccountEmailAndFailureReasonAndAttemptedAtAfter(any(), any(), any())
        } returns properties.lockoutThreshold + 1
        val saves = mutableListOf<LoginAttemptEntity>()
        every { loginAttemptRepository.save(capture(saves)) } answers { firstArg() }

        service.recordLoginFailure(
            email = "alice@example.com",
            ip = "203.0.113.10",
            userAgent = null,
            reason = BruteForceProtectionService.REASON_MFA_REQUIRED,
        )

        assertThat(saves).hasSize(1)
        assertThat(saves[0].failureReason).isEqualTo("mfa_required")
    }

    // ---------------------------------------------------------------------
    // recordLoginSuccess — new device detection
    // ---------------------------------------------------------------------

    @Test
    fun `recordLoginSuccess saves success row and flags new device when IP unknown`() {
        val user = User(id = UUID.randomUUID(), email = "alice@example.com", passwordHash = "h")
        every {
            loginAttemptRepository.findRecentSuccessfulIpsByAccount(
                "alice@example.com",
                PageRequest.of(0, properties.newDeviceHistorySize),
            )
        } returns listOf("10.0.0.1", "10.0.0.2")
        val saved = slot<LoginAttemptEntity>()
        every { loginAttemptRepository.save(capture(saved)) } answers { firstArg() }

        service.recordLoginSuccess(user, "198.51.100.99", "ua")

        assertThat(saved.captured.success).isTrue()
        assertThat(saved.captured.failureReason).isNull()
        assertThat(saved.captured.ipAddress).isEqualTo("198.51.100.99")
        // New-device detection logs via SLF4J — verified by the absence of
        // exceptions; the structured event is asserted via the production
        // logback config (see ADR-021). Unit-level we just assert the
        // history query was made with the correct paging.
        verify(exactly = 1) {
            loginAttemptRepository.findRecentSuccessfulIpsByAccount(
                "alice@example.com",
                PageRequest.of(0, properties.newDeviceHistorySize),
            )
        }
    }

    @Test
    fun `recordLoginSuccess does not flag new device when IP appears in recent history`() {
        val user = User(id = UUID.randomUUID(), email = "alice@example.com", passwordHash = "h")
        every {
            loginAttemptRepository.findRecentSuccessfulIpsByAccount(any(), any())
        } returns listOf("198.51.100.99", "10.0.0.2")
        every { loginAttemptRepository.save(any<LoginAttemptEntity>()) } answers { firstArg() }

        service.recordLoginSuccess(user, "198.51.100.99", "ua")

        // No assertion on log output here — happy path just needs not to
        // throw. The "new device" branch is covered by the preceding test.
        verify(exactly = 1) { loginAttemptRepository.save(any<LoginAttemptEntity>()) }
    }

    @Test
    fun `recordLoginSuccess on first ever login does not flag new device`() {
        val user = User(id = UUID.randomUUID(), email = "alice@example.com", passwordHash = "h")
        every {
            loginAttemptRepository.findRecentSuccessfulIpsByAccount(any(), any())
        } returns emptyList()
        every { loginAttemptRepository.save(any<LoginAttemptEntity>()) } answers { firstArg() }

        service.recordLoginSuccess(user, "198.51.100.99", "ua")

        verify(exactly = 1) { loginAttemptRepository.save(any<LoginAttemptEntity>()) }
    }

    // ---------------------------------------------------------------------
    // purgeExpiredLoginAttempts
    // ---------------------------------------------------------------------

    @Test
    fun `purgeExpiredLoginAttempts deletes by cutoff = now minus retention days`() {
        every { loginAttemptRepository.deleteByAttemptedAtBefore(any()) } returns 42

        service.purgeExpiredLoginAttempts()

        val expectedCutoff = now.minus(Duration.ofDays(properties.cleanupRetentionDays))
        verify(exactly = 1) { loginAttemptRepository.deleteByAttemptedAtBefore(expectedCutoff) }
    }

    @Test
    fun `purgeExpiredLoginAttempts is a no-op log when nothing was deleted`() {
        every { loginAttemptRepository.deleteByAttemptedAtBefore(any()) } returns 0

        service.purgeExpiredLoginAttempts()

        verify(exactly = 1) { loginAttemptRepository.deleteByAttemptedAtBefore(any()) }
        confirmVerified(turnstileClient)
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private inline fun measureMillis(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000
    }
}
