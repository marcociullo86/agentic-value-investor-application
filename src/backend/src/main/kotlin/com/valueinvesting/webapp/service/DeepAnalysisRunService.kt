package com.valueinvesting.webapp.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.valueinvesting.webapp.api.model.DeepAnalysisResponse
import com.valueinvesting.webapp.api.model.DeepAnalysisRunStatusResponse
import com.valueinvesting.webapp.api.model.LatestDeepAnalysisResponse
import com.valueinvesting.webapp.api.model.RunError
import com.valueinvesting.webapp.persistence.entity.DeepAnalysisRunEntity
import com.valueinvesting.webapp.persistence.repository.DeepAnalysisRunRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Instant

// Orchestratore dell'execution model asincrono della deep analysis:
//
//   enqueue(ticker, invokeLlm) — chiamato dal POST /runs:
//     - normalizza ticker (uppercase)
//     - se esiste una run RUNNING per quel ticker → dedupe: ritorna lo stato
//       della run esistente (niente nuova row, niente nuova chiamata @Async).
//       Rationale: la deep analysis impiega minuti; un retry del client
//       non deve moltiplicare il carico LLM/embedding per lo stesso ticker.
//     - altrimenti crea una row status=RUNNING, requested_at=now, salva,
//       delega all'executor @Async e ritorna lo stato della nuova run.
//
//   getLatest(ticker) — chiamato dal GET /latest:
//     - cerca l'ultima run per ticker; se nessuna → status=NONE.
//     - su SUCCESS deserializza result_json nel DTO DeepAnalysisResponse;
//       in caso di parse error (schema drift), declassa a errore interno
//       senza fallire la response (status="FAILED", reason=internal_error).
//
// NOTA self-invocation: il bean dell'executor (DeepAnalysisRunExecutor) è
// iniettato e invocato da qui — il metodo @Async vive in un bean separato,
// quindi il proxy Spring AOP si attiva correttamente.
@Service
class DeepAnalysisRunService(
    private val deepAnalysisRunRepository: DeepAnalysisRunRepository,
    private val deepAnalysisRunExecutor: DeepAnalysisRunExecutor,
    private val objectMapper: ObjectMapper,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun enqueue(ticker: String, invokeLlm: Boolean): DeepAnalysisRunStatusResponse {
        val t = ticker.uppercase()

        val existing = deepAnalysisRunRepository
            .findFirstByTickerAndStatusOrderByRequestedAtDesc(t, "RUNNING")
        if (existing != null) {
            log.info(
                "DeepAnalysisRunService.enqueue dedupe: existing RUNNING run found ticker={} runId={}",
                t, existing.id,
            )
            return DeepAnalysisRunStatusResponse(
                runId = existing.id.toString(),
                ticker = existing.ticker,
                status = existing.status,
                invokeLlm = existing.invokeLlm,
            )
        }

        val entity = DeepAnalysisRunEntity(
            ticker = t,
            status = "RUNNING",
            invokeLlm = invokeLlm,
            requestedAt = Instant.now(),
        )
        val saved = deepAnalysisRunRepository.save(entity)
        log.info(
            "DeepAnalysisRunService.enqueue created run ticker={} runId={} invokeLlm={}",
            t, saved.id, invokeLlm,
        )

        // Delega all'executor @Async (bean separato → proxy AOP attivo).
        // Race-condition fix: lanciamo il task DOPO il commit della transazione
        // di enqueue. Se schedulassimo `execute(saved.id)` inline, il thread
        // @Async potrebbe partire prima del commit e il suo `findById(runId)`
        // non vedrebbe la nuova row (READ_COMMITTED su Postgres) → log warn
        // "run not found" + run perduta. Se nessuna transazione è attiva
        // (es. unit test che chiama enqueue senza @Transactional), partiamo
        // immediatamente.
        val runId = saved.id
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        deepAnalysisRunExecutor.execute(runId)
                    }
                },
            )
        } else {
            deepAnalysisRunExecutor.execute(runId)
        }

        return DeepAnalysisRunStatusResponse(
            runId = saved.id.toString(),
            ticker = saved.ticker,
            status = saved.status,
            invokeLlm = saved.invokeLlm,
        )
    }

    @Transactional(readOnly = true)
    fun getLatest(ticker: String): LatestDeepAnalysisResponse {
        val t = ticker.uppercase()
        val latest = deepAnalysisRunRepository.findFirstByTickerOrderByRequestedAtDesc(t)

        if (latest == null) {
            return LatestDeepAnalysisResponse(
                ticker = t,
                status = "NONE",
                runId = null,
                invokeLlm = false,
                requestedAt = null,
                completedAt = null,
                result = null,
                error = null,
            )
        }

        val result: DeepAnalysisResponse? = if (latest.status == "SUCCESS" && latest.resultJson != null) {
            try {
                objectMapper.readValue(latest.resultJson, DeepAnalysisResponse::class.java)
            } catch (ex: Exception) {
                log.warn(
                    "DeepAnalysisRunService.getLatest: failed to deserialize result_json runId={}: {}",
                    latest.id, ex.message,
                )
                null
            }
        } else {
            null
        }

        val error: RunError? = if (latest.status == "FAILED") {
            RunError(
                reason = latest.errorReason ?: "internal_error",
                message = latest.errorMessage,
            )
        } else {
            null
        }

        // Se il payload SUCCESS non è deserializzabile (schema drift), riportiamo
        // lo stato come FAILED/internal_error verso il client invece di restituire
        // un result=null silenzioso che il FE interpreterebbe come "in corso".
        val effectiveStatus: String
        val effectiveError: RunError?
        if (latest.status == "SUCCESS" && result == null) {
            effectiveStatus = "FAILED"
            effectiveError = RunError(
                reason = "internal_error",
                message = "Stored result is not deserializable",
            )
        } else {
            effectiveStatus = latest.status
            effectiveError = error
        }

        return LatestDeepAnalysisResponse(
            ticker = latest.ticker,
            status = effectiveStatus,
            runId = latest.id.toString(),
            invokeLlm = latest.invokeLlm,
            requestedAt = latest.requestedAt,
            completedAt = latest.completedAt,
            result = result,
            error = effectiveError,
        )
    }
}
