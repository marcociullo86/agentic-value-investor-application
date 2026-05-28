package com.valueinvesting.webapp.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.http.Fault
import com.valueinvesting.webapp.api.model.RegisterRequest
import com.valueinvesting.webapp.persistence.repository.LoginAttemptRepository
import com.valueinvesting.webapp.persistence.repository.RefreshTokenRepository
import com.valueinvesting.webapp.persistence.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
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
import java.security.MessageDigest

/**
 * WireMock IT for TSK-236 — HIBP k-anonymity password check.
 *
 * Covers four DoD scenarios:
 *   1. Compromised password → 400 password-compromised (registration rejected).
 *   2. Safe password → 201 (registration accepted).
 *   3. HIBP API unavailable → graceful degradation, 201 (password allowed, warning logged).
 *   4. k-anonymity: only the 5-char SHA-1 prefix is forwarded to the HIBP range API.
 *
 * The test profile disables HIBP (`hibp.enabled=false`); DynamicPropertySource
 * re-enables it and points the client at the local WireMock server.
 *
 * [^src: management/kanban/EP-018-hardening-sicurezza-compliance/US-081-protezione-identita-accesso/TSK-236.md]
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class HibpWireMockIT {

    companion object {
        private const val HIBP_BASE_PATH = "/range"

        private val wireMockServer: WireMockServer =
            WireMockServer(wireMockConfig().dynamicPort()).also { it.start() }

        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("pgvector/pgvector:pg17")
            .withDatabaseName("value_investing_test")
            .withUsername("test")
            .withPassword("test")

        @DynamicPropertySource
        @JvmStatic
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            // The test profile sets hibp.enabled=false; override so HibpClientRestClient is active.
            registry.add("app.security.hibp.enabled") { true }
            registry.add("app.security.hibp.api-url") { "${wireMockServer.baseUrl()}$HIBP_BASE_PATH" }
        }

        @AfterAll
        @JvmStatic
        fun stopWireMock() {
            wireMockServer.stop()
        }

        private fun sha1Hex(input: String): String {
            val digest = MessageDigest.getInstance("SHA-1")
            return digest.digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02X".format(it) }
        }

        private fun hibpPrefix(password: String): String = sha1Hex(password).substring(0, 5)
        private fun hibpSuffix(password: String): String = sha1Hex(password).substring(5)
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    private lateinit var loginAttemptRepository: LoginAttemptRepository

    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    @BeforeEach
    fun resetState() {
        wireMockServer.resetAll()
        loginAttemptRepository.deleteAll()
        refreshTokenRepository.deleteAll()
        userRepository.deleteAll()
    }

    // -------------------------------------------------------------------------
    // Scenario 1 — compromised password is rejected
    // -------------------------------------------------------------------------

    @Test
    fun `compromised password is rejected on register with 400 password-compromised`() {
        val password = "Br3ach3dPwdXYZ99!"
        val prefix = hibpPrefix(password)
        val suffix = hibpSuffix(password)

        wireMockServer.stubFor(
            get(urlPathEqualTo("$HIBP_BASE_PATH/$prefix"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        // Simulate the HIBP range response: suffix is present with a non-zero count.
                        .withBody("AABBCC0011223344556677889900AABBCCDD:1\n$suffix:9876543\nDDEEFF001122334455667788:2"),
                ),
        )

        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                RegisterRequest("hibp-compromised@example.com", password),
            )
        }.andExpect {
            status { isBadRequest() }
            content { contentType("application/problem+json") }
            jsonPath("$.status") { value(400) }
            jsonPath("$.type") { value("https://api/errors/password-compromised") }
            jsonPath("$.title") { value("Password not allowed") }
            jsonPath("$.detail") {
                value("This password has appeared in a known data breach. Please choose a different password.")
            }
        }

        assertThat(userRepository.findByEmailIgnoreCase("hibp-compromised@example.com"))
            .`as`("User must NOT be persisted when password is compromised")
            .isNull()
    }

    // -------------------------------------------------------------------------
    // Scenario 2 — safe password is accepted
    // -------------------------------------------------------------------------

    @Test
    fun `safe password is accepted on register with 201`() {
        val password = "Sup3rUniq!Pw#9876"
        val prefix = hibpPrefix(password)
        val suffix = hibpSuffix(password)

        // Build a response body that intentionally does NOT include this password's suffix.
        val safeBody = buildString {
            appendLine("0000000000000000000000000000000000001:1")
            appendLine("0000000000000000000000000000000000002:3")
            appendLine("0000000000000000000000000000000000003:7")
        }
        check(!safeBody.contains(suffix, ignoreCase = true)) {
            "Test setup error: safeBody accidentally contains the safe-password suffix '$suffix'"
        }

        wireMockServer.stubFor(
            get(urlPathEqualTo("$HIBP_BASE_PATH/$prefix"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody(safeBody),
                ),
        )

        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                RegisterRequest("hibp-safe@example.com", password),
            )
        }.andExpect {
            status { isCreated() }
            jsonPath("$.email") { value("hibp-safe@example.com") }
        }

        assertThat(userRepository.findByEmailIgnoreCase("hibp-safe@example.com"))
            .`as`("User must be persisted when password is safe")
            .isNotNull()
    }

    // -------------------------------------------------------------------------
    // Scenario 3 — HIBP API unavailable → graceful degradation
    // -------------------------------------------------------------------------

    @Test
    fun `HIBP API unavailable allows registration with graceful degradation`() {
        val password = "Degrad@tion!Test9876"

        wireMockServer.stubFor(
            get(urlPathMatching("$HIBP_BASE_PATH/.*"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)),
        )

        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                RegisterRequest("hibp-degraded@example.com", password),
            )
        }.andExpect {
            status { isCreated() }
            jsonPath("$.email") { value("hibp-degraded@example.com") }
        }

        assertThat(userRepository.findByEmailIgnoreCase("hibp-degraded@example.com"))
            .`as`("User must be persisted when HIBP is unavailable (graceful degradation)")
            .isNotNull()
    }

    // -------------------------------------------------------------------------
    // Scenario 4 — k-anonymity: only the 5-char SHA-1 prefix is forwarded
    // -------------------------------------------------------------------------

    @Test
    fun `only 5-char SHA-1 prefix is sent to HIBP API (k-anonymity)`() {
        val password = "KAnonTest!P@ss9876"
        val expectedPrefix = hibpPrefix(password)
        val suffix = hibpSuffix(password)

        // Return a body that does NOT contain the suffix → registration succeeds.
        wireMockServer.stubFor(
            get(urlPathEqualTo("$HIBP_BASE_PATH/$expectedPrefix"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("0000000000000000000000000000000000001:1"),
                ),
        )

        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                RegisterRequest("hibp-kanon@example.com", password),
            )
        }.andExpect { status { isCreated() } }

        val requests = wireMockServer.findAll(getRequestedFor(urlPathMatching("$HIBP_BASE_PATH/.*")))
        assertThat(requests)
            .`as`("Exactly one request must reach the HIBP mock")
            .hasSize(1)

        val urlPath = requests.first().url        // e.g. "/range/ABCDE"
        val sentSegment = urlPath.removePrefix("$HIBP_BASE_PATH/")

        assertThat(sentSegment)
            .`as`("HIBP request segment must be exactly 5 characters (k-anonymity prefix)")
            .hasSize(5)

        assertThat(sentSegment)
            .`as`("HIBP request segment must be uppercase hex")
            .matches("[A-F0-9]{5}")

        assertThat(sentSegment)
            .`as`("The segment must equal the expected SHA-1 prefix")
            .isEqualTo(expectedPrefix)

        assertThat(sentSegment)
            .`as`("Full SHA-1 hash must NOT be sent (k-anonymity guard)")
            .isNotEqualTo(sha1Hex(password))

        assertThat(sentSegment)
            .`as`("Suffix must NOT be sent as part of the URL (k-anonymity guard)")
            .doesNotContain(suffix)
    }
}
