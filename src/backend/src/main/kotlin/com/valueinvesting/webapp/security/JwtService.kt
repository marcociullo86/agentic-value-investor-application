package com.valueinvesting.webapp.security

import com.valueinvesting.webapp.config.AppProperties
import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

/**
 * Issues and validates HS256 JWTs per RFC 7519/7515 (JJWT 0.12+).
 *
 * Claims: sub = user.id (UUID), email, iat, exp. TTL governed by
 * `app.jwt.access-ttl-minutes` (default 15 min — ADR-006 §Token).
 *
 * Secret is loaded from `app.jwt.signing-secret` (env var `JWT_SIGNING_SECRET`
 * in prod). Minimum 256 bit enforced at bean construction; tests use a fixed
 * secret bound via application.yml profile=test.
 *
 * [^src: design_&_architecture/decisions/ADR-006-authentication.md §Token]
 * [^src: raw/tech_stack.md §Standards verbatim — JWT (RFC 7519/7515)]
 */
@Service
class JwtService(
    private val appProperties: AppProperties,
    private val clock: Clock,
) {

    private val signingKey: SecretKey by lazy {
        val secret = appProperties.jwt.signingSecret
        require(secret.isNotBlank()) {
            "JWT signing secret is not configured (app.jwt.signing-secret / JWT_SIGNING_SECRET env var)"
        }
        val bytes = secret.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size >= 32) {
            "JWT signing secret must be >= 256 bit (32 bytes) for HS256 — current ${bytes.size} bytes"
        }
        Keys.hmacShaKeyFor(bytes)
    }

    fun issueAccessToken(userId: UUID, email: String): IssuedToken {
        val now = Instant.now(clock)
        val expiry = now.plusSeconds(appProperties.jwt.accessTtlMinutes * 60)
        val token = Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .claim(PURPOSE_CLAIM, PURPOSE_ACCESS)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(signingKey, Jwts.SIG.HS256)
            .compact()
        return IssuedToken(
            token = token,
            issuedAt = now,
            expiresAt = expiry,
            expiresInSeconds = appProperties.jwt.accessTtlMinutes * 60,
        )
    }

    /**
     * Short-lived JWT minted by [com.valueinvesting.webapp.service.AuthService.login]
     * when the user has MFA enabled. Bearer-equivalent for the MFA challenge
     * step only — JwtAuthenticationFilter rejects tokens with this purpose so
     * an mfaToken cannot impersonate a fully-authenticated session.
     *
     * [^src: design_&_architecture/decisions/ADR-025-security-hardening-pci-dss.md §4]
     */
    fun issueMfaChallengeToken(userId: UUID, email: String): IssuedToken {
        val now = Instant.now(clock)
        val ttlSeconds = appProperties.jwt.mfaChallengeTtlMinutes * 60
        val expiry = now.plusSeconds(ttlSeconds)
        val token = Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .claim(PURPOSE_CLAIM, PURPOSE_MFA_CHALLENGE)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(signingKey, Jwts.SIG.HS256)
            .compact()
        return IssuedToken(
            token = token,
            issuedAt = now,
            expiresAt = expiry,
            expiresInSeconds = ttlSeconds,
        )
    }

    /**
     * Parse an access-purpose JWT. Tokens minted with a different purpose
     * (e.g. mfa_challenge) are rejected so [JwtAuthenticationFilter] cannot
     * be tricked into authenticating with an mfaToken.
     */
    fun parse(token: String): ParsedJwt {
        val parsed = parseAny(token)
        // Backward-compat: tokens minted before TSK-228 carry no purpose claim
        // and are treated as access tokens.
        val purpose = parsed.purpose
        if (purpose != null && purpose != PURPOSE_ACCESS) {
            throw InvalidJwtException("JWT purpose '$purpose' is not valid for an access token")
        }
        return parsed
    }

    /**
     * Parse a JWT and require [PURPOSE_MFA_CHALLENGE]. Used by MfaController
     * to validate the [issueMfaChallengeToken] handed to the FE.
     */
    fun parseMfaChallengeToken(token: String): ParsedJwt {
        val parsed = parseAny(token)
        if (parsed.purpose != PURPOSE_MFA_CHALLENGE) {
            throw InvalidJwtException("JWT is not an MFA challenge token")
        }
        return parsed
    }

    private fun parseAny(token: String): ParsedJwt {
        val claims = try {
            Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (ex: JwtException) {
            throw InvalidJwtException("Invalid JWT: ${ex.message}", ex)
        }
        val subject = claims.subject
            ?: throw InvalidJwtException("JWT missing subject")
        val userId = runCatching { UUID.fromString(subject) }.getOrElse {
            throw InvalidJwtException("JWT subject is not a valid UUID: $subject")
        }
        val email = claims.get("email", String::class.java)
            ?: throw InvalidJwtException("JWT missing email claim")
        val purpose = claims.get(PURPOSE_CLAIM, String::class.java)
        return ParsedJwt(userId = userId, email = email, claims = claims, purpose = purpose)
    }

    companion object {
        const val PURPOSE_CLAIM: String = "purpose"
        const val PURPOSE_ACCESS: String = "access"
        const val PURPOSE_MFA_CHALLENGE: String = "mfa_challenge"
    }
}

data class IssuedToken(
    val token: String,
    val issuedAt: Instant,
    val expiresAt: Instant,
    val expiresInSeconds: Long,
)

data class ParsedJwt(
    val userId: UUID,
    val email: String,
    val claims: Claims,
    val purpose: String? = null,
)

class InvalidJwtException(message: String, cause: Throwable? = null) :
    org.springframework.security.core.AuthenticationException(message, cause)
