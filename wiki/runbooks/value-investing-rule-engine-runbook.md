---
type: runbook
sources: ["raw/06_Documento_Funzionale_WebApp_Value_Investing.md"]
status: draft
created: 2026-05-20
updated: 2026-05-21
tags: [runbook, rule-engine, value-investing, kotlin, fmp, roe, roic, dcf, margin-of-safety, implementation, vi-domain]
domain: value-investing
---
# Runbook: Implementare il Value Investing Rule Engine

> Procedura step-by-step per implementare il [[value-investing-rule-engine]] nel backend Kotlin/Spring Boot: acquisizione dati FMP, validazione regole quantitative, calcolo valore intrinseco e Margin of Safety.

## Prerequisiti

- Backend Kotlin/Spring Boot configurato (vedi [[webapp-architecture-vi]]).
- API Key FMP attiva e configurata (vedi [[fmp-api]]).
- Accesso agli endpoint: `income-statement`, `balance-sheet-statement`, `cash-flow-statement`, `key-metrics` (tutti con `limit=10`).
- Cache layer 24h attivo (PostgreSQL o in-memory) per ridurre le chiamate FMP.

## Stato implementazione (v2026-05-21, Sprint 2)

| Step runbook | Stato | Artefatto Kotlin |
|--------------|-------|------------------|
| 1 Acquisizione FMP | Fatto | `FmpAdapter`, `FmpCacheService`, `FinancialDataService`, `ResilientFmpAdapter` |
| 2 Regole quantitative | Fatto | 7× `ValuationRule` in `ruleengine/rules/` |
| 3a Graham Number | Fatto | `GrahamNumberCalculator` |
| 3b DCF | Fatto | `DcfCalculator`, `GreenwaldMaintenanceCapexEstimator`, `FcfFallbackEstimator` |
| 3c Margin of Safety | Fatto | `MarginOfSafetyEvaluator` (soglia 70% DCF) |
| 4 Composizione risultato | Fatto | `AnalyzeTickerService` → `RuleEngineResultResponse` |
| 5 Esposizione API | Fatto | `GET /api/analysis/{ticker}` — vedi [[analysis-api-pipeline]] |
| Diagnostica bilancio | Fatto | `GET /api/financials/{ticker}` |
| Override DCF | Fatto | `POST/DELETE /api/dcf-overrides` (auth stub `X-User-Id`) |

Test: unit rule/calculator; E2E `AnalysisControllerIT`; contract `gradle contractCheck`. Frontend Traffic Light (TSK-021) **non** ancora implementato.

## Step 1 — Acquisizione Dati FMP (RF2)

Per ogni ticker analizzato, il backend deve invocare i quattro endpoint in parallelo o in sequenza: [^src: raw/06_Documento_Funzionale_WebApp_Value_Investing.md §RF2: Integrazione API (Financial Modeling Prep)]

```
GET /stable/income-statement?symbol={ticker}&limit=10&apikey={KEY}
GET /stable/balance-sheet-statement?symbol={ticker}&limit=10&apikey={KEY}
GET /stable/cash-flow-statement?symbol={ticker}&limit=10&apikey={KEY}
GET /stable/key-metrics?symbol={ticker}&limit=10&apikey={KEY}
```

- Verifica cache 24h prima di ogni chiamata.
- In caso di risposta vuota o errore: attivare Retry (max 3 tentativi) poi fallback su dati in cache se disponibili.
- Mappare le risposte JSON in data classes Kotlin tipizzate (Null Safety obbligatoria per campi finanziari opzionali).

[^src: raw/06_Documento_Funzionale_WebApp_Value_Investing.md §3. Flusso dei Dati (Data Flow)]

## Step 2 — Validazione Regole Quantitative (RF3)

Applicare le regole nell'ordine seguente, emettendo per ognuna un segnale `VERDE / GIALLO / ROSSO`:

### 2a. Redditività

- Calcola ROE medio su 10 anni da `key-metrics`.
  - VERDE se ROE > 15% per almeno 8/10 anni.
  - GIALLO se ROE tra 10-15%.
  - ROSSO se ROE < 10%.
- Calcola ROIC medio su 10 anni da `key-metrics`.
  - VERDE se ROIC > 12% costante.

[^src: raw/06_Documento_Funzionale_WebApp_Value_Investing.md §RF3: Il "Value Investing Rule Engine" (Logica di Business)]

### 2b. Pricing Power

- Calcola Gross Margin e Net Margin da `income-statement` (media 10 anni).
  - VERDE se Gross Margin > 40% e Net Margin > 10%.
  - GIALLO se Gross Margin tra 30-40%.
  - ROSSO se sotto le soglie.

### 2c. Solidita' Finanziaria

- Current Ratio da `balance-sheet-statement` (anno corrente):
  - VERDE se > 2 (o > 1.5 per settori stabili come utilities/consumer staples).
- Debito LT / Utile Netto:
  - Estrai `longTermDebt` da balance sheet e `netIncome` da income statement.
  - VERDE se rapporto < 4; ROSSO se > 5.

### 2d. Intensita' di Capitale

- CapEx da `cash-flow-statement`; Net Income da `income-statement`.
  - VERDE se CapEx/NetIncome < 25%; GIALLO se tra 25-30%; ROSSO se > 30%.

## Step 3 — Calcolo Valore Intrinseco (RF4)

### 3a. Graham Number

```kotlin
val grahamNumber = Math.sqrt(22.5 * eps * bookValuePerShare)
```

Dati da `key-metrics` (EPS, BVPS dell'anno corrente). [^src: raw/06_Documento_Funzionale_WebApp_Value_Investing.md §RF4: Calcolo del Valore Intrinseco e Margin of Safety]

### 3b. DCF (Discounted Cash Flow)

1. Estrai serie storica FCF o Owner Earnings da `cash-flow-statement` (10 anni).
2. Calcola tasso di crescita medio storico. Limita al massimo al 5-7% (cap precauzionale).
3. Proietta FCF per 10 anni futuri con il tasso capped.
4. Applica discount rate del 9-10%.
5. Calcola valore terminale con tasso di crescita perpetua del 2-3%.
6. Somma valori attuali (PV) dei flussi proiettati + valore terminale attualizzato.

### 3c. Margin of Safety

- Recupera prezzo corrente da `GET /stable/profile?symbol={ticker}` (campo `price`).
- MoS segnalato (VERDE) se: `prezzoAttuale < valoreDCF * 0.70`.
- MoS assente (ROSSO) se: `prezzoAttuale >= valoreDCF`.

[^src: raw/06_Documento_Funzionale_WebApp_Value_Investing.md §RF4: Calcolo del Valore Intrinseco e Margin of Safety]

## Step 4 — Composizione Risultato

Costruire un oggetto risultato con:

- Segnali Traffic Light per ogni regola (array `{regola, segnale, valore, soglia}`).
- `grahamNumber`, `dcfValue`, `mosPercent`.
- Flag `isMosSatisfied: Boolean`.
- Dati grezzi per i grafici storici (serie ricavi e utile netto).

## Step 5 — Invio al Frontend

Esporre il risultato tramite endpoint REST o GraphQL. Includere sempre il timestamp dei dati FMP usati (per trasparenza della freschezza dei dati in cache).

## Gestione degli Errori

| Scenario | Azione |
|---|---|
| FMP risponde 429 (rate limit) | Throttle: attendere prima del Retry; loggare l'evento |
| Campo contabile null/mancante | Usare `null` nella data class Kotlin; escludere dalla media senza crashare |
| Cache scaduta e FMP non raggiungibile | Restituire risultato parziale da cache con warning |
| Ticker non trovato su FMP | Risposta 404 strutturata al frontend |

[^src: raw/06_Documento_Funzionale_WebApp_Value_Investing.md §5. Requisiti Non Funzionali]

## Concetti correlati
[[value-investing-rule-engine]]
[[webapp-architecture-vi]]
[[intrinsic-value]]
[[margin-of-safety]]
[[graham-number]]
[[fmp-financial-statements-stable]]
[[fmp-key-metrics-ratios]]
[[fmp-api]]

## Pagine collegate
[[vi-06-webapp-value-investing-fsd]]
[[webapp-value-investing-spec]]
[[fmp-api-quickstart]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
- EP-003 Rule Engine: US-007/008/009/010
- EP-004 Valore intrinseco: US-011/012/013/020 (US-020 override DCF method per utente autenticato)
