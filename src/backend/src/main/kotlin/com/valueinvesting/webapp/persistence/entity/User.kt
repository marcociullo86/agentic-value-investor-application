package com.valueinvesting.webapp.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

// JPA entity for application users. password_hash sized for BCrypt cost 12.
// Uniqueness on LOWER(email) enforced by partial unique index in V001 migration
// — application-level pre-check still performed in AuthService for friendly 409.
// [^src: design_&_architecture/data/er-diagram.md §users]
// [^src: design_&_architecture/decisions/ADR-006-authentication.md §Schema utenti]
@Entity
@Table(name = "users")
data class User(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID = UUID.randomUUID(),

    @Column(name = "email", length = 255, nullable = false)
    var email: String,

    @Column(name = "password_hash", length = 72, nullable = false)
    var passwordHash: String,

    @Column(name = "display_name", length = 120)
    var displayName: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "last_login_at")
    var lastLoginAt: Instant? = null,
)
