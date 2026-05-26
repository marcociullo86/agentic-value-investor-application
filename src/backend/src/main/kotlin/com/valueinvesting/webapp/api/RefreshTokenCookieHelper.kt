package com.valueinvesting.webapp.api

import org.springframework.http.ResponseCookie
import java.time.Duration

/**
 * Builds the `refresh_token` [ResponseCookie] with the httpOnly / Secure /
 * SameSite=Strict / Path=/api/auth attributes mandated by ADR-024 §3.
 *
 * [secure] is driven by `app.jwt.cookie-secure` (default true in prod,
 * false in test/dev over plain HTTP).
 */
object RefreshTokenCookieHelper {

    const val COOKIE_NAME: String = "refresh_token"
    const val COOKIE_PATH: String = "/api/auth"

    fun create(value: String, maxAge: Duration, secure: Boolean): ResponseCookie =
        ResponseCookie.from(COOKIE_NAME, value)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Strict")
            .path(COOKIE_PATH)
            .maxAge(maxAge)
            .build()

    fun delete(secure: Boolean): ResponseCookie =
        ResponseCookie.from(COOKIE_NAME, "")
            .httpOnly(true)
            .secure(secure)
            .sameSite("Strict")
            .path(COOKIE_PATH)
            .maxAge(0)
            .build()
}
