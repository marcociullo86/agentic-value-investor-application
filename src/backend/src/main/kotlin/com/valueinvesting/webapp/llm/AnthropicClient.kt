package com.valueinvesting.webapp.llm

// Interface for LLM completions via Anthropic Claude.
//
// Concrete implementations:
//   - AnthropicRestClient  (HTTP direct, default — always available)
//   - AnthropicSdkClient   (official SDK, optional — requires classpath + property)
//   - AnthropicClientStub  (dev/test fallback when ANTHROPIC_API_KEY is absent)
//
// Caller contract: all callers (MungerInversionService, NewsSentimentClassifier,
// NewsScoutService) depend ONLY on this interface — never on a concrete impl.
//
// [^src: design_&_architecture/decisions/ADR-017-anthropic-sdk-jvm.md §1]
interface AnthropicClient {

    /**
     * Sends a completion request to Claude and returns the structured response.
     * Throws [LlmException] subtypes on API errors; Resilience4j exceptions
     * ([io.github.resilience4j.ratelimiter.RequestNotPermitted],
     * [io.github.resilience4j.circuitbreaker.CallNotPermittedException]) on
     * resilience gate failures.
     */
    fun complete(request: LlmRequest): LlmResponse

    /**
     * Backward-compatible convenience method for callers using the simple
     * (prompt, maxTokens) signature (e.g. NewsSentimentService).
     * Delegates to [complete] with an empty system prompt.
     */
    fun complete(prompt: String, maxTokens: Int = DEFAULT_MAX_TOKENS): String =
        complete(
            LlmRequest(
                systemPrompt = "",
                userPrompt = prompt,
                maxTokens = maxTokens,
            ),
        ).content

    companion object {
        const val DEFAULT_MAX_TOKENS = 1024
    }
}
