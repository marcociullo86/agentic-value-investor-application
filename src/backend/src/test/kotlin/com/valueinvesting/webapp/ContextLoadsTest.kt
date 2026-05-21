package com.valueinvesting.webapp

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

// Minimal smoke test: verify Spring context boots in `test` profile.
// Full integration tests (Testcontainers PostgreSQL) live in successive TSKs.
@SpringBootTest
@ActiveProfiles("test")
class ContextLoadsTest {

    @Test
    fun contextLoads() {
        // assertion-free smoke: Spring would throw before reaching here if wiring is broken.
    }
}
