package com.valueinvesting.webapp.contract

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.nio.file.Path

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
        "/v3/api-docs",
        "/v3/api-docs.yaml",
        "/swagger-ui.html",
    )

    val RUNTIME_PATH_PREFIX_IGNORE: List<String> = listOf(
        "/swagger-ui",
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

    private val yamlMapper: ObjectMapper = jacksonObjectMapper(YAMLFactory())

    fun loadCanonicalOpenApi(canonicalPath: Path): JsonNode =
        yamlMapper.readTree(canonicalPath.toFile())

    fun parseOpenApiJson(json: String): JsonNode =
        jacksonObjectMapper().readValue(json)

    fun pathOperations(pathsNode: JsonNode?): Map<String, Map<String, JsonNode>> {
        if (pathsNode == null || !pathsNode.isObject) return emptyMap()
        return pathsNode.fields().asSequence().associate { (path, item) ->
            path to item.fields().asSequence()
                .filter { (name, _) -> name.lowercase() in HTTP_METHODS }
                .associate { (name, op) -> name.lowercase() to op }
        }
    }

    fun schemaNames(components: JsonNode?): Set<String> {
        val schemas = components?.get("schemas") ?: return emptySet()
        return schemas.fieldNames().asSequence().toSet()
    }

    fun responseSchemaName(operation: JsonNode, status: String): String? {
        val refNode = operation.path("responses").path(status)
            .path("content").path("application/json").path("schema").path("\$ref")
        if (refNode.isMissingNode || refNode.isNull) return null
        return refNode.asText().substringAfterLast("/")
    }

    fun isIgnoredRuntimePath(path: String): Boolean =
        path in RUNTIME_PATH_IGNORE ||
            RUNTIME_PATH_PREFIX_IGNORE.any { path.startsWith(it) }
}
