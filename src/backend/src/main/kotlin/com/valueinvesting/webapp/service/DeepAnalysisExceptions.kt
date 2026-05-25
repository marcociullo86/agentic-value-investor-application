package com.valueinvesting.webapp.service

class NoSecFilingsException(
    val ticker: String,
    message: String = "No SEC filings available for ticker: $ticker",
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class LlmUnavailableException(
    val ticker: String,
    message: String = "LLM service unavailable during deep analysis for ticker: $ticker",
    cause: Throwable? = null,
) : RuntimeException(message, cause)
