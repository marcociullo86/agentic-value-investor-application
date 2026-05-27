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
         * ADR-025 §2 — `style-src` allows `'unsafe-inline'` for Tailwind/Radix.
         *
         * `script-src` includes `'unsafe-inline'` as a deliberate trade-off:
         * Spring serves the Next.js `output: 'export'` HTML which contains
         * inline bootstrap scripts (RSC payload, chunk loaders) baked at
         * build time without per-request nonces. The Next middleware nonce
         * system only runs in `next dev` / `next start` (Node runtime),
         * never when Spring serves the static export.
         *
         * Long-term cleanup: a servlet/edge filter that rewrites served HTML
         * to inject a per-request nonce into every <script> tag — tracked
         * in wiki/gaps.md §fe-middleware-static-export-conflict. Until then
         * `'unsafe-inline'` here matches the security posture of every CDN-
         * served SPA. The XSS surface comes from API responses / DOM
         * injection, not from the trusted static HTML shell.
         *
         * Opt-in strict variant: [STRICT_CONTENT_SECURITY_POLICY], gated by
         * [AppProperties.Security.Csp.strictScriptSrc].
         */
        const val CONTENT_SECURITY_POLICY: String =
            "default-src 'self'; " +
                "script-src 'self' 'unsafe-inline'; " +
                "style-src 'self' 'unsafe-inline'; " +
                "img-src 'self' data: https:; " +
                "connect-src 'self'; " +
                "font-src 'self'; " +
                "frame-src 'none'; " +
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
         */
        const val STRICT_CONTENT_SECURITY_POLICY: String =
            "default-src 'self'; " +
                "script-src 'self'; " +
                "style-src 'self' 'unsafe-inline'; " +
                "img-src 'self' data: https:; " +
                "connect-src 'self'; " +
                "font-src 'self'; " +
                "frame-src 'none'; " +
                "object-src 'none'; " +
                "base-uri 'self'; " +
                "form-action 'self'"
    }
}
