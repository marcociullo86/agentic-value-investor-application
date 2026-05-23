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

## Aggiornamenti (v2026-05-22)

**Fonte aggiunta:** `raw/investitore intelligente.txt` — Cap.14 e' la fonte primaria dei 7 criteri Graham. Il mapping seguente documenta la genealogia dal testo del 1973 al codice Kotlin 2026.

### Genealogia: Da Graham Cap.14 ai 7 ruleId

L'Investitore Intelligente, Cap.14, elenca 7 criteri per il portafoglio difensivo (vedi [[seven-criteria-defensive-stock-selection]]). La tabella seguente traccia la linea da ogni criterio Graham al `ruleId` corrispondente nel Rule Engine, con la mediazione teorica di Buffett dove la soglia e' stata aggiornata.

| Criterio Graham (Cap.14) | Soglia Graham 1973 | ruleId WebApp | Soglia WebApp | Note |
|---|---|---|---|---|
| Criterio 2 — Current Ratio | ≥ 2:1 | `CURRENT_RATIO_LATEST` | >2 GREEN; 1.5-2 YELLOW | Soglia Graham preservata; YELLOW banda aggiunta (Buffett: "business molto stabili") |
| Criterio 2 — LT Debt | ≤ Net Current Assets | `DEBT_TO_INCOME_LATEST` | <4 GREEN; 4-5 YELLOW | Buffett riformula in anni di utili; misura lo stesso rischio |
| Criterio 3 — Stabilita' Utili | Positivi ogni anno 10y | `ROE_10Y_AVG` + `NET_MARGIN_10Y_AVG` | Costanti 10y | ROE/Margin costanti sono la forma evoluta della stabilita' degli utili |
| Criterio 5 — Crescita EPS | ≥+33% in 10 anni | `ROE_10Y_AVG` + `ROIC_10Y_AVG` | >15% / >12% | ROIC stabile implica crescita della base di utili; piu' robusto dell'EPS grezzo (meno manipolabile) |
| Criterio 6 + 7 — P/E × P/B | P/E≤15; P/B≤1.5; P/E×P/B≤22.5 | `grahamNumber` (calculator) | Prezzo massimo = sqrt(22.5×EPS×BVPS) | Non e' un ruleId (segnale GREEN/RED) ma un valore scalare; il prezzo si confronta esternamente |
| **Aggiunte Buffett** (non in Graham) | n/a | `GROSS_MARGIN_10Y_AVG` | >40% GREEN | Pricing power; non presente nei 7 criteri originali Graham |
| **Aggiunte Buffett** (non in Graham) | n/a | `CAPEX_INTENSITY_10Y_AVG` | <25% GREEN | Business a bassa intensita' di capitale; non presente in Graham 1973 |

**Criteri Graham senza ruleId nel MVP**:
- Criterio 1 (Dimensioni ≥ $100M): non implementato — dati FMP disponibili ma nessun ruleId dedicato.
- Criterio 4 (Dividendi 20 anni): non implementato — richiederebbe storico FMP dividendi 20 anni.

**Implicazione**: il Rule Engine e' una sintesi Graham/Buffett, non una replica meccanica del Cap.14. I criteri piu' facilmente proxy-abili (liquidita', debito, redditività) sono implementati; i criteri che richiedono storico molto lungo (dividendi 20 anni) o che coinvolgono la valutazione del prezzo (P/E, P/B) sono delegati al `grahamNumber` e al `mosSignal`.

## Aggiornamenti (v2026-05-23)

**Fonte aggiunta:** `raw/agent.py` (Value Investor Bot v2.6.1) + `raw/09_agent_py_method_analysis.md`.

### Confronto con agent.py (v2.6.1)

Mapping tra i `ruleId` Kotlin del Rule Engine MVP e i check equivalenti in agent.py, con indicazione dei delta metodologici.

| ruleId Kotlin | Check agent.py | Delta metodologico |
|---|---|---|
| `ROE_10Y_AVG` | `roe_medio_5y` (5 anni, non 10) | **Delta**: Kotlin usa 10y (piu' conservativo, allineato Graham); agent.py usa 5y (piu' reattivo, cattura turnaround). Trade-off: 10y favorisce stabilita', 5y favorisce business in crescita. |
| `ROIC_10Y_AVG` | Non implementato in agent.py | **Delta**: il Rule Engine aggiunge ROIC come misura dell'efficienza di allocazione del capitale (non presente in Graham 1973 ne' in agent.py). |
| `GROSS_MARGIN_10Y_AVG` | `gross_margin_medio` (5 anni) | **Delta**: lookback differente (10y vs 5y). Stessa soglia implicita (>40%). |
| `NET_MARGIN_10Y_AVG` | `net_margin_medio` (5 anni) | **Delta**: lookback differente (10y vs 5y). |
| `CURRENT_RATIO_LATEST` | `current_ratio` calcolato ma non usato nel routing `munger_decision` | **Delta critico**: il Rule Engine Kotlin usa Current Ratio come segnale (GREEN/YELLOW/RED) e lo include nel report; agent.py lo calcola ma non lo applica come gate decisionale. |
| `DEBT_TO_INCOME_LATEST` | `debt_equity < 0.5` (D/E, non LT-debt/netIncome) | **Delta metodologico**: Kotlin misura Debt/Income in anni di rimborso; agent.py usa D/E come proxy. Entrambi sono varianti Buffett del Criterio 2b Graham (LT Debt ≤ NCAV). |
| `CAPEX_INTENSITY_10Y_AVG` | `capex / net_income` (implicito in formula OE) | **Delta**: agent.py calcola l'intensita' di CapEx come parte della formula Owner Earnings semplificata (`NI + D&A - |CapEx|`), non come segnale autonomo; il Rule Engine la espone come `ruleId` separato con soglia esplicita (<25% GREEN). |
| `GrahamNumberCalculator` | Non presente | **Delta**: agent.py non calcola il Graham Number; usa solo il DCF Owner Earnings per la valutazione. |
| `DcfCalculator` (r=9.5%) | `node_calcola_valore_buffett` (r=4.5%) | **Delta critico**: discount rate 4.5% (risk-free only, Buffett puro) vs 9.5% (WACC standard CFA). Vedi [[dcf-discount-rate-policy]] per l'analisi completa. |
| n/a (non in Kotlin MVP) | `munger_decision` cascade | **Aggiunta agent.py**: routing a 6 outcomes con anti-value-trap e panic-buy detection. Da portare in EP-011. Vedi [[panic-buy-vs-value-trap-detection]]. |
| n/a (non in Kotlin MVP) | `node_leggi_report_10k` (RAG Munger) | **Aggiunta agent.py**: analisi qualitativa 10-K/10-Q con 10 query inversione. Da portare in EP-011. Vedi [[munger-inversion-rag]]. |

**Segnali implementati in Kotlin ma non in agent.py**: `ROIC_10Y_AVG`, `GrahamNumber` — aggiunte Kotlin che rafforzano l'analisi rispetto al prototipo Python.

**Segnali in agent.py non ancora in Kotlin**: Current Ratio come gate (non solo calcolato), EPS CAGR con soglia, P/E, P/B, dividendi 20y — tutti da colmare con EP-010.

[^src: raw/09_agent_py_method_analysis.md §2.3] [^src: raw/agent.py:1029-1116] [^src: raw/agent.py:1901-1936]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
- EP-003 (Rule Engine quantitativo): US-007 redditività, US-008 pricing power, US-009 solidità, US-010 capitale intensivo
- EP-004 (Valore intrinseco e MoS): US-011 Graham Number, US-012 DCF, US-013 Margin of Safety
