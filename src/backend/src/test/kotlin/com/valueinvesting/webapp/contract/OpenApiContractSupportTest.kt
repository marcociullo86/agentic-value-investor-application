package com.valueinvesting.webapp.contract

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.Paths
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OpenApiContractSupportTest {

    @Test
    fun `pathOperationsFromOpenApi preserves HTTP verbs on PathItem`() {
        val openAPI = OpenAPI().paths(
            Paths().addPathItem(
                "/api/financials/{ticker}",
                PathItem().get(
                    Operation().responses(
                        ApiResponses().addApiResponse("200", ApiResponse().description("ok")),
                    ),
                ),
            ),
        )

        val ops = OpenApiContractSupport.pathOperationsFromOpenApi(openAPI)

        assertThat(ops).containsKey("/api/financials/{ticker}")
        assertThat(ops["/api/financials/{ticker}"]).containsKey("get")
    }
}
