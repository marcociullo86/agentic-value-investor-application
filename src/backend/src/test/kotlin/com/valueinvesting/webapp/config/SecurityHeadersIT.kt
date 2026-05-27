package com.valueinvesting.webapp.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * TSK-221 — verifies Content-Security-Policy header on HTTP responses (US-080 AC).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class SecurityHeadersIT {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("pgvector/pgvector:pg17")
            .withDatabaseName("value_investing_test")
            .withUsername("test")
            .withPassword("test")

        @DynamicPropertySource
        @JvmStatic
        fun registerDataSource(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `actuator health includes Content-Security-Policy header`() {
        val result = mockMvc.get("/actuator/health").andReturn()
        val csp = result.response.getHeader("Content-Security-Policy")

        assertThat(csp).isEqualTo(SecurityHeadersConfig.CONTENT_SECURITY_POLICY)
    }

    @Test
    fun `api screener includes Content-Security-Policy header`() {
        val result = mockMvc.get("/api/screener").andReturn()
        val csp = result.response.getHeader("Content-Security-Policy")

        assertThat(csp).isEqualTo(SecurityHeadersConfig.CONTENT_SECURITY_POLICY)
    }
}
