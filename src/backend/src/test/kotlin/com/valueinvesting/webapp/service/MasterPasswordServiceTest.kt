package com.valueinvesting.webapp.service

import com.valueinvesting.webapp.persistence.entity.MasterPasswordEntity
import com.valueinvesting.webapp.persistence.repository.MasterPasswordRepository
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.time.Instant

class MasterPasswordServiceTest {

    private val repository: MasterPasswordRepository = mockk()
    private val encoder = BCryptPasswordEncoder(12)
    private val service = MasterPasswordService(repository, encoder)

    private fun seed(raw: String) {
        every { repository.findTopByOrderByIdAsc() } returns
            MasterPasswordEntity(id = 1L, passwordHash = encoder.encode(raw), updatedAt = Instant.now())
    }

    @Test
    fun `verify returns true for the correct password`() {
        seed("agenticvalueinvestor")
        assertThat(service.verify("agenticvalueinvestor")).isTrue()
    }

    @Test
    fun `verify returns false for a wrong password`() {
        seed("agenticvalueinvestor")
        assertThat(service.verify("wrong-password")).isFalse()
    }

    @Test
    fun `verify returns false on blank input`() {
        assertThat(service.verify("")).isFalse()
    }

    @Test
    fun `verify returns false when no master password row exists`() {
        every { repository.findTopByOrderByIdAsc() } returns null
        assertThat(service.verify("anything")).isFalse()
    }
}
