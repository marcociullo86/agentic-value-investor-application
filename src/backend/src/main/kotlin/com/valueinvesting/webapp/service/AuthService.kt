package com.valueinvesting.webapp.service

import com.valueinvesting.webapp.api.model.LoginRequest
import com.valueinvesting.webapp.api.model.RegisterRequest
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
    private val compromisedPasswordGuard: CompromisedPasswordGuard,
    private val clock: Clock,
) {

    @Transactional
    fun register(request: RegisterRequest): UserProfileResponse {
        val normalizedEmail = request.email.trim()
        if (userRepository.findByEmailIgnoreCase(normalizedEmail) != null) {
            throw EmailAlreadyRegisteredException(normalizedEmail)
        }
        compromisedPasswordGuard.assertNotCompromised(request.password)
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
    fun login(request: LoginRequest): AuthResult {
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
    fun refresh(refreshTokenValue: String): AuthResult {
        // Anti-enum-attack (TSK-041): clients never see why a refresh was
        // rejected (revoked vs expired vs cap vs unknown); the cause lives
        // only in [InvalidRefreshTokenException.reason] for server-side
        // logging by GlobalExceptionHandler.
        val token = refreshTokenRepository.findByTokenValue(refreshTokenValue)
            ?: throw InvalidRefreshTokenException(REASON_NOT_FOUND)
        val now = Instant.now(clock)
        if (token.revokedAt != null) {
            throw InvalidRefreshTokenException(REASON_REVOKED)
        }
        if (!token.expiresAt.isAfter(now)) {
            throw InvalidRefreshTokenException(REASON_SLIDING_EXPIRED)
        }
        // ADR-010 §3 — cap assoluto dal login originale (first_issued_at).
        // La catena di rotation conserva first_issued_at del refresh iniziale.
        val capDays = appProperties.jwt.refreshAbsoluteCapDays
        val capDeadline = token.firstIssuedAt.plusSeconds(capDays * SECONDS_PER_DAY)
        if (!capDeadline.isAfter(now)) {
            throw InvalidRefreshTokenException(REASON_ABSOLUTE_CAP)
        }
        val user = userRepository.findById(token.userId).orElseThrow {
            InvalidRefreshTokenException(REASON_USER_UNKNOWN)
        }
        // Rotation (ADR-006 §Refresh token rotation): segna il vecchio come
        // revocato e emetti un nuovo refresh preservando first_issued_at.
        token.revokedAt = now
        refreshTokenRepository.save(token)
        return issueTokenPair(user, firstIssuedAt = token.firstIssuedAt)
    }

    /**
     * Cambio password autenticato (US-081 / ADR-025 §5). Endpoint HTTP sarà
     * aggiunto da TSK successivi; la guard HIBP vive qui per riuso.
     */
    @Transactional
    fun changePassword(userId: UUID, currentPassword: String, newPassword: String) {
        val user = userRepository.findById(userId).orElseThrow {
            BadCredentialsException("Invalid email or password")
        }
        if (!passwordEncoder.matches(currentPassword, user.passwordHash)) {
            throw BadCredentialsException("Invalid email or password")
        }
        compromisedPasswordGuard.assertNotCompromised(newPassword)
        user.passwordHash = passwordEncoder.encode(newPassword)
        userRepository.save(user)
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

    // [firstIssuedAt] = null su login (catena nuova → now); valorizzato dal
    // refresh per preservare la testa della catena (ADR-010 §3).
    private fun issueTokenPair(user: User, firstIssuedAt: Instant? = null): AuthResult {
        val issued = jwtService.issueAccessToken(user.id, user.email)
        val now = Instant.now(clock)
        val slidingSeconds = appProperties.jwt.refreshSlidingTtlDays * SECONDS_PER_DAY
        val refresh = RefreshToken(
            userId = user.id,
            tokenValue = UUID.randomUUID().toString(),
            expiresAt = now.plusSeconds(slidingSeconds),
            firstIssuedAt = firstIssuedAt ?: now,
        )
        val savedRefresh = refreshTokenRepository.save(refresh)
        return AuthResult(
            accessToken = issued.token,
            refreshTokenValue = savedRefresh.tokenValue,
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

data class AuthResult(
    val accessToken: String,
    val refreshTokenValue: String,
    val expiresInSeconds: Long,
)

class EmailAlreadyRegisteredException(val email: String) :
    RuntimeException("Email already registered: $email")

private const val SECONDS_PER_DAY: Long = 86_400

// Anti-enum reason codes carried server-side only by InvalidRefreshTokenException.
// Stable identifiers so log-sink queries / security dashboards can filter by cause.
private const val REASON_NOT_FOUND: String = "not_found"
private const val REASON_REVOKED: String = "revoked"
private const val REASON_SLIDING_EXPIRED: String = "sliding_expired"
private const val REASON_ABSOLUTE_CAP: String = "absolute_cap"
private const val REASON_USER_UNKNOWN: String = "user_unknown"
