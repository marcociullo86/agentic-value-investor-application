package com.valueinvesting.webapp.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.valueinvesting.webapp.api.model.DeepAnalysisResponse
import com.valueinvesting.webapp.api.model.DeepAnalysisRunStatusResponse
import com.valueinvesting.webapp.api.model.IngestStatusResponse
import com.valueinvesting.webapp.api.model.IngestSummary
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
import java.util.UUID

// Orchestratore dell'execution model asincrono della deep analysis post-split
// EP-011 (V028). Due famiglie di operazioni, stessa tabella, stesso executor:
//
//   enqueueAnalysis(ticker, invokeLlm) — chiamato dal POST /deep/runs:
//     - normalizza ticker (uppercase)
//     - dedupe su (ticker, kind=ANALYSIS, status=RUNNING): retry del client
//       sulla stessa ANALYSIS in corso non moltiplica il carico LLM.
//     - crea una row kind=ANALYSIS, delega all'executor (afterCommit).
//
//   enqueueIngest(ticker) — chiamato dal POST /deep/ingest:
//     - stesso pattern, kind=INGEST, invokeLlm=false (l'INGEST non chiama LLM).
//     - dedupe limitato a INGEST RUNNING (può coesistere con ANALYSIS RUNNING).
//
//   getLatestAnalysis(ticker) — chiamato dal GET /deep/latest:
//     - ultima run kind=ANALYSIS; status=NONE se nessuna.
//     - su SUCCESS deserializza in DeepAnalysisResponse (degrade a internal_error
//       se il payload memorizzato non è più deserializzabile — schema drift).
//
//   getLatestIngest(ticker) — chiamato dal GET /deep/ingest/latest:
//     - ultima run kind=INGEST; status=NONE se nessuna.
//     - su SUCCESS deserializza in IngestSummary (stesso pattern di degrade).
//
// NOTA self-invocation: l'executor è un bean separato → proxy AOP @Async
// attivo. La sync afterCommit evita race con READ_COMMITTED sul findById.
@Service
class DeepAnalysisRunService(
    private val deepAnalysisRunRepository: DeepAnalysisRunRepository,
    private val deepAnalysisRunExecutor: DeepAnalysisRunExecutor,
    private val objectMapper: ObjectMapper,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val KIND_ANALYSIS = "ANALYSIS"
        private const val KIND_INGEST = "INGEST"
        private const val STATUS_RUNNING = "RUNNING"
    }

    // ── ANALYSIS family ────────────────────────────────────────────────────

    // Manteniamo `enqueue` come thin alias di enqueueAnalysis per non rompere
    // chiamanti storici (test, eventuale codice legacy). Tutti i call-site
    // andrebbero migrati a enqueueAnalysis nel tempo.
    @Transactional
    fun enqueue(ticker: String, invokeLlm: Boolean): DeepAnalysisRunStatusResponse =
        enqueueAnalysis(ticker, invokeLlm)

    @Transactional
    fun enqueueAnalysis(ticker: String, invokeLlm: Boolean): DeepAnalysisRunStatusResponse {
        return doEnqueue(ticker = ticker, kind = KIND_ANALYSIS, invokeLlm = invokeLlm)
    }

    @Transactional(readOnly = true)
    fun getLatest(ticker: String): LatestDeepAnalysisResponse = getLatestAnalysis(ticker)

    @Transactional(readOnly = true)
    fun getLatestAnalysis(ticker: String): LatestDeepAnalysisResponse {
        val t = ticker.uppercase()
        val latest = deepAnalysisRunRepository
            .findFirstByTickerAndKindOrderByRequestedAtDesc(t, KIND_ANALYSIS)

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
                    "DeepAnalysisRunService.getLatestAnalysis: failed to deserialize result_json runId={}: {}",
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
        // FAILED/internal_error invece di un result=null silenzioso che il FE
        // interpreterebbe come "in corso".
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

    // ── INGEST family ──────────────────────────────────────────────────────

    @Transactional
    fun enqueueIngest(ticker: String): DeepAnalysisRunStatusResponse {
        // L'INGEST non invoca LLM mai: invokeLlm=false hard-coded così la
        // semantica del flag resta limitata al ramo ANALYSIS.
        return doEnqueue(ticker = ticker, kind = KIND_INGEST, invokeLlm = false)
    }

    @Transactional(readOnly = true)
    fun getLatestIngest(ticker: String): IngestStatusResponse {
        val t = ticker.uppercase()
        val latest = deepAnalysisRunRepository
            .findFirstByTickerAndKindOrderByRequestedAtDesc(t, KIND_INGEST)

        if (latest == null) {
            return IngestStatusResponse(
                ticker = t,
                status = "NONE",
                runId = null,
                requestedAt = null,
                completedAt = null,
                summary = null,
                error = null,
            )
        }

        val summary: IngestSummary? = if (latest.status == "SUCCESS" && latest.resultJson != null) {
            try {
                objectMapper.readValue(latest.resultJson, IngestSummary::class.java)
            } catch (ex: Exception) {
                log.warn(
                    "DeepAnalysisRunService.getLatestIngest: failed to deserialize result_json runId={}: {}",
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

        // Stesso degrade pattern di getLatestAnalysis: SUCCESS con payload
        // non parsabile → FAILED/internal_error visibile al FE.
        val effectiveStatus: String
        val effectiveError: RunError?
        if (latest.status == "SUCCESS" && summary == null) {
            effectiveStatus = "FAILED"
            effectiveError = RunError(
                reason = "internal_error",
                message = "Stored ingest summary is not deserializable",
            )
        } else {
            effectiveStatus = latest.status
            effectiveError = error
        }

        return IngestStatusResponse(
            ticker = latest.ticker,
            status = effectiveStatus,
            runId = latest.id.toString(),
            requestedAt = latest.requestedAt,
            completedAt = latest.completedAt,
            summary = summary,
            error = effectiveError,
        )
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun doEnqueue(
        ticker: String,
        kind: String,
        invokeLlm: Boolean,
    ): DeepAnalysisRunStatusResponse {
        val t = ticker.uppercase()

        val existing = deepAnalysisRunRepository
            .findFirstByTickerAndKindAndStatusOrderByRequestedAtDesc(t, kind, STATUS_RUNNING)
        if (existing != null) {
            log.info(
                "DeepAnalysisRunService.enqueue dedupe: existing RUNNING run found ticker={} kind={} runId={}",
                t, kind, existing.id,
            )
            return DeepAnalysisRunStatusResponse(
                runId = existing.id.toString(),
                ticker = existing.ticker,
                kind = existing.kind,
                status = existing.status,
                invokeLlm = existing.invokeLlm,
            )
        }

        val entity = DeepAnalysisRunEntity(
            ticker = t,
            kind = kind,
            status = STATUS_RUNNING,
            invokeLlm = invokeLlm,
            requestedAt = Instant.now(),
        )
        val saved = deepAnalysisRunRepository.save(entity)
        log.info(
            "DeepAnalysisRunService.enqueue created run ticker={} kind={} runId={} invokeLlm={}",
            t, kind, saved.id, invokeLlm,
        )

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
            kind = saved.kind,
            status = saved.status,
            invokeLlm = saved.invokeLlm,
        )
    }
}
