package com.valueinvesting.webapp.config

import org.springframework.boot.context.properties.ConfigurationProperties

// Typed configuration backed by `app.*` keys in application.yml.
// [^src: design_&_architecture/decisions/ADR-009-deployment-target.md §Variabili d'ambiente]
@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val cors: Cors = Cors(),
    val jwt: Jwt = Jwt(),
    val fmp: Fmp = Fmp(),
    val security: Security = Security(),
) {
    data class Cors(
        val allowedOrigins: String = "http://localhost:3000",
    )

    // JWT signing per RFC 7519/7515 — secret must be >= 256 bits in prod
    // [^src: design_&_architecture/decisions/ADR-006-authentication.md §Token]
    // [^src: design_&_architecture/decisions/ADR-010-auth-consolidation.md §3]
    data class Jwt(
        val signingSecret: String = "",
        val accessTtlMinutes: Long = 15,
        val cookieSecure: Boolean = true,
        // Sliding TTL del refresh: l'expires_at del nuovo refresh emesso a
        // ogni /refresh riuscito viene riportato a now() + questo valore.
        val refreshSlidingTtlDays: Long = 7,
        // Cap assoluto dal `first_issued_at` (login originale): oltre questo
        // valore /refresh ritorna 401 invalid-refresh, l'utente deve
        // ri-autenticarsi. Mitiga il rischio di refresh-token "eterno" in
        // caso di leak persistente.
        val refreshAbsoluteCapDays: Long = 30,
        // Legacy single-TTL kept for back-compat with config; non più letto
        // dall'AuthService dopo ADR-010 (usa refreshSlidingTtlDays).
        @Deprecated("Replaced by refreshSlidingTtlDays + refreshAbsoluteCapDays (ADR-010 §3)")
        val refreshTtlDays: Long = 30,
    )

    // FMP integration parameters [^src: design_&_architecture/decisions/ADR-004-fmp-integration.md]
    data class Fmp(
        val baseUrl: String = "https://financialmodelingprep.com/stable",
        val apiKey: String = "",
        val mock: Boolean = false,
    )

    // EP-018 / ADR-025 §5 — HIBP, rate limiting, MFA (subset wired per TSK).
    data class Security(
        val hibp: Hibp = Hibp(),
        val mfa: Mfa = Mfa(),
        val csp: Csp = Csp(),
    ) {
        data class Hibp(
            val enabled: Boolean = true,
            val apiUrl: String = "https://api.pwnedpasswords.com/range/",
        )

        // ADR-025 §4 — TOTP enrollment/challenge (TSK-227 TotpService).
        data class Mfa(
            val issuer: String = "ValueInvestor",
            val totpPeriodSeconds: Int = 30,
            val recoveryCodesCount: Int = 8,
            /** AES-256-GCM key material for `totp_secret_encrypted` (env MFA_ENCRYPTION_KEY in prod). */
            val encryptionKey: String = "",
        )

        /**
         * Content-Security-Policy posture controls (TSK-221 / ADR-025 §2,
         * findings iter-1).
         *
         * `strictScriptSrc=false` (default) keeps `'unsafe-inline'` in
         * `script-src` because Spring serves the Next.js `output: 'export'`
         * HTML whose inline bootstrap scripts cannot be noncified at build
         * time (the Next middleware nonce only runs in `next dev` /
         * `next start`).
         *
         * Set `strictScriptSrc=true` in deployments fronted by a nonce-
         * injecting middleware (e.g. an edge worker rewriting served HTML
         * to inject a per-request nonce into every <script> tag) — see
         * wiki/gaps.md §fe-middleware-static-export-conflict. Until that
         * gap is closed the production default remains `false`, matching
         * the security posture of every CDN-served static SPA.
         */
        data class Csp(
            val strictScriptSrc: Boolean = false,
        )
    }
}
