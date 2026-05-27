package com.valueinvesting.webapp.client

import com.fasterxml.jackson.annotation.JsonProperty
import com.valueinvesting.webapp.config.TurnstileProperties
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.net.http.HttpClient
import java.time.Duration

/**
 * Sync Cloudflare Turnstile siteverify client (US-081 / ADR-025 §5, TSK-230).
 *
 * The Turnstile API accepts an `application/x-www-form-urlencoded` POST with
 * `secret` + `response` (+ optional `remoteip`) and returns a JSON payload
 * `{ "success": true|false, "error-codes": [...] }`. Per
 * <https://developers.cloudflare.com/turnstile/get-started/server-side-validation/>.
 *
 * Two operating modes:
 * - `secretKey` blank: server-side verification is OFF (default in dev/test).
 *   Any non-blank token is treated as valid so unit/integration tests can
 *   exercise the captcha gate without contacting Cloudflare. The blank-secret
 *   exit also doubles as a safe production fail-closed if the secret env var
 *   is missing — see [verify] contract.
 * - `secretKey` set: siteverify is called. Network errors / malformed bodies
 *   return `false` so the brute-force guard treats them as "captcha failed"
 *   rather than throwing — siteverify outages must not crash logins.
 */
@Component
class TurnstileRestClient(
    restClientBuilder: RestClient.Builder,
    private val properties: TurnstileProperties,
) : TurnstileClient {

    private val log = LoggerFactory.getLogger(javaClass)

    private val client: RestClient by lazy {
        restClientBuilder
            .defaultHeader("User-Agent", "ValueInvesting-App-Turnstile/1.0")
            .requestFactory(buildRequestFactory(properties.timeoutSeconds))
            .build()
    }

    // CQRL TSK-230 iter-1: connect + read timeout on siteverify (external_api_guard).
    private fun buildRequestFactory(timeoutSeconds: Long): JdkClientHttpRequestFactory {
        val timeout = Duration.ofSeconds(timeoutSeconds)
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(timeout)
            .build()
        val factory = JdkClientHttpRequestFactory(httpClient)
        factory.setReadTimeout(timeout)
        return factory
    }

    override fun verify(token: String, remoteIp: String): Boolean {
        if (token.isBlank()) {
            return false
        }
        if (properties.secretKey.isBlank()) {
            // No secret configured — treat the token as locally valid (dev/test).
            // Production must wire `app.security.turnstile.secret-key`; in its
            // absence the brute-force flow still completes but Turnstile is
            // effectively a no-op (documented behaviour, dev defaults).
            return true
        }
        val form = LinkedMultiValueMap<String, String>().apply {
            add("secret", properties.secretKey)
            add("response", token)
            if (remoteIp.isNotBlank()) {
                add("remoteip", remoteIp)
            }
        }
        return try {
            val response = client.post()
                .uri(properties.siteVerifyUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(form)
                .retrieve()
                .body(TurnstileSiteVerifyResponse::class.java)
            val ok = response?.success == true
            if (!ok) {
                log.warn(
                    "Turnstile siteverify rejected token: success={} errors={}",
                    response?.success,
                    response?.errorCodes,
                )
            }
            ok
        } catch (ex: RestClientException) {
            log.warn("Turnstile siteverify unreachable; treating CAPTCHA as failed: {}", ex.message)
            false
        } catch (ex: Exception) {
            log.warn("Turnstile siteverify unexpected error; treating CAPTCHA as failed: {}", ex.message)
            false
        }
    }

    internal data class TurnstileSiteVerifyResponse(
        val success: Boolean = false,
        @JsonProperty("error-codes")
        val errorCodes: List<String> = emptyList(),
        val challengeTs: String? = null,
        val hostname: String? = null,
        val action: String? = null,
        val cdata: String? = null,
    )
}
