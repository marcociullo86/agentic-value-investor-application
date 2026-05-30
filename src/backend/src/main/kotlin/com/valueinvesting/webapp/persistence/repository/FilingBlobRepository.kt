package com.valueinvesting.webapp.persistence.repository

import com.valueinvesting.webapp.persistence.entity.FilingBlobEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface FilingBlobRepository : JpaRepository<FilingBlobEntity, Long> {

    fun findByAccessionNumber(accessionNumber: String): FilingBlobEntity?

    fun findByAccessionNumberAndExpiresAtAfter(
        accessionNumber: String,
        now: Instant,
    ): FilingBlobEntity?

    fun findByTickerAndExpiresAtAfterOrderByFilingDateDesc(
        ticker: String,
        now: Instant,
    ): List<FilingBlobEntity>

    // Variante senza filtro di scadenza, usata da DeepAnalysisService.analyze
    // per popolare `filingsUsed` nel response: l'ANALYSIS NON deve scaricare
    // né reindicizzare (lo fa l'INGEST), quindi qui ci serve la lista di
    // qualunque blob già in cache per ticker, scaduto o no — il dato è solo
    // di reporting (accession number / form type / data filing).
    fun findByTickerOrderByFilingDateDesc(ticker: String): List<FilingBlobEntity>
}
