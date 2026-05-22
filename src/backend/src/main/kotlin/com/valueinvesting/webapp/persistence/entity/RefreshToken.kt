package com.valueinvesting.webapp.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

// Opaque refresh token (UUID-based value) persisted server-side so it can be
// revoked on logout / rotation. Access token (JWT) remains stateless.
// [^src: design_&_architecture/decisions/ADR-006-authentication.md §Token]
// [^src: design_&_architecture/data/er-diagram.md §refresh_tokens]
@Entity
@Table(name = "refresh_tokens")
data class RefreshToken(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    var userId: UUID,

    @Column(name = "token_value", length = 128, nullable = false, unique = true)
    var tokenValue: String,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,
)
