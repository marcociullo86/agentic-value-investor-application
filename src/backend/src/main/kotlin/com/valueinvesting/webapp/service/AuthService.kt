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
    private val mfaService: MfaService,
    private val bruteForceProtectionService: BruteForceProtectionService,
    private val securityEventLogger: SecurityEventLogger,
    private val clock: Clock,
) {

    @Transactional
    fun register(
        request: RegisterRequest,
        ip: String = UNKNOWN_IP,
        userAgent: String? = null,
    ): UserProfileResponse {
        // ADR-025 §5 — per-IP CAPTCHA gate also covers registration so a bot
        // burning the rate-limit per-IP=5/5min on /register doesn't bypass the
        // captcha challenge. Throws CaptchaRequiredException(401) when the IP
        // has tripped the threshold and no valid token is supplied.
        bruteForceProtectionService.guardRegister(ip, request.captchaToken)

        val normalizedEmail = request.email.trim()
        if (userRepository.findByEmailIgnoreCase(normalizedEmail) != null) {
            bruteForceProtectionService.recordLoginFailure(
                email = normalizedEmail,
                ip = ip,
                userAgent = userAgent,
                reason = BruteForceProtectionService.REASON_REGISTER_FAILURE,
            )
            throw EmailAlreadyRegisteredException(normalizedEmail)
        }
        try {
            compromisedPasswordGuard.assertNotCompromised(request.password)
        } catch (ex: CompromisedPasswordException) {
            bruteForceProtectionService.recordLoginFailure(
                email = normalizedEmail,
                ip = ip,
                userAgent = userAgent,
                reason = BruteForceProtectionService.REASON_REGISTER_FAILURE,
            )
            throw ex
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
    fun login(
        request: LoginRequest,
        ip: String = UNKNOWN_IP,
        userAgent: String? = null,
    ): LoginOutcome {
        // ADR-025 §5 — pre-flight: lockout / captcha / progressive delay.
        // [bruteForceProtectionService.guardLogin] runs with propagation
        // NOT_SUPPORTED so the optional [Thread.sleep] does not hold this
        // method's transaction connection.
        bruteForceProtectionService.guardLogin(request.email, ip, request.captchaToken)

        val user = userRepository.findByEmailIgnoreCase(request.email.trim())
        if (user == null) {
            bruteForceProtectionService.recordLoginFailure(
                email = request.email,
                ip = ip,
                userAgent = userAgent,
                reason = BruteForceProtectionService.REASON_BAD_CREDENTIALS,
            )
            throw BadCredentialsException("Invalid email or password")
        }
        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            bruteForceProtectionService.recordLoginFailure(
                email = user.email,
                ip = ip,
                userAgent = userAgent,
                reason = BruteForceProtectionService.REASON_BAD_CREDENTIALS,
            )
            throw BadCredentialsException("Invalid email or password")
        }
        // ADR-025 §4 — short-circuit when MFA is enabled: emit a 5-min mfaToken
        // and do NOT touch lastLoginAt or issue refresh tokens until the user
        // proves the second factor at /api/auth/mfa/challenge|recovery.
        if (mfaService.isMfaEnabled(user.id)) {
            // Record an intermediate "mfa_required" row — counts as neither
            // success nor bad_credentials so it does NOT fuel brute-force
            // counters but DOES leave an audit trail for the security log.
            bruteForceProtectionService.recordLoginFailure(
                email = user.email,
                ip = ip,
                userAgent = userAgent,
                reason = BruteForceProtectionService.REASON_MFA_REQUIRED,
            )
            val issued = jwtService.issueMfaChallengeToken(user.id, user.email)
            return LoginOutcome.MfaRequired(
                mfaToken = issued.token,
                expiresInSeconds = issued.expiresInSeconds,
            )
        }
        user.lastLoginAt = Instant.now(clock)
        userRepository.save(user)
        val tokens = issueTokenPair(user)
        // New-device detection (ADR-025 §7) — fires only on the password-only
        // login branch. The MFA branch logs on /api/auth/mfa/{challenge,recovery}
        // when the full session is finally issued (out of scope for TSK-230 —
        // see wiki/gaps.md if extended).
        bruteForceProtectionService.recordLoginSuccess(user, ip, userAgent)
        return LoginOutcome.TokenPair(
            accessToken = tokens.accessToken,
            refreshTokenValue = tokens.refreshTokenValue,
            expiresInSeconds = tokens.expiresInSeconds,
        )
    }

    /**
     * Completes the MFA login leg by issuing the regular access + refresh
     * pair after [MfaService.verifyTotpForLogin] / [MfaService.consumeRecoveryCodeForLogin]
     * has succeeded. Caller passes the userId carried by the parsed mfaToken.
     */
    @Transactional
    fun completeMfaChallenge(userId: UUID): AuthResult {
        val user = userRepository.findById(userId).orElseThrow {
            BadCredentialsException("Invalid email or password")
        }
        user.lastLoginAt = Instant.now(clock)
        userRepository.save(user)
        return issueTokenPair(user)
    }

    // ADR-027 §2 — il ramo reuse-detection esegue la cascade `revokeAllActiveByUserId`
    // e POI lancia InvalidRefreshTokenException(REASON_REUSE_DETECTED). Con il
    // rollback di default di Spring su RuntimeException la UPDATE di revoca verrebbe
    // ANNULLATA: il kill-switch non persisterebbe nulla (regressione CascadeRevocationIT
    // scenari 1/2/5). `noRollbackFor` garantisce che la cascade venga committata E che
    // il 401 opaco venga comunque propagato. Gli altri rami di throw (not_found,
    // sliding_expired, absolute_cap, user_unknown) non scrivono prima di lanciare,
    // quindi l'esenzione dal rollback è innocua per loro.
    @Transactional(noRollbackFor = [InvalidRefreshTokenException::class])
    fun refresh(refreshTokenValue: String): AuthResult {
        // Anti-enum-attack (TSK-041): clients never see why a refresh was
        // rejected (revoked vs expired vs cap vs unknown); the cause lives
        // only in [InvalidRefreshTokenException.reason] for server-side
        // logging by GlobalExceptionHandler.
        val token = refreshTokenRepository.findByTokenValue(refreshTokenValue)
            ?: throw InvalidRefreshTokenException(REASON_NOT_FOUND)
        val now = Instant.now(clock)
        if (token.revokedAt != null) {
            // ADR-027 §1 — reuse detection: a refresh token already rotated
            // (revoked_at != null) is being presented again → cascade-revoke
            // every still-active token of the user (kill-switch on a strong
            // compromise signal). Runs in the same @Transactional as refresh(),
            // so the bulk UPDATE and the throw are atomic. Idempotent: a second
            // replay of the same token finds zero active rows → revokedCount=0,
            // no error, still throws REASON_REUSE_DETECTED. The client keeps
            // seeing the opaque 401 invalid-refresh (anti-enum §3) — only the
            // server-side security log carries the new reason code + family +
            // revokedCount for SOC correlation (§4).
            val revokedCount = refreshTokenRepository.revokeAllActiveByUserId(token.userId, now)
            securityEventLogger.refreshTokenReuseDetected(
                userId = token.userId,
                family = token.firstIssuedAt,
                revokedCount = revokedCount,
            )
            throw InvalidRefreshTokenException(REASON_REUSE_DETECTED)
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

/**
 * Outcome of [AuthService.login]: either a full token pair (no MFA on the
 * account or MFA already proven) or an `mfaRequired` short-circuit with a
 * short-lived JWT carrying the MFA challenge purpose (ADR-025 §4).
 */
sealed class LoginOutcome {
    data class TokenPair(
        val accessToken: String,
        val refreshTokenValue: String,
        val expiresInSeconds: Long,
    ) : LoginOutcome()

    data class MfaRequired(
        val mfaToken: String,
        val expiresInSeconds: Long,
    ) : LoginOutcome()
}

class EmailAlreadyRegisteredException(val email: String) :
    RuntimeException("Email already registered: $email")

private const val SECONDS_PER_DAY: Long = 86_400

/**
 * Placeholder IP used when the controller-side resolver returns blank (no
 * X-Forwarded-For, no remoteAddr) — keeps the brute-force counters keyed on
 * SOMETHING rather than crashing on a null. Production should always carry a
 * real address (forward-headers-strategy=framework in application.yml).
 */
private const val UNKNOWN_IP: String = "0.0.0.0"

// Anti-enum reason codes carried server-side only by InvalidRefreshTokenException.
// Stable identifiers so log-sink queries / security dashboards can filter by cause.
// Kept file-private here (same compilation unit as the only caller, AuthService)
// to preserve the existing idiom — ADR-027 §3 introduces REASON_REUSE_DETECTED
// alongside the pre-existing reasons; the client surface stays uniformly
// `401 invalid-refresh` opaque.
private const val REASON_NOT_FOUND: String = "not_found"

// Kept after ADR-027: pre-existing identifier reserved in the reason vocabulary;
// the refresh() reuse branch now emits REASON_REUSE_DETECTED instead, but the
// constant is preserved so log-sink queries and out-of-scope callers
// (e.g. logout, future revocation flows) keep a stable token to reference.
@Suppress("unused")
private const val REASON_REVOKED: String = "revoked"
private const val REASON_SLIDING_EXPIRED: String = "sliding_expired"
private const val REASON_ABSOLUTE_CAP: String = "absolute_cap"
private const val REASON_USER_UNKNOWN: String = "user_unknown"
private const val REASON_REUSE_DETECTED: String = "reuse_detected"
