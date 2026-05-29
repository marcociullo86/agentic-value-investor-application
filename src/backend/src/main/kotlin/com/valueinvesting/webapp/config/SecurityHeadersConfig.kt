package com.valueinvesting.webapp.config

import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity

/**
 * HTTP security headers (US-080, TSK-221).
 *
 * CSP is emitted as a `Content-Security-Policy` response header via Spring Security,
 * not as an HTML meta tag. [^src: design_&_architecture/decisions/ADR-025-security-hardening-pci-dss.md §2]
 *
 * ## CSP posture (TSK-221 finding iter-1)
 *
 * The default policy ([CONTENT_SECURITY_POLICY]) keeps `'unsafe-inline'` in
 * `script-src` because Spring serves the Next.js static export whose inline
 * bootstrap scripts cannot be noncified at build time (Next's middleware
 * nonce only runs under `next dev` / `next start`) — see kdoc on
 * [CONTENT_SECURITY_POLICY] and wiki/gaps.md §fe-middleware-static-export-conflict.
 *
 * Deployments behind a nonce-injecting edge middleware can opt into
 * [STRICT_CONTENT_SECURITY_POLICY] by setting
 * `app.security.csp.strict-script-src=true` (see
 * [AppProperties.Security.Csp]). The strict variant removes
 * `'unsafe-inline'` from `script-src`; everything else is identical.
 */
@Configuration
class SecurityHeadersConfig(
    private val appProperties: AppProperties,
) {

    /** Resolved policy honoring `app.security.csp.strict-script-src`. */
    fun activePolicy(): String =
        if (appProperties.security.csp.strictScriptSrc) STRICT_CONTENT_SECURITY_POLICY
        else CONTENT_SECURITY_POLICY

    fun configureHeaders(http: HttpSecurity): HttpSecurity =
        http.headers { headers ->
            headers.contentSecurityPolicy { csp ->
                csp.policyDirectives(activePolicy())
            }
        }

    companion object {
        /**
         * Cloudflare Turnstile origin (TSK-270, ADR-025 §5).
         *
         * The per-IP CAPTCHA widget (TSK-238) loads its bootstrap script,
         * mounts a challenge iframe, and pings telemetry from
         * `https://challenges.cloudflare.com`. Spring serves the Next.js
         * `output: 'export'` build in production (ADR-009), so the CSP
         * applied at runtime is the one emitted here — the FE Edge
         * middleware policy in `src/frontend/lib/security/csp.ts` only
         * runs under `next dev` and has no effect on the static export.
         * Without this allow-list, the widget is CSP-blocked and the
         * US-081 brute-force gate "CAPTCHA dopo soglia" is not executable
         * in production.
         *
         * Mirrored verbatim from
         * [`src/frontend/lib/security/csp.ts`](../../../../../../frontend/lib/security/csp.ts)
         * `TURNSTILE_ORIGIN` to keep FE/BE policies coherent.
         *
         * [^src: design_&_architecture/decisions/ADR-025-security-hardening-pci-dss.md §5]
         */
        const val TURNSTILE_ORIGIN: String = "https://challenges.cloudflare.com"

        /**
         * ADR-025 §2 — `style-src` allows `'unsafe-inline'` for Tailwind/Radix.
         *
         * `script-src` includes `'unsafe-inline'` as a deliberate trade-off:
         * Spring serves the Next.js static export whose inline bootstrap
         * scripts (RSC payload, chunk loaders) cannot be noncified at
         * build time. The Next middleware nonce system only runs in
         * `next dev` / `next start` (Node runtime), never when Spring
         * serves the static export.
         *
         * Long-term cleanup: a servlet/edge filter that rewrites served HTML
         * to inject a per-request nonce into every <script> tag — tracked
         * in wiki/gaps.md §fe-middleware-static-export-conflict. Until then
         * `'unsafe-inline'` here matches the security posture of every CDN-
         * served SPA. The XSS surface comes from API responses / DOM
         * injection, not from the trusted static HTML shell.
         *
         * TSK-270 / ADR-025 §5 — [TURNSTILE_ORIGIN] is allow-listed on
         * `script-src` (widget loader), `frame-src` (challenge iframe),
         * and `connect-src` (widget telemetry) so the per-IP CAPTCHA can
         * render and verify when Spring serves the static SPA build.
         * `frame-src` is otherwise `'none'`; Turnstile is the only
         * permitted framed origin.
         *
         * Opt-in strict variant: [STRICT_CONTENT_SECURITY_POLICY], gated by
         * [AppProperties.Security.Csp.strictScriptSrc].
         */
        const val CONTENT_SECURITY_POLICY: String =
            "default-src 'self'; " +
                "script-src 'self' 'unsafe-inline' $TURNSTILE_ORIGIN; " +
                "style-src 'self' 'unsafe-inline'; " +
                "img-src 'self' data: https:; " +
                "connect-src 'self' $TURNSTILE_ORIGIN; " +
                "font-src 'self'; " +
                "frame-src $TURNSTILE_ORIGIN; " +
                "object-src 'none'; " +
                "base-uri 'self'; " +
                "form-action 'self'"

        /**
         * Strict CSP variant — drops `'unsafe-inline'` from `script-src`.
         * Opt-in via `app.security.csp.strict-script-src=true`. Only safe
         * when an upstream component (edge worker, reverse proxy filter,
         * SSR runtime) injects per-request nonces or hashes for every
         * inline `<script>` tag emitted by the Next.js export. Activating
         * without that prerequisite will break the SPA bootstrap.
         *
         * TSK-270 — keeps the same Turnstile allow-list as
         * [CONTENT_SECURITY_POLICY]: the CAPTCHA gate is orthogonal to the
         * inline-script trade-off and must remain reachable under both
         * variants, otherwise toggling `strict-script-src` would silently
         * disable the brute-force gate.
         */
        const val STRICT_CONTENT_SECURITY_POLICY: String =
            "default-src 'self'; " +
                "script-src 'self' $TURNSTILE_ORIGIN; " +
                "style-src 'self' 'unsafe-inline'; " +
                "img-src 'self' data: https:; " +
                "connect-src 'self' $TURNSTILE_ORIGIN; " +
                "font-src 'self'; " +
                "frame-src $TURNSTILE_ORIGIN; " +
                "object-src 'none'; " +
                "base-uri 'self'; " +
                "form-action 'self'"
    }
}
