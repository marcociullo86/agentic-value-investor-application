package com.valueinvesting.webapp.secedgar

import com.valueinvesting.webapp.secedgar.dto.SecFilingMetadata

// Interface to SEC EDGAR (Securities and Exchange Commission, US).
// Recupero metadata + download HTML dei filing 10-K (annual) e 10-Q (quarterly).
//
// L'API SEC è fair-access-policy: 10 req/s per User-Agent identificato (email
// valida obbligatoria). Tutti i metodi vanno chiamati attraverso il decorator
// `ResilientSecEdgarAdapter` che gate i token Resilience4j. Vedi
// `SecEdgarResilienceConfig.kt` per la chain.
//
// Endpoint sottostanti (empiricamente verificati 2026-05-25):
//   - GET https://www.sec.gov/files/company_tickers.json  → ticker→CIK map
//   - GET https://data.sec.gov/submissions/CIK{padded10}.json  → recent filings
//   - GET https://www.sec.gov/Archives/edgar/data/{cik}/{acc}/{doc}  → HTML
//
// [^src: wiki/concepts/sec-filings-analysis.md §Accesso ai filing]
// [^src: wiki/runbooks/sec-10k-10q-analysis-playbook.md §Prerequisiti]
// [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-038-sec-edgar-adapter/TSK-091.md]
interface SecEdgarAdapter {

    /**
     * Risolve il CIK SEC a 10 cifre (zero-padded) a partire dal ticker.
     * Esempio: "AAPL" → "0000320193".
     *
     * Ritorna null se il ticker non è presente nel ticker-CIK map SEC.
     * (Equivalente a "ticker non quotato SEC" — può accadere per ETF/ADR fuori
     * dalla copertura `company_tickers.json`.)
     *
     * @param ticker simbolo case-insensitive (l'implementazione normalizza a uppercase).
     */
    fun resolveCikFromTicker(ticker: String): String?

    /**
     * Lista i filing SEC più recenti per il CIK passato, filtrati per `formTypes`.
     * Ordine: dal più recente al più vecchio (come ritornato da SEC).
     *
     * @param cik CIK a 10 cifre zero-padded (es. "0000320193"). NON normalizzato.
     * @param formTypes lista form da includere; default ["10-K", "10-Q"] per analisi VI.
     * @param limit numero massimo di record da ritornare (default 10).
     *              Cap pratico ~250 (gli `recent` SEC contengono fino a ~1000 record).
     */
    fun listFilings(
        cik: String,
        formTypes: List<String> = listOf("10-K", "10-Q"),
        limit: Int = 10,
    ): List<SecFilingMetadata>

    /**
     * Scarica il body HTML del primary document di un filing.
     *
     * @param url URL assoluta del primary document
     *            (es. `https://www.sec.gov/Archives/edgar/data/320193/000032019325000123/aapl-20250928.htm`).
     * @return body HTML come String, oppure null se URL irraggiungibile / 404.
     *
     * NB: per 10-K/10-Q tipici il body è 0.5–3 MB. Timeout HTTP esteso configurato
     * nell'implementazione. Errori 5xx → `SecEdgarServiceException`.
     */
    fun downloadFilingHtml(url: String): String?
}
