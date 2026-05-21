package com.valueinvesting.webapp.contract

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OpenApiContractValidatorTest {

    @Test
    fun `findUndeclaredRuntimePaths detects endpoint absent from canonical contract`() {
        val canonical = setOf("/api/analysis/{ticker}", "/api/financials/{ticker}")
        val runtime = setOf(
            "/api/analysis/{ticker}",
            "/api/financials/{ticker}",
            "/api/secret-endpoint",
        )

        assertThat(OpenApiContractValidator.findUndeclaredRuntimePaths(canonical, runtime))
            .containsExactly("/api/secret-endpoint")
    }

    @Test
    fun `findUndeclaredRuntimePaths ignores actuator and swagger paths`() {
        val canonical = setOf("/api/analysis/{ticker}")
        val runtime = setOf("/api/analysis/{ticker}", "/actuator/health", "/swagger-ui/index.html")

        assertThat(OpenApiContractValidator.findUndeclaredRuntimePaths(canonical, runtime))
            .isEmpty()
    }
}
