package com.valueinvesting.webapp.llm

import org.slf4j.LoggerFactory

// Placeholder stub active when ANTHROPIC_API_KEY is not configured (dev/test).
// Returns a valid JSON classification so downstream services don't crash.
// The real AnthropicRestClient is activated by setting the env var.
//
// Bean lifecycle managed by AnthropicConfig (not component-scanned).
class AnthropicClientStub : AnthropicClient {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun complete(request: LlmRequest): LlmResponse {
        log.warn(
            "AnthropicClientStub invoked — returning stub response. " +
                "Set env var ANTHROPIC_API_KEY for the real client.",
        )
        return LlmResponse(
            content = """{"classe": "NEUTRAL", "motivazione": "Stub — no LLM backend configured"}""",
            inputTokens = 0,
            outputTokens = 0,
            stopReason = "end_turn",
            model = "stub",
        )
    }
}
