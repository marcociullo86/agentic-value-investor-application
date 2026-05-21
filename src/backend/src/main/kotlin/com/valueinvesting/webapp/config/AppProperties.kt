package com.valueinvesting.webapp.config

import org.springframework.boot.context.properties.ConfigurationProperties

// Typed configuration backed by `app.*` keys in application.yml.
// [^src: design_&_architecture/decisions/ADR-009-deployment-target.md §Variabili d'ambiente]
@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val cors: Cors = Cors(),
    val jwt: Jwt = Jwt(),
    val fmp: Fmp = Fmp(),
) {
    data class Cors(
        val allowedOrigins: String = "http://localhost:3000",
    )

    // JWT signing per RFC 7519/7515 — secret must be >= 256 bits in prod
    // [^src: design_&_architecture/decisions/ADR-006-authentication.md §Token]
    data class Jwt(
        val signingSecret: String = "",
        val accessTtlMinutes: Long = 15,
        val refreshTtlDays: Long = 30,
    )

    // FMP integration parameters [^src: design_&_architecture/decisions/ADR-004-fmp-integration.md]
    data class Fmp(
        val baseUrl: String = "https://financialmodelingprep.com/api/v3",
        val apiKey: String = "",
        val mock: Boolean = false,
    )
}
