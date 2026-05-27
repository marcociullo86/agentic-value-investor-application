package com.valueinvesting.webapp.service

/**
 * Account temporarily locked after exceeding the configured failed-attempt
 * threshold within the lockout window (US-081 / ADR-025 §5, TSK-230).
 *
 * Maps to HTTP 423 Locked in [com.valueinvesting.webapp.api.error.GlobalExceptionHandler]
 * with `Retry-After` driven by [retryAfterSeconds] so the FE can show a
 * countdown without storing the lockout deadline locally.
 */
class AccountLockedException(
    val retryAfterSeconds: Long,
) : RuntimeException("Account temporarily locked due to repeated failed login attempts")

/**
 * CAPTCHA challenge required for the current IP after exceeding the per-IP
 * failure threshold (US-081 / ADR-025 §5, TSK-230). Maps to HTTP 401 with
 * `captchaRequired=true` in the RFC 9457 ProblemDetail extension so the FE
 * can show the Turnstile widget without parsing user-facing text.
 *
 * Carries [reason] for server-side logging only — never leaked to the client.
 */
class CaptchaRequiredException(
    val reason: Reason,
) : RuntimeException("CAPTCHA verification required") {
    enum class Reason {
        /** IP threshold tripped and the client did not submit a captchaToken. */
        TOKEN_MISSING,

        /** IP threshold tripped and Cloudflare siteverify rejected the token. */
        TOKEN_INVALID,
    }
}
