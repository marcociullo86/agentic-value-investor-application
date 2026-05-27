package com.valueinvesting.webapp.service

import com.valueinvesting.webapp.client.HibpClient
import org.springframework.stereotype.Component

/**
 * Application-level guard invoked before persisting a new password
 * (registration, password change, MFA disable confirmation, etc.).
 */
@Component
class CompromisedPasswordGuard(
    private val hibpClient: HibpClient,
) {

    fun assertNotCompromised(plainPassword: String) {
        if (hibpClient.isPasswordCompromised(plainPassword)) {
            throw CompromisedPasswordException()
        }
    }
}
