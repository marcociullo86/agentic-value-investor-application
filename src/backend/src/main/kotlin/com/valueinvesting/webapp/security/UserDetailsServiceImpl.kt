package com.valueinvesting.webapp.security

import com.valueinvesting.webapp.persistence.repository.UserRepository
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class UserDetailsServiceImpl(
    private val userRepository: UserRepository,
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val user = userRepository.findByEmailIgnoreCase(username)
            ?: throw UsernameNotFoundException("User not found: $username")
        return UserPrincipal(
            userId = user.id,
            emailValue = user.email,
            passwordHash = user.passwordHash,
        )
    }
}
