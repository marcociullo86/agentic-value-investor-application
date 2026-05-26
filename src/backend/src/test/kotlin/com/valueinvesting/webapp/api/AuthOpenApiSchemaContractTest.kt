package com.valueinvesting.webapp.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Contract test for auth OpenAPI schema (TSK-210, ADR-024 §3).
 *
 * Verifies the runtime springdoc OpenAPI spec:
 * - login/refresh response schemas have NO `refreshToken` field
 * - login/refresh/logout document a `Set-Cookie` response header
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Testcontainers
@Tag("contract")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthOpenApiSchemaContractTest {

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

    private val objectMapper = jacksonObjectMapper()
    private lateinit var openApiDoc: JsonNode

    @BeforeAll
    fun loadOpenApiSpec() {
        val response = mockMvc.get("/api/openapi.json") { accept(MediaType.APPLICATION_JSON) }
            .andReturn().response
        check(response.status == 200) {
            "GET /api/openapi.json failed (${response.status}): ${response.contentAsString.take(500)}"
        }
        openApiDoc = objectMapper.readTree(response.contentAsString)
    }

    @Test
    fun `login response schema must not contain refreshToken field`() {
        val schemaRef = responseSchemaRef("/api/auth/login", "post", "200")
        assertThat(schemaRef).isNotNull

        val schemaNode = resolveSchema(schemaRef!!)
        val properties = schemaNode?.get("properties")
        assertThat(properties).isNotNull
        assertThat(properties!!.has("refreshToken"))
            .withFailMessage("login 200 response schema still contains 'refreshToken' property")
            .isFalse()

        assertThat(properties.has("accessToken")).isTrue()
        assertThat(properties.has("expiresInSeconds")).isTrue()
    }

    @Test
    fun `refresh response schema must not contain refreshToken field`() {
        val schemaRef = responseSchemaRef("/api/auth/refresh", "post", "200")
        assertThat(schemaRef).isNotNull

        val schemaNode = resolveSchema(schemaRef!!)
        val properties = schemaNode?.get("properties")
        assertThat(properties).isNotNull
        assertThat(properties!!.has("refreshToken"))
            .withFailMessage("refresh 200 response schema still contains 'refreshToken' property")
            .isFalse()

        assertThat(properties.has("accessToken")).isTrue()
        assertThat(properties.has("expiresInSeconds")).isTrue()
    }

    @Test
    fun `login response documents Set-Cookie header`() {
        assertSetCookieHeaderDocumented("/api/auth/login", "post", "200")
    }

    @Test
    fun `refresh response documents Set-Cookie header`() {
        assertSetCookieHeaderDocumented("/api/auth/refresh", "post", "200")
    }

    @Test
    fun `logout response documents Set-Cookie header`() {
        assertSetCookieHeaderDocumented("/api/auth/logout", "post", "204")
    }

    @Test
    fun `runtime schemas do not contain TokenPairResponse`() {
        val schemas = openApiDoc.path("components").path("schemas")
        assertThat(schemas.has("TokenPairResponse"))
            .withFailMessage("Deprecated TokenPairResponse still present in runtime OpenAPI schemas")
            .isFalse()
        assertThat(schemas.has("TokenPair"))
            .withFailMessage("Deprecated TokenPair still present in runtime OpenAPI schemas")
            .isFalse()
    }

    private fun responseSchemaRef(path: String, method: String, status: String): String? {
        val ref = openApiDoc.path("paths").path(path).path(method)
            .path("responses").path(status)
            .path("content").path("application/json").path("schema")
            .path("\$ref")
        return if (ref.isMissingNode || ref.isNull) null else ref.asText()
    }

    private fun resolveSchema(ref: String): JsonNode? {
        val name = ref.substringAfterLast("/")
        return openApiDoc.path("components").path("schemas").path(name)
    }

    private fun assertSetCookieHeaderDocumented(path: String, method: String, status: String) {
        val headers = openApiDoc.path("paths").path(path).path(method)
            .path("responses").path(status).path("headers")
        assertThat(headers.isMissingNode || headers.isNull)
            .withFailMessage("$method $path $status response has no headers block in OpenAPI spec")
            .isFalse()
        assertThat(headers.has("Set-Cookie"))
            .withFailMessage("$method $path $status response does not document Set-Cookie header")
            .isTrue()
    }
}
