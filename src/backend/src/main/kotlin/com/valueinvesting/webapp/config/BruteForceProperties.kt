package com.valueinvesting.webapp.config

import org.springframework.boot.context.properties.ConfigurationProperties

// Brute-force protection thresholds (US-081 / ADR-025 §5, TSK-230).
// All windows expressed in minutes; thresholds count `bad_credentials` rows in
// the `login_attempts` table — rate-limit probe rows (TSK-229) use a different
// `failure_reason` and are not conflated.
// [^src: design_&_architecture/decisions/ADR-025-security-hardening-pci-dss.md §5]
@ConfigurationProperties(prefix = "app.security.brute-force")
data class BruteForceProperties(
    val failureWindowMinutes: Long = 5,
    val progressiveDelayThreshold: Long = 5,
    val progressiveDelayCapSeconds: Long = 60,
    val ipCaptchaThreshold: Long = 10,
    val lockoutWindowMinutes: Long = 15,
    val lockoutThreshold: Long = 20,
    val lockoutDurationMinutes: Long = 30,
    val newDeviceHistorySize: Int = 5,
    val cleanupRetentionDays: Long = 90,
    val cleanupCron: String = "0 0 4 * * *",
    val cleanupZone: String = "UTC",
)
