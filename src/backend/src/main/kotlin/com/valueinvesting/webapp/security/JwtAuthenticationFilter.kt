package com.valueinvesting.webapp.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Per-request JWT validator. Populates Spring SecurityContext with
 * `UserPrincipal` when a valid `Authorization: Bearer <jwt>` is present.
 *
 * Invalid / expired tokens are intentionally not thrown here — the filter
 * leaves the context empty and lets the SecurityFilterChain decide whether
 * the requested endpoint requires authentication. Endpoints marked permitAll
 * still serve anonymously even when accompanied by a broken token.
 *
 * Intentionally NOT a @Component: Spring Boot would auto-register a Filter
 * bean as a servlet-context filter that runs on every request, bypassing
 * `@AutoConfigureMockMvc(addFilters = false)` in test slices that don't
 * want security wired (this is exactly what broke AnalysisControllerIT in
 * Sprint 3). The bean is instantiated as a @Bean inside SecurityConfig and
 * only attached to the SecurityFilterChain via `.addFilterBefore(...)`.
 *
 * See design_&_architecture/components/backend-components.md §JwtAuthenticationFilter.
 * See design_&_architecture/decisions/ADR-006-authentication.md §Architettura.
 */
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            val token = header.substring(BEARER_PREFIX.length).trim()
            runCatching { jwtService.parse(token) }
                .onSuccess { parsed ->
                    val principal = UserPrincipal(
                        userId = parsed.userId,
                        emailValue = parsed.email,
                    )
                    val auth = UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.authorities,
                    )
                    auth.details = WebAuthenticationDetailsSource().buildDetails(request)
                    SecurityContextHolder.getContext().authentication = auth
                }
                .onFailure { ex -> log.debug("Rejecting bearer token: {}", ex.message) }
        }
        filterChain.doFilter(request, response)
    }

    companion object {
        const val BEARER_PREFIX = "Bearer "
    }
}
