package com.valueinvesting.webapp.llm

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.retry.Retry
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.net.http.HttpClient
import java.time.Duration

// HTTP implementation of AnthropicClient using Spring RestClient 6.1+.
//
// Calls POST https://api.anthropic.com/v1/messages with headers:
//   x-api-key: ${ANTHROPIC_API_KEY}
//   anthropic-version: 2023-06-01
//
// Request body omits temperature/top_p/top_k by default (Adaptive Thinking is
// applied internally by the model). Model is resolved from anthropic.model
// (env ANTHROPIC_MODEL) unless LlmRequest.model is explicitly set.
//
// Resilience chain applied programmatically (same rationale as FmpAdapterRestClient):
//   RateLimiter → CircuitBreaker → Retry → HTTP call
//
// [^src: design_&_architecture/decisions/ADR-017-anthropic-sdk-jvm.md §2]
// [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-041-munger-inversion-llm/TSK-104.md §1,2]
class AnthropicRestClient(
    restClientBuilder: RestClient.Builder,
    private val properties: AnthropicProperties,
    private val objectMapper: ObjectMapper,
    private val circuitBreaker: CircuitBreaker,
    private val rateLimiter: RateLimiter,
    private val retry: Retry,
    private val budgetGuard: LlmBudgetGuard,
    private val costCounterService: LlmCostCounterService,
) : AnthropicClient {

    private val log = LoggerFactory.getLogger(javaClass)

    init {
        require(properties.apiKey.isNotBlank()) {
            "Anthropic API key is required: set env var ANTHROPIC_API_KEY or property anthropic.api-key"
        }
    }

    private val restClient: RestClient = restClientBuilder
        .baseUrl(properties.baseUrl)
        .defaultHeader(HEADER_API_KEY, properties.apiKey)
        .defaultHeader(HEADER_API_VERSION, API_VERSION)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .requestFactory(buildRequestFactory(properties.timeoutSeconds))
        .build()

    override fun complete(request: LlmRequest): LlmResponse {
        // ADR-019 §6 — pre-chain guard: short-circuit when admin froze LLM traffic.
        // Runs OUTSIDE the Resilience4j chain so it does not move the circuit-breaker.
        budgetGuard.checkOrThrow()
        return rateLimiter.executeSupplier {
            circuitBreaker.executeSupplier {
                retry.executeSupplier {
                    executeHttpCall(request)
                }
            }
        }
    }

    private fun executeHttpCall(request: LlmRequest): LlmResponse {
        val startMs = System.currentTimeMillis()
        val model = request.model.ifBlank { properties.model }

        val apiRequest = ApiRequest(
            model = model,
            maxTokens = request.maxTokens,
            system = request.systemPrompt.ifBlank { null },
            messages = listOf(ApiMessage(role = "user", content = request.userPrompt)),
        )

        log.debug("Anthropic API call: model={}, maxTokens={}", model, request.maxTokens)

        val responseBody: String = try {
            restClient.post()
                .uri("/messages")
                .body(apiRequest)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError) { _, response ->
                    val status = response.statusCode.value()
                    val bodyBytes = try {
                        response.body.readAllBytes()
                    } catch (_: Exception) {
                        ByteArray(0)
                    }
                    throwForClientError(status, bodyBytes, response.headers)
                }
                .onStatus(HttpStatusCode::is5xxServerError) { _, response ->
                    val status = response.statusCode.value()
                    if (status == ANTHROPIC_OVERLOADED_STATUS) {
                        throw LlmException.Overloaded(null)
                    }
                    throw LlmException.ServerError(status, null)
                }
                .body(String::class.java)
                ?: throw LlmException.ServerError(status = 0, cause = null)
        } catch (ex: LlmException) {
            throw ex
        } catch (ex: RestClientResponseException) {
            throw mapRestClientError(ex)
        } catch (ex: Exception) {
            throw LlmException.Timeout(cause = ex)
        }

        val parsed = parseResponse(responseBody)
        recordTelemetry(parsed, startMs)
        return parsed
    }

    // ADR-019 §2 — post-call telemetry (llm_call_log insert + llm_cost_counter UPSERT).
    // Wrapped: a telemetry hiccup must never break a paying LLM request.
    private fun recordTelemetry(response: LlmResponse, startMs: Long) {
        val latencyMs = (System.currentTimeMillis() - startMs).toInt()
        try {
            costCounterService.recordCall(
                model = response.model,
                endpoint = ENDPOINT_LABEL,
                purpose = PURPOSE_LABEL,
                inputTokens = response.inputTokens,
                outputTokens = response.outputTokens,
                latencyMs = latencyMs,
            )
        } catch (ex: Exception) {
            log.warn("LLM telemetry recording failed for model={}: {}", response.model, ex.message)
        }
    }

    // ADR-019 §1 — apply Anthropic per-call HTTP timeout (review TSK-156 finding 3).
    // Connect timeout matches read timeout: an unreachable Anthropic endpoint must
    // not pin the calling thread past the expected per-call budget.
    private fun buildRequestFactory(timeoutSeconds: Long): JdkClientHttpRequestFactory {
        val timeout = Duration.ofSeconds(timeoutSeconds)
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(timeout)
            .build()
        val factory = JdkClientHttpRequestFactory(httpClient)
        factory.setReadTimeout(timeout)
        return factory
    }

    private fun throwForClientError(
        status: Int,
        bodyBytes: ByteArray,
        headers: HttpHeaders,
    ) {
        val errorMsg = parseErrorMessage(bodyBytes)
        when (status) {
            400 -> throw LlmException.InvalidRequest(errorMsg, null)
            401, 403 -> throw LlmException.AuthError(null)
            429 -> {
                val retryAfter = headers.getFirst("retry-after")?.toIntOrNull()
                throw LlmException.RateLimited(retryAfter, null)
            }
            else -> throw LlmException.ServerError(status, null)
        }
    }

    private fun mapRestClientError(ex: RestClientResponseException): LlmException {
        return when (ex.statusCode.value()) {
            400 -> LlmException.InvalidRequest(ex.message ?: "Bad request", ex)
            401, 403 -> LlmException.AuthError(ex)
            429 -> LlmException.RateLimited(cause = ex)
            ANTHROPIC_OVERLOADED_STATUS -> LlmException.Overloaded(ex)
            in 500..599 -> LlmException.ServerError(ex.statusCode.value(), ex)
            else -> LlmException.ServerError(ex.statusCode.value(), ex)
        }
    }

    private fun parseErrorMessage(bodyBytes: ByteArray): String {
        if (bodyBytes.isEmpty()) return "unknown error"
        val body = String(bodyBytes)
        return try {
            objectMapper.readValue(body, ApiErrorBody::class.java)?.error?.message ?: body
        } catch (_: Exception) {
            body
        }
    }

    private fun parseResponse(body: String): LlmResponse {
        val apiResponse = try {
            objectMapper.readValue(body, ApiResponse::class.java)
        } catch (ex: Exception) {
            throw LlmException.InvalidRequest(
                "Failed to parse Anthropic response: ${ex.message}",
                ex,
            )
        }

        val textContent = apiResponse.content
            ?.firstOrNull { it.type == "text" }
            ?.text
            ?: throw LlmException.InvalidRequest(
                "Anthropic response contains no text content block",
                null,
            )

        return LlmResponse(
            content = textContent,
            inputTokens = apiResponse.usage?.inputTokens ?: 0,
            outputTokens = apiResponse.usage?.outputTokens ?: 0,
            stopReason = apiResponse.stopReason ?: "unknown",
            model = apiResponse.model ?: properties.model,
        )
    }

    companion object {
        const val API_VERSION = "2023-06-01"
        const val HEADER_API_KEY = "x-api-key"
        const val HEADER_API_VERSION = "anthropic-version"
        private const val ANTHROPIC_OVERLOADED_STATUS = 529
        // Endpoint/purpose tags for `llm_call_log`. Single value here is acceptable
        // because every AnthropicRestClient call serves the Munger deep-analysis
        // pipeline today (US-041); other purposes get their own client wrappers.
        private const val ENDPOINT_LABEL = "anthropic-messages"
        private const val PURPOSE_LABEL = "munger"
    }

    // -------------------------------------------------------------------------
    // Anthropic Messages API DTOs (internal — not domain model)
    // -------------------------------------------------------------------------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    internal data class ApiRequest(
        val model: String,
        @JsonProperty("max_tokens") val maxTokens: Int,
        val system: String?,
        val messages: List<ApiMessage>,
    )

    internal data class ApiMessage(
        val role: String,
        val content: String,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class ApiResponse(
        val id: String? = null,
        val content: List<ContentBlock>? = null,
        val model: String? = null,
        @JsonProperty("stop_reason") val stopReason: String? = null,
        val usage: ApiUsage? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class ContentBlock(
        val type: String? = null,
        val text: String? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class ApiUsage(
        @JsonProperty("input_tokens") val inputTokens: Int = 0,
        @JsonProperty("output_tokens") val outputTokens: Int = 0,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class ApiErrorBody(
        val type: String? = null,
        val error: ApiErrorDetail? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class ApiErrorDetail(
        val type: String? = null,
        val message: String? = null,
    )
}
