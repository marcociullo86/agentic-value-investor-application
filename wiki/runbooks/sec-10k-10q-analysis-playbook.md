---
type: runbook
sources: ["raw/05_Analisi_10K_10Q_e_Regole_Buffett.md", "raw/fmp_docs.md"]
status: draft
created: 2026-05-20
updated: 2026-05-30
tags: [runbook, value-investing, sec, 10-k, 10-q, buffett, analysis, sec-edgar, filing-cache]
---
# Playbook: Analisi 10-K/10-Q con Metodo Buffett

> Procedura operativa step-by-step per sezionare un report SEC annuale o trimestrale applicando i filtri qualitativi e quantitativi di Warren Buffett, con recupero dati via FMP API e accesso diretto a SEC EDGAR.

## Prerequisiti

- Simbolo azionario del titolo da analizzare (es. `AAPL`).
- API key FMP configurata (vedi [[fmp-api]]).
- Accesso a EDGAR (https://www.sec.gov/cgi-bin/browse-edgar) per il testo narrativo 10-K/10-Q.
- **Per accesso programmatico** (US-038/039, implementato 2026-05-25/26): env var `SEC_EDGAR_USER_AGENT_EMAIL` configurata in `.env` (SEC fair-access policy richiede User-Agent identificativo con email valida).

## Step 0 — Download programmatico del filing (opzionale, automated pipeline)

Quando la pipeline EP-011 Deep Analysis è attiva, lo Step 1-5 manuale può essere preceduto da un download automatizzato del filing HTML:

1. **Discovery** via `FmpAdapter.getSecFilings(ticker, ["10-K", "10-Q"], limit=10, lookbackMonths=15)`: ritorna metadata `List<SecFilingFmpDto>` con CIK pre-risolto + `finalLink` canonical URL al primary document. Endpoint canonico `GET /stable/sec-filings-search/symbol?symbol={ticker}` (raw/fmp_docs.md:10815). NB: `from`/`to` sono obbligatori (senza → 400; finestra = 15 mesi indietro) e l'endpoint non filtra `formType` server-side → si fa una chiamata per ciascun formType (10-K, 10-Q) con page-limit ampio + filtro client-side. Vedi [[fmp-api]] §Discovery filing SEC — quirk operativi.

2. **Resolve CIK** via `SecEdgarAdapter.resolveCikFromTicker(ticker)` se serve cross-validation autoritativa (cache Caffeine TTL 30gg, populate da `https://www.sec.gov/files/company_tickers.json`).

3. **Download HTML** via `SecEdgarAdapter.downloadFilingHtml(finalLink)` (rate-limit 10 req/s SEC hard-cap), poi `Filing10KQDownloaderService` (TSK-096) esegue HTML strip → testo plain → persist in tabella `filing_blob` (V013, TTL 90gg, size hard-cap 50 MB).

4. **Lookup successivo**: il testo plain estratto in `filing_blob.extracted_text` viene usato come input per:
   - Embedding pgvector → ricerca semantica ([[pgvector-vector-store]], US-040)
   - LLM Munger inversion ([[value-investor-bot-architecture]], US-041)

Se il filing non è ancora in cache → fallback a EDGAR browser manuale (Step 1-5 sotto). [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-039-download-cache-filings/TSK-094.md]

## Step 1 — Comprendi il Business (Item 1)

Leggi l'Item 1 del 10-K su EDGAR. Rispondi a:

- Come genera cassa l'azienda?
- Chi sono i clienti target e i canali distributivi?
- L'azienda rientra nel tuo cerchio di competenza? Il modello e' prevedibile a 10 anni?

Se la risposta all'ultima domanda e' "no" → scarta il titolo a priori (regola del Cerchio di Competenza Buffett). [^src: raw/05_Analisi_10K_10Q_e_Regole_Buffett.md §3A. Il Cerchio di Competenza (Circle of Competence)]

## Step 2 — Analizza i Rischi (Item 1A)

Cerca rischi strutturali oltre le avvertenze standard:

- Dipendenza da singolo cliente (>10% dei ricavi)?
- Dipendenza da singolo fornitore critico?
- Rischio di obsolescenza tecnologica imminente?
- Litigation pendente con possibile esborso significativo?

[^src: raw/05_Analisi_10K_10Q_e_Regole_Buffett.md §Step 2: Analisi dei Rischi (Item 1A - Risk Factors)]

## Step 3 — Test dell'Onesta' del Management (Item 7 MD&A)

Leggi il MD&A e applica il Test dell'Onesta':

- Il management attribuisce i risultati negativi solo a fattori esterni (cambi valutari, meteo, pandemia)?
- Si prende il merito esclusivo dei risultati positivi?
- Se si': segnale di bassa affidabilita' del management. Considera uno sconto aggiuntivo sul valore intrinseco. [^src: raw/05_Analisi_10K_10Q_e_Regole_Buffett.md §Step 3: MD&A - Management's Discussion and Analysis (Item 7)]

## Step 4 — Analisi Incrociata dei Tre Rendiconti (Item 8)

Recupera i dati via FMP API (vedi [[fmp-financial-statements-stable]]):

```
GET /income-statement/{symbol}?period=annual&limit=10
GET /balance-sheet-statement/{symbol}?period=annual&limit=10
GET /cash-flow-statement/{symbol}?period=annual&limit=10
```

Verifica:

| Check | Segnale positivo |
|---|---|
| Crescita organica ricavi | >5% CAGR su 10 anni |
| Margini operativi stabili | Gross Margin >40%, Net Margin >10% |
| Current Ratio | >2 (criterio Graham) |
| FCF / Net Income | >0.8 (l'utile si converte in cassa) |
| Debito LT / Utile Netto | <4x (estinguibile in <4 anni) |

[^src: raw/05_Analisi_10K_10Q_e_Regole_Buffett.md §Step 4: I Tre Prospetti Finanziari (Item 8)]

## Step 5 — Note al Bilancio: Cerca gli Scheletri

Leggi le Note al Bilancio su EDGAR. Attenzione a:

- **Riconoscimento ricavi aggressivo**: i ricavi vengono riconosciuti prima della cassa?
- **Stock option dilutive**: il piano di remunerazione management diluisce gli azionisti?
- **Off-balance-sheet arrangements**: debiti e passivita' non contabilizzate nel bilancio. [^src: raw/05_Analisi_10K_10Q_e_Regole_Buffett.md §Step 5: Le Note al Bilancio (Notes to Financial Statements)]

## Step 6 — Verifica del Fossato Economico

Recupera metriche storiche (5-10 anni) via [[fmp-key-metrics-ratios]]:

```
GET /key-metrics/{symbol}?period=annual&limit=10
GET /ratios/{symbol}?period=annual&limit=10
```

Applica i filtri Buffett:

| Metrica | Soglia | Fonte FMP |
|---|---|---|
| ROE | >15%, costante | Financial Ratios |
| ROIC | >12-15% | Key Metrics |
| Gross Margin | >40% | Financial Ratios |
| CapEx / Net Income | <25-30% | Cash Flow + Income |

Se ROE e ROIC sono elevati e stabili nel tempo → probabile [[economic-moat]]. [^src: raw/05_Analisi_10K_10Q_e_Regole_Buffett.md §3C. Le Regole Finanziarie Quantitative]

## Step 7 — Calcolo del Valore Intrinseco e Margine di Sicurezza

1. Stima gli Owner Earnings: `Net Income + Depreciation - CapEx (manutenzione)`.
2. Proietta gli Owner Earnings su 10 anni con un tasso di crescita conservativo.
3. Attualizza con tasso di sconto (es. rendimento Treasury 10Y + premio rischio).
4. Applica uno sconto del 25-30% come [[margin-of-safety]].
5. Confronta con il prezzo di mercato corrente (via [[fmp-quotes-stable]]).

Riferimento consensus: controlla anche il DCF FMP: `GET /discounted-cash-flow/{symbol}`.

[^src: raw/05_Analisi_10K_10Q_e_Regole_Buffett.md §4. Il Margine di Sicurezza (Margin of Safety)]

## Decisione finale

| Condizione | Azione |
|---|---|
| Prezzo < Valore Intrinseco - MoS AND moat verificato | Acquisto |
| Prezzo tra Valore Intrinseco e Valore Intrinseco - MoS | Watchlist, attendi [[mr-market]] |
| Prezzo > Valore Intrinseco | Non acquistare; se in portafoglio, valuta vendita |

## Concetti correlati
[[sec-filings-analysis]]
[[margin-of-safety]]
[[economic-moat]]
[[intrinsic-value]]
[[mr-market]]
[[fmp-financial-statements-stable]]
[[fmp-key-metrics-ratios]]
[[fmp-quotes-stable]]
[[fmp-api]]

## Pagine collegate
[[vi-05-analisi-10k-10q-buffett]]
[[warren-buffett]]
[[value-investing-fmp-integration]]
[[fmp-api-quickstart]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
