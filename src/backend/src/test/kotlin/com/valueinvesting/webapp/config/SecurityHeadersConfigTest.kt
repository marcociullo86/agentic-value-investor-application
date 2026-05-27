package com.valueinvesting.webapp.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * TSK-221 — CSP policy contract (US-080, ADR-025 §2).
 */
class SecurityHeadersConfigTest {

    @Test
    fun `script-src does not allow unsafe-inline`() {
        val policy = SecurityHeadersConfig.CONTENT_SECURITY_POLICY
        val scriptSrc = policy.split(";")
            .map { it.trim() }
            .first { it.startsWith("script-src") }

        assertThat(scriptSrc).isEqualTo("script-src 'self'")
        assertThat(scriptSrc).doesNotContain("unsafe-inline")
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
}
