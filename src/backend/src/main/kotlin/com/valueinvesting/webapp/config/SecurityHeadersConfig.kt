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
         * ADR-025 §2 — `script-src` without `'unsafe-inline'`; `style-src` allows
         * `'unsafe-inline'` for Tailwind/Radix utility styles.
         */
        const val CONTENT_SECURITY_POLICY: String =
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
