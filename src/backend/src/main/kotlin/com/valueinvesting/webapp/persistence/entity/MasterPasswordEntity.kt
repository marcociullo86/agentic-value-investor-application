package com.valueinvesting.webapp.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

// Credenziale master per operazioni admin distruttive (reset ticker). Una sola
// riga attesa (seed da V031). `passwordHash` è un hash BCrypt cost 12 (ADR-006).
@Entity
@Table(name = "master_password")
class MasterPasswordEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null,

    @Column(name = "password_hash", length = 100, nullable = false)
    var passwordHash: String = "",

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
