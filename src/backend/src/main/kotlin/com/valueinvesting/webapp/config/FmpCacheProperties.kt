package com.valueinvesting.webapp.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

// FMP snapshot cache TTLs — profile distinct from financial (ADR-014 / ADR-004).
// [^src: design_&_architecture/decisions/ADR-014-fmp-profile-snapshot-ttl.md §Decisione]
@ConfigurationProperties(prefix = "fmp.cache")
data class FmpCacheProperties(
    /** TTL for `fmp_profile_snapshot` (price + meta). Default 1h. */
    val profileTtlHours: Long = 1,
) {
    val profileTtl: Duration get() = Duration.ofHours(profileTtlHours)
}
