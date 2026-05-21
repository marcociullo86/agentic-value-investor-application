package com.valueinvesting.webapp.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// OpenAPI 3.1 metadata + Bearer JWT security scheme.
// [^src: design_&_architecture/decisions/ADR-007-api-contract.md]
// [^src: raw/tech_stack.md §Standards verbatim — OpenAPI 3.1, JWT RFC 7519/7515]
@Configuration
class OpenApiConfig {

    @Bean
    fun customOpenAPI(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Value Investing WebApp API")
                .description("REST API for value investing analysis — see ADR-007 for conventions.")
                .version("0.1.0")
                .license(License().name("Proprietary"))
        )
        .components(
            Components().addSecuritySchemes(
                "bearerAuth",
                SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
            )
        )
        .addSecurityItem(SecurityRequirement().addList("bearerAuth"))
}
