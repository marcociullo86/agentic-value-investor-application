package com.valueinvesting.webapp.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.valueinvesting.webapp.config.AppProperties
import com.valueinvesting.webapp.persistence.entity.MfaSecretEntity
import com.valueinvesting.webapp.persistence.entity.User
import com.valueinvesting.webapp.persistence.repository.MfaSecretRepository
import com.valueinvesting.webapp.persistence.repository.RefreshTokenRepository
import com.valueinvesting.webapp.persistence.repository.UserRepository
import dev.samstevens.totp.code.DefaultCodeGenerator
import dev.samstevens.totp.code.HashingAlgorithm
import dev.samstevens.totp.time.SystemTimeProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID

/**
 * Unit-level coverage for [MfaService] (TSK-228 / US-081 / ADR-025 §4).
 * The TOTP cryptographic primitives are exercised with the real [TotpService]
 * (deterministic given a fixed secret + period) while persistence boundaries
 * are mocked with MockK — same convention as [AuthServiceTest].
 */
class MfaServiceTest {

    private val now: Instant = Instant.parse("2026-05-28T12:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    private val mfaConfig = AppProperties.Security.Mfa(
        issuer = "ValueInvestorTest",
        totpPeriodSeconds = 30,
        recoveryCodesCount = 8,
        encryptionKey = "test-only-mfa-encryption-key-32chars-min",
    )
    private val appProperties = AppProperties(security = AppProperties.Security(mfa = mfaConfig))
    private val passwordEncoder = BCryptPasswordEncoder(12)
    private val totpService = TotpService(appProperties, passwordEncoder, ObjectMapper())
    private val totpGenerator = DefaultCodeGenerator(HashingAlgorithm.SHA1, 6)
    private val timeProvider = SystemTimeProvider()

    private lateinit var mfaSecretRepository: MfaSecretRepository
    private lateinit var userRepository: UserRepository
    private lateinit var refreshTokenRepository: RefreshTokenRepository
    private lateinit var service: MfaService

    @BeforeEach
    fun setUp() {
        mfaSecretRepository = mockk(relaxed = true)
        userRepository = mockk(relaxed = true)
        refreshTokenRepository = mockk(relaxed = true)
        service = MfaService(
            mfaSecretRepository = mfaSecretRepository,
            userRepository = userRepository,
            refreshTokenRepository = refreshTokenRepository,
            totpService = totpService,
            passwordEncoder = passwordEncoder,
            clock = clock,
        )
    }

    @Test
    fun `startEnrollment persists fresh secret and returns plain recovery codes`() {
        val user = newUser("alice@example.com")
        every { userRepository.findById(user.id) } returns Optional.of(user)
        every { mfaSecretRepository.findByUserId(user.id) } returns null
        val saved = slot<MfaSecretEntity>()
        every { mfaSecretRepository.save(capture(saved)) } answers { saved.captured }

        val result = service.startEnrollment(user.id)

        assertThat(result.recoveryCodes).hasSize(8)
        assertThat(result.qrCodeUri).startsWith("otpauth://")
        assertThat(saved.captured.userId).isEqualTo(user.id)
        assertThat(saved.captured.enabled).isFalse()
        assertThat(totpService.decryptSecret(saved.captured.totpSecretEncrypted))
            .isEqualTo(result.secret)
    }

    @Test
    fun `startEnrollment refuses when MFA already enabled`() {
        val user = newUser("alice@example.com")
        val existing = MfaSecretEntity(
            userId = user.id,
            totpSecretEncrypted = totpService.encryptSecret(totpService.generateSecret()),
            enabled = true,
            enabledAt = now,
        )
        every { userRepository.findById(user.id) } returns Optional.of(user)
        every { mfaSecretRepository.findByUserId(user.id) } returns existing

        assertThatThrownBy { service.startEnrollment(user.id) }
            .isInstanceOf(MfaAlreadyEnabledException::class.java)
    }

    @Test
    fun `activate flips enabled when TOTP is valid for current period`() {
        val secret = totpService.generateSecret()
        val entity = MfaSecretEntity(
            userId = UUID.randomUUID(),
            totpSecretEncrypted = totpService.encryptSecret(secret),
            enabled = false,
        )
        every { mfaSecretRepository.findByUserId(entity.userId) } returns entity
        every { mfaSecretRepository.save(any()) } answers { firstArg() }

        val code = currentTotp(secret)
        service.activate(entity.userId, code)

        assertThat(entity.enabled).isTrue()
        assertThat(entity.enabledAt).isEqualTo(now)
        verify { mfaSecretRepository.save(entity) }
    }

    @Test
    fun `activate rejects wrong TOTP and leaves enabled=false`() {
        val secret = totpService.generateSecret()
        val entity = MfaSecretEntity(
            userId = UUID.randomUUID(),
            totpSecretEncrypted = totpService.encryptSecret(secret),
            enabled = false,
        )
        every { mfaSecretRepository.findByUserId(entity.userId) } returns entity

        assertThatThrownBy { service.activate(entity.userId, "000000") }
            .isInstanceOf(InvalidTotpCodeException::class.java)
        assertThat(entity.enabled).isFalse()
    }

    @Test
    fun `consumeRecoveryCodeForLogin removes hash on first use and rejects replay`() {
        val secret = totpService.generateSecret()
        val bundle = totpService.generateRecoveryCodes()
        val userId = UUID.randomUUID()
        val entity = MfaSecretEntity(
            userId = userId,
            totpSecretEncrypted = totpService.encryptSecret(secret),
            enabled = true,
            enabledAt = now,
            recoveryCodesHash = bundle.recoveryCodesHash,
        )
        every { mfaSecretRepository.findByUserId(userId) } returns entity
        every { mfaSecretRepository.save(any()) } answers { firstArg() }

        service.consumeRecoveryCodeForLogin(userId, bundle.plainCodes.first())
        assertThat(entity.recoveryCodesHash).isNotEqualTo(bundle.recoveryCodesHash)

        assertThatThrownBy { service.consumeRecoveryCodeForLogin(userId, bundle.plainCodes.first()) }
            .isInstanceOf(InvalidRecoveryCodeException::class.java)
    }

    @Test
    fun `disable requires matching password and removes the secret row`() {
        val password = "correct-horse-battery-staple-1234"
        val user = newUser("alice@example.com").apply {
            passwordHash = passwordEncoder.encode(password)
        }
        val entity = MfaSecretEntity(
            userId = user.id,
            totpSecretEncrypted = totpService.encryptSecret(totpService.generateSecret()),
            enabled = true,
            enabledAt = now,
        )
        every { userRepository.findById(user.id) } returns Optional.of(user)
        every { mfaSecretRepository.findByUserId(user.id) } returns entity

        assertThatThrownBy { service.disable(user.id, "wrong-password-on-known-account") }
            .isInstanceOf(BadCredentialsException::class.java)
        verify(exactly = 0) { mfaSecretRepository.delete(any()) }

        service.disable(user.id, password)
        verify { mfaSecretRepository.delete(entity) }
    }

    private fun newUser(email: String): User = User(
        id = UUID.randomUUID(),
        email = email,
        passwordHash = "irrelevant",
        createdAt = now,
    )

    private fun currentTotp(secret: String): String {
        val counter = timeProvider.time / 30L
        return totpGenerator.generate(secret, counter)
    }
}
