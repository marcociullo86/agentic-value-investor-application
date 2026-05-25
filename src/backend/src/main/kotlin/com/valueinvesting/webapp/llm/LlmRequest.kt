package com.valueinvesting.webapp.llm

// [^src: design_&_architecture/decisions/ADR-017-anthropic-sdk-jvm.md §1]
data class LlmRequest(
    val systemPrompt: String,
    val userPrompt: String,
    val maxTokens: Int = DEFAULT_MAX_TOKENS,
    val model: String = DEFAULT_MODEL,
    // temperature/top_p/top_k volutamente assenti — Opus 4.7 li rifiuta (HTTP 400).
    // Il modello applica Adaptive Thinking internamente.
) {
    companion object {
        const val DEFAULT_MAX_TOKENS = 2000
        const val DEFAULT_MODEL = "claude-opus-4-7"
    }
}
