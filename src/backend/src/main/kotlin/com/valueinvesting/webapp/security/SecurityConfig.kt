package com.valueinvesting.webapp.security

import com.valueinvesting.webapp.api.error.ProblemDetailsMapper
import com.valueinvesting.webapp.config.CsrfTokenConfig
import com.fasterxml.jackson.databind.ObjectMapper
import com.valueinvesting.webapp.config.FlatteningProblemDetailHttpMessageConverter
import com.valueinvesting.webapp.config.SecurityHeadersConfig
import com.valueinvesting.webapp.security.filter.RateLimitingFilter
import com.valueinvesting.webapp.service.AuthRateLimitService
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.server.ServletServerHttpResponse
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler
import org.springframework.security.web.csrf.CsrfTokenRepository

/**
 * Stateless JWT-backed security chain (TSK-033) — supersedes the temporary
 * permissive config formerly in `config/SecurityConfig.kt`.
 *
 * Endpoint policy mirrors ADR-006 §Endpoint policy.
 *
 *  - permitAll: api auth, search, screener, financials, analysis, historical,
 *    actuator health, openapi.json, springdoc.
 *  - authenticated: api watchlist, moat-checklist, dcf-overrides.
 *  - hasRole(ADMIN): `/admin/` subtree (US-079 / ADR-025 §1; @PreAuthorize on controllers).
 *  - CSRF: cookie `XSRF-TOKEN` + header `X-CSRF-Token` on POST
 *    `/api/auth/refresh` and `/api/auth/logout` only (TSK-223 / ADR-025 §3).
 *
 * Method security: @EnableMethodSecurity activates @PreAuthorize enforcement.
 *
 * See design_&_architecture/decisions/ADR-006-authentication.md §Endpoint policy.
 * See design_&_architecture/decisions/ADR-025-security-hardening-pci-dss.md §1.
 * See design_&_architecture/components/backend-components.md §SecurityConfig.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val userDetailsService: UserDetailsServiceImpl,
    private val securityHeadersConfig: SecurityHeadersConfig,
    private val csrfTokenConfig: CsrfTokenConfig,
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(BCRYPT_STRENGTH)

    // Filter is registered ONLY inside the SecurityFilterChain via
    // `.addFilterBefore(...)` — never as a top-level servlet filter.
    //
    // Spring Boot auto-wraps any Filter-typed bean in a FilterRegistrationBean
    // and adds it to the servlet context. That would make the filter run on
    // every request, bypassing `@AutoConfigureMockMvc(addFilters = false)` in
    // test slices. The explicit FilterRegistrationBean with isEnabled = false
    // suppresses the auto-registration; the SecurityFilterChain still picks
    // up the bean and uses it inside its chain.
    @Bean
    fun jwtAuthenticationFilter(jwtService: JwtService): JwtAuthenticationFilter =
        JwtAuthenticationFilter(jwtService)

    @Bean
    fun jwtAuthenticationFilterRegistration(
        filter: JwtAuthenticationFilter,
    ): FilterRegistrationBean<JwtAuthenticationFilter> {
        val registration = FilterRegistrationBean(filter)
        registration.isEnabled = false
        return registration
    }

    @Bean
    fun rateLimitingFilter(
        authRateLimitService: AuthRateLimitService,
        objectMapper: ObjectMapper,
    ): RateLimitingFilter = RateLimitingFilter(authRateLimitService, objectMapper)

    @Bean
    fun rateLimitingFilterRegistration(
        filter: RateLimitingFilter,
    ): FilterRegistrationBean<RateLimitingFilter> {
        val registration = FilterRegistrationBean(filter)
        registration.isEnabled = false
        return registration
    }

    @Bean
    fun authenticationProvider(passwordEncoder: PasswordEncoder): DaoAuthenticationProvider {
        val provider = DaoAuthenticationProvider()
        provider.setUserDetailsService(userDetailsService)
        provider.setPasswordEncoder(passwordEncoder)
        return provider
    }

    @Bean
    fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager =
        config.authenticationManager

    // Returns 401 (not the Spring Security default 403) for unauthenticated
    // requests against protected endpoints — REST-friendly per ADR-006.
    //
    // The body is built via [ProblemDetailsMapper] and serialized via
    // [FlatteningProblemDetailHttpMessageConverter] so the payload is
    // byte-identical to the ones produced by [GlobalExceptionHandler] for
    // controller-level AuthenticationException: same RFC 9457 shape, same
    // top-level extension flattening (ADR-012), same timestamp / requestId /
    // correlationId carried from MDC (TSK-033 finding iter-1).
    @Bean
    fun authenticationEntryPoint(
        problemDetailsMapper: ProblemDetailsMapper,
        problemDetailWriter: FlatteningProblemDetailHttpMessageConverter,
    ): AuthenticationEntryPoint =
        AuthenticationEntryPoint { request, response, _ ->
            val problem = problemDetailsMapper.build(
                status = HttpStatus.UNAUTHORIZED,
                type = "https://api/errors/unauthorized",
                title = "Unauthorized",
                detail = "Authentication required or invalid",
                request = request,
            )
            response.status = HttpStatus.UNAUTHORIZED.value()
            problemDetailWriter.write(
                problem,
                MediaType.APPLICATION_PROBLEM_JSON,
                ServletServerHttpResponse(response),
            )
        }

    @Bean
    fun accessDeniedHandler(
        problemDetailsMapper: ProblemDetailsMapper,
        problemDetailWriter: FlatteningProblemDetailHttpMessageConverter,
    ): AccessDeniedHandler =
        AccessDeniedHandler { request, response, _ ->
            val problem = problemDetailsMapper.build(
                status = HttpStatus.FORBIDDEN,
                type = "https://api/errors/forbidden",
                title = "Forbidden",
                detail = "Access denied",
                request = request,
            )
            response.status = HttpStatus.FORBIDDEN.value()
            problemDetailWriter.write(
                problem,
                MediaType.APPLICATION_PROBLEM_JSON,
                ServletServerHttpResponse(response),
            )
        }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        rateLimitingFilter: RateLimitingFilter,
        jwtAuthenticationFilter: JwtAuthenticationFilter,
        authenticationEntryPoint: AuthenticationEntryPoint,
        accessDeniedHandler: AccessDeniedHandler,
        csrfTokenRepository: CsrfTokenRepository,
        csrfTokenRequestHandler: CsrfTokenRequestAttributeHandler,
    ): SecurityFilterChain {
        var chain = securityHeadersConfig.configureHeaders(http)
        chain = csrfTokenConfig.configureCsrf(chain, csrfTokenRepository, csrfTokenRequestHandler)
        return chain
            // CORS for browser clients is handled by CorsConfig (WebMvcConfigurer).
            // Preflight OPTIONS is permitted below so it reaches the MVC handler.
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling {
                it.authenticationEntryPoint(authenticationEntryPoint)
                it.accessDeniedHandler(accessDeniedHandler)
            }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    // SPA static assets bundled in the BE container (ADR-009 §2).
                    // Next.js export produces /index.html, /_next/**, and a folder
                    // per route (trailingSlash: true). These paths must reach the
                    // static-resource handler without auth; protected APIs live
                    // exclusively under /api/.
                    .requestMatchers(
                        HttpMethod.GET,
                        "/",
                        "/index.html",
                        "/favicon.ico",
                        "/_next/**",
                        "/login/**",
                        "/register/**",
                        "/watchlist/**",
                        "/screener/**",
                        "/moat/**",
                        "/analysis/**",
                        "/top-picks/**",
                        // Root-level static assets emitted by Next.js export
                        // (e.g. /theme-init.js, /globals.css, hashed chunks).
                        "/*.js",
                        "/*.css",
                        "/*.map",
                        "/*.svg",
                        "/*.png",
                        "/*.jpg",
                        "/*.ico",
                        "/*.txt",
                        "/*.json",
                        "/*.webmanifest",
                        "/*.woff",
                        "/*.woff2",
                        "/*.ttf",
                        "/*.eot",
                    ).permitAll()
                    .requestMatchers(
                        "/api/auth/register",
                        "/api/auth/login",
                        "/api/auth/refresh",
                        // MFA challenge / recovery during login: the user has
                        // proven password but not yet the second factor; the
                        // mfaToken (validated in-controller) carries identity,
                        // so we cannot require Bearer here (TSK-228 / ADR-025 §4).
                        "/api/auth/mfa/challenge",
                        "/api/auth/mfa/recovery",
                    ).permitAll()
                    .requestMatchers(
                        "/api/search/**",
                        "/api/screener",
                        "/api/screener/**",
                        "/api/financials/**",
                        "/api/analysis/**",
                        "/api/historical/**",
                        "/api/top-picks",
                        "/api/top-picks/**",
                    ).permitAll()
                    .requestMatchers(
                        "/actuator/health",
                        "/actuator/health/**",
                        "/actuator/info",
                        "/api/openapi.json",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                    ).permitAll()
                    .requestMatchers(
                        "/api/watchlist/**",
                        "/api/moat-checklist/**",
                        "/api/dcf-overrides/**",
                        "/api/auth/logout",
                        // Bearer-protected MFA management endpoints (post-login):
                        //   POST   /api/auth/mfa/enroll  — start enrollment
                        //   POST   /api/auth/mfa/verify  — activate MFA
                        //   DELETE /api/auth/mfa         — disable MFA (+ password)
                        // (TSK-228 / ADR-025 §4)
                        "/api/auth/mfa/enroll",
                        "/api/auth/mfa/verify",
                        "/api/auth/mfa",
                    ).authenticated()
                    .requestMatchers("/admin/**").hasRole("ADMIN")
                    .anyRequest().authenticated()
            }
            .addFilterBefore(
                rateLimitingFilter,
                UsernamePasswordAuthenticationFilter::class.java,
            )
            .addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter::class.java,
            )
            .build()
    }

    companion object {
        // ADR-006 §Token — BCrypt cost 12 (~250ms hash on commodity hardware).
        const val BCRYPT_STRENGTH: Int = 12
    }
}
