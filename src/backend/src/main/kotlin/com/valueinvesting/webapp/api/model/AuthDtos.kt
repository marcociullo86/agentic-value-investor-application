package com.valueinvesting.webapp.api.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

@Schema(name = "RegisterRequest")
data class RegisterRequest(
    @field:Email
    @field:NotBlank
    val email: String,

    @field:NotBlank
    @field:Size(min = 12, message = "Password must be at least 12 characters")
    val password: String,

    @field:Size(max = 120)
    val displayName: String? = null,

    @Schema(
        description = "Cloudflare Turnstile captcha token. Required only when a previous failure " +
            "response set `captchaRequired=true` for this IP (US-081, ADR-025 §5).",
        nullable = true,
    )
    val captchaToken: String? = null,
)

@Schema(name = "LoginRequest")
data class LoginRequest(
    @field:Email
    @field:NotBlank
    val email: String,

    @field:NotBlank
    val password: String,

    @Schema(
        description = "Cloudflare Turnstile captcha token. Required only when a previous failure " +
            "response set `captchaRequired=true` for this IP (US-081, ADR-025 §5).",
        nullable = true,
    )
    val captchaToken: String? = null,
)

@Schema(
    name = "AccessTokenResponse",
    description = "Short-lived access token. The refresh token is transported via httpOnly cookie (ADR-024 §3).",
)
// `ignoreUnknown = true` so test ObjectMappers can deserialize this DTO from
// the unified `/api/auth/login` body that may carry extra MFA-related fields
// (`mfaRequired`, `mfaToken`) introduced by TSK-228 / ADR-025 §4.
@JsonIgnoreProperties(ignoreUnknown = true)
data class AccessTokenResponse(
    @Schema(description = "JWT access token (Bearer)")
    val accessToken: String,
    @Schema(description = "Token lifetime in seconds", example = "900")
    val expiresInSeconds: Long,
)

@Schema(name = "UserProfile")
data class UserProfileResponse(
    val id: UUID,
    val email: String,
    val displayName: String?,
    val createdAt: Instant,
)
