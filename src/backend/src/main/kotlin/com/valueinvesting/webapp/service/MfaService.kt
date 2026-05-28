package com.valueinvesting.webapp.service

import com.valueinvesting.webapp.persistence.entity.MfaSecretEntity
import com.valueinvesting.webapp.persistence.repository.MfaSecretRepository
import com.valueinvesting.webapp.persistence.repository.RefreshTokenRepository
import com.valueinvesting.webapp.persistence.repository.UserRepository
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * MFA TOTP lifecycle: enrollment, activation, verification (TOTP + recovery),
 * and disable. Persists `mfa_secrets` rows, delegates crypto to [TotpService].
 *
 * Behavioral contract (ADR-025 §4):
 * - `enroll` is idempotent for *not-yet-activated* secrets: re-issues a fresh
 *   secret + recovery codes if the user previously started enrollment without
 *   verifying. Already-enabled MFA cannot be re-enrolled (must disable first).
 * - `verify` flips `enabled = true` only on a valid current-period TOTP; the
 *   secret persisted at enrollment is reused (we do NOT regenerate on verify).
 * - `verifyTotpForLogin` and `consumeRecoveryCodeForLogin` are idempotent on
 *   failure: failed attempts do not invalidate the stored secret/recovery list
 *   (rate limiting + lockout live in AuthRateLimitService / TSK-229..230).
 * - `disable` deletes the row; re-enabling MFA is a fresh enrollment.
 *
 * [^src: design_&_architecture/decisions/ADR-025-security-hardening-pci-dss.md §4]
 * [^src: management/kanban/EP-018-hardening-sicurezza-compliance/US-081-protezione-identita-accesso/TSK-228.md]
 */
@Service
class MfaService(
    private val mfaSecretRepository: MfaSecretRepository,
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val totpService: TotpService,
    private val passwordEncoder: PasswordEncoder,
    private val clock: Clock,
) {

    @Transactional
    fun startEnrollment(userId: UUID): EnrollmentResult {
        val user = userRepository.findById(userId).orElseThrow {
            BadCredentialsException("Invalid email or password")
        }
        val existing = mfaSecretRepository.findByUserId(userId)
        if (existing != null && existing.enabled) {
            throw MfaAlreadyEnabledException()
        }
        val material = totpService.createEnrollmentMaterial(user.email)
        val now = Instant.now(clock)
        if (existing != null) {
            existing.totpSecretEncrypted = material.totpSecretEncrypted
            existing.recoveryCodesHash = material.recoveryCodes.recoveryCodesHash
            existing.enabled = false
            existing.enabledAt = null
            mfaSecretRepository.save(existing)
        } else {
            mfaSecretRepository.save(
                MfaSecretEntity(
                    userId = userId,
                    totpSecretEncrypted = material.totpSecretEncrypted,
                    recoveryCodesHash = material.recoveryCodes.recoveryCodesHash,
                    enabled = false,
                    createdAt = now,
                ),
            )
        }
        return EnrollmentResult(
            secret = material.secret,
            qrCodeUri = material.qrCodeUri,
            recoveryCodes = material.recoveryCodes.plainCodes,
        )
    }

    @Transactional
    fun activate(userId: UUID, totpCode: String) {
        val secret = mfaSecretRepository.findByUserId(userId)
            ?: throw MfaNotEnrolledException()
        if (secret.enabled) {
            throw MfaAlreadyEnabledException()
        }
        val plainSecret = totpService.decryptSecret(secret.totpSecretEncrypted)
        if (!totpService.verifyTotp(plainSecret, totpCode)) {
            throw InvalidTotpCodeException()
        }
        secret.enabled = true
        secret.enabledAt = Instant.now(clock)
        mfaSecretRepository.save(secret)
        // Sessions opened before MFA was activated must not be able to skip the
        // second factor — revoke all refresh tokens for this user so the next
        // /api/auth/login goes through the MFA challenge flow.
        refreshTokenRepository.deleteAllByUserId(userId)
    }

    fun isMfaEnabled(userId: UUID): Boolean {
        return mfaSecretRepository.findByUserId(userId)?.enabled == true
    }

    /**
     * Validates the TOTP code presented during login. Throws if MFA is not
     * enabled or the code is wrong. Does NOT persist anything on success
     * (login attempt audit is owned by AuthRateLimitService).
     */
    fun verifyTotpForLogin(userId: UUID, totpCode: String) {
        val secret = mfaSecretRepository.findByUserId(userId)
            ?: throw MfaNotEnabledException()
        if (!secret.enabled) {
            throw MfaNotEnabledException()
        }
        val plainSecret = totpService.decryptSecret(secret.totpSecretEncrypted)
        if (!totpService.verifyTotp(plainSecret, totpCode)) {
            throw InvalidTotpCodeException()
        }
    }

    /**
     * One-time consumption of a recovery code: on success the matching hash is
     * removed from `recovery_codes_hash`. Throws on miss; the caller is responsible
     * for generic-error mapping (anti-enum) at the controller boundary.
     */
    @Transactional
    fun consumeRecoveryCodeForLogin(userId: UUID, recoveryCode: String) {
        val secret = mfaSecretRepository.findByUserId(userId)
            ?: throw MfaNotEnabledException()
        if (!secret.enabled) {
            throw MfaNotEnabledException()
        }
        val result = totpService.verifyAndConsumeRecoveryCode(recoveryCode, secret.recoveryCodesHash)
        if (!result.valid) {
            throw InvalidRecoveryCodeException()
        }
        secret.recoveryCodesHash = result.updatedRecoveryCodesHash
        mfaSecretRepository.save(secret)
    }

    @Transactional
    fun disable(userId: UUID, password: String) {
        val user = userRepository.findById(userId).orElseThrow {
            BadCredentialsException("Invalid email or password")
        }
        if (!passwordEncoder.matches(password, user.passwordHash)) {
            throw BadCredentialsException("Invalid email or password")
        }
        val existing = mfaSecretRepository.findByUserId(userId)
            ?: throw MfaNotEnabledException()
        if (!existing.enabled) {
            throw MfaNotEnabledException()
        }
        mfaSecretRepository.delete(existing)
    }

    data class EnrollmentResult(
        val secret: String,
        val qrCodeUri: String,
        val recoveryCodes: List<String>,
    )
}
