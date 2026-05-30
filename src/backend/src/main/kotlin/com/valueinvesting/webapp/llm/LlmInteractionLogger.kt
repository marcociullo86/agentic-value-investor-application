package com.valueinvesting.webapp.llm

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Logger gated delle interazioni LLM (US-088 / TSK-299): quando attivo, scrive nei
 * log il system prompt, lo user prompt e la risposta raw di ogni chiamata LLM della
 * deep analysis (10 query Munger + 1 sintesi + sintesi news), con troncamento.
 *
 * Default OFF: in produzione l'attivazione è esplicita via
 * `DEEP_ANALYSIS_LLM_LOG_INTERACTIONS=true` (vedi .env / docker-compose). I prompt
 * contengono solo dati pubblici (filing SEC, news); nessun PII.
 *
 * I parametri del costruttore hanno default così i test unitari che istanziano
 * direttamente i servizi (es. MungerInversionAnalyzerTest) ottengono un logger
 * disabilitato senza dover passare la dipendenza; Spring inietta comunque il bean
 * configurato.
 * [^src: management/kanban/EP-020-trasparenza-analisi-llm/US-088-log-interazioni-llm/TSK-299.md]
 */
@Component
class LlmInteractionLogger(
    @Value("\${deep-analysis.llm.log-interactions:false}") private val enabled: Boolean = false,
    @Value("\${deep-analysis.llm.log-max-chars:8000}") private val maxChars: Int = 8000,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun log(
        context: String,
        systemPrompt: String?,
        userPrompt: String,
        response: String,
        durationMs: Long? = null,
    ) {
        if (!enabled) return
        log.info(
            "LLM interaction [{}] durationMs={}\n--- SYSTEM PROMPT ---\n{}\n--- USER PROMPT ---\n{}\n--- RESPONSE ---\n{}",
            context,
            durationMs ?: -1,
            truncate(systemPrompt),
            truncate(userPrompt),
            truncate(response),
        )
    }

    private fun truncate(s: String?): String {
        if (s.isNullOrEmpty()) return ""
        return if (s.length <= maxChars) s else s.take(maxChars) + "…[troncato ${s.length - maxChars} char]"
    }
}
