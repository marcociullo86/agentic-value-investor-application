package com.valueinvesting.webapp.service

import com.valueinvesting.webapp.api.model.TickerResetResult
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

/**
 * Reset distruttivo dei dati deep-analysis di un ticker: cancella cache e dati
 * derivati così che una successiva analisi riparta da zero (re-ingest filing,
 * re-run Munger/news/price). Gated da master password (MasterPasswordService).
 *
 * Tabelle impattate (tutte hanno colonna `ticker`), in ordine FK-safe:
 *   filing_chunks → filing_blob (FK ON DELETE CASCADE, esplicitato),
 *   deep_analysis_report, deep_analysis_run, deep_analysis_event_log,
 *   news_classification, price_action_snapshot.
 * NON tocca `stocks` (il ticker resta registrato) né le cache FMP raw (fmp_*).
 */
@Service
class TickerResetService(
    private val masterPasswordService: MasterPasswordService,
    private val jdbcTemplate: JdbcTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        // Nomi tabella STATICI (nessuna injection): solo `ticker` è parametrizzato.
        private val TABLES = listOf(
            "filing_chunks",
            "filing_blob",
            "deep_analysis_report",
            "deep_analysis_run",
            "deep_analysis_event_log",
            "news_classification",
            "price_action_snapshot",
        )
    }

    @Transactional
    fun reset(ticker: String, masterPassword: String): TickerResetResult {
        if (!masterPasswordService.verify(masterPassword)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Master password non valida")
        }
        val t = ticker.trim().uppercase()
        require(t.isNotBlank()) { "ticker must not be blank" }

        val deleted = LinkedHashMap<String, Int>()
        TABLES.forEach { table ->
            deleted[table] = jdbcTemplate.update("DELETE FROM $table WHERE ticker = ?", t)
        }
        val total = deleted.values.sum()
        log.warn("Ticker reset for {} — deleted rows per table: {} (total {})", t, deleted, total)
        return TickerResetResult(ticker = t, deletedByTable = deleted, totalDeleted = total)
    }
}
