package com.valueinvesting.webapp.llm

/**
 * Interface for LLM completions via Anthropic Claude.
 * Concrete implementations: AnthropicRestClient (HTTP default),
 * AnthropicSdkClient (optional via @ConditionalOnClass).
 * [^src: design_&_architecture/decisions/ADR-017]
 */
interface AnthropicClient {
    fun complete(prompt: String, maxTokens: Int = 1024): String
}
