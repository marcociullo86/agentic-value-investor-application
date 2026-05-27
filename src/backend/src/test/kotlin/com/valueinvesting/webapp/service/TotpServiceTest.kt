package com.valueinvesting.webapp.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.valueinvesting.webapp.config.AppProperties
import dev.samstevens.totp.code.DefaultCodeGenerator
import dev.samstevens.totp.code.HashingAlgorithm
import dev.samstevens.totp.time.SystemTimeProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

class TotpServiceTest {

    private val mfaConfig = AppProperties.Security.Mfa(
        issuer = "ValueInvestorTest",
        totpPeriodSeconds = 30,
        recoveryCodesCount = 8,
        encryptionKey = "test-only-mfa-encryption-key-32chars-min",
    )
    private val appProperties = AppProperties(security = AppProperties.Security(mfa = mfaConfig))
    private val passwordEncoder = BCryptPasswordEncoder(12)
    private lateinit var totpService: TotpService

    @BeforeEach
    fun setUp() {
        totpService = TotpService(appProperties, passwordEncoder, ObjectMapper())
    }

    @Test
    fun `generateSecret returns non-empty base32 secret`() {
        val secret = totpService.generateSecret()
        assertThat(secret).isNotBlank()
        assertThat(secret).matches("[A-Z2-7]+")
    }

    @Test
    fun `buildQrCodeUri returns valid otpauth URI`() {
        val secret = totpService.generateSecret()
        val uri = totpService.buildQrCodeUri(secret, "user@example.com")
        assertThat(uri).startsWith("otpauth://totp/")
        assertThat(uri).contains("secret=$secret")
        assertThat(uri).contains("issuer=ValueInvestorTest")
        assertThat(uri).contains("period=30")
    }

    @Test
    fun `verifyTotp accepts current code and rejects invalid`() {
        val secret = totpService.generateSecret()
        val codeGenerator = DefaultCodeGenerator(HashingAlgorithm.SHA1, 6)
        val timeProvider = SystemTimeProvider()
        val counter = timeProvider.time / mfaConfig.totpPeriodSeconds
        val currentCode = codeGenerator.generate(secret, counter)

        assertThat(totpService.verifyTotp(secret, currentCode)).isTrue()
        assertThat(totpService.verifyTotp(secret, "000000")).isFalse()
        assertThat(totpService.verifyTotp(secret, "abcdef")).isFalse()
    }

    @Test
    fun `generateRecoveryCodes produces eight unique bcrypt hashes`() {
        val bundle = totpService.generateRecoveryCodes()
        assertThat(bundle.plainCodes).hasSize(8)
        assertThat(bundle.plainCodes).doesNotHaveDuplicates()

        val storedHashes: List<String> = ObjectMapper().readValue(
            bundle.recoveryCodesHash,
            object : com.fasterxml.jackson.core.type.TypeReference<List<String>>() {},
        )
        assertThat(storedHashes).hasSize(8)
        storedHashes.forEach { assertThat(it).startsWith("\$2") }
    }

    @Test
    fun `verifyAndConsumeRecoveryCode is one-time`() {
        val bundle = totpService.generateRecoveryCodes()
        val code = bundle.plainCodes.first()

        val first = totpService.verifyAndConsumeRecoveryCode(code, bundle.recoveryCodesHash)
        assertThat(first.valid).isTrue()
        assertThat(first.updatedRecoveryCodesHash).isNotEqualTo(bundle.recoveryCodesHash)

        val second = totpService.verifyAndConsumeRecoveryCode(code, first.updatedRecoveryCodesHash)
        assertThat(second.valid).isFalse()
    }

    @Test
    fun `verifyAndConsumeRecoveryCode accepts code without dashes`() {
        val bundle = totpService.generateRecoveryCodes()
        val dashed = bundle.plainCodes.first()
        val normalized = dashed.replace("-", "")

        val result = totpService.verifyAndConsumeRecoveryCode(normalized, bundle.recoveryCodesHash)
        assertThat(result.valid).isTrue()
    }

    @Test
    fun `encryptSecret round-trips through decryptSecret`() {
        val secret = totpService.generateSecret()
        val encrypted = totpService.encryptSecret(secret)
        assertThat(encrypted).isNotEqualTo(secret)
        assertThat(totpService.decryptSecret(encrypted)).isEqualTo(secret)
    }

    @Test
    fun `createEnrollmentMaterial bundles secret qr recovery and encrypted storage`() {
        val material = totpService.createEnrollmentMaterial("user@example.com")
        assertThat(material.secret).isNotBlank()
        assertThat(material.qrCodeUri).startsWith("otpauth://")
        assertThat(material.recoveryCodes.plainCodes).hasSize(8)
        assertThat(material.totpSecretEncrypted).isNotBlank()
        assertThat(totpService.decryptSecret(material.totpSecretEncrypted)).isEqualTo(material.secret)
    }
}
