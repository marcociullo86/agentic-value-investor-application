package com.valueinvesting.webapp.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.valueinvesting.webapp.config.AppProperties
import dev.samstevens.totp.code.DefaultCodeGenerator
import dev.samstevens.totp.code.DefaultCodeVerifier
import dev.samstevens.totp.code.HashingAlgorithm
import dev.samstevens.totp.qr.QrData
import dev.samstevens.totp.recovery.RecoveryCodeGenerator
import dev.samstevens.totp.secret.DefaultSecretGenerator
import dev.samstevens.totp.time.SystemTimeProvider
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * TOTP secret lifecycle, QR otpauth URI, and BCrypt-hashed recovery codes (ADR-025 §4).
 *
 * [^src: design_&_architecture/decisions/ADR-025-security-hardening-pci-dss.md §4]
 * [^src: management/kanban/EP-018-hardening-sicurezza-compliance/US-081-protezione-identita-accesso/TSK-227.md]
 */
@Service
class TotpService(
    private val appProperties: AppProperties,
    private val passwordEncoder: PasswordEncoder,
    private val objectMapper: ObjectMapper,
) {

    private val mfaConfig get() = appProperties.security.mfa

    private val secretGenerator = DefaultSecretGenerator()
    private val recoveryCodeGenerator = RecoveryCodeGenerator()
    private val timeProvider = SystemTimeProvider()

    private val codeVerifier: DefaultCodeVerifier by lazy {
        val period = mfaConfig.totpPeriodSeconds
        DefaultCodeVerifier(
            DefaultCodeGenerator(HashingAlgorithm.SHA1, TOTP_DIGITS),
            timeProvider,
        ).apply {
            setTimePeriod(period)
            setAllowedTimePeriodDiscrepancy(TOTP_DISCREPANCY)
        }
    }

    private val aesKey: SecretKeySpec by lazy {
        val keyMaterial = mfaConfig.encryptionKey
        require(keyMaterial.length >= MIN_ENCRYPTION_KEY_CHARS) {
            "app.security.mfa.encryption-key must be at least $MIN_ENCRYPTION_KEY_CHARS characters"
        }
        val digest = MessageDigest.getInstance("SHA-256")
        SecretKeySpec(digest.digest(keyMaterial.toByteArray(StandardCharsets.UTF_8)), "AES")
    }

    fun generateSecret(): String = secretGenerator.generate()

    fun buildQrCodeUri(secret: String, accountLabel: String): String {
        val qrData = QrData.Builder()
            .label(accountLabel)
            .secret(secret)
            .issuer(mfaConfig.issuer)
            .algorithm(HashingAlgorithm.SHA1)
            .digits(TOTP_DIGITS)
            .period(mfaConfig.totpPeriodSeconds)
            .build()
        return qrData.uri
    }

    fun verifyTotp(secret: String, code: String): Boolean {
        val normalized = code.trim()
        if (!TOTP_CODE_PATTERN.matches(normalized)) {
            return false
        }
        return codeVerifier.isValidCode(secret, normalized)
    }

    /**
     * Plain recovery codes for one-time display plus BCrypt hashes JSON for `recovery_codes_hash`.
     */
    fun generateRecoveryCodes(): RecoveryCodeBundle {
        val plainCodes = recoveryCodeGenerator.generateCodes(mfaConfig.recoveryCodesCount).toList()
        return RecoveryCodeBundle(
            plainCodes = plainCodes,
            recoveryCodesHash = hashRecoveryCodes(plainCodes),
        )
    }

    fun hashRecoveryCodes(plainCodes: List<String>): String {
        val hashes = plainCodes.map { passwordEncoder.encode(normalizeRecoveryCode(it)) }
        return objectMapper.writeValueAsString(hashes)
    }

    /**
     * Verifies a recovery code and returns updated hashes with the matched entry removed (one-time use).
     */
    fun verifyAndConsumeRecoveryCode(
        plainCode: String,
        recoveryCodesHashJson: String?,
    ): RecoveryCodeConsumeResult {
        if (recoveryCodesHashJson.isNullOrBlank()) {
            return RecoveryCodeConsumeResult(false, recoveryCodesHashJson)
        }
        val hashes: MutableList<String> =
            objectMapper.readValue(recoveryCodesHashJson, RECOVERY_HASH_LIST_TYPE).toMutableList()
        if (hashes.isEmpty()) {
            return RecoveryCodeConsumeResult(false, recoveryCodesHashJson)
        }
        val normalized = normalizeRecoveryCode(plainCode)
        val matchIndex = hashes.indexOfFirst { passwordEncoder.matches(normalized, it) }
        if (matchIndex < 0) {
            return RecoveryCodeConsumeResult(false, recoveryCodesHashJson)
        }
        hashes.removeAt(matchIndex)
        val updatedJson = objectMapper.writeValueAsString(hashes)
        return RecoveryCodeConsumeResult(true, updatedJson)
    }

    fun encryptSecret(plainSecret: String): String {
        val cipher = Cipher.getInstance(AES_GCM)
        val iv = ByteArray(GCM_IV_BYTES)
        SecureRandom().nextBytes(iv)
        val spec = GCMParameterSpec(GCM_TAG_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, spec)
        val ciphertext = cipher.doFinal(plainSecret.toByteArray(StandardCharsets.UTF_8))
        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
        return Base64.getEncoder().encodeToString(combined)
    }

    fun decryptSecret(encryptedSecret: String): String {
        val combined = Base64.getDecoder().decode(encryptedSecret)
        require(combined.size > GCM_IV_BYTES) { "Invalid encrypted TOTP secret payload" }
        val iv = combined.copyOfRange(0, GCM_IV_BYTES)
        val ciphertext = combined.copyOfRange(GCM_IV_BYTES, combined.size)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        val plain = cipher.doFinal(ciphertext)
        return String(plain, StandardCharsets.UTF_8)
    }

    fun createEnrollmentMaterial(accountLabel: String): TotpEnrollmentMaterial {
        val secret = generateSecret()
        return TotpEnrollmentMaterial(
            secret = secret,
            qrCodeUri = buildQrCodeUri(secret, accountLabel),
            recoveryCodes = generateRecoveryCodes(),
            totpSecretEncrypted = encryptSecret(secret),
        )
    }

    private fun normalizeRecoveryCode(code: String): String =
        code.trim().lowercase().replace("-", "").replace(" ", "")

    data class TotpEnrollmentMaterial(
        val secret: String,
        val qrCodeUri: String,
        val recoveryCodes: RecoveryCodeBundle,
        val totpSecretEncrypted: String,
    )

    data class RecoveryCodeBundle(
        val plainCodes: List<String>,
        val recoveryCodesHash: String,
    )

    data class RecoveryCodeConsumeResult(
        val valid: Boolean,
        val updatedRecoveryCodesHash: String?,
    )

    companion object {
        private const val TOTP_DIGITS = 6
        private const val TOTP_DISCREPANCY = 1
        private const val MIN_ENCRYPTION_KEY_CHARS = 32
        private const val AES_GCM = "AES/GCM/NoPadding"
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128

        private val TOTP_CODE_PATTERN = Regex("^\\d{6}$")
        private val RECOVERY_HASH_LIST_TYPE = object : TypeReference<List<String>>() {}
    }
}
