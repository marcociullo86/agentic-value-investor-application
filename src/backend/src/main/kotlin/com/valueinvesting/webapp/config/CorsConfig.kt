package com.valueinvesting.webapp.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

// CORS configuration driven by app.cors.allowed-origins env var.
// [^src: design_&_architecture/decisions/ADR-009-deployment-target.md §Variabili d'ambiente]
@Configuration
class CorsConfig(
    private val appProperties: AppProperties,
) {

    @Bean
    fun corsConfigurer(): WebMvcConfigurer = object : WebMvcConfigurer {
        override fun addCorsMappings(registry: CorsRegistry) {
            val origins = appProperties.cors.allowedOrigins
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toTypedArray()

            registry.addMapping("/api/**")
                .allowedOrigins(*origins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Request-Id", "X-Data-Snapshot-At", "X-Data-Stale")
                .allowCredentials(true)
                .maxAge(3600)
        }
    }
}
