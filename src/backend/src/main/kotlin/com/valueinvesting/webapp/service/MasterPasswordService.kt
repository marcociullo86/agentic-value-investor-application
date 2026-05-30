package com.valueinvesting.webapp.service

import com.valueinvesting.webapp.persistence.repository.MasterPasswordRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

/**
 * Verifica la master password (operazioni admin distruttive). L'hash è memorizzato
 * in `master_password` (BCrypt cost 12, seed da V031) e confrontato con lo stesso
 * PasswordEncoder usato per il login (ADR-006).
 */
@Service
class MasterPasswordService(
    private val repository: MasterPasswordRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    fun verify(rawPassword: String): Boolean {
        if (rawPassword.isBlank()) return false
        val entity = repository.findTopByOrderByIdAsc() ?: return false
        return passwordEncoder.matches(rawPassword, entity.passwordHash)
    }
}
