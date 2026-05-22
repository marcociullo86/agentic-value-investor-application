package com.valueinvesting.webapp.security

import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/**
 * Stateless JWT-backed security chain (TSK-033) — supersedes the temporary
 * permissive config formerly in `config/SecurityConfig.kt`.
 *
 * Endpoint policy mirrors ADR-006 §Endpoint policy.
 *
 *  - permitAll: api auth, search, screener, financials, analysis, historical,
 *    actuator health, openapi.json, springdoc.
 *  - authenticated: api watchlist, moat-checklist, dcf-overrides.
 *
 * See design_&_architecture/decisions/ADR-006-authentication.md §Endpoint policy.
 * See design_&_architecture/components/backend-components.md §SecurityConfig.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val userDetailsService: UserDetailsServiceImpl,
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
    @Bean
    fun authenticationEntryPoint(): AuthenticationEntryPoint =
        AuthenticationEntryPoint { _, response, _ ->
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
            response.writer.write(
                "{\"type\":\"https://api/errors/unauthorized\"," +
                    "\"title\":\"Unauthorized\"," +
                    "\"status\":401," +
                    "\"detail\":\"Authentication required or invalid\"}",
            )
        }

    @Bean
    fun accessDeniedHandler(): AccessDeniedHandler =
        AccessDeniedHandler { _, response, _ ->
            response.status = HttpServletResponse.SC_FORBIDDEN
            response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
            response.writer.write(
                "{\"type\":\"https://api/errors/forbidden\"," +
                    "\"title\":\"Forbidden\"," +
                    "\"status\":403," +
                    "\"detail\":\"Access denied\"}",
            )
        }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtAuthenticationFilter: JwtAuthenticationFilter,
        authenticationEntryPoint: AuthenticationEntryPoint,
        accessDeniedHandler: AccessDeniedHandler,
    ): SecurityFilterChain =
        http
            // CSRF disabled because the API is stateless JWT-based (ADR-006).
            .csrf { it.disable() }
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
                        "/*.svg",
                        "/*.png",
                        "/*.jpg",
                        "/*.ico",
                        "/*.txt",
                        "/*.json",
                        "/*.webmanifest",
                    ).permitAll()
                    .requestMatchers(
                        "/api/auth/register",
                        "/api/auth/login",
                        "/api/auth/refresh",
                    ).permitAll()
                    .requestMatchers(
                        "/api/search/**",
                        "/api/screener",
                        "/api/screener/**",
                        "/api/financials/**",
                        "/api/analysis/**",
                        "/api/historical/**",
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
                    ).authenticated()
                    .anyRequest().authenticated()
            }
            .addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter::class.java,
            )
            .build()

    companion object {
        // ADR-006 §Token — BCrypt cost 12 (~250ms hash on commodity hardware).
        const val BCRYPT_STRENGTH: Int = 12
    }
}
