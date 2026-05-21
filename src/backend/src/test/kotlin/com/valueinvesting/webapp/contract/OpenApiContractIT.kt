package com.valueinvesting.webapp.contract

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springdoc.core.service.OpenAPIService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.nio.file.Path
import java.util.Locale

/**
 * Contract test: runtime springdoc OpenAPI vs design_&_architecture/api/openapi.yaml (TSK-037).
 * Uses [OpenAPIService] directly (same document as /api/openapi.json) to avoid MockMvc path issues.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Tag("contract")
class OpenApiContractIT {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
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
    private lateinit var openAPIService: OpenAPIService

    @Value("\${contract.openapi.canonical}")
    private lateinit var canonicalOpenApiPath: String

    @Test
    fun `implemented operations are declared in canonical and runtime OpenAPI`() {
        val canonical = OpenApiContractSupport.pathOperations(
            OpenApiContractSupport.loadCanonicalOpenApi(Path.of(canonicalOpenApiPath)).get("paths"),
        )
        val runtime = loadRuntimePaths()

        val missing = OpenApiContractValidator.findMissingImplementedOperations(canonical, runtime)
        assertThat(missing)
            .withFailMessage {
                buildString {
                    appendLine("Implemented API drift:")
                    missing.forEach { appendLine("  - $it") }
                    appendLine("Runtime paths: ${runtime.keys.sorted()}")
                    appendLine("Canonical paths: ${canonical.keys.sorted()}")
                }
            }
            .isEmpty()
    }

    @Test
    fun `runtime API paths must not exceed canonical contract`() {
        val canonicalDoc = OpenApiContractSupport.loadCanonicalOpenApi(Path.of(canonicalOpenApiPath))
        val canonicalPaths = OpenApiContractSupport.pathOperations(canonicalDoc.get("paths")).keys
        val runtimePaths = OpenApiContractSupport.runtimePathKeys(openAPIService)

        val undeclared = OpenApiContractValidator.findUndeclaredRuntimePaths(canonicalPaths, runtimePaths)
        assertThat(undeclared)
            .withFailMessage(
                "Runtime exposes paths not in openapi.yaml (add to contract or remove controller): $undeclared",
            )
            .isEmpty()
    }

    @Test
    fun `implemented response schemas are present in runtime components`() {
        val runtimeOpenApi = openAPIService.build(Locale.ENGLISH)
        val runtimePaths = OpenApiContractSupport.pathOperationsFromOpenApi(runtimeOpenApi)
        val runtimeSchemas = OpenApiContractSupport.schemaNames(
            OpenApiContractSupport.buildRuntimeOpenApi(openAPIService).get("components"),
        )

        val missing = OpenApiContractValidator.findMissingResponseSchemas(runtimePaths, runtimeSchemas)
        assertThat(missing)
            .withFailMessage("Schema drift:\n${missing.joinToString("\n")}")
            .isEmpty()
    }

    private fun loadRuntimePaths() =
        OpenApiContractSupport.pathOperationsFromOpenApi(openAPIService.build(Locale.ENGLISH))
}
