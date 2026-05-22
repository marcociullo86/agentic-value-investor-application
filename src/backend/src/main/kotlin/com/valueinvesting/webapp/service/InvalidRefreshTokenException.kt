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
 * [^src: design_&_architecture/decisions/ADR-010-auth-consolidation.md §3]
 */
class InvalidRefreshTokenException(message: String) : RuntimeException(message)
