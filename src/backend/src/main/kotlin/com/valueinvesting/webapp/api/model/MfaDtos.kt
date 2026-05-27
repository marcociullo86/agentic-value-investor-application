package com.valueinvesting.webapp.api.model

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

// MFA TOTP enrollment, challenge, recovery, and disable DTOs (US-081, TSK-228).
// [^src: design_&_architecture/decisions/ADR-025-security-hardening-pci-dss.md §4]

@Schema(
    name = "MfaEnrollmentResponse",
    description = "Initial enrollment material — secret + provisioning URI for QR display + one-time recovery codes. " +
        "Recovery codes are returned ONCE in plain text; the server stores only BCrypt hashes.",
)
data class MfaEnrollmentResponse(
    @Schema(description = "Base32 TOTP shared secret (RFC 6238)")
    val secret: String,
    @Schema(description = "otpauth:// provisioning URI to render as QR code")
    val qrCodeUri: String,
    @Schema(description = "8 plain-text recovery codes, one-time consumable")
    val recoveryCodes: List<String>,
)

@Schema(name = "MfaVerifyRequest", description = "Activates MFA after enrollment by proving the user can compute a TOTP code.")
data class MfaVerifyRequest(
    @field:NotBlank
    @Schema(description = "6-digit TOTP code from the authenticator app")
    val totpCode: String,
)

@Schema(
    name = "MfaChallengeRequest",
    description = "Second factor during login. Trades the short-lived `mfaToken` issued by /api/auth/login for a regular access token.",
)
data class MfaChallengeRequest(
    @field:NotBlank
    val mfaToken: String,
    @field:NotBlank
    @Schema(description = "6-digit TOTP code from the authenticator app")
    val totpCode: String,
)

@Schema(
    name = "MfaRecoveryRequest",
    description = "Recovery-code alternative to TOTP during login. Each code is one-time use.",
)
data class MfaRecoveryRequest(
    @field:NotBlank
    val mfaToken: String,
    @field:NotBlank
    @Schema(description = "Plain-text recovery code (dashes optional)")
    val recoveryCode: String,
)

@Schema(name = "MfaDisableRequest", description = "Confirms identity via password before disabling MFA.")
data class MfaDisableRequest(
    @field:NotBlank
    val password: String,
)

@Schema(
    name = "LoginResponse",
    description = "Login outcome. When MFA is enabled the body carries `mfaRequired=true` + a short-lived `mfaToken` " +
        "and NO access token / refresh cookie; the FE must call /api/auth/mfa/challenge.",
)
// `NON_NULL`: keeps the non-MFA payload byte-compatible with [AccessTokenResponse]
// (only `accessToken` + `expiresInSeconds` + `mfaRequired:false` are emitted) so
// pre-TSK-228 clients deserializing as AccessTokenResponse keep working.
@JsonInclude(JsonInclude.Include.NON_NULL)
data class LoginResponse(
    @Schema(description = "JWT access token (Bearer); null when mfaRequired=true")
    val accessToken: String? = null,
    @Schema(description = "Token lifetime in seconds; null when mfaRequired=true")
    val expiresInSeconds: Long? = null,
    @Schema(description = "True when the account has MFA enabled and a challenge is required to complete login")
    val mfaRequired: Boolean = false,
    @Schema(description = "Short-lived JWT (≈5 min) to be replayed at /api/auth/mfa/challenge or /recovery; null when mfaRequired=false")
    val mfaToken: String? = null,
)
