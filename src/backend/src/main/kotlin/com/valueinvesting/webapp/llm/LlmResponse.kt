package com.valueinvesting.webapp.llm

// [^src: design_&_architecture/decisions/ADR-017-anthropic-sdk-jvm.md §1]
data class LlmResponse(
    val content: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val stopReason: String,
    val model: String,
)
