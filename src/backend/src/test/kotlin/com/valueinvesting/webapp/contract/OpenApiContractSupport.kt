package com.valueinvesting.webapp.contract

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.swagger.v3.oas.models.OpenAPI
import java.nio.file.Path
import java.util.Locale

/**
 * Operations implemented in Sprint 2 (subset of canonical openapi.yaml).
 * Extend this list as new controllers ship; contract-check gates drift on runtime only.
 */
data class ImplementedOperation(
    val path: String,
    val method: String,
    val successStatus: String = "200",
    val responseSchema: String? = null,
)

object OpenApiContractSupport {

    val HTTP_METHODS: Set<String> = setOf("get", "post", "put", "patch", "delete", "head", "options")

    val RUNTIME_PATH_IGNORE: Set<String> = setOf(
        "/api/openapi.json",
        "/api/swagger-ui.html",
        "/v3/api-docs",
        "/v3/api-docs.yaml",
        "/swagger-ui.html",
    )

    val RUNTIME_PATH_PREFIX_IGNORE: List<String> = listOf(
        "/swagger-ui",
        "/api/swagger-ui",
        "/actuator",
    )

    /** Canonical names in openapi.yaml → acceptable springdoc-generated schema names. */
    val SCHEMA_ALIASES: Map<String, Set<String>> = mapOf(
        "RuleEngineResult" to setOf("RuleEngineResult", "RuleEngineResultResponse"),
        "DcfOverride" to setOf("DcfOverride", "DcfOverrideResponse"),
        "DcfOverrideRequest" to setOf("DcfOverrideRequest"),
        "FinancialDataset" to setOf("FinancialDataset"),
        "ProblemDetails" to setOf("ProblemDetails"),
    )

    val IMPLEMENTED_OPERATIONS: List<ImplementedOperation> = listOf(
        ImplementedOperation("/api/financials/{ticker}", "get", "200", "FinancialDataset"),
        ImplementedOperation("/api/analysis/{ticker}", "get", "200", "RuleEngineResult"),
        ImplementedOperation("/api/dcf-overrides", "post", "201", "DcfOverride"),
        ImplementedOperation("/api/dcf-overrides/{ticker}", "delete", "204", null),
    )

    private val yamlMapper: ObjectMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
    private val jsonMapper: ObjectMapper = jacksonObjectMapper()

    fun loadCanonicalOpenApi(canonicalPath: Path): JsonNode =
        yamlMapper.readTree(canonicalPath.toFile())

    /**
     * Extract path operations from the springdoc [OpenAPI] model.
     * Do not round-trip via swagger [Json.mapper]: PathItem beans use fields named `get`/`post`/…
     * and the swagger serializer often omits HTTP verbs on deserialization.
     */
    fun pathOperationsFromOpenApi(openAPI: OpenAPI): Map<String, Map<String, JsonNode>> {
        val paths = openAPI.paths ?: return emptyMap()
        return paths.mapValues { (_, pathItem) ->
            pathItem.readOperationsMap().mapKeys { (method, _) ->
                method.name.lowercase(Locale.ENGLISH)
            }.mapValues { (_, operation) ->
                jsonMapper.valueToTree(operation)
            }
        }
    }

    fun pathOperations(pathsNode: JsonNode?): Map<String, Map<String, JsonNode>> {
        if (pathsNode == null || !pathsNode.isObject) return emptyMap()
        return pathsNode.properties().associate { (path, item) ->
            path to item.properties()
                .filter { (name, _) -> name.lowercase() in HTTP_METHODS }
                .associate { (name, op) -> name.lowercase() to op }
        }
    }

    fun schemaNames(components: JsonNode?): Set<String> {
        val schemas = components?.get("schemas") ?: return emptySet()
        return schemas.properties().map { it.key }.toSet()
    }

    fun resolveResponseSchemaName(operation: JsonNode, status: String): String? {
        val schema = operation.path("responses").path(status)
            .path("content").path("application/json").path("schema")
        if (schema.isMissingNode || schema.isNull) return null

        val directRef = schema.path("\$ref")
        if (!directRef.isMissingNode && !directRef.isNull) {
            return directRef.asText().substringAfterLast("/")
        }

        val allOf = schema.path("allOf")
        if (allOf.isArray) {
            for (entry in allOf) {
                val ref = entry.path("\$ref")
                if (!ref.isMissingNode && !ref.isNull) {
                    return ref.asText().substringAfterLast("/")
                }
            }
        }
        return null
    }

    fun hasAcceptableSchemaInComponents(canonicalName: String, runtimeSchemas: Set<String>): Boolean {
        val acceptable = SCHEMA_ALIASES[canonicalName] ?: setOf(canonicalName)
        return acceptable.any { it in runtimeSchemas }
    }

    fun isIgnoredRuntimePath(path: String): Boolean =
        path in RUNTIME_PATH_IGNORE ||
            RUNTIME_PATH_PREFIX_IGNORE.any { path.startsWith(it) }

    fun buildRuntimeOpenApi(openAPIService: org.springdoc.core.service.OpenAPIService): JsonNode =
        jsonMapper.valueToTree(openAPIService.build(Locale.ENGLISH))

    fun runtimePathKeys(openAPIService: org.springdoc.core.service.OpenAPIService): Set<String> =
        openAPIService.build(Locale.ENGLISH).paths?.keys.orEmpty()
}
