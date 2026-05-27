package com.valueinvesting.webapp.llm

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.retry.Retry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.web.client.RestClient

// Spring configuration for the Anthropic Claude client bean.
//
// If ANTHROPIC_API_KEY is set (non-blank) → AnthropicRestClient (real HTTP).
// If ANTHROPIC_API_KEY is absent/blank  → AnthropicClientStub (dev/test fallback).
//
// AnthropicSdkClient (conditional on property + classpath) is a future extension
// when the official SDK reaches GA on Maven Central — see ADR-017 §3.
//
// [^src: design_&_architecture/decisions/ADR-017-anthropic-sdk-jvm.md §3]
// [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-041-munger-inversion-llm/TSK-104.md §2]
@Configuration
@EnableConfigurationProperties(AnthropicProperties::class, LlmPricingProperties::class)
class AnthropicConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    @Primary
    fun anthropicClient(
        restClientBuilder: RestClient.Builder,
        properties: AnthropicProperties,
        objectMapper: ObjectMapper,
        @Qualifier("llmCircuitBreaker") circuitBreaker: CircuitBreaker,
        @Qualifier("llmRateLimiter") rateLimiter: RateLimiter,
        @Qualifier("llmRetry") retry: Retry,
        budgetGuard: LlmBudgetGuard,
        costCounterService: LlmCostCounterService,
    ): AnthropicClient {
        if (properties.apiKey.isBlank()) {
            log.warn(
                "ANTHROPIC_API_KEY not set — using AnthropicClientStub. " +
                    "Set env var ANTHROPIC_API_KEY for production.",
            )
            return AnthropicClientStub()
        }
        log.info(
            "Anthropic client configured: model={}, baseUrl={}, timeoutSeconds={}",
            properties.model,
            properties.baseUrl,
            properties.timeoutSeconds,
        )
        return AnthropicRestClient(
            restClientBuilder, properties, objectMapper,
            circuitBreaker, rateLimiter, retry,
            budgetGuard, costCounterService,
        )
    }
}
