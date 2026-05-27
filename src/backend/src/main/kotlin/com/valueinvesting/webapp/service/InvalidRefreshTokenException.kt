package com.valueinvesting.webapp.service

/**
 * Raised by [AuthService.refresh] when the presented refresh token is
 * missing, revoked, sliding-TTL expired, or beyond the absolute 30-day
 * cap from `first_issued_at` (ADR-010 §3 — session inactivity).
 *
 * Mapped to `401 invalid-refresh` by GlobalExceptionHandler. Separating
 * this from BadCredentialsException lets the FE differentiate
 * "credentials wrong, retry login" from "session decayed, show the
 * 'Sessione scaduta' banner" (TSK-043).
 *
 * ## Anti-enumeration contract (TSK-041 finding iter-1)
 *
 * The runtime [message] is a **uniform** string ([CLIENT_DETAIL]) — it is
 * the only value GlobalExceptionHandler may surface to clients. The
 * differentiating cause lives in [reason] (a stable token like
 * `not_found` / `revoked` / `sliding_expired` / `absolute_cap` /
 * `user_unknown`) and is consumed exclusively by server-side log
 * sinks, never propagated to the HTTP body. This prevents an attacker
 * from probing refresh-token state via the `detail` field in the
 * RFC 9457 ProblemDetail.
 *
 * [^src: design_&_architecture/decisions/ADR-010-auth-consolidation.md §3]
 * [^src: code_quality/reports/TSK-041-iter-1.md §Finding ordinati]
 */
class InvalidRefreshTokenException(
    val reason: String,
) : RuntimeException(CLIENT_DETAIL) {

    companion object {
        /** Uniform client-facing detail; identical across every cause. */
        const val CLIENT_DETAIL: String = "Invalid refresh token"
    }
}
