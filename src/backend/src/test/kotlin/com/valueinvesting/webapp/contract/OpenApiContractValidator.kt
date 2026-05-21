package com.valueinvesting.webapp.contract

import com.fasterxml.jackson.databind.JsonNode

object OpenApiContractValidator {

    fun findMissingImplementedOperations(
        canonical: Map<String, Map<String, JsonNode>>,
        runtime: Map<String, Map<String, JsonNode>>,
        implemented: List<ImplementedOperation> = OpenApiContractSupport.IMPLEMENTED_OPERATIONS,
    ): List<String> =
        implemented.mapNotNull { op ->
            val canonicalOp = canonical[op.path]?.get(op.method)
            val runtimeOp = runtime[op.path]?.get(op.method)
            when {
                canonicalOp == null -> "${op.method.uppercase()} ${op.path} missing from canonical openapi.yaml"
                runtimeOp == null -> "${op.method.uppercase()} ${op.path} missing from runtime springdoc schema"
                else -> null
            }
        }

    fun findUndeclaredRuntimePaths(
        canonicalPaths: Set<String>,
        runtimePaths: Set<String>,
    ): Set<String> =
        runtimePaths
            .filter { !OpenApiContractSupport.isIgnoredRuntimePath(it) }
            .filter { it.startsWith("/api/") }
            .filter { it !in canonicalPaths }
            .toSet()

    fun findMissingResponseSchemas(
        runtime: Map<String, Map<String, JsonNode>>,
        runtimeSchemas: Set<String>,
        implemented: List<ImplementedOperation> = OpenApiContractSupport.IMPLEMENTED_OPERATIONS,
    ): List<String> =
        implemented.mapNotNull { op ->
            val schema = op.responseSchema ?: return@mapNotNull null
            val runtimeOp = runtime[op.path]?.get(op.method) ?: return@mapNotNull null
            val runtimeName = OpenApiContractSupport.responseSchemaName(runtimeOp, op.successStatus)
            val acceptable = OpenApiContractSupport.SCHEMA_ALIASES[schema] ?: setOf(schema)
            when {
                runtimeName == null -> "$schema response schema ref missing for ${op.method.uppercase()} ${op.path}"
                runtimeName !in acceptable && runtimeName !in runtimeSchemas ->
                    "$schema not found in runtime (got $runtimeName)"
                runtimeName !in acceptable ->
                    "$schema alias mismatch: runtime uses $runtimeName, expected one of $acceptable"
                else -> null
            }
        }
}
