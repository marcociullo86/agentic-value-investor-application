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
        // TSK-270: also includes Cloudflare Turnstile origin (ADR-025 §5).
        val policy = SecurityHeadersConfig.CONTENT_SECURITY_POLICY
        val scriptSrc = policy.split(";")
            .map { it.trim() }
            .first { it.startsWith("script-src") }

        assertThat(scriptSrc).isEqualTo(
            "script-src 'self' 'unsafe-inline' https://challenges.cloudflare.com",
        )
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
        // TSK-270 (ADR-025 §5): connect-src and frame-src now allow the
        // Cloudflare Turnstile origin alongside 'self' (frame-src previously
        // 'none'). Other directives unchanged.
        val policy = SecurityHeadersConfig.CONTENT_SECURITY_POLICY
        assertThat(policy).contains("default-src 'self'")
        assertThat(policy).contains("img-src 'self' data: https:")
        assertThat(policy).contains("connect-src 'self' https://challenges.cloudflare.com")
        assertThat(policy).contains("font-src 'self'")
        assertThat(policy).contains("frame-src https://challenges.cloudflare.com")
        assertThat(policy).contains("object-src 'none'")
        assertThat(policy).contains("base-uri 'self'")
        assertThat(policy).contains("form-action 'self'")
    }

    // -- TSK-221 finding iter-1: opt-in strict-script-src ---------------------

    @Test
    fun `strict variant drops unsafe-inline from script-src only`() {
        // TSK-270: strict variant still allows the Turnstile origin on
        // script-src — the CAPTCHA gate must remain functional when
        // strict-script-src=true (otherwise toggling the flag would
        // silently disable US-081 brute-force protection).
        val strict = SecurityHeadersConfig.STRICT_CONTENT_SECURITY_POLICY
        val scriptSrc = strict.split(";")
            .map { it.trim() }
            .first { it.startsWith("script-src") }

        assertThat(scriptSrc).isEqualTo("script-src 'self' https://challenges.cloudflare.com")
        // style-src must keep unsafe-inline for Tailwind even in strict mode.
        val styleSrc = strict.split(";")
            .map { it.trim() }
            .first { it.startsWith("style-src") }
        assertThat(styleSrc).isEqualTo("style-src 'self' 'unsafe-inline'")
    }

    // -- TSK-270: Cloudflare Turnstile allow-list (ADR-025 §5) ----------------

    @Test
    fun `TURNSTILE_ORIGIN constant matches Cloudflare host used by FE csp ts`() {
        // Coordinated verbatim with src/frontend/lib/security/csp.ts
        // TURNSTILE_ORIGIN. Drift here would let the BE block the widget
        // even if the FE policy allowed it (or vice versa).
        assertThat(SecurityHeadersConfig.TURNSTILE_ORIGIN)
            .isEqualTo("https://challenges.cloudflare.com")
    }

    @Test
    fun `default policy allows Turnstile origin on script-src frame-src connect-src`() {
        val policy = SecurityHeadersConfig.CONTENT_SECURITY_POLICY
        val origin = SecurityHeadersConfig.TURNSTILE_ORIGIN
        val directives = policy.split(";").map { it.trim() }

        val scriptSrc = directives.first { it.startsWith("script-src") }
        val frameSrc = directives.first { it.startsWith("frame-src") }
        val connectSrc = directives.first { it.startsWith("connect-src") }

        assertThat(scriptSrc).contains(origin)
        assertThat(frameSrc).contains(origin)
        assertThat(connectSrc).contains(origin)
    }

    @Test
    fun `strict policy allows Turnstile origin on script-src frame-src connect-src`() {
        val policy = SecurityHeadersConfig.STRICT_CONTENT_SECURITY_POLICY
        val origin = SecurityHeadersConfig.TURNSTILE_ORIGIN
        val directives = policy.split(";").map { it.trim() }

        val scriptSrc = directives.first { it.startsWith("script-src") }
        val frameSrc = directives.first { it.startsWith("frame-src") }
        val connectSrc = directives.first { it.startsWith("connect-src") }

        assertThat(scriptSrc).contains(origin)
        assertThat(frameSrc).contains(origin)
        assertThat(connectSrc).contains(origin)
    }

    @Test
    fun `default policy frame-src replaces 'none' with Turnstile origin only`() {
        // frame-src is otherwise 'none'; Turnstile is the single permitted
        // framed origin. Guards against accidentally regressing to 'none' OR
        // widening to other origins.
        val policy = SecurityHeadersConfig.CONTENT_SECURITY_POLICY
        val frameSrc = policy.split(";")
            .map { it.trim() }
            .first { it.startsWith("frame-src") }

        assertThat(frameSrc).isEqualTo("frame-src https://challenges.cloudflare.com")
    }

    @Test
    fun `strict policy frame-src replaces 'none' with Turnstile origin only`() {
        val policy = SecurityHeadersConfig.STRICT_CONTENT_SECURITY_POLICY
        val frameSrc = policy.split(";")
            .map { it.trim() }
            .first { it.startsWith("frame-src") }

        assertThat(frameSrc).isEqualTo("frame-src https://challenges.cloudflare.com")
    }

    @Test
    fun `default policy connect-src keeps 'self' alongside Turnstile origin`() {
        val policy = SecurityHeadersConfig.CONTENT_SECURITY_POLICY
        val connectSrc = policy.split(";")
            .map { it.trim() }
            .first { it.startsWith("connect-src") }

        assertThat(connectSrc).isEqualTo("connect-src 'self' https://challenges.cloudflare.com")
    }

    @Test
    fun `strict policy connect-src keeps 'self' alongside Turnstile origin`() {
        val policy = SecurityHeadersConfig.STRICT_CONTENT_SECURITY_POLICY
        val connectSrc = policy.split(";")
            .map { it.trim() }
            .first { it.startsWith("connect-src") }

        assertThat(connectSrc).isEqualTo("connect-src 'self' https://challenges.cloudflare.com")
    }

    @Test
    fun `unrelated directives are untouched by Turnstile allow-list`() {
        // Guard against accidental widening: only script-src, frame-src,
        // connect-src may mention the Turnstile origin.
        val defaultPolicy = SecurityHeadersConfig.CONTENT_SECURITY_POLICY
        val strictPolicy = SecurityHeadersConfig.STRICT_CONTENT_SECURITY_POLICY
        val origin = SecurityHeadersConfig.TURNSTILE_ORIGIN

        listOf(defaultPolicy, strictPolicy).forEach { policy ->
            val directives = policy.split(";").map { it.trim() }
            val unaffectedPrefixes = listOf(
                "default-src",
                "style-src",
                "img-src",
                "font-src",
                "object-src",
                "base-uri",
                "form-action",
            )
            unaffectedPrefixes.forEach { prefix ->
                val directive = directives.first { it.startsWith(prefix) }
                assertThat(directive)
                    .`as`("$prefix must not mention Turnstile origin in policy <$policy>")
                    .doesNotContain(origin)
            }
        }
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
