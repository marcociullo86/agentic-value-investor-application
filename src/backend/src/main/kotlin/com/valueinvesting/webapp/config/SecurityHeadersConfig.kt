package com.valueinvesting.webapp.config

import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity

/**
 * HTTP security headers (US-080, TSK-221).
 *
 * CSP is emitted as a `Content-Security-Policy` response header via Spring Security,
 * not as an HTML meta tag. [^src: design_&_architecture/decisions/ADR-025-security-hardening-pci-dss.md §2]
 */
@Configuration
class SecurityHeadersConfig {

    fun configureHeaders(http: HttpSecurity): HttpSecurity =
        http.headers { headers ->
            headers.contentSecurityPolicy { csp ->
                csp.policyDirectives(CONTENT_SECURITY_POLICY)
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
         * Long-term cleanup: a servlet filter that rewrites served HTML to
         * inject a per-request nonce into every <script> tag — tracked in
         * wiki/gaps.md §fe-middleware-static-export-conflict. Until then
         * `'unsafe-inline'` here matches the security posture of every CDN-
         * served SPA. The XSS surface comes from API responses / DOM
         * injection, not from the trusted static HTML shell.
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
    }
}
