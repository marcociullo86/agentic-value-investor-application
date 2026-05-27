package com.valueinvesting.webapp.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

// MFA TOTP secret + recovery codes per user (V026, US-081, TSK-228).
// `totp_secret_encrypted` stored AES-256-GCM ciphertext (TotpService).
// `recovery_codes_hash` is a JSON array of BCrypt hashes; one-time consumption.
// [^src: design_&_architecture/decisions/ADR-025-security-hardening-pci-dss.md §Schema DB]
@Entity
@Table(name = "mfa_secrets")
class MfaSecretEntity(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false, unique = true)
    var userId: UUID,

    @Column(name = "totp_secret_encrypted", length = 255, nullable = false)
    var totpSecretEncrypted: String,

    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = false,

    @Column(name = "enabled_at")
    var enabledAt: Instant? = null,

    @Column(name = "recovery_codes_hash", columnDefinition = "TEXT")
    var recoveryCodesHash: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)
