---
type: source
sources: ["raw/06_Documento_Funzionale_WebApp_Value_Investing.md"]
status: draft
created: 2026-05-20
updated: 2026-05-20
tags: [product-spec, value-investing, fmp, kotlin, spring-boot, rule-engine, webapp, fsd]
---
# Documento di Specifica Funzionale — WebApp Value Investing

> Specifica funzionale e architetturale della piattaforma web per lo screening di titoli sottovalutati secondo i principi Graham/Buffett, integrata con FMP API.

## Contesto

Il documento definisce i requisiti per una web application che digitalizza e automatizza i processi analitici derivati dalla filosofia di Benjamin Graham e Warren Buffett, interfacciandosi con le API di Financial Modeling Prep (FMP) per l'estrazione in tempo reale e storica dei dati di bilancio. [^src: raw/06_Documento_Funzionale_WebApp_Value_Investing.md §1. Scopo del Progetto]

## Architettura di Sistema (§2)

Il sistema e' suddiviso in tre livelli principali per garantire scalabilita', manutenibilita' e prestazioni ottimali: [^src: raw/06_Documento_Funzionale_WebApp_Value_Investing.md §2. Architettura di Sistema Raccomandata]

- **Frontend (Client):** Single Page Application (React, Vue.js o Angular) per la fruizione delle dashboard e dei risultati di screening.
- **Backend (Server):** Applicazione Kotlin con Spring Framework (Spring Boot, Spring Data). Gestisce routing, caching delle chiamate API (ottimizzazione costi), logica di validazione finanziaria, endpoint REST/GraphQL.
- **Data Provider:** Integrazione con `financialmodelingprep.com` per Income Statement, Balance Sheet, Cash Flow Statement e Key Metrics.
- **Database:** Relazionale (PostgreSQL via Spring Data JPA) per configurazioni utente, watchlist e caching dati di bilancio giornalieri.

## Flusso dei Dati (§3)

Il flusso operativo standard segue sei passi: [^src: raw/06_Documento_Funzionale_WebApp_Value_Investing.md §3. Flusso dei Dati (Data Flow)]

1. L'utente richiede l'analisi di un ticker singolo o avvia uno screener parametrico.
2. Il backend Kotlin verifica cache (valida 24h) prima di contattare FMP.
3. Se non in cache, interroga gli endpoint FMP necessari.
4. I dati JSON grezzi vengono mappati in oggetti di dominio (data classes Kotlin).
5. Il "Value Investing Rule Engine" calcola Valore Intrinseco, Margine di Sicurezza e check finanziari.
6. Il risultato strutturato viene inviato al frontend per il rendering.

## Requisiti Funzionali (§4)

### RF1: Ricerca e Screening

- Ricerca per ticker simbolo (es. AAPL, MSFT) con analisi istantanea.
- Screener di mercato con filtri su capitalizzazione e settore.

[^src: raw/06_Documento_Funzionale_WebApp_Value_Investing.md §RF1: Motore di Ricerca e Screening]

### RF2: Endpoint FMP da integrare

Il backend implementa client per i seguenti endpoint: [^src: raw/06_Documento_Funzionale_WebApp_Value_Investing.md §RF2: Integrazione API (Financial Modeling Prep)]

- `GET /api/v3/income-statement/{ticker}?limit=10`
- `GET /api/v3/balance-sheet-statement/{ticker}?limit=10`
- `GET /api/v3/cash-flow-statement/{ticker}?limit=10`
- `GET /api/v3/key-metrics/{ticker}?limit=10`

### RF3: Value Investing Rule Engine

Il motore di regole valida automaticamente: [^src: raw/06_Documento_Funzionale_WebApp_Value_Investing.md §RF3: Il "Value Investing Rule Engine" (Logica di Business)]

- **Redditività:** ROE > 15% e ROIC > 12-15% costanti su 5-10 anni.
- **Pricing Power:** Gross Margin > 40% e Net Margin > 10%.
- **Solidita' Finanziaria:** Current Ratio > 2 (o > 1.5 per business stabili); Debito LT / Utile Netto < 4.
- **Capitale Intensivo:** CapEx / Utile Netto < 25-30%.

### RF4: Calcolo Valore Intrinseco e Margin of Safety

Il sistema calcola autonomamente tre metriche: [^src: raw/06_Documento_Funzionale_WebApp_Value_Investing.md §RF4: Calcolo del Valore Intrinseco e Margin of Safety]

1. **Indice di Graham:** `Sqrt(22.5 * EPS * BVPS)`.
2. **DCF:** Proiezione FCF/Owner Earnings (crescita media storica, max 5-7%), attualizzata con discount rate 9-10% e tasso terminale 2-3%. [^src: raw/06_Documento_Funzionale_WebApp_Value_Investing.md §RF4: Calcolo del Valore Intrinseco e Margin of Safety]
3. **Margin of Safety:** Segnalazione visiva se `Prezzo Attuale < Valore Intrinseco DCF * 0.70` (sconto 30%).

### RF5: Dashboard e UI

- Pannello "Traffic Light": semaforo Verde/Giallo/Rosso per ogni regola.
- Grafici storici di ricavi e utile netto.
- Sezione qualitativa (Moat): checklist annotabile su Asset Immateriali, Switching Costs, Network Effect.

[^src: raw/06_Documento_Funzionale_WebApp_Value_Investing.md §RF5: Dashboard e Interfaccia Utente (UI)]

## Requisiti Non Funzionali (§5)

- **Rate Limiting:** Throttling nel backend Spring per rispettare i limiti FMP.
- **Resilienza:** Retry e fallback su cache in caso di fallimento API esterne.
- **Tipizzazione:** Data classes Kotlin per mappatura sicura delle risposte JSON FMP, prevenendo Null Pointer Exception su dati contabili mancanti.

[^src: raw/06_Documento_Funzionale_WebApp_Value_Investing.md §5. Requisiti Non Funzionali]

## Concetti correlati
[[value-investing-rule-engine]]
[[webapp-architecture-vi]]
[[intrinsic-value]]
[[margin-of-safety]]
[[graham-number]]
[[economic-moat]]
[[fmp-financial-statements-stable]]
[[fmp-key-metrics-ratios]]

## Pagine collegate
[[value-investing-fmp-integration]]
[[webapp-value-investing-spec]]
[[benjamin-graham]]
[[warren-buffett]]
[[fmp-api]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
- EP-001 — Ricerca e Screening titoli (US-001, US-002, US-003) — **done** R1.0
- EP-002 — Integrazione FMP Data Provider (US-004, US-005, US-006) — **done** R1.0
- EP-003 — Value Investing Rule Engine quantitativo (US-007…US-010) — **done** R1.0
- EP-004 — Valore Intrinseco e Margin of Safety (US-011…US-013, US-020) — **done** R1.0
- EP-005 — Dashboard, Traffic Light e Moat (US-014…US-016) — **done** R1.0
- EP-006 — Watchlist, auth e profilo (US-017…US-019) — **done** R1.0
- EP-007 — Hardening produzione (US-021…US-025) — **R1.1**
- EP-008 — Deploy e operatività produzione (US-026…US-028) — **R1.1**
- EP-009 — Throttling FMP e runbook (US-029, US-030) — **R1.1**
