package com.valueinvesting.webapp.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.valueinvesting.webapp.fmp.FmpTickerNotFoundException
import com.valueinvesting.webapp.persistence.repository.DeepAnalysisRunRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

// Async executor della deep-analysis pipeline (componente separato da
// DeepAnalysisRunService per evitare self-invocation di @Async: una chiamata
// metodo→metodo nello stesso bean bypasserebbe il proxy AOP e l'esecuzione
// resterebbe sincrona).
//
// Flow per run:
//   1. carica row, marca RUNNING + started_at, salva (REQUIRES_NEW così
//      l'aggiornamento è visibile anche se la run continua a girare a lungo).
//   2. invoca DeepAnalysisService.analyze; su success serializza la response
//      in result_json e marca SUCCESS + completed_at.
//   3. su qualunque eccezione di dominio o generica → marca FAILED con
//      error_reason allineato a GlobalExceptionHandler e error_message.
//   4. try/finally: garantisce che nessuna row resti RUNNING in caso di
//      Throwable non previsti (OOM, errori non-Exception). Se l'aggiornamento
//      finale fallisce a sua volta, logga ma non rilancia (siamo già in un
//      @Async, non c'è caller a cui propagare).
//
// Le reason matchano 1:1 quelle esposte in GlobalExceptionHandler per il GET
// sincrono, così il FE può riusare la stessa logica di rendering dei codici
// d'errore sui risultati persistiti.
@Component
class DeepAnalysisRunExecutor(
    private val deepAnalysisService: DeepAnalysisService,
    private val deepAnalysisRunRepository: DeepAnalysisRunRepository,
    private val objectMapper: ObjectMapper,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Async("deepAnalysisExecutor")
    fun execute(runId: UUID) {
        val startMs = System.currentTimeMillis()
        val run = deepAnalysisRunRepository.findById(runId).orElse(null)
        if (run == null) {
            log.warn("DeepAnalysisRunExecutor: run not found id={}", runId)
            return
        }
        val ticker = run.ticker
        val invokeLlm = run.invokeLlm

        log.info("DeepAnalysis async start: runId={} ticker={} invokeLlm={}", runId, ticker, invokeLlm)

        markRunning(runId)

        var settled = false
        try {
            val response = deepAnalysisService.analyze(ticker, invokeLlm)
            val json = objectMapper.writeValueAsString(response)
            markSuccess(runId, json)
            settled = true
            val durationMs = System.currentTimeMillis() - startMs
            log.info(
                "DeepAnalysis async SUCCESS: runId={} ticker={} durationMs={}",
                runId, ticker, durationMs,
            )
        } catch (ex: FmpTickerNotFoundException) {
            markFailedSafe(runId, ticker, "not_found", ex)
            settled = true
        } catch (ex: NoSecFilingsException) {
            markFailedSafe(runId, ticker, "no_sec_filings", ex)
            settled = true
        } catch (ex: LlmUnavailableException) {
            markFailedSafe(runId, ticker, "llm_unavailable", ex)
            settled = true
        } catch (ex: EmbeddingServiceUnavailableException) {
            markFailedSafe(runId, ticker, "embedding_unavailable", ex)
            settled = true
        } catch (ex: EmbeddingTimeoutException) {
            markFailedSafe(runId, ticker, "embedding_unavailable", ex)
            settled = true
        } catch (ex: Exception) {
            markFailedSafe(runId, ticker, "internal_error", ex)
            settled = true
        } finally {
            // Robustezza: se per qualche motivo non siamo passati né dal ramo
            // success né da un catch (es. Throwable non-Exception come Error),
            // marchiamo FAILED con reason generico così la row non resta
            // RUNNING per sempre.
            if (!settled) {
                try {
                    markFailedNoCause(runId, "internal_error", "Unsettled execution")
                } catch (inner: Exception) {
                    log.error(
                        "DeepAnalysis async: failed to finalize unsettled run runId={}: {}",
                        runId, inner.message, inner,
                    )
                }
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markRunning(runId: UUID) {
        val run = deepAnalysisRunRepository.findById(runId).orElse(null) ?: return
        run.status = "RUNNING"
        run.startedAt = Instant.now()
        deepAnalysisRunRepository.save(run)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markSuccess(runId: UUID, resultJson: String) {
        val run = deepAnalysisRunRepository.findById(runId).orElse(null) ?: return
        run.status = "SUCCESS"
        run.resultJson = resultJson
        run.completedAt = Instant.now()
        run.errorReason = null
        run.errorMessage = null
        deepAnalysisRunRepository.save(run)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markFailed(runId: UUID, reason: String, errorMessage: String?) {
        val run = deepAnalysisRunRepository.findById(runId).orElse(null) ?: return
        run.status = "FAILED"
        run.errorReason = reason
        run.errorMessage = errorMessage
        run.completedAt = Instant.now()
        deepAnalysisRunRepository.save(run)
    }

    private fun markFailedSafe(runId: UUID, ticker: String, reason: String, ex: Exception) {
        log.warn(
            "DeepAnalysis async FAILED: runId={} ticker={} reason={} message={}",
            runId, ticker, reason, ex.message,
        )
        try {
            markFailed(runId, reason, ex.message)
        } catch (inner: Exception) {
            log.error(
                "DeepAnalysis async: failed to persist FAILED state runId={}: {}",
                runId, inner.message, inner,
            )
        }
    }

    private fun markFailedNoCause(runId: UUID, reason: String, message: String) {
        markFailed(runId, reason, message)
    }
}
