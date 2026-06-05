---
type: runbook
sources: ["raw/investitore intelligente.txt", "raw/02_L_Investitore_Difensivo_vs_Intraprendente.md"]
status: draft
created: 2026-05-22
updated: 2026-05-22
tags: [value-investing, graham, enterprising-investor, checklist, net-net, stock-selection, fmp, runbook, vi-domain]
domain: value-investing
---
# Checklist Operativa — Investitore Intraprendente (Cap.15)

> Playbook per applicare i criteri del Capitolo 15 de L'Investitore Intelligente: criteri specifici per l'investitore attivo che cerca rendimenti superiori tramite analisi selettiva, incluso il criterio net-net working capital.

## Prerequisiti

- Stessa infrastruttura della [[defensive-investor-checklist]].
- Maggior tolleranza all'analisi manuale e al tempo dedicato.
- Comprensione che l'intraprendente Graham non e' uno speculatore: aumenta la disciplina analitica, non il rischio assunto.

## Differenza Fondamentale vs Profilo Difensivo

L'investitore intraprendente Graham non assume piu' rischio del difensivo — assume piu' lavoro. La sua ricerca si concentra su:
1. Azioni impopolari o temporaneamente depresse (non settori in declino permanente).
2. Situazioni speciali (fusioni, spin-off, arbitraggi, aziende in ristrutturazione con margin of safety).
3. Net-net stocks ([[net-net-stocks]]): azioni quotate sotto il valore di liquidazione corrente.

[^src: raw/investitore intelligente.txt §Cap.15 — Selezione Titoli per l'Investitore Intraprendente]

---

## Step 1 — Filtro Liquidita' Minima

**Soglia Graham**: Current Ratio ≥ 1.5 (standard inferiore rispetto al difensivo 2.0 — l'intraprendente accetta maggiore rischio di liquidita' in cambio di valutazione piu' bassa).

**FMP Endpoint**:
```
GET /stable/balance-sheet-statement/{ticker}?limit=1
```
Calcolo: `totalCurrentAssets / totalCurrentLiabilities ≥ 1.5`

**Esito**: se Current Ratio < 1.5 → ESCLUDI.

---

## Step 2 — Nessuna Perdita negli Ultimi 5 Anni

**Soglia Graham**: Net Income > 0 in ciascuno degli ultimi cinque anni (rispetto ai 10 del difensivo).

**FMP Endpoint**:
```
GET /stable/income-statement/{ticker}?limit=5
```
Campo: `netIncome` — tutti i 5 valori devono essere positivi.

**Esito**: se anche un anno ha `netIncome ≤ 0` negli ultimi 5 anni → ESCLUDI.

---

## Step 3 — Dividendo Presente

**Soglia Graham**: l'azienda deve distribuire un dividendo (non richiede i 20 anni del profilo difensivo — basta la presenza attuale).

**FMP Endpoint**:
```
GET /stable/key-metrics/{ticker}?limit=1
```
Campo: `dividendYield` > 0 oppure `dividendPerShare` > 0.

**Esito**: nessun dividendo → area di attenzione (non esclusione obbligatoria per intraprendente).

---

## Step 4 — P/E Molto Contenuto

**Soglia Graham**: P/E ≤ 9 (multiplo non superiore a 9 volte gli utili).

**FMP Endpoint**:
```
GET /stable/key-metrics/{ticker}?limit=1
```
Campo: `peRatio`

oppure calcolo manuale:
```
GET /stable/quote/{ticker}         → price
GET /stable/income-statement/{ticker}?limit=1  → netIncome + sharesOutstanding → EPS
```
Calcolo: `price / EPS ≤ 9`

**Nota**: questa soglia e' molto restrittiva — nel 2026, il P/E medio S&P 500 e' circa 20-25x. Titoli con P/E ≤ 9 si trovano tipicamente in settori ciclici in fase bassa, financial services, o aziende con aspettative molto pessimistiche. Per l'intraprendente Graham questo e' esattamente il territorio di interesse.

**Esito**: se P/E > 9 → non idoneo per questo filtro Graham puro.

---

## Step 5 — Prezzo vs Attivi Netti Tangibili

**Soglia Graham**: Prezzo ≤ 120% degli attivi netti tangibili per azione.

**Attivi Netti Tangibili (TNA)** = Total Assets - Intangible Assets - Goodwill - Total Liabilities.

**FMP Endpoint**:
```
GET /stable/balance-sheet-statement/{ticker}?limit=1
```
Campi: `totalAssets`, `intangibleAssets`, `goodwill`, `totalLiabilities`, `commonStock` (shares outstanding).

Calcolo:
```
TNA = totalAssets - intangibleAssets - goodwill - totalLiabilities
TNA_per_share = TNA / sharesOutstanding
Limite = TNA_per_share × 1.20
```

**Esito**: se `price > TNA_per_share × 1.20` → ESCLUDI.

---

## Step 6 — Debito vs Net Current Assets (Criterio Addizionale)

**Soglia Graham**: Debito totale ≤ 110% del Net Current Asset Value.

**Calcolo**:
```
NCAV = totalCurrentAssets - totalLiabilities
Debito totale = longTermDebt + shortTermDebt (o totalDebt)
Limite: totalDebt ≤ NCAV × 1.10
```

**FMP Endpoint**:
```
GET /stable/balance-sheet-statement/{ticker}?limit=1
```

**Esito**: se `totalDebt > NCAV × 1.10` → ESCLUDI.

---

## Step 7 — Criterio Net-Net (Opzionale, alta convinzione)

Per i titoli piu' conservativi nel portafoglio intraprendente, applicare il criterio net-net ([[net-net-stocks]]):

**Soglia**: Prezzo < (2/3) × NCAV per azione.

**Calcolo**:
```
NCAV = totalCurrentAssets - totalLiabilities
NCAV_per_share = NCAV / sharesOutstanding
Soglia_acquisto = NCAV_per_share × 0.667
```

**Esito**: se `price < NCAV_per_share × 0.667` → ottima opportunita' net-net con MoS del 33% sul valore di liquidazione.

---

## Checklist Finale

```
[ ] Filtro 1: Current Ratio ≥ 1.5        → Fonte: Balance Sheet
[ ] Filtro 2: No perdite 5 anni           → Fonte: Income Statement (netIncome, 5 anni)
[ ] Filtro 3: Dividendo presente          → Fonte: Key Metrics (dividendYield > 0)
[ ] Filtro 4: P/E ≤ 9                     → Fonte: Key Metrics / Quote + Income
[ ] Filtro 5: Prezzo ≤ 120% TNA/share     → Fonte: Balance Sheet (calcolo manuale)
[ ] Filtro 6: Debito ≤ 110% NCAV          → Fonte: Balance Sheet (calcolo manuale)
[ ] Filtro 7: Prezzo < 2/3 NCAV/share     → Criterio net-net (opzionale, alta convinzione)
```

**Decisione finale**: se i Filtri 1-6 sono soddisfatti → idoneo al portafoglio intraprendente Graham. Se anche il Filtro 7 e' soddisfatto → net-net con margine di sicurezza strutturale.

---

## Limitazioni Operative nel 2026

La rarita' dei titoli net-net nei mercati sviluppati (vedere [[net-net-stocks]]) rende questa checklist piu' utile come framework mentale che come strumento di ricerca quotidiana. Gli step 1-4 (liquidity, stability, dividend, P/E ≤ 9) sono comunque applicabili per trovare titoli sottovalutati che non soddisfano il criterio net-net ma offrono comunque valutazioni conservative.

**Aree di ricerca suggerite**:
- Titoli in settori ciclici nella fase bassa del ciclo.
- Aziende con problemi temporanei (non strutturali) di comunicazione con il mercato.
- Spin-off e scorpori (spesso sottovalutati nei primi mesi perche' i grandi fondi devono venderli).
- Mercati emergenti e small-cap non coperti dagli analisti.

---

## Integrazione WebApp

Il [[value-investing-rule-engine]] non implementa i criteri specifici dell'intraprendente (P/E ≤ 9, net-net, TNA). Tuttavia, il risultato di `GET /api/analysis/{ticker}` offre dati utili:

- `CURRENT_RATIO_LATEST`: verifica il Filtro 1 (soglia 1.5 = YELLOW nel Rule Engine, che usa soglia difensiva 2.0).
- `DEBT_TO_INCOME_LATEST`: verifica sostenibilita' debito (proxy del Filtro 6).
- `grahamNumber`: soglia massima di prezzo compatibile con P/E × P/B ≤ 22.5 (proxy del Filtro 4).
- `dcfIntrinsicValue` + `mosSignal`: verifica se c'e' MoS anche sul DCF.

Per i criteri specifici dell'intraprendente (P/E ≤ 9, TNA, NCAV), la verifica manuale tramite FMP endpoint rimane necessaria.

## Concetti correlati
[[net-net-stocks]]
[[seven-criteria-defensive-stock-selection]]
[[graham-number]]
[[margin-of-safety]]
[[value-investing-rule-engine]]
[[defensive-vs-enterprising-investor]]

## Pagine collegate
[[intelligent-investor]]
[[benjamin-graham]]
[[defensive-investor-checklist]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
