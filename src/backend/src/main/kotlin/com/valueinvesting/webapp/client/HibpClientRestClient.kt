package com.valueinvesting.webapp.client

import com.valueinvesting.webapp.config.AppProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.security.MessageDigest

/**
 * Sync HIBP range client on Spring 6 [RestClient] (coerente con FMP/SEC adapters).
 *
 * Resilience: se l'API non è raggiungibile → log warning e `false` (password ammessa).
 * [^src: management/kanban/EP-018-hardening-sicurezza-compliance/US-081-protezione-identita-accesso/TSK-231.md §Technical Specs]
 */
@Component
class HibpClientRestClient(
    restClientBuilder: RestClient.Builder,
    private val appProperties: AppProperties,
) : HibpClient {

    private val log = LoggerFactory.getLogger(javaClass)

    private val client: RestClient by lazy {
        val baseUrl = appProperties.security.hibp.apiUrl.trimEnd('/') + "/"
        restClientBuilder
            .baseUrl(baseUrl)
            .defaultHeader("User-Agent", "ValueInvesting-App-HibpClient/1.0")
            .build()
    }

    override fun isPasswordCompromised(plainPassword: String): Boolean {
        if (!appProperties.security.hibp.enabled) {
            return false
        }
        val (prefix, suffix) = sha1PrefixAndSuffix(plainPassword)
        return try {
            val body = client.get()
                .uri(prefix)
                .retrieve()
                .body(String::class.java)
                ?: return false
            responseContainsSuffix(body, suffix)
        } catch (ex: RestClientException) {
            log.warn("HIBP range API unavailable; allowing password (graceful degradation): {}", ex.message)
            false
        } catch (ex: Exception) {
            log.warn("HIBP check failed unexpectedly; allowing password (graceful degradation): {}", ex.message)
            false
        }
    }

    internal fun sha1PrefixAndSuffix(plainPassword: String): Pair<String, String> {
        val digest = MessageDigest.getInstance("SHA-1")
        val hashBytes = digest.digest(plainPassword.toByteArray(Charsets.UTF_8))
        val hashHex = hashBytes.joinToString("") { "%02X".format(it) }
        return hashHex.substring(0, SHA1_PREFIX_LENGTH) to hashHex.substring(SHA1_PREFIX_LENGTH)
    }

    internal fun responseContainsSuffix(responseBody: String, suffix: String): Boolean {
        val target = suffix.uppercase()
        return responseBody.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .any { line ->
                val colon = line.indexOf(':')
                if (colon <= 0) {
                    false
                } else {
                    line.substring(0, colon).equals(target, ignoreCase = true)
                }
            }
    }

    private companion object {
        const val SHA1_PREFIX_LENGTH = 5
    }
}
