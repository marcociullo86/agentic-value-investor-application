---
type: concept
sources: ["raw/06_Documento_Funzionale_WebApp_Value_Investing.md"]
status: draft
created: 2026-05-20
updated: 2026-05-21
tags: [product-spec, value-investing, rule-engine, roe, roic, margin, current-ratio, dcf, capex]
---
# Value Investing Rule Engine

> Motore di regole quantitativo che valida automaticamente la qualita' finanziaria di un'azienda e calcola il valore intrinseco secondo i criteri Graham/Buffett.

## Contesto

Il Rule Engine e' il cuore logico della WebApp Value Investing: riceve oggetti di dominio mappati dalle risposte FMP e applica regole stringenti derivate dai principi di [[benjamin-graham]] e [[warren-buffett]] per classificare ogni titolo come investibile, da monitorare o da escludere. [^src: raw/06_Documento_Funzionale_WebApp_Value_Investing.md §RF3: Il "Value Investing Rule Engine" (Logica di Business)]

## Dettaglio

### Regole di Validazione (RF3)

Le regole sono organizzate in quattro categorie, ognuna mappabile a endpoint FMP specifici:

#### Redditività

- **ROE > 15%** costante negli ultimi 5-10 anni.
- **ROIC > 12-15%** costante negli ultimi 5-10 anni.

Fonte dati: `GET /api/v3/key-metrics/{ticker}?limit=10` (ROIC, ROE) e `GET /api/v3/income-statement/{ticker}?limit=10`. [^src: raw/06_Documento_Funzionale_WebApp_Value_Investing.md §RF3: Il "Value Investing Rule Engine" (Logica di Business)]

#### Pricing Power

- **Gross Margin > 40%**: indicatore di vantaggio competitivo durevole ([[economic-moat]]).
- **Net Margin > 10%**: efficienza complessiva del business.

Fonte dati: `GET /api/v3/income-statement/{ticker}?limit=10` per calcolo, oppure [[fmp-key-metrics-ratios]] (Financial Ratios TTM). [^src: raw/06_Documento_Funzionale_WebApp_Value_Investing.md §RF3: Il "Value Investing Rule Engine" (Logica di Business)]

#### Solidita' Finanziaria

- **Current Ratio > 2** (o > 1.5 per business molto stabili): liquidita' corrente vs obblighi a breve.
- **Debito Lungo Termine / Utile Netto < 4**: il debito deve essere estinguibile con massimo 4 anni di utili.

Fonte dati: `GET /api/v3/balance-sheet-statement/{ticker}?limit=10` + Income Statement per l'utile netto. [^src: raw/06_Documento_Funzionale_WebApp_Value_Investing.md §RF3: Il "Value Investing Rule Engine" (Logica di Business)]

#### Capitale Intensivo

- **CapEx / Utile Netto < 25-30%**: identifica business a basso assorbimento di capitale (caratteristica Buffett).

Fonte dati: `GET /api/v3/cash-flow-statement/{ticker}?limit=10` per CapEx e Free Cash Flow. [^src: raw/06_Documento_Funzionale_WebApp_Value_Investing.md §RF3: Il "Value Investing Rule Engine" (Logica di Business)]

### Calcolo Valore Intrinseco (RF4)

Il Rule Engine implementa due modelli di valutazione distinti: [^src: raw/06_Documento_Funzionale_WebApp_Value_Investing.md §RF4: Calcolo del Valore Intrinseco e Margin of Safety]

**Indice di Graham:**
```
GrahamNumber = Sqrt(22.5 * EPS * BVPS)
```
Stima rapida del prezzo massimo sostenibile per un titolo difensivo (vedi [[graham-number]]).

**DCF (Discounted Cash Flow):**
- Proiezione del Free Cash Flow (o Owner Earnings) basata sulla media storica di crescita.
- Tasso di crescita limitato precauzionalmente al massimo 5-7%.
- Discount rate: 9-10%; tasso terminale: 2-3%.

**Margin of Safety:**
- Segnalazione visiva attivata quando: `Prezzo Attuale < Valore Intrinseco DCF * 0.70` (sconto minimo 30%).
- Allineato alla definizione canonica in [[margin-of-safety]].

### Output del Rule Engine: il "Traffic Light"

Per ogni regola, il motore emette un segnale classificatorio: [^src: raw/06_Documento_Funzionale_WebApp_Value_Investing.md §RF5: Dashboard e Interfaccia Utente (UI)]

- **Verde**: regola soddisfatta (es. ROE > 15%).
- **Giallo**: regola parzialmente soddisfatta (area di attenzione).
- **Rosso**: regola non soddisfatta (es. ROE < 10%).

## Relazione con FMP API

Ogni regola del motore si mappa direttamente a endpoint FMP (vedi [[fmp-financial-statements-stable]] e [[fmp-key-metrics-ratios]]). Il backend Kotlin applica caching 24h per ridurre il numero di chiamate API. [^src: raw/06_Documento_Funzionale_WebApp_Value_Investing.md §3. Flusso dei Dati (Data Flow)]

## Concetti correlati
[[margin-of-safety]]
[[graham-number]]
[[intrinsic-value]]
[[economic-moat]]
[[fmp-financial-statements-stable]]
[[fmp-key-metrics-ratios]]

## Pagine collegate
[[vi-06-webapp-value-investing-fsd]]
[[value-investing-fmp-integration]]
[[webapp-value-investing-spec]]
[[warren-buffett]]
[[benjamin-graham]]

## Aggiornamenti (v2026-05-20)

**Gap chiuso:** `vi-webapp-owner-earnings-formula` — formula Owner Earnings ora formalizzata.

La formula degli Owner Earnings da implementare nel motore DCF (RF4) e' ora documentata nel raw `08_Risoluzione_Q001_Owner_Earnings.md`. La formula canonica Buffett (1986) e': [^src: raw/08_Risoluzione_Q001_Owner_Earnings.md §1. La Definizione Ufficiale di Warren Buffett]

```
Owner Earnings = Net Income + D&A +/- Non-Cash Charges - Maintenance CapEx
```

**Metodo primario nel Rule Engine:** Metodo Greenwald (PPE_Ratio-based) per derivare Maintenance CapEx da Total CapEx. **Metodo fallback (flag DB):** Metodo 3 (FCF tradizionale) per settori ad alta intensita' di capitale (Utilities, Telecomunicazioni). [^src: raw/08_Risoluzione_Q001_Owner_Earnings.md §3. Implementazione Pratica (Aggiornamento US-012)]

Vedi [[vi-08-risoluzione-q001-owner-earnings]] per la specifica completa dei tre metodi di stima.

## Aggiornamenti (v2026-05-21)

**Stato L5 (`master`, Sprint 2 merged):** le sette strategie `ValuationRule` sono implementate in Kotlin con `ruleId` stabili; l'aggregazione avviene in `RuleEngineService.evaluateAll()` (ordinamento lessicografico per `ruleId`). [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/ruleengine/RuleEngineService.kt]

| ruleId | Classe | Soglia sintetica |
|--------|--------|------------------|
| `ROE_10Y_AVG` | `RoeRule` | &gt;15% GREEN; 10–15% YELLOW; &lt;10% RED |
| `ROIC_10Y_AVG` | `RoicRule` | &gt;12% GREEN; 8–12% YELLOW; &lt;8% RED |
| `GROSS_MARGIN_10Y_AVG` | `GrossMarginRule` | &gt;40% / 30–40% / &lt;30% |
| `NET_MARGIN_10Y_AVG` | `NetMarginRule` | &gt;10% GREEN, altrimenti RED |
| `CURRENT_RATIO_LATEST` | `CurrentRatioRule` | &gt;2 GREEN; 1.5–2 YELLOW; &lt;1.5 RED |
| `DEBT_TO_INCOME_LATEST` | `DebtToIncomeRule` | &lt;4 GREEN; 4–5 YELLOW; &gt;5 RED |
| `CAPEX_INTENSITY_10Y_AVG` | `CapexIntensityRule` | &lt;25% GREEN; 25–30% YELLOW; &gt;30% RED |

**Segnali:** enum `Signal` include `INDETERMINATE` (dati insufficienti, es. &lt; 5 anni) e `NOT_CALCULABLE` (input assenti) — distinti da `RED`. [^src: design_&_architecture/decisions/ADR-005-rule-engine-design.md]

**Endpoint analisi:** `GET /api/analysis/{ticker}` restituisce `signals` (7 elementi) + `grahamNumber` + `dcfIntrinsicValue` + `dcfMethod` + `mosSignal`. Dettaglio pipeline: [[analysis-api-pipeline]].

**DCF implementato:** `DcfCalculator` con `GreenwaldMaintenanceCapexEstimator` (primario) e `FcfFallbackEstimator`; override per utente su `dcf_method_override` (V007). Parametri: growth 5–7%, discount 9.5%, terminal 2.5%.

**Non ancora in produzione (Sprint 3+):** search, screener, historical, moat checklist, auth JWT (TSK-033), watchlist — restano nel contratto OpenAPI pieno ma fuori allowlist `IMPLEMENTED_OPERATIONS`.

**Sync 2026-05-21:** sette regole + Graham + DCF + MoS confermati coerenti con [[analysis-api-pipeline]]; contract CI verifica schema `RuleEngineResult` via springdoc runtime (TSK-037).

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
- EP-003 (Rule Engine quantitativo): US-007 redditività, US-008 pricing power, US-009 solidità, US-010 capitale intensivo
- EP-004 (Valore intrinseco e MoS): US-011 Graham Number, US-012 DCF, US-013 Margin of Safety
