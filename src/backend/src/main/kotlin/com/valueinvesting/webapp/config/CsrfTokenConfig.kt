package com.valueinvesting.webapp.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler
import org.springframework.security.web.csrf.CsrfTokenRepository
import org.springframework.security.web.util.matcher.AntPathRequestMatcher
import org.springframework.security.web.util.matcher.OrRequestMatcher
import org.springframework.security.web.util.matcher.RequestMatcher

/**
 * Cookie-based CSRF for refresh/logout only (TSK-223, US-080, ADR-025 §3).
 *
 * Defense-in-depth alongside `SameSite=Strict` on all auth cookies (ADR-024).
 * Bearer-authenticated API routes are excluded — custom headers are not sent
 * cross-origin by browsers.
 *
 * [^src: design_&_architecture/decisions/ADR-025-security-hardening-pci-dss.md §3]
 */
@Configuration
class CsrfTokenConfig(
    private val appProperties: AppProperties,
) {

    @Bean
    fun csrfTokenRepository(): CookieCsrfTokenRepository =
        CookieCsrfTokenRepository.withHttpOnlyFalse().apply {
            cookieName = CSRF_COOKIE_NAME
            cookiePath = "/"
            setCookieCustomizer { cookie ->
                cookie.sameSite("Strict")
                if (appProperties.jwt.cookieSecure) {
                    cookie.secure(true)
                }
            }
        }

    @Bean
    fun csrfTokenRequestHandler(): CsrfTokenRequestAttributeHandler =
        CsrfTokenRequestAttributeHandler().apply {
            setHeaderName(CSRF_HEADER_NAME)
        }

    fun configureCsrf(
        http: HttpSecurity,
        csrfTokenRepository: CsrfTokenRepository,
        csrfTokenRequestHandler: CsrfTokenRequestAttributeHandler,
    ): HttpSecurity =
        http.csrf { csrf ->
            csrf
                .csrfTokenRepository(csrfTokenRepository)
                .csrfTokenRequestHandler(csrfTokenRequestHandler)
                .requireCsrfProtectionMatcher(CSRF_PROTECTED_MATCHER)
        }

    companion object {
        const val CSRF_COOKIE_NAME: String = "XSRF-TOKEN"
        const val CSRF_HEADER_NAME: String = "X-CSRF-Token"

        val CSRF_PROTECTED_MATCHER: RequestMatcher =
            OrRequestMatcher(
                AntPathRequestMatcher("/api/auth/refresh", HttpMethod.POST.name()),
                AntPathRequestMatcher("/api/auth/logout", HttpMethod.POST.name()),
            )
    }
}
