package com.valueinvesting.webapp.config

import org.springframework.boot.context.properties.ConfigurationProperties

// Auth endpoint rate limits (US-081 / ADR-025 §5).
// [^src: design_&_architecture/decisions/ADR-025-security-hardening-pci-dss.md §Configurazione]
@ConfigurationProperties(prefix = "app.security.rate-limiting")
data class RateLimitingProperties(
    val windowMinutes: Long = 5,
    val login: EndpointLimits = EndpointLimits(perIp = 10, perAccount = 5),
    val register: EndpointLimits = EndpointLimits(perIp = 5, perAccount = null),
    val passwordReset: EndpointLimits = EndpointLimits(perIp = 3, perAccount = 3),
) {
    data class EndpointLimits(
        val perIp: Int,
        val perAccount: Int? = null,
    )
}
