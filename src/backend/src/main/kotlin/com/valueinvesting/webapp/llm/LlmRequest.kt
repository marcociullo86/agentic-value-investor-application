package com.valueinvesting.webapp.llm

// [^src: design_&_architecture/decisions/ADR-017-anthropic-sdk-jvm.md §1]
data class LlmRequest(
    val systemPrompt: String,
    val userPrompt: String,
    val maxTokens: Int = DEFAULT_MAX_TOKENS,
    // Blank ⇒ il client risolve il modello da `anthropic.model` (env ANTHROPIC_MODEL).
    // Valorizzare solo per forzare un modello specifico su una singola chiamata.
    val model: String = "",
) {
    companion object {
        const val DEFAULT_MAX_TOKENS = 2000
    }
}
