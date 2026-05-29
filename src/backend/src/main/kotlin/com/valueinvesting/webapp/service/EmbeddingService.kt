package com.valueinvesting.webapp.service

import com.valueinvesting.webapp.config.EmbeddingsProperties
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.time.Duration

/**
 * HTTP client to the Python embeddings sidecar (TSK-099).
 * Batches requests and converts response to FloatArray vectors.
 *
 * The sidecar embed call is wrapped in connect/read timeouts driven by
 * `embeddings.sidecar.timeout-seconds` (TSK-100 hardening — wave-04 review):
 * absent timeouts let a stalled sidecar pin RAG indexing threads indefinitely.
 *
 * [^src: design_&_architecture/decisions/ADR-018]
 * [^src: code_quality/reports/TSK-100-iter-1.md §Finding 1]
 */
@Service
class EmbeddingService(
    restClientBuilder: RestClient.Builder,
    private val props: EmbeddingsProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val client: RestClient = restClientBuilder
        .baseUrl(props.sidecar.url)
        .requestFactory(buildRequestFactory(props.sidecar.timeoutSeconds))
        .build()

    // SimpleClientHttpRequestFactory (HttpURLConnection) bufferizza interamente il
    // body e imposta Content-Length prima di inviare. Lo scegliamo al posto di
    // JdkClientHttpRequestFactory: quest'ultimo usa un body-publisher in streaming
    // dell'oggetto e, quando la Content-Length non è nota a priori, può inviare
    // `noBody()` → il sidecar FastAPI riceve body vuoto e risponde 422
    // ({"loc":["body"],"msg":"Field required","input":null}). Mantenuti i timeout
    // connect+read di TSK-100 (un sidecar bloccato non deve pinnare i thread RAG).
    private fun buildRequestFactory(timeoutSeconds: Long): ClientHttpRequestFactory {
        val timeout = Duration.ofSeconds(timeoutSeconds)
        val factory = SimpleClientHttpRequestFactory()
        factory.setConnectTimeout(timeout)
        factory.setReadTimeout(timeout)
        return factory
    }

    fun embed(texts: List<String>): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()

        val batches = texts.chunked(props.batchSize)
        val result = mutableListOf<FloatArray>()

        batches.forEachIndexed { idx, batch ->
            log.info("Embedding batch {}/{}, model: {}", idx + 1, batches.size, props.model.name)
            val response = callSidecar(batch)
            result.addAll(response)
        }

        return result
    }

    private fun callSidecar(texts: List<String>): List<FloatArray> {
        val request = EmbedRequest(texts)
        try {
            val response = client.post()
                .uri("/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(EmbedResponse::class.java)
                ?: throw EmbeddingServiceUnavailableException("Null response from sidecar")

            return response.embeddings.map { it.toFloatArray() }
        } catch (e: RestClientException) {
            val message = e.message ?: "Unknown error"
            if (message.contains("503") || message.contains("Connection refused")) {
                throw EmbeddingServiceUnavailableException("Sidecar unavailable: $message", e)
            }
            throw EmbeddingTimeoutException("Sidecar call failed: $message", e)
        }
    }

    private data class EmbedRequest(val texts: List<String>)
    private data class EmbedResponse(val embeddings: List<List<Float>>)
}

class EmbeddingServiceUnavailableException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

class EmbeddingTimeoutException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
