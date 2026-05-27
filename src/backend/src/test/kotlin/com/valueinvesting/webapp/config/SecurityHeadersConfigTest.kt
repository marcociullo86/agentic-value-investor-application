package com.valueinvesting.webapp.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * TSK-221 — CSP policy contract (US-080, ADR-025 §2).
 */
class SecurityHeadersConfigTest {

    @Test
    fun `script-src allows unsafe-inline for Spring-served Next export`() {
        // Trade-off documented in SecurityHeadersConfig kdoc: Spring serves the
        // Next.js static export whose inline bootstrap scripts cannot be
        // noncified (the Next middleware nonce only runs under `next dev`).
        // Tracked in wiki/gaps.md §fe-middleware-static-export-conflict.
        val policy = SecurityHeadersConfig.CONTENT_SECURITY_POLICY
        val scriptSrc = policy.split(";")
            .map { it.trim() }
            .first { it.startsWith("script-src") }

        assertThat(scriptSrc).isEqualTo("script-src 'self' 'unsafe-inline'")
    }

    @Test
    fun `style-src allows unsafe-inline for Tailwind`() {
        val policy = SecurityHeadersConfig.CONTENT_SECURITY_POLICY
        val styleSrc = policy.split(";")
            .map { it.trim() }
            .first { it.startsWith("style-src") }

        assertThat(styleSrc).isEqualTo("style-src 'self' 'unsafe-inline'")
    }

    @Test
    fun `policy includes required directives from ADR-025`() {
        val policy = SecurityHeadersConfig.CONTENT_SECURITY_POLICY
        assertThat(policy).contains("default-src 'self'")
        assertThat(policy).contains("img-src 'self' data: https:")
        assertThat(policy).contains("connect-src 'self'")
        assertThat(policy).contains("font-src 'self'")
        assertThat(policy).contains("frame-src 'none'")
        assertThat(policy).contains("object-src 'none'")
        assertThat(policy).contains("base-uri 'self'")
        assertThat(policy).contains("form-action 'self'")
    }

    // -- TSK-221 finding iter-1: opt-in strict-script-src ---------------------

    @Test
    fun `strict variant drops unsafe-inline from script-src only`() {
        val strict = SecurityHeadersConfig.STRICT_CONTENT_SECURITY_POLICY
        val scriptSrc = strict.split(";")
            .map { it.trim() }
            .first { it.startsWith("script-src") }

        assertThat(scriptSrc).isEqualTo("script-src 'self'")
        // style-src must keep unsafe-inline for Tailwind even in strict mode.
        val styleSrc = strict.split(";")
            .map { it.trim() }
            .first { it.startsWith("style-src") }
        assertThat(styleSrc).isEqualTo("style-src 'self' 'unsafe-inline'")
    }

    @Test
    fun `activePolicy returns default when strictScriptSrc is false`() {
        val config = SecurityHeadersConfig(
            AppProperties(
                security = AppProperties.Security(
                    csp = AppProperties.Security.Csp(strictScriptSrc = false),
                ),
            ),
        )
        assertThat(config.activePolicy()).isEqualTo(SecurityHeadersConfig.CONTENT_SECURITY_POLICY)
    }

    @Test
    fun `activePolicy returns strict variant when strictScriptSrc is true`() {
        val config = SecurityHeadersConfig(
            AppProperties(
                security = AppProperties.Security(
                    csp = AppProperties.Security.Csp(strictScriptSrc = true),
                ),
            ),
        )
        assertThat(config.activePolicy()).isEqualTo(SecurityHeadersConfig.STRICT_CONTENT_SECURITY_POLICY)
    }
}
