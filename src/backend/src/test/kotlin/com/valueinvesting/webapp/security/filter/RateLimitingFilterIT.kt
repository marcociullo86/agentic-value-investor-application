package com.valueinvesting.webapp.security.filter

import com.fasterxml.jackson.databind.ObjectMapper
import com.valueinvesting.webapp.api.model.LoginRequest
import com.valueinvesting.webapp.persistence.repository.LoginAttemptRepository
import com.valueinvesting.webapp.persistence.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Integration tests for auth rate limiting (TSK-229).
 * Test profile sets low limits in application.yml (login per-ip=3, per-account=2).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Tag("integration")
class RateLimitingFilterIT {

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

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var loginAttemptRepository: LoginAttemptRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @BeforeEach
    fun clean() {
        loginAttemptRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `login returns 429 with Retry-After when per-IP limit exceeded`() {
        val body = objectMapper.writeValueAsString(
            LoginRequest(email = "unknown@example.com", password = "wrong-password-12"),
        )

        repeat(3) {
            mockMvc.post("/api/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect { status { isUnauthorized() } }
        }

        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isTooManyRequests() }
            header { exists(RateLimitingFilter.RETRY_AFTER_HEADER) }
        }

        assertThat(loginAttemptRepository.count()).isEqualTo(3)
    }

    @Test
    fun `login returns 429 when per-account limit exceeded before per-IP`() {
        val email = "same-account@example.com"
        val body = objectMapper.writeValueAsString(
            LoginRequest(email = email, password = "wrong-password-12"),
        )

        repeat(2) {
            mockMvc.post("/api/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect { status { isUnauthorized() } }
        }

        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = body
            header("X-Forwarded-For", "198.51.100.99")
        }.andExpect {
            status { isTooManyRequests() }
            header { exists(RateLimitingFilter.RETRY_AFTER_HEADER) }
        }
    }
}
