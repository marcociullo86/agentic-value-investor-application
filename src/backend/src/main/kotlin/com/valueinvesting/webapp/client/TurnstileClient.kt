package com.valueinvesting.webapp.client

/**
 * Cloudflare Turnstile siteverify abstraction (US-081 / ADR-025 §5).
 *
 * Implementations call the Turnstile siteverify HTTPS endpoint with the
 * client-supplied token and remote IP; the function returns `true` when
 * Cloudflare confirms the challenge was solved by a human.
 *
 * Failure modes (network error, 5xx from Cloudflare, malformed response)
 * MUST return `false` — never throw — so the brute-force guard treats them
 * as "captcha not verified" and the caller can react accordingly.
 *
 * [^src: design_&_architecture/decisions/ADR-025-security-hardening-pci-dss.md §5 CAPTCHA]
 */
interface TurnstileClient {

    /**
     * @param token client-supplied Turnstile response token (cf-turnstile-response)
     * @param remoteIp the client IP the token was issued from; passed through to
     *   Cloudflare so they can correlate token issuance with verification
     * @return `true` when siteverify confirms the token, `false` on any other
     *   outcome (invalid token, network error, server disabled)
     */
    fun verify(token: String, remoteIp: String): Boolean
}
