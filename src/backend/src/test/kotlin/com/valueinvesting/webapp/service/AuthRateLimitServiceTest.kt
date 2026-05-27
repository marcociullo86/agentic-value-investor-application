package com.valueinvesting.webapp.service

import com.valueinvesting.webapp.config.RateLimitingProperties
import com.valueinvesting.webapp.persistence.repository.LoginAttemptRepository
import com.valueinvesting.webapp.service.AuthRateLimitService.AuthEndpoint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@ExtendWith(MockitoExtension::class)
class AuthRateLimitServiceTest {

    @Mock
    private lateinit var loginAttemptRepository: LoginAttemptRepository

    private val fixedInstant = Instant.parse("2026-05-27T12:00:00Z")
    private val clock: Clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    private lateinit var service: AuthRateLimitService

    @BeforeEach
    fun setUp() {
        val props = RateLimitingProperties(
            windowMinutes = 5,
            login = RateLimitingProperties.EndpointLimits(perIp = 2, perAccount = 1),
        )
        service = AuthRateLimitService(loginAttemptRepository, props, clock)
    }

    @Test
    fun `allows request under IP and account limits and records attempt`() {
        whenever(loginAttemptRepository.countByIpAddressAndAttemptedAtAfter(any(), any())).thenReturn(0)
        whenever(loginAttemptRepository.countByAccountEmailAndAttemptedAtAfter(any(), any())).thenReturn(0)

        val decision = service.checkAndRecord(
            AuthEndpoint.LOGIN,
            "203.0.113.1",
            "user@example.com",
            "JUnit",
        )

        assertThat(decision.allowed).isTrue()
        verify(loginAttemptRepository).save(any())
    }

    @Test
    fun `blocks when IP limit exceeded with retry-after`() {
        whenever(loginAttemptRepository.countByIpAddressAndAttemptedAtAfter(any(), any())).thenReturn(2)
        whenever(loginAttemptRepository.findOldestAttemptedAtByIpSince(any(), any()))
            .thenReturn(fixedInstant.minusSeconds(120))

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
        whenever(loginAttemptRepository.countByIpAddressAndAttemptedAtAfter(any(), any())).thenReturn(0)
        whenever(loginAttemptRepository.countByAccountEmailAndAttemptedAtAfter(any(), any())).thenReturn(1)
        whenever(loginAttemptRepository.findOldestAttemptedAtByAccountSince(any(), any()))
            .thenReturn(fixedInstant.minusSeconds(60))

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
