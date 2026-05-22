package com.valueinvesting.webapp.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/**
 * Stateless JWT-backed security chain (TSK-033) — supersedes the temporary
 * permissive config formerly in `config/SecurityConfig.kt`.
 *
 * Endpoint policy mirrors ADR-006 §Endpoint policy:
 *  - `permitAll`: `/api/auth/*`, `/api/search/*`, `/api/screener`,
 *    `/api/financials/*`, `/api/analysis/*`, `/api/historical/*`,
 *    `/actuator/health`, `/api/openapi.json`, springdoc.
 *  - `authenticated`: `/api/watchlist/*`, `/api/moat-checklist/*`,
 *    `/api/dcf-overrides/*`.
 *
 * [^src: design_&_architecture/decisions/ADR-006-authentication.md §Endpoint policy]
 * [^src: design_&_architecture/components/backend-components.md §SecurityConfig]
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val userDetailsService: UserDetailsServiceImpl,
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(BCRYPT_STRENGTH)

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

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            // CSRF disabled because the API is stateless JWT-based (ADR-006).
            .csrf { it.disable() }
            // CORS for browser clients is handled by CorsConfig (WebMvcConfigurer).
            // Preflight OPTIONS is permitted below so it reaches the MVC handler.
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
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
