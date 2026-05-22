package com.valueinvesting.webapp.api.model

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

// DTOs aligned with design_&_architecture/api/openapi.yaml §components.schemas
// (RegisterRequest, LoginRequest, RefreshRequest, TokenPair, UserProfile).

data class RegisterRequest(
    @field:Email
    @field:NotBlank
    val email: String,

    @field:NotBlank
    @field:Size(min = 12, message = "Password must be at least 12 characters")
    val password: String,

    @field:Size(max = 120)
    val displayName: String? = null,
)

data class LoginRequest(
    @field:Email
    @field:NotBlank
    val email: String,

    @field:NotBlank
    val password: String,
)

data class RefreshRequest(
    @field:NotBlank
    val refreshToken: String,
)

data class TokenPairResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
)

data class UserProfileResponse(
    val id: UUID,
    val email: String,
    val displayName: String?,
    val createdAt: Instant,
)
