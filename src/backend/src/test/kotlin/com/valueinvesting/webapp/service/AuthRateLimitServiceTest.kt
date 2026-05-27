package com.valueinvesting.webapp.service

import com.valueinvesting.webapp.config.RateLimitingProperties
import com.valueinvesting.webapp.persistence.entity.LoginAttemptEntity
import com.valueinvesting.webapp.persistence.repository.LoginAttemptRepository
import com.valueinvesting.webapp.service.AuthRateLimitService.AuthEndpoint
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AuthRateLimitServiceTest {

    private val loginAttemptRepository: LoginAttemptRepository = mockk(relaxed = true)

    private val fixedInstant = Instant.parse("2026-05-27T12:00:00Z")
    private val clock: Clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    private lateinit var service: AuthRateLimitService

    @BeforeEach
    fun setUp() {
        every { loginAttemptRepository.save(any<LoginAttemptEntity>()) } answers { firstArg() }
        val props = RateLimitingProperties(
            windowMinutes = 5,
            login = RateLimitingProperties.EndpointLimits(perIp = 2, perAccount = 1),
        )
        service = AuthRateLimitService(loginAttemptRepository, props, clock)
    }

    @Test
    fun `allows request under IP and account limits and records attempt`() {
        every { loginAttemptRepository.countByIpAddressAndAttemptedAtAfter(any(), any()) } returns 0
        every { loginAttemptRepository.countByAccountEmailAndAttemptedAtAfter(any(), any()) } returns 0

        val decision = service.checkAndRecord(
            AuthEndpoint.LOGIN,
            "203.0.113.1",
            "user@example.com",
            "JUnit",
        )

        assertThat(decision.allowed).isTrue()
        verify { loginAttemptRepository.save(any()) }
    }

    @Test
    fun `blocks when IP limit exceeded with retry-after`() {
        every { loginAttemptRepository.countByIpAddressAndAttemptedAtAfter(any(), any()) } returns 2
        every { loginAttemptRepository.findOldestAttemptedAtByIpSince(any(), any()) } returns
            fixedInstant.minusSeconds(120)

        val decision = service.checkAndRecord(
            AuthEndpoint.LOGIN,
            "203.0.113.1",
            "user@example.com",
            null,
        )

        assertThat(decision.allowed).isFalse()
        assertThat(decision.retryAfterSeconds).isGreaterThan(0)
    }

    @Test
    fun `blocks when account limit exceeded`() {
        every { loginAttemptRepository.countByIpAddressAndAttemptedAtAfter(any(), any()) } returns 0
        every { loginAttemptRepository.countByAccountEmailAndAttemptedAtAfter(any(), any()) } returns 1
        every { loginAttemptRepository.findOldestAttemptedAtByAccountSince(any(), any()) } returns
            fixedInstant.minusSeconds(60)

        val decision = service.checkAndRecord(
            AuthEndpoint.LOGIN,
            "203.0.113.1",
            "user@example.com",
            null,
        )

        assertThat(decision.allowed).isFalse()
        assertThat(decision.retryAfterSeconds).isEqualTo(240)
    }
}
