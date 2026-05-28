package com.valueinvesting.webapp.service

import com.valueinvesting.webapp.client.TurnstileClient
import com.valueinvesting.webapp.config.BruteForceProperties
import com.valueinvesting.webapp.persistence.entity.LoginAttemptEntity
import com.valueinvesting.webapp.persistence.entity.User
import com.valueinvesting.webapp.persistence.repository.LoginAttemptRepository
import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.LoggerFactory
import org.slf4j.MarkerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Brute-force protection for `/api/auth/login` and `/api/auth/register`
 * (US-081 / ADR-025 §5, TSK-230).
 *
 * Stack of defences applied in the order documented by ADR-025 §5 — every
 * check is backed by the `login_attempts` table populated here so we never
 * leak attacker state into a separate in-memory cache that would reset on
 * pod restart.
 *
 * | Threshold (window)              | Effect                                           |
 * |---------------------------------|--------------------------------------------------|
 * | 5+ bad_credentials/account/5min | Progressive delay 2^(n-5) s, cap [progressiveDelayCapSeconds] |
 * | 10+ bad_credentials/IP/5min     | CAPTCHA required ([CaptchaRequiredException])    |
 * | 20+ bad_credentials/account/15min | 30 min account lockout ([AccountLockedException]) |
 *
 * Counters look at rows where `failure_reason = REASON_BAD_CREDENTIALS`. The
 * rate-limit probe rows written by [AuthRateLimitService] use a different
 * `failure_reason` (`rate_limit_probe:*`) so they never inflate brute-force
 * counters — and vice versa.
 *
 * ## Transaction semantics
 *
 * Recording methods run with [Propagation.REQUIRES_NEW] so they survive an
 * outer rollback (the typical case: `AuthService.login` throws
 * `BadCredentialsException` and the wrapping transaction rolls back — the
 * attempted-at row MUST survive to fuel the counter for the next request).
 *
 * The guard itself runs with [Propagation.NOT_SUPPORTED] so the
 * progressive-delay [Thread.sleep] does not hold a DB connection from the
 * outer transaction's pool slot. Each count query opens its own short
 * read against the connection pool, then releases it before the sleep.
 *
 * ## Cleanup
 *
 * [purgeExpiredLoginAttempts] is `@Scheduled` daily at 04:00 UTC (after the
 * 02:00 TopValuePicksJob and the 03:00 FMP event-log purge) and removes
 * rows older than [BruteForceProperties.cleanupRetentionDays].
 *
 * [^src: design_&_architecture/decisions/ADR-025-security-hardening-pci-dss.md §5]
 * [^src: management/kanban/EP-018-hardening-sicurezza-compliance/US-081-protezione-identita-accesso/TSK-230.md]
 */
@Service
class BruteForceProtectionService(
    private val loginAttemptRepository: LoginAttemptRepository,
    private val properties: BruteForceProperties,
    private val turnstileClient: TurnstileClient,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger("SECURITY")
    private val securityEventMarker = MarkerFactory.getMarker("SECURITY_EVENT")

    /**
     * Pre-login guard. Throws [AccountLockedException] / [CaptchaRequiredException]
     * to short-circuit the controller; otherwise sleeps for the configured
     * progressive delay (if any) and returns normally so the caller can proceed
     * to password verification.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun guardLogin(email: String, ip: String, captchaToken: String?) {
        val normalizedEmail = normalize(email)
        val now = clock.instant()

        val lockedUntil = accountLockedUntil(normalizedEmail, now)
        if (lockedUntil != null) {
            val retryAfter = Duration.between(now, lockedUntil).seconds.coerceAtLeast(1)
            log.warn(
                securityEventMarker,
                "Login rejected: account locked",
                kv("event", "LOGIN_LOCKED"),
                kv("email", normalizedEmail),
                kv("ip", ip),
                kv("retryAfterSeconds", retryAfter),
            )
            throw AccountLockedException(retryAfter)
        }

        if (isCaptchaRequiredForIp(ip, now)) {
            verifyCaptchaOrFail(captchaToken, ip, normalizedEmail)
        }

        if (normalizedEmail != null) {
            val accountFailures = countAccountFailures(normalizedEmail, now, properties.failureWindowMinutes)
            if (accountFailures >= properties.progressiveDelayThreshold) {
                val delaySeconds = progressiveDelaySeconds(accountFailures)
                log.warn(
                    securityEventMarker,
                    "Progressive login delay applied",
                    kv("event", "LOGIN_PROGRESSIVE_DELAY"),
                    kv("email", normalizedEmail),
                    kv("ip", ip),
                    kv("failureCount", accountFailures),
                    kv("delaySeconds", delaySeconds),
                )
                try {
                    Thread.sleep(delaySeconds * MILLIS_PER_SECOND)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
    }

    /**
     * Same as [guardLogin] but tailored for `/api/auth/register`: only the
     * per-IP CAPTCHA threshold applies (no per-account lockout / progressive
     * delay, since the account does not exist yet). Counts the same
     * `bad_credentials` rows for the IP — a bot brute-forcing logins from
     * one IP is also a candidate for register abuse.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun guardRegister(ip: String, captchaToken: String?) {
        val now = clock.instant()
        if (isCaptchaRequiredForIp(ip, now)) {
            verifyCaptchaOrFail(captchaToken, ip, accountEmail = null)
        }
    }

    /**
     * Persist a successful login row and emit a `LOGIN_NEW_DEVICE` security
     * event when the IP does not appear in the last
     * [BruteForceProperties.newDeviceHistorySize] successful logins.
     *
     * REQUIRES_NEW so the row is visible to the next request even if the
     * outer transaction (currently issuing the access + refresh pair) is
     * later rolled back for an unrelated reason.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordLoginSuccess(user: User, ip: String, userAgent: String?) {
        val normalizedEmail = normalize(user.email) ?: return
        val priorIps = loginAttemptRepository
            .findRecentSuccessfulIpsByAccount(
                normalizedEmail,
                PageRequest.of(0, properties.newDeviceHistorySize),
            )
            .toSet()

        loginAttemptRepository.save(
            LoginAttemptEntity(
                ipAddress = ip,
                accountEmail = normalizedEmail,
                attemptedAt = clock.instant(),
                success = true,
                failureReason = null,
                userAgent = userAgent?.take(USER_AGENT_MAX_LENGTH),
            ),
        )

        // Compared against history BEFORE recording the current success so we
        // don't treat the new row as its own "known device". If the user has
        // any prior history (non-empty set) and the current IP is new, flag.
        if (priorIps.isNotEmpty() && ip !in priorIps) {
            logNewDevice(user.id, normalizedEmail, ip, userAgent)
        }
    }

    /**
     * Persist a failed login attempt with the supplied [reason] (typically
     * [REASON_BAD_CREDENTIALS]). When the failure pushes the account over
     * [BruteForceProperties.lockoutThreshold] within
     * [BruteForceProperties.lockoutWindowMinutes], an
     * [REASON_ACCOUNT_LOCKED] sentinel row is inserted so subsequent
     * [guardLogin] calls reject for the configured lockout duration.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordLoginFailure(
        email: String?,
        ip: String,
        userAgent: String?,
        reason: String,
    ) {
        val normalizedEmail = normalize(email)
        val now = clock.instant()
        loginAttemptRepository.save(
            LoginAttemptEntity(
                ipAddress = ip,
                accountEmail = normalizedEmail,
                attemptedAt = now,
                success = false,
                failureReason = reason,
                userAgent = userAgent?.take(USER_AGENT_MAX_LENGTH),
            ),
        )

        if (reason == REASON_BAD_CREDENTIALS && normalizedEmail != null) {
            maybeTriggerLockout(normalizedEmail, ip, userAgent, now)
        }
    }

    /**
     * Daily purge of `login_attempts` older than
     * [BruteForceProperties.cleanupRetentionDays] (default 90, ADR-025 §8).
     * Runs in its own transaction; deletes hard so probe + brute-force +
     * lockout sentinel rows all age out together.
     */
    // Cron/zone resolved from `app.security.brute-force.cleanup-cron/zone`
    // (BruteForceProperties defaults); `${...}` placeholders are used instead
    // of `#{@bean}` SpEL because @ConfigurationPropertiesScan registers beans
    // with names like `{prefix}-{FQCN}`, not the short class name — same
    // pattern as TopValuePicksJob.
    @Scheduled(
        cron = "\${app.security.brute-force.cleanup-cron:0 0 4 * * *}",
        zone = "\${app.security.brute-force.cleanup-zone:UTC}",
    )
    @Transactional
    fun purgeExpiredLoginAttempts() {
        val cutoff = clock.instant().minus(Duration.ofDays(properties.cleanupRetentionDays))
        val deleted = loginAttemptRepository.deleteByAttemptedAtBefore(cutoff)
        if (deleted > 0) {
            log.info(
                "login_attempts purge: deleted {} rows older than {} days",
                deleted,
                properties.cleanupRetentionDays,
            )
        }
    }

    private fun maybeTriggerLockout(
        normalizedEmail: String,
        ip: String,
        userAgent: String?,
        now: Instant,
    ) {
        // The bad_credentials row was already persisted above, so the count
        // we read here includes it. Threshold check uses the configured 15-min
        // lockout window, distinct from the 5-min progressive-delay window.
        if (accountLockedUntil(normalizedEmail, now) != null) {
            return // already locked — no need to add another sentinel
        }
        val failuresInLockoutWindow = countAccountFailures(
            normalizedEmail,
            now,
            properties.lockoutWindowMinutes,
        )
        if (failuresInLockoutWindow < properties.lockoutThreshold) {
            return
        }
        loginAttemptRepository.save(
            LoginAttemptEntity(
                ipAddress = ip,
                accountEmail = normalizedEmail,
                attemptedAt = now,
                success = false,
                failureReason = REASON_ACCOUNT_LOCKED,
                userAgent = userAgent?.take(USER_AGENT_MAX_LENGTH),
            ),
        )
        log.warn(
            securityEventMarker,
            "Account locked after sustained failed logins",
            kv("event", "ACCOUNT_LOCKED"),
            kv("email", normalizedEmail),
            kv("ip", ip),
            kv("failureCount", failuresInLockoutWindow),
            kv("lockoutDurationMinutes", properties.lockoutDurationMinutes),
        )
    }

    private fun verifyCaptchaOrFail(captchaToken: String?, ip: String, accountEmail: String?) {
        if (captchaToken.isNullOrBlank()) {
            log.warn(
                securityEventMarker,
                "CAPTCHA required — token missing",
                kv("event", "LOGIN_CAPTCHA_REQUIRED"),
                kv("email", accountEmail),
                kv("ip", ip),
                kv("reason", "token_missing"),
            )
            throw CaptchaRequiredException(CaptchaRequiredException.Reason.TOKEN_MISSING)
        }
        val valid = turnstileClient.verify(captchaToken, ip)
        if (!valid) {
            log.warn(
                securityEventMarker,
                "CAPTCHA required — token invalid",
                kv("event", "LOGIN_CAPTCHA_REQUIRED"),
                kv("email", accountEmail),
                kv("ip", ip),
                kv("reason", "token_invalid"),
            )
            throw CaptchaRequiredException(CaptchaRequiredException.Reason.TOKEN_INVALID)
        }
    }

    private fun accountLockedUntil(normalizedEmail: String?, now: Instant): Instant? {
        if (normalizedEmail == null) return null
        val lockoutDuration = Duration.ofMinutes(properties.lockoutDurationMinutes)
        val since = now.minus(lockoutDuration)
        val latestLockout = loginAttemptRepository.findLatestAttemptedAtByAccountSince(
            accountEmail = normalizedEmail,
            failureReason = REASON_ACCOUNT_LOCKED,
            since = since,
        ) ?: return null
        val until = latestLockout.plus(lockoutDuration)
        return until.takeIf { it.isAfter(now) }
    }

    private fun isCaptchaRequiredForIp(ip: String, now: Instant): Boolean {
        val since = now.minus(Duration.ofMinutes(properties.failureWindowMinutes))
        val ipFailures = loginAttemptRepository.countByIpAddressAndFailureReasonAndAttemptedAtAfter(
            ipAddress = ip,
            failureReason = REASON_BAD_CREDENTIALS,
            since = since,
        )
        return ipFailures >= properties.ipCaptchaThreshold
    }

    private fun countAccountFailures(
        normalizedEmail: String,
        now: Instant,
        windowMinutes: Long,
    ): Long {
        val since = now.minus(Duration.ofMinutes(windowMinutes))
        return loginAttemptRepository.countByAccountEmailAndFailureReasonAndAttemptedAtAfter(
            accountEmail = normalizedEmail,
            failureReason = REASON_BAD_CREDENTIALS,
            since = since,
        )
    }

    private fun progressiveDelaySeconds(failureCount: Long): Long {
        // n - threshold; clamp the exponent so we never overflow even on absurd
        // counters (Long shift is well-defined up to 62 bits).
        val exponent = (failureCount - properties.progressiveDelayThreshold).coerceAtLeast(0).coerceAtMost(SAFE_SHIFT_LIMIT)
        val raw = 1L shl exponent.toInt()
        return raw.coerceAtMost(properties.progressiveDelayCapSeconds)
    }

    private fun logNewDevice(userId: UUID, email: String, ip: String, userAgent: String?) {
        log.info(
            securityEventMarker,
            "Login from new device/IP",
            kv("event", "LOGIN_NEW_DEVICE"),
            kv("userId", userId.toString()),
            kv("email", email),
            kv("ip", ip),
            kv("userAgent", userAgent),
            kv("newDevice", true),
        )
    }

    private fun normalize(email: String?): String? =
        email?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    companion object {
        // Reason codes written to login_attempts.failure_reason. Kept distinct
        // from the rate-limit probe family ("rate_limit_probe:*", TSK-229) so
        // brute-force counters and rate-limit counters never overlap.
        /** Real auth failure: password did not match (or user not found). */
        const val REASON_BAD_CREDENTIALS: String = "bad_credentials"

        /** Sentinel row marking the start of a 30 min account lockout. */
        const val REASON_ACCOUNT_LOCKED: String = "account_locked"

        /** Password ok but MFA challenge still pending. */
        const val REASON_MFA_REQUIRED: String = "mfa_required"

        /** Register failure (duplicate email, compromised password, …). */
        const val REASON_REGISTER_FAILURE: String = "register_failure"

        private const val MILLIS_PER_SECOND: Long = 1_000
        private const val USER_AGENT_MAX_LENGTH: Int = 500

        // Long has 63 usable bits — cap the shift well below to keep arithmetic
        // safe regardless of the configured progressiveDelayCapSeconds.
        private const val SAFE_SHIFT_LIMIT: Long = 30
    }
}
