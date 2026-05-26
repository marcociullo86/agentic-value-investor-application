package com.valueinvesting.webapp.service

import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.LoggerFactory
import org.slf4j.MarkerFactory
import org.springframework.stereotype.Component

/**
 * Typed security-event logger that emits structured log entries with the
 * `SECURITY_EVENT` Logback marker for routing to a dedicated retention
 * appender (365 days, ADR-021 §7).
 *
 * Each method maps to one security-event category defined in ADR-021 §6.
 * Context fields are passed as [StructuredArguments][net.logstash.logback.argument.StructuredArguments]
 * so they appear as top-level JSON keys in production logs. The correlation-id
 * and user-id MDC entries are attached automatically by [CorrelationIdFilter]
 * and [JwtAuthenticationFilter] respectively.
 *
 * PII in context fields (ip, email) is redacted at the encoder layer by
 * `PiiRedactionEncoder` (TSK-175) — callers pass raw values.
 *
 * [^src: design_&_architecture/decisions/ADR-021-structured-logging-pii-redaction.md §6]
 */
@Component
class SecurityEventLogger {

    private val log = LoggerFactory.getLogger("SECURITY")
    private val SECURITY_EVENT = MarkerFactory.getMarker("SECURITY_EVENT")

    fun loginSuccess(userId: Long, ip: String, userAgent: String?) {
        log.info(
            SECURITY_EVENT,
            "Authenticated user successfully",
            kv("event", "LOGIN_SUCCESS"),
            kv("userId", userId),
            kv("ip", ip),
            kv("userAgent", userAgent),
        )
    }

    fun loginFailure(email: String?, reason: String) {
        log.warn(
            SECURITY_EVENT,
            "Failed authentication attempt",
            kv("event", "LOGIN_FAILURE"),
            kv("email", email),
            kv("reason", reason),
        )
    }

    fun passwordChanged(userId: Long) {
        log.info(
            SECURITY_EVENT,
            "Password changed",
            kv("event", "PASSWORD_CHANGED"),
            kv("userId", userId),
        )
    }

    fun passwordResetRequested(userId: Long) {
        log.info(
            SECURITY_EVENT,
            "Password reset requested",
            kv("event", "PASSWORD_RESET_REQUESTED"),
            kv("userId", userId),
        )
    }

    fun mfaEnabled(userId: Long, method: String) {
        log.info(
            SECURITY_EVENT,
            "MFA enabled",
            kv("event", "MFA_ENABLED"),
            kv("userId", userId),
            kv("method", method),
        )
    }

    fun mfaDisabled(userId: Long) {
        log.info(
            SECURITY_EVENT,
            "MFA disabled",
            kv("event", "MFA_DISABLED"),
            kv("userId", userId),
        )
    }

    fun mfaFallback(userId: Long, method: String) {
        log.info(
            SECURITY_EVENT,
            "MFA fallback used",
            kv("event", "MFA_FALLBACK"),
            kv("userId", userId),
            kv("method", method),
        )
    }

    fun permissionGranted(userId: Long, role: String, grantedBy: Long) {
        log.info(
            SECURITY_EVENT,
            "Permission granted",
            kv("event", "PERMISSION_GRANTED"),
            kv("userId", userId),
            kv("role", role),
            kv("grantedBy", grantedBy),
        )
    }

    fun permissionRevoked(userId: Long, role: String, revokedBy: Long) {
        log.info(
            SECURITY_EVENT,
            "Permission revoked",
            kv("event", "PERMISSION_REVOKED"),
            kv("userId", userId),
            kv("role", role),
            kv("revokedBy", revokedBy),
        )
    }

    fun accessDenied(userId: Long?, resource: String, currentRole: String?) {
        log.warn(
            SECURITY_EVENT,
            "Access denied",
            kv("event", "ACCESS_DENIED"),
            kv("userId", userId),
            kv("resource", resource),
            kv("currentRole", currentRole),
        )
    }
}
