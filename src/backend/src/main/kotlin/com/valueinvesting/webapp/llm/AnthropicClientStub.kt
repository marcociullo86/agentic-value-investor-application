package com.valueinvesting.webapp.llm

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Placeholder stub until TSK-104 (AnthropicRestClient) is implemented.
 * The real implementation will be @Primary and override this bean.
 * Returns a valid JSON classification so NewsSentimentService doesn't crash.
 */
@Component
class AnthropicClientStub : AnthropicClient {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun complete(prompt: String, maxTokens: Int): String {
        log.warn("AnthropicClientStub invoked — returning NEUTRAL. Replace with real client (TSK-104).")
        return """{"classe": "NEUTRAL", "motivazione": "Stub — no LLM backend configured"}"""
    }
}
