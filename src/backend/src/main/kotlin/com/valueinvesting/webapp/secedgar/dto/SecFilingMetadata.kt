package com.valueinvesting.webapp.secedgar.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDate

// DTO per metadata di un filing SEC EDGAR (10-K, 10-Q, 8-K, ecc.).
//
// Tutti i campi sono nullable per allinearsi al principio US-004 "campi mancanti
// = assenti, mai 0". La SEC API espone gli array `filings.recent.{accessionNumber,
// form, filingDate, primaryDocument, ...}` come parallel arrays di stringhe;
// l'adapter `SecEdgarRestClient` zippa per indice e popola questo DTO.
//
// `filedAt` come `LocalDate` perché il campo `filingDate` SEC è in formato ISO
// `yyyy-MM-dd` (verificato empiricamente su CIK0000320193, AAPL — 2026-05-25).
// Il `JavaTimeModule` è registrato in `JacksonConfig` quindi la deserializzazione
// funziona out-of-the-box.
//
// `primaryDocumentUrl` è la URL **assoluta** ricostruita dall'adapter
// (`https://www.sec.gov/Archives/edgar/data/{cikNumeric}/{accessionNoDashes}/{primaryDocument}`)
// e non il path relativo grezzo restituito dalla SEC API.
//
// `@JsonIgnoreProperties(ignoreUnknown = true)` per tolleranza ai campi extra
// dello schema SEC (in continua evoluzione; vedi `reportDate`,
// `acceptanceDateTime`, `act`, `fileNumber`, `filmNumber`, `items`, `core_type`,
// `size`, `isXBRL`, `isInlineXBRL`, `isXBRLNumeric` che NON ci servono).
//
// [^src: wiki/concepts/sec-filings-analysis.md §Dettaglio]
// [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-038-sec-edgar-adapter/TSK-091.md]
@JsonIgnoreProperties(ignoreUnknown = true)
data class SecFilingMetadata(
    val accessionNumber: String? = null,
    val formType: String? = null,
    val filedAt: LocalDate? = null,
    val primaryDocumentUrl: String? = null,
)
