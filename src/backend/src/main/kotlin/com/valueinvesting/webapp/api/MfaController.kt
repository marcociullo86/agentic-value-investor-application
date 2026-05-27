package com.valueinvesting.webapp.api

import com.valueinvesting.webapp.api.model.AccessTokenResponse
import com.valueinvesting.webapp.api.model.MfaChallengeRequest
import com.valueinvesting.webapp.api.model.MfaDisableRequest
import com.valueinvesting.webapp.api.model.MfaEnrollmentResponse
import com.valueinvesting.webapp.api.model.MfaRecoveryRequest
import com.valueinvesting.webapp.api.model.MfaVerifyRequest
import com.valueinvesting.webapp.config.AppProperties
import com.valueinvesting.webapp.security.JwtService
import com.valueinvesting.webapp.security.UserPrincipal
import com.valueinvesting.webapp.service.AuthService
import com.valueinvesting.webapp.service.MfaService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

/**
 * EP-018 / US-081 / ADR-025 §4 — MFA TOTP enrollment, challenge, recovery,
 * and disable endpoints. Coordinated with [AuthController]:
 *
 * - `/enroll` and `/verify` are Bearer-authenticated — the user must be
 *   already logged in (no MFA enabled yet).
 * - `/challenge` and `/recovery` are permitAll: the second factor during
 *   login. The short-lived `mfaToken` minted by /api/auth/login carries the
 *   user identity (validated against [JwtService.parseMfaChallengeToken]).
 *   Successful challenge yields the same `LoginResponse` shape produced by
 *   the no-MFA login path (access token in body + refresh cookie).
 * - DELETE `/api/auth/mfa` is Bearer-authenticated and requires password
 *   confirmation in the body — analogous to a sensitive write that
 *   re-asserts identity.
 *
 * Errors follow RFC 9457 ProblemDetail (ADR-007/012).
 *
 * [^src: design_&_architecture/decisions/ADR-025-security-hardening-pci-dss.md §4]
 * [^src: design_&_architecture/decisions/ADR-024-session-lifecycle-credential-storage.md §3]
 */
@RestController
@RequestMapping("/api/auth/mfa")
@Tag(name = "auth-mfa", description = "MFA TOTP enrollment, challenge, recovery, and disable (EP-018 / US-081)")
class MfaController(
    private val mfaService: MfaService,
    private val authService: AuthService,
    private val jwtService: JwtService,
    private val appProperties: AppProperties,
) {

    @PostMapping("/enroll")
    @Operation(
        summary = "Start MFA enrollment — generate secret + QR + recovery codes",
        description = "Generates a fresh TOTP secret + 8 one-time recovery codes for the authenticated user. " +
            "Recovery codes are returned in plain text ONCE; the server stores BCrypt hashes. " +
            "Idempotent for non-activated enrollments (re-issues fresh material if `verify` was never called).",
        security = [SecurityRequirement(name = "bearerAuth")],
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Enrollment material",
                content = [Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = Schema(implementation = MfaEnrollmentResponse::class),
                )],
            ),
            ApiResponse(responseCode = "401", description = "Missing or invalid Bearer token"),
            ApiResponse(responseCode = "409", description = "MFA already enabled (RFC 9457)"),
        ],
    )
    fun enroll(
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<MfaEnrollmentResponse> {
        val result = mfaService.startEnrollment(principal.userId)
        return ResponseEntity.ok(
            MfaEnrollmentResponse(
                secret = result.secret,
                qrCodeUri = result.qrCodeUri,
                recoveryCodes = result.recoveryCodes,
            ),
        )
    }

    @PostMapping("/verify")
    @Operation(
        summary = "Activate MFA by proving the user can compute a TOTP code",
        security = [SecurityRequirement(name = "bearerAuth")],
        responses = [
            ApiResponse(responseCode = "204", description = "MFA activated"),
            ApiResponse(responseCode = "400", description = "Invalid TOTP code (RFC 9457)"),
            ApiResponse(responseCode = "401", description = "Missing or invalid Bearer token"),
            ApiResponse(responseCode = "409", description = "MFA already enabled or no enrollment in progress"),
        ],
    )
    fun verify(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody request: MfaVerifyRequest,
    ): ResponseEntity<Void> {
        mfaService.activate(principal.userId, request.totpCode)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/challenge")
    @Operation(
        summary = "Complete login MFA challenge with a TOTP code",
        description = "Trades the short-lived `mfaToken` (from /api/auth/login) + a current TOTP code " +
            "for the regular access token + refresh cookie pair. RFC 9457 ProblemDetail on invalid code.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "MFA challenge succeeded",
                headers = [Header(
                    name = "Set-Cookie",
                    description = "refresh_token={value}; HttpOnly; Secure; SameSite=Strict; Path=/api/auth; Max-Age=604800",
                    schema = Schema(type = "string"),
                )],
                content = [Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = Schema(implementation = AccessTokenResponse::class),
                )],
            ),
            ApiResponse(responseCode = "400", description = "Invalid TOTP code"),
            ApiResponse(responseCode = "401", description = "Invalid or expired mfaToken"),
        ],
    )
    fun challenge(
        @Valid @RequestBody request: MfaChallengeRequest,
    ): ResponseEntity<AccessTokenResponse> {
        val parsed = jwtService.parseMfaChallengeToken(request.mfaToken)
        mfaService.verifyTotpForLogin(parsed.userId, request.totpCode)
        return completeChallenge(parsed.userId)
    }

    @PostMapping("/recovery")
    @Operation(
        summary = "Complete login MFA challenge with a recovery code (one-time use)",
        description = "Alternative to TOTP for users who lost access to their authenticator. " +
            "Consumes one of the 8 recovery codes generated at enrollment.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Recovery succeeded",
                headers = [Header(
                    name = "Set-Cookie",
                    description = "refresh_token={value}; HttpOnly; Secure; SameSite=Strict; Path=/api/auth; Max-Age=604800",
                    schema = Schema(type = "string"),
                )],
                content = [Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = Schema(implementation = AccessTokenResponse::class),
                )],
            ),
            ApiResponse(responseCode = "400", description = "Invalid or already-used recovery code"),
            ApiResponse(responseCode = "401", description = "Invalid or expired mfaToken"),
        ],
    )
    fun recovery(
        @Valid @RequestBody request: MfaRecoveryRequest,
    ): ResponseEntity<AccessTokenResponse> {
        val parsed = jwtService.parseMfaChallengeToken(request.mfaToken)
        mfaService.consumeRecoveryCodeForLogin(parsed.userId, request.recoveryCode)
        return completeChallenge(parsed.userId)
    }

    @DeleteMapping
    @Operation(
        summary = "Disable MFA after re-asserting identity with the account password",
        security = [SecurityRequirement(name = "bearerAuth")],
        responses = [
            ApiResponse(responseCode = "204", description = "MFA disabled"),
            ApiResponse(responseCode = "401", description = "Wrong password or missing Bearer token"),
            ApiResponse(responseCode = "409", description = "MFA was not enabled"),
        ],
    )
    fun disable(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody request: MfaDisableRequest,
    ): ResponseEntity<Void> {
        mfaService.disable(principal.userId, request.password)
        return ResponseEntity.noContent().build()
    }

    /**
     * Issues the access + refresh pair after a successful MFA challenge or
     * recovery and writes the refresh-token cookie per ADR-024 §3. Shared
     * helper between [challenge] and [recovery] to keep the response shape
     * byte-identical with the no-MFA login path.
     */
    private fun completeChallenge(userId: java.util.UUID): ResponseEntity<AccessTokenResponse> {
        val tokens = authService.completeMfaChallenge(userId)
        val cookie = RefreshTokenCookieHelper.create(
            tokens.refreshTokenValue,
            Duration.ofDays(appProperties.jwt.refreshSlidingTtlDays),
            appProperties.jwt.cookieSecure,
        )
        return ResponseEntity.status(HttpStatus.OK)
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(AccessTokenResponse(tokens.accessToken, tokens.expiresInSeconds))
    }
}
