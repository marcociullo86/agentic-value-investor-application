package com.valueinvesting.webapp.service

import com.valueinvesting.webapp.client.HibpClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CompromisedPasswordGuardTest {

    private val hibpClient: HibpClient = mockk()
    private val guard = CompromisedPasswordGuard(hibpClient)

    @Test
    fun `assertNotCompromised throws when HIBP reports breach`() {
        every { hibpClient.isPasswordCompromised("password123") } returns true

        assertThatThrownBy { guard.assertNotCompromised("password123") }
            .isInstanceOf(CompromisedPasswordException::class.java)
            .hasMessageContaining("data breach")
    }

    @Test
    fun `assertNotCompromised passes when HIBP reports safe`() {
        every { hibpClient.isPasswordCompromised(any()) } returns false

        guard.assertNotCompromised("unique-local-test-passphrase-xyzzy")

        verify { hibpClient.isPasswordCompromised("unique-local-test-passphrase-xyzzy") }
    }
}
