package com.valueinvesting.webapp.config

import org.springframework.boot.context.properties.ConfigurationProperties

// Cloudflare Turnstile CAPTCHA siteverify configuration (US-081 / ADR-025 §5,
// TSK-230). `secretKey` blank disables server-side verification (dev/test);
// in that mode any non-blank token submitted by the client is treated as
// valid so the brute-force flow remains testable without contacting Cloudflare.
// [^src: design_&_architecture/decisions/ADR-025-security-hardening-pci-dss.md §5 CAPTCHA]
@ConfigurationProperties(prefix = "app.security.turnstile")
data class TurnstileProperties(
    val secretKey: String = "",
    val siteVerifyUrl: String = "https://challenges.cloudflare.com/turnstile/v0/siteverify",
    val timeoutSeconds: Long = 5,
)
