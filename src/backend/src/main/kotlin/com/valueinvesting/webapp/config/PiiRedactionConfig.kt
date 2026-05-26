package com.valueinvesting.webapp.config

import org.springframework.boot.context.properties.ConfigurationProperties

// Externalized PII redaction patterns and toggle for PiiRedactionEncoder.
// [^src: design_&_architecture/decisions/ADR-021-structured-logging-pii-redaction.md §4]
@ConfigurationProperties(prefix = "app.logging.pii")
data class PiiRedactionConfig(
    val enabled: Boolean = true,
    val environmentAware: Boolean = true,
    val patterns: Map<String, String> = emptyMap(),
)
