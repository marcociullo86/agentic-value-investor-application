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

// Sollevata da DeepAnalysisService.analyze quando il client richiede
// invoke_llm=true ma non esistono chunk indicizzati per il ticker (nessun
// INGEST ancora eseguito o INGEST FAILED). Diversa da NoSecFilingsException
// che indica assenza di filing presso SEC: qui i filing potrebbero anche
// esistere ma l'utente deve esplicitamente lanciare l'INGEST prima di
// chiedere l'ANALYSIS-with-LLM. Mappata a 409 reason=not_indexed sia in
// GlobalExceptionHandler che in DeepAnalysisRunExecutor.
class FilingsNotIndexedException(
    val ticker: String,
    message: String = "Filings not indexed for ticker '$ticker'. Run INGEST before requesting LLM analysis.",
    cause: Throwable? = null,
) : RuntimeException(message, cause)
