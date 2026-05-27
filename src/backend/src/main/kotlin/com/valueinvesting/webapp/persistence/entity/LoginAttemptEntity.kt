package com.valueinvesting.webapp.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

// Audit row for auth rate limiting and brute-force protection (V025, US-081).
// [^src: design_&_architecture/decisions/ADR-025-security-hardening-pci-dss.md §Schema DB]
@Entity
@Table(name = "login_attempts")
class LoginAttemptEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null,

    @Column(name = "ip_address", nullable = false, length = 45)
    var ipAddress: String = "",

    @Column(name = "account_email", length = 255)
    var accountEmail: String? = null,

    @Column(name = "attempted_at", nullable = false)
    var attemptedAt: Instant = Instant.EPOCH,

    @Column(name = "success", nullable = false)
    var success: Boolean = false,

    @Column(name = "failure_reason", length = 100)
    var failureReason: String? = null,

    @Column(name = "user_agent", length = 500)
    var userAgent: String? = null,
)
