package com.valueinvesting.webapp.service

import com.valueinvesting.webapp.api.model.LoginRequest
import com.valueinvesting.webapp.api.model.RegisterRequest
import com.valueinvesting.webapp.api.model.TokenPairResponse
import com.valueinvesting.webapp.api.model.UserProfileResponse
import com.valueinvesting.webapp.config.AppProperties
import com.valueinvesting.webapp.persistence.entity.RefreshToken
import com.valueinvesting.webapp.persistence.entity.User
import com.valueinvesting.webapp.persistence.repository.RefreshTokenRepository
import com.valueinvesting.webapp.persistence.repository.UserRepository
import com.valueinvesting.webapp.security.JwtService
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Auth orchestration: registration, password verification, refresh-token
 * lifecycle. Stateless JWT issuance is delegated to [JwtService]; refresh
 * tokens are opaque UUIDs persisted in `refresh_tokens` so they can be
 * revoked server-side (ADR-006 §Token).
 *
 * [^src: design_&_architecture/decisions/ADR-006-authentication.md §Architettura]
 * [^src: design_&_architecture/components/backend-components.md §AuthService]
 */
@Service
class AuthService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val appProperties: AppProperties,
    private val clock: Clock,
) {

    @Transactional
    fun register(request: RegisterRequest): UserProfileResponse {
        val normalizedEmail = request.email.trim()
        if (userRepository.findByEmailIgnoreCase(normalizedEmail) != null) {
            throw EmailAlreadyRegisteredException(normalizedEmail)
        }
        val user = User(
            email = normalizedEmail,
            passwordHash = passwordEncoder.encode(request.password),
            displayName = request.displayName?.takeIf { it.isNotBlank() },
            createdAt = Instant.now(clock),
        )
        val saved = userRepository.save(user)
        return saved.toProfile()
    }

    @Transactional
    fun login(request: LoginRequest): TokenPairResponse {
        val user = userRepository.findByEmailIgnoreCase(request.email.trim())
            ?: throw BadCredentialsException("Invalid email or password")
        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw BadCredentialsException("Invalid email or password")
        }
        user.lastLoginAt = Instant.now(clock)
        userRepository.save(user)
        return issueTokenPair(user)
    }

    @Transactional
    fun refresh(refreshTokenValue: String): TokenPairResponse {
        val token = refreshTokenRepository.findByTokenValue(refreshTokenValue)
            ?: throw BadCredentialsException("Invalid refresh token")
        val now = Instant.now(clock)
        if (token.revokedAt != null || token.expiresAt.isBefore(now)) {
            throw BadCredentialsException("Refresh token revoked or expired")
        }
        val user = userRepository.findById(token.userId).orElseThrow {
            BadCredentialsException("Refresh token references unknown user")
        }
        // Rotate refresh token (ADR-006 §Refresh token rotation lato server).
        token.revokedAt = now
        refreshTokenRepository.save(token)
        return issueTokenPair(user)
    }

    @Transactional
    fun logout(userId: UUID, refreshTokenValue: String?) {
        if (refreshTokenValue.isNullOrBlank()) return
        val token = refreshTokenRepository.findByTokenValue(refreshTokenValue) ?: return
        if (token.userId != userId) return
        if (token.revokedAt == null) {
            token.revokedAt = Instant.now(clock)
            refreshTokenRepository.save(token)
        }
    }

    private fun issueTokenPair(user: User): TokenPairResponse {
        val issued = jwtService.issueAccessToken(user.id, user.email)
        val now = Instant.now(clock)
        val refresh = RefreshToken(
            userId = user.id,
            tokenValue = UUID.randomUUID().toString(),
            expiresAt = now.plusSeconds(appProperties.jwt.refreshTtlDays * 86_400),
        )
        val savedRefresh = refreshTokenRepository.save(refresh)
        return TokenPairResponse(
            accessToken = issued.token,
            refreshToken = savedRefresh.tokenValue,
            expiresInSeconds = issued.expiresInSeconds,
        )
    }

    private fun User.toProfile(): UserProfileResponse = UserProfileResponse(
        id = id,
        email = email,
        displayName = displayName,
        createdAt = createdAt,
    )
}

class EmailAlreadyRegisteredException(val email: String) :
    RuntimeException("Email already registered: $email")
