package com.valueinvesting.webapp.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.valueinvesting.webapp.api.model.LoginRequest
import com.valueinvesting.webapp.api.model.RegisterRequest
import com.valueinvesting.webapp.persistence.repository.RefreshTokenRepository
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
 * Contract-test for auth error policies (TSK-042, ADR-010 §2 / US-019 AC#2).
 *
 * Verifies the **generic error policy** on /api/auth/login: unknown email and
 * wrong password must produce a body that is byte-for-byte identical on the
 * fields a client could pivot on (`type`, `status`, `detail`). If the two
 * bodies diverge, a client can enumerate accounts via reaction-time or
 * detail-string side-channels.
 *
 * Also asserts that /api/auth/register surfaces the documented
 * RFC 9457 ProblemDetails on the 409 path (sanity check vs. OpenAPI canonical;
 * full path coverage in [com.valueinvesting.webapp.contract.OpenApiContractIT]).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Tag("contract")
class AuthControllerContractTest {

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
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    private val knownEmail = "registered@example.com"
    private val knownPassword = "correct-horse-battery-staple-1234"

    @BeforeEach
    fun setup() {
        refreshTokenRepository.deleteAll()
        userRepository.deleteAll()
        // Pre-condition: one known account so we can exercise "wrong password"
        // against a real user. Avoids the trivially-equal case where both
        // branches hit the same "user not found" path.
        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                RegisterRequest(knownEmail, knownPassword, null),
            )
        }.andExpect { status { isCreated() } }
    }

    @Test
    fun `login unknown email and wrong password share identical ProblemDetails body`() {
        val unknownEmailBody = postLoginExpectingUnauthorized(
            email = "ghost@example.com",
            password = "any-password-whatsoever-12345",
        )
        val wrongPasswordBody = postLoginExpectingUnauthorized(
            email = knownEmail,
            password = "wrong-password-on-known-account",
        )

        // ADR-010 §2 generic error policy: the fields a client could use to
        // distinguish "unknown email" from "wrong password" MUST be byte-for-byte
        // equal. `instance`, `timestamp`, `requestId` may differ.
        assertThat(unknownEmailBody.get("type").asText())
            .isEqualTo("https://api/errors/invalid-credentials")
        assertThat(wrongPasswordBody.get("type").asText())
            .isEqualTo("https://api/errors/invalid-credentials")
        assertThat(unknownEmailBody.get("status").asInt()).isEqualTo(401)
        assertThat(wrongPasswordBody.get("status").asInt()).isEqualTo(401)
        assertThat(unknownEmailBody.get("detail").asText())
            .isEqualTo("Invalid email or password")
        assertThat(wrongPasswordBody.get("detail").asText())
            .isEqualTo("Invalid email or password")

        // Belt-and-braces: collapse to the "discriminating" subset and demand
        // structural equality. Any future drift on type/status/detail breaks
        // this assertion immediately.
        val discriminating = listOf("type", "status", "detail", "title")
        discriminating.forEach { field ->
            assertThat(unknownEmailBody.get(field))
                .withFailMessage(
                    "Field '$field' must be identical to mitigate enum-attack; " +
                        "unknown-email=${unknownEmailBody.get(field)} vs " +
                        "wrong-password=${wrongPasswordBody.get(field)}",
                )
                .isEqualTo(wrongPasswordBody.get(field))
        }
    }

    @Test
    fun `register duplicate email returns 409 ProblemDetails matching OpenAPI contract`() {
        // /api/auth/register 409 path is documented in
        // design_&_architecture/api/openapi.yaml (lines around the /auth/register
        // path). Here we just assert the runtime body matches the documented
        // RFC 9457 shape (type/title/status/detail).
        val body = mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                RegisterRequest(knownEmail, "another-strong-password-1234", null),
            )
        }.andReturn().response.contentAsString
        val json = objectMapper.readTree(body)

        assertThat(json.get("type").asText())
            .isEqualTo("https://api/errors/email-already-registered")
        assertThat(json.get("status").asInt()).isEqualTo(409)
        assertThat(json.get("title").asText()).isEqualTo("Email already registered")
        assertThat(json.get("detail").asText())
            .isEqualTo("Email already registered: $knownEmail")
    }

    private fun postLoginExpectingUnauthorized(email: String, password: String): JsonNode {
        val response = mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(LoginRequest(email, password))
        }.andReturn().response
        check(response.status == 401) {
            "login was expected to return 401 (generic-error policy); got ${response.status}: ${response.contentAsString}"
        }
        return objectMapper.readTree(response.contentAsString)
    }
}
