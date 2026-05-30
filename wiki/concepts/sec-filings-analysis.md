---
type: concept
sources: ["raw/05_Analisi_10K_10Q_e_Regole_Buffett.md", "raw/fmp_docs.md"]
status: draft
created: 2026-05-20
updated: 2026-05-30
tags: [value-investing, sec, 10-k, 10-q, financial-analysis, management-quality, off-balance-sheet, sec-edgar, filing-cache]
---
# Analisi dei Report SEC (10-K e 10-Q)

> Metodologia di lettura analitica dei report annuali (10-K) e trimestrali (10-Q) della SEC: non lettura passiva ma "caccia agli indizi" in cinque step per validare la tesi di investimento.

## Contesto

Il 10-K e il 10-Q sono i documenti regolamentari primari per l'analisi fondamentale delle societa' quotate negli USA. Il 10-K e' annuale, completo e auditato; il 10-Q e' trimestrale, meno dettagliato e non auditato. [^src: raw/05_Analisi_10K_10Q_e_Regole_Buffett.md §1. Introduzione: 10-K vs 10-Q]

## Dettaglio

### 10-K vs 10-Q: differenze operative

| Attributo | 10-K | 10-Q |
|---|---|---|
| Frequenza | Annuale | Trimestrale |
| Revisione | Auditato da revisori indipendenti | Non auditato |
| Uso | Visione strategica a lungo termine | Monitoraggio tattico della tesi |
| Dettaglio | Completo | Sintetico |

### I Cinque Step di Analisi

L'analisi segue una sequenza precisa che va dall'operativo al finanziario: [^src: raw/05_Analisi_10K_10Q_e_Regole_Buffett.md §2. Step Procedurali per l'Analisi di un 10-K / 10-Q]

**Step 1 — Item 1 (Business)**: comprensione del modello di business, clienti target, canali distributivi, contesto normativo. Non comprare cio' che non si capisce.

**Step 2 — Item 1A (Risk Factors)**: identificare rischi strutturali reali (dipendenza da singolo cliente/fornitore, obsolescenza, litigation) oltre le avvertenze boilerplate. [^src: raw/05_Analisi_10K_10Q_e_Regole_Buffett.md §Step 2: Analisi dei Rischi (Item 1A - Risk Factors)]

**Step 3 — Item 7 (MD&A)**: analisi narrativa del management. Il Test dell'Onesta': il management attribuisce gli insuccessi a fattori esterni e si prende il merito esclusivo dei successi? Segnale di bassa affidabilita'.

**Step 4 — Item 8 (Financial Statements)**: analisi incrociata dei tre rendiconti. Il Rendiconto Finanziario (Cash Flow) e' il documento piu' difficile da manipolare contabilmente; il Free Cash Flow deve trasformare l'utile netto in cassa reale. [^src: raw/05_Analisi_10K_10Q_e_Regole_Buffett.md §Step 4: I Tre Prospetti Finanziari (Item 8)]

**Step 5 — Note al Bilancio**: ricerca di scheletri nell'armadio: politiche contabili aggressive, stock option dilutive, off-balance-sheet arrangements. [^src: raw/05_Analisi_10K_10Q_e_Regole_Buffett.md §Step 5: Le Note al Bilancio (Notes to Financial Statements)]

### Dati FMP per supportare l'analisi SEC

I tre rendiconti del 10-K/10-Q sono accessibili programmaticamente tramite [[fmp-financial-statements-stable]] (Income Statement, Balance Sheet, Cash Flow Statement). Le metriche calcolate (ROE, ROIC, margini, ratios) sono disponibili via [[fmp-key-metrics-ratios]].

### Accesso programmatico ai filing (implementazione 2026-05-25/26)

L'implementazione EP-011 Deep Analysis fornisce due fonti complementari per il recupero programmatico dei filing 10-K/10-Q:

**1. SecEdgarAdapter (US-038, TSK-091/092/093 — fonte autoritativa)**

Adapter Kotlin nel package `com.valueinvesting.webapp.secedgar` che chiama direttamente l'API SEC EDGAR raw:

- `resolveCikFromTicker(ticker)` → `CIK` 10-digit padded, via `https://www.sec.gov/files/company_tickers.json` (cache Caffeine TTL 30 giorni, ~10k entries ~3 MB in-memory).
- `listFilings(cik, formTypes, limit)` → metadata `List<SecFilingMetadata>` (accessionNumber, formType, filedAt, primaryDocumentUrl) via `https://data.sec.gov/submissions/CIK{padded10}.json`.
- `downloadFilingHtml(url)` → body HTML del primary document (10-K typical 0.5-3 MB).

Resilience4j: RateLimiter 10 req/s (hard-cap SEC fair-access policy) + CircuitBreaker 50% + Retry 3 attempts. User-Agent obbligatorio `ValueInvesting-App/1.0 {sec.edgar.user-agent.email}` (env var `SEC_EDGAR_USER_AGENT_EMAIL`).

**2. FmpAdapter.getSecFilings (US-039, TSK-094 — discovery rapida)**

Estensione di FmpAdapter ([[fmp-api]]) che chiama `GET /stable/sec-filings-search/symbol?symbol={ticker}&formType={ft}&from={from}&to={to}` (endpoint canonico verificato in `raw/fmp_docs.md:10815`, SEC Filings By Symbol API). Ritorna `List<SecFilingFmpDto>` con metadata arricchiti (CIK pre-risolto, `link`/`finalLink` canonical URL, `filingDate` ISO). NON ritorna il body HTML — quello viene scaricato via `SecEdgarAdapter.downloadFilingHtml`.

Quirk operativi load-bearing (verificati sul campo, mag 2026): `from`/`to` sono **obbligatori** (senza → 400; finestra applicata = 15 mesi indietro, `lookbackMonths=15`); l'endpoint **non filtra `formType` server-side** e ritorna tutti i form type ordinati DESC → si fa **una chiamata per ciascun formType** (10-K, 10-Q) con page-limit ampio (1000) + filtro client-side, poi union deduplicata per link. L'endpoint gemello `/sec-filings-search/form-type` ignora il `symbol` → inutilizzabile per ticker singolo. Dettaglio completo in [[fmp-api]] §Discovery filing SEC — quirk operativi. [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/fmp/FmpAdapterRestClient.kt §getSecFilings]

**Cache HTML body (TSK-095)**

Tabella PostgreSQL `filing_blob` (migration V013, rinumerata da V011 per collisione con EP-010): cache per `(accession_number UNIQUE, ticker, cik, form_type, filing_date)` + `html_body TEXT` + `extracted_text TEXT` (plain text estratto per pgvector embedding US-040 e LLM Munger US-041). TTL 90 giorni, hard-cap size 50 MB. Indici su `(ticker, filing_date DESC)`, `(expires_at)`, `(form_type, filing_date DESC)` per lookup principale + cleanup TTL + analytics.

Whitelist `'sec-filings'` aggiunta al CHECK constraint `fmp_fin_snap_endpoint_chk` (migration V012) per cache-aside metadata response FmpAdapter via `FmpCacheService.getOrFetch`.

**Orchestrator (TSK-096, in corso)**

`Filing10KQDownloaderService` orchestra la pipeline: discovery via FmpAdapter.getSecFilings → fetch HTML via SecEdgarAdapter.downloadFilingHtml → HTML strip → persist su `filing_blob` con TTL 90gg. Cross-validation FMP↔SEC su `accession_number`.

## Concetti correlati
[[intrinsic-value]]
[[economic-moat]]
[[fmp-financial-statements-stable]]
[[fmp-key-metrics-ratios]]
[[fmp-api]]
[[pgvector-vector-store]]
[[analysis-api-pipeline]]

## Pagine collegate
[[vi-05-analisi-10k-10q-buffett]]
[[warren-buffett]]
[[sec-10k-10q-analysis-playbook]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
