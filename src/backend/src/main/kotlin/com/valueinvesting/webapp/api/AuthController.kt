package com.valueinvesting.webapp.api

import com.valueinvesting.webapp.api.model.AccessTokenResponse
import com.valueinvesting.webapp.api.model.LoginRequest
import com.valueinvesting.webapp.api.model.LoginResponse
import com.valueinvesting.webapp.api.model.RegisterRequest
import com.valueinvesting.webapp.api.model.UserProfileResponse
import com.valueinvesting.webapp.config.AppProperties
import com.valueinvesting.webapp.security.UserPrincipal
import com.valueinvesting.webapp.service.AuthService
import com.valueinvesting.webapp.service.LoginOutcome
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

/**
 * EP-006 / EP-017 authentication endpoints.
 *
 * ADR-024 §3: refresh token is transported via httpOnly Secure SameSite=Strict
 * cookie (`Path=/api/auth`). The response body carries only the access token.
 *
 * See design_&_architecture/api/openapi.yaml §paths under /api/auth.
 * See design_&_architecture/decisions/ADR-024-session-lifecycle-credential-storage.md §3.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "auth", description = "Authentication & session lifecycle (EP-006 / EP-017)")
class AuthController(
    private val authService: AuthService,
    private val appProperties: AppProperties,
) {

    @PostMapping("/register")
    @Operation(
        summary = "Register a new user account",
        responses = [
            ApiResponse(
                responseCode = "201",
                description = "Account created",
                content = [Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = Schema(implementation = UserProfileResponse::class),
                )],
            ),
            ApiResponse(responseCode = "409", description = "Email already registered (RFC 9457 ProblemDetails)"),
            ApiResponse(responseCode = "400", description = "Validation error or compromised password (RFC 9457)"),
        ],
    )
    fun register(
        @Valid @RequestBody request: RegisterRequest,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<UserProfileResponse> {
        val profile = authService.register(
            request = request,
            ip = resolveClientIp(httpRequest),
            userAgent = httpRequest.getHeader(HttpHeaders.USER_AGENT),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(profile)
    }

    @PostMapping("/login")
    @Operation(
        summary = "Authenticate and obtain an access token",
        description = "Returns an access token in the body when MFA is not enabled. " +
            "When MFA is enabled the body carries `mfaRequired=true` + a short-lived `mfaToken` " +
            "and NO access token / refresh cookie — the FE must call /api/auth/mfa/challenge " +
            "(ADR-025 §4). The refresh token is set as an httpOnly Secure SameSite=Strict cookie " +
            "(Path=/api/auth) only when MFA is not required (ADR-024 §3).",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Login successful (or MFA challenge required)",
                headers = [Header(
                    name = "Set-Cookie",
                    description = "refresh_token={value}; HttpOnly; Secure; SameSite=Strict; Path=/api/auth; Max-Age=604800 — set only when MFA is not required",
                    schema = Schema(type = "string"),
                )],
                content = [Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = Schema(implementation = LoginResponse::class),
                )],
            ),
            ApiResponse(responseCode = "401", description = "Invalid email or password (RFC 9457 ProblemDetails)"),
        ],
    )
    fun login(
        @Valid @RequestBody request: LoginRequest,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<LoginResponse> {
        return when (
            val outcome = authService.login(
                request = request,
                ip = resolveClientIp(httpRequest),
                userAgent = httpRequest.getHeader(HttpHeaders.USER_AGENT),
            )
        ) {
            is LoginOutcome.TokenPair -> {
                val cookie = RefreshTokenCookieHelper.create(
                    outcome.refreshTokenValue,
                    Duration.ofDays(appProperties.jwt.refreshSlidingTtlDays),
                    appProperties.jwt.cookieSecure,
                )
                ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(
                        LoginResponse(
                            accessToken = outcome.accessToken,
                            expiresInSeconds = outcome.expiresInSeconds,
                            mfaRequired = false,
                            mfaToken = null,
                        ),
                    )
            }
            is LoginOutcome.MfaRequired -> ResponseEntity.ok(
                LoginResponse(
                    accessToken = null,
                    expiresInSeconds = null,
                    mfaRequired = true,
                    mfaToken = outcome.mfaToken,
                ),
            )
        }
    }

    @PostMapping("/refresh")
    @Operation(
        summary = "Refresh the access token using the httpOnly cookie",
        description = "Reads the refresh token from the httpOnly cookie (not the request body). " +
            "Returns a new access token and rotates the refresh cookie (ADR-024 §3).",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Token refreshed",
                headers = [Header(
                    name = "Set-Cookie",
                    description = "Rotated refresh_token cookie; HttpOnly; Secure; SameSite=Strict; Path=/api/auth",
                    schema = Schema(type = "string"),
                )],
                content = [Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = Schema(implementation = AccessTokenResponse::class),
                )],
            ),
            ApiResponse(responseCode = "400", description = "Missing refresh_token cookie"),
            ApiResponse(responseCode = "401", description = "Invalid or expired refresh token"),
        ],
    )
    fun refresh(
        @CookieValue(RefreshTokenCookieHelper.COOKIE_NAME) refreshToken: String,
    ): ResponseEntity<AccessTokenResponse> {
        val result = authService.refresh(refreshToken)
        val cookie = RefreshTokenCookieHelper.create(
            result.refreshTokenValue,
            Duration.ofDays(appProperties.jwt.refreshSlidingTtlDays),
            appProperties.jwt.cookieSecure,
        )
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(AccessTokenResponse(result.accessToken, result.expiresInSeconds))
    }

    @PostMapping("/logout")
    @Operation(
        summary = "Logout and revoke the refresh token",
        description = "Revokes the refresh token in DB and clears the cookie via Max-Age=0 (ADR-024 §3).",
        security = [SecurityRequirement(name = "bearerAuth")],
        responses = [
            ApiResponse(
                responseCode = "204",
                description = "Logged out",
                headers = [Header(
                    name = "Set-Cookie",
                    description = "refresh_token=; Max-Age=0 (cookie deleted)",
                    schema = Schema(type = "string"),
                )],
            ),
            ApiResponse(responseCode = "401", description = "Missing or invalid Bearer token"),
        ],
    )
    fun logout(
        @AuthenticationPrincipal principal: UserPrincipal,
        @CookieValue(RefreshTokenCookieHelper.COOKIE_NAME, required = false) refreshToken: String?,
    ): ResponseEntity<Void> {
        authService.logout(principal.userId, refreshToken)
        val cookie = RefreshTokenCookieHelper.delete(appProperties.jwt.cookieSecure)
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .build()
    }

    // Mirrors the IP-resolution policy already used by [RateLimitingFilter]
    // (TSK-229) — X-Forwarded-For first entry when present, otherwise the
    // direct remoteAddr. Required so brute-force counters and rate-limit
    // counters key on the same client identifier.
    private fun resolveClientIp(request: HttpServletRequest): String {
        val forwarded = request.getHeader("X-Forwarded-For")
        if (!forwarded.isNullOrBlank()) {
            return forwarded.split(",").first().trim()
        }
        return request.remoteAddr.orEmpty().ifBlank { UNKNOWN_CLIENT_IP }
    }

    companion object {
        // Synced with AuthService.UNKNOWN_IP — kept private to its module since
        // the constant has different visibility semantics in the two layers.
        private const val UNKNOWN_CLIENT_IP: String = "0.0.0.0"
    }
}
