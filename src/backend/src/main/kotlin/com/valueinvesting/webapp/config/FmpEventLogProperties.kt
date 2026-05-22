package com.valueinvesting.webapp.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "fmp.event-log")
data class FmpEventLogProperties(
    val retentionDays: Long = DEFAULT_RETENTION_DAYS,
) {
    companion object {
        const val DEFAULT_RETENTION_DAYS = 90L
    }
}
