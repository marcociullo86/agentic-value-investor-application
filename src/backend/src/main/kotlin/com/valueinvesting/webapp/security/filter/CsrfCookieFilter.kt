package com.valueinvesting.webapp.security.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpMethod
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Materializes the deferred [CsrfToken] so the `XSRF-TOKEN` cookie is actually
 * written to the browser (US-080 follow-up — refresh/logout F5 logout bug).
 *
 * Background: with [org.springframework.security.web.csrf.CookieCsrfTokenRepository]
 * + [org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler]
 * (Spring Security 6) the token is *deferred* — the cookie is emitted only when
 * `CsrfToken.getToken()` is accessed. On a stateless JWT API nothing reads it,
 * so the `XSRF-TOKEN` cookie was never set and the FE had nothing to echo as
 * `X-CSRF-Token`. The result was a 403 on `POST /api/auth/refresh`, which on an
 * F5 (in-memory access token lost → silent refresh) logged the user out.
 *
 * This filter forces materialization. It runs right after
 * [org.springframework.security.web.csrf.CsrfFilter] (which has already set the
 * deferred-token request attribute on every request), so:
 *
 *  - **safe methods (GET/HEAD)** — materialize *before* the handler runs, so
 *    large/committed responses (e.g. the static SPA `index.html`) still carry
 *    the `Set-Cookie` header. This seeds the cookie on the very first page load.
 *  - **state-changing methods** — materialize *after* the handler, so the
 *    `XSRF-TOKEN` cookie is appended *after* any endpoint-set cookie (e.g. the
 *    `refresh_token` cookie on login/refresh/challenge). This preserves the
 *    `Set-Cookie` header ordering relied upon by existing IT assertions and
 *    guarantees the cookie is present right after a successful login (covers
 *    the cross-origin `next dev` case where the page itself is not served by
 *    Spring).
 *
 * The [CookieCsrfTokenRepository] only writes a `Set-Cookie` when the token is
 * absent from the request, so repeated safe navigations do not rotate it.
 *
 * [^src: design_&_architecture/decisions/ADR-025-security-hardening-pci-dss.md §3]
 */
class CsrfCookieFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val csrfToken = request.getAttribute(CsrfToken::class.java.name) as? CsrfToken
        val safeMethod =
            HttpMethod.GET.matches(request.method) || HttpMethod.HEAD.matches(request.method)

        if (safeMethod) {
            // Render the cookie before the (possibly committed) response body.
            csrfToken?.token
            filterChain.doFilter(request, response)
        } else {
            filterChain.doFilter(request, response)
            // Append XSRF-TOKEN after any cookie set by the handler.
            csrfToken?.token
        }
    }
}
