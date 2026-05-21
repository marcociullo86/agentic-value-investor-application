package com.valueinvesting.webapp.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

// Centralised `Clock` bean.  Injecting Clock (rather than calling Instant.now()
// directly) makes TTL-based logic in FmpCacheService unit-testable with a
// `Clock.fixed(...)` substitute — see US-005 DoD "Unit test con orologio virtualizzato".
// Default is system UTC; tests inject Clock.fixed/Clock.offset to advance time.
// [^src: management/kanban/.../TSK-010.md §DoD]
@Configuration
class ClockConfig {

    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
