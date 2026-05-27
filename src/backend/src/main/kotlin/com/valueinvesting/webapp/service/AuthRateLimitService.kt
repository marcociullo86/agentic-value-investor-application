package com.valueinvesting.webapp.service

import com.valueinvesting.webapp.config.RateLimitingProperties
import com.valueinvesting.webapp.persistence.entity.LoginAttemptEntity
import com.valueinvesting.webapp.persistence.repository.LoginAttemptRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant

// Rate-limit checks backed by login_attempts (TSK-229).
// [^src: design_&_architecture/decisions/ADR-025-security-hardening-pci-dss.md §5]
@Service
class AuthRateLimitService(
    private val loginAttemptRepository: LoginAttemptRepository,
    private val rateLimitingProperties: RateLimitingProperties,
    private val clock: Clock,
) {

    enum class AuthEndpoint {
        LOGIN,
        REGISTER,
        PASSWORD_RESET,
    }

    data class RateLimitDecision(
        val allowed: Boolean,
        val retryAfterSeconds: Long = 0,
    )

    @Transactional
    fun checkAndRecord(
        endpoint: AuthEndpoint,
        ipAddress: String,
        accountEmail: String?,
        userAgent: String?,
    ): RateLimitDecision {
        val limits = limitsFor(endpoint)
        val window = Duration.ofMinutes(rateLimitingProperties.windowMinutes)
        val since = clock.instant().minus(window)

        val probeReason = probeReason(endpoint)
        val ipCount =
            loginAttemptRepository.countByIpAddressAndFailureReasonAndAttemptedAtAfter(
                ipAddress,
                probeReason,
                since,
            )
        if (ipCount >= limits.perIp) {
            val oldest = loginAttemptRepository.findOldestAttemptedAtByIpSince(ipAddress, probeReason, since)
            return RateLimitDecision(
                allowed = false,
                retryAfterSeconds = retryAfterSeconds(oldest, window),
            )
        }

        val perAccount = limits.perAccount
        if (perAccount != null && !accountEmail.isNullOrBlank()) {
            val normalizedEmail = accountEmail.trim().lowercase()
            val accountCount =
                loginAttemptRepository.countByAccountEmailAndFailureReasonAndAttemptedAtAfter(
                    normalizedEmail,
                    probeReason,
                    since,
                )
            if (accountCount >= perAccount) {
                val oldest =
                    loginAttemptRepository.findOldestAttemptedAtByAccountSince(
                        normalizedEmail,
                        probeReason,
                        since,
                    )
                return RateLimitDecision(
                    allowed = false,
                    retryAfterSeconds = retryAfterSeconds(oldest, window),
                )
            }
        }

        recordAttempt(endpoint, ipAddress, accountEmail, userAgent)
        return RateLimitDecision(allowed = true)
    }

    private fun recordAttempt(
        endpoint: AuthEndpoint,
        ipAddress: String,
        accountEmail: String?,
        userAgent: String?,
    ) {
        loginAttemptRepository.save(
            LoginAttemptEntity(
                ipAddress = ipAddress,
                accountEmail = accountEmail?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
                attemptedAt = clock.instant(),
                success = false,
                failureReason = probeReason(endpoint),
                userAgent = userAgent?.take(500),
            ),
        )
    }

    private fun probeReason(endpoint: AuthEndpoint): String =
        "$RATE_LIMIT_PROBE_REASON:${endpoint.name}"

    private fun limitsFor(endpoint: AuthEndpoint): RateLimitingProperties.EndpointLimits =
        when (endpoint) {
            AuthEndpoint.LOGIN -> rateLimitingProperties.login
            AuthEndpoint.REGISTER -> rateLimitingProperties.register
            AuthEndpoint.PASSWORD_RESET -> rateLimitingProperties.passwordReset
        }

    private fun retryAfterSeconds(oldestInWindow: Instant?, window: Duration): Long {
        if (oldestInWindow == null) {
            return window.seconds.coerceAtLeast(1)
        }
        val retryAt = oldestInWindow.plus(window)
        val seconds = Duration.between(clock.instant(), retryAt).seconds
        return seconds.coerceAtLeast(1)
    }

    companion object {
        /** Distinguishes rate-limit counter rows from real auth outcomes (TSK-230). */
        const val RATE_LIMIT_PROBE_REASON: String = "rate_limit_probe"
    }
}
