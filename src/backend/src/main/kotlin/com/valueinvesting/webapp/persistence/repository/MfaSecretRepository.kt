package com.valueinvesting.webapp.persistence.repository

import com.valueinvesting.webapp.persistence.entity.MfaSecretEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

// MfaSecretEntity lookup keyed by user — at most one row per user (UNIQUE).
// Used by MfaService for enrollment lifecycle and login MFA branching (TSK-228).
// [^src: design_&_architecture/decisions/ADR-025-security-hardening-pci-dss.md §Schema DB]
@Repository
interface MfaSecretRepository : JpaRepository<MfaSecretEntity, UUID> {

    fun findByUserId(userId: UUID): MfaSecretEntity?

    fun deleteByUserId(userId: UUID): Long
}
