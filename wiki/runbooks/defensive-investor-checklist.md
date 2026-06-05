---
type: runbook
sources: ["raw/investitore intelligente.txt", "raw/02_L_Investitore_Difensivo_vs_Intraprendente.md"]
status: draft
created: 2026-05-22
updated: 2026-05-22
tags: [value-investing, graham, defensive-investor, checklist, stock-selection, fmp, runbook, vi-domain]
domain: value-investing
---
# Checklist Operativa — Investitore Difensivo (Cap.14)

> Playbook step-by-step per applicare i 7 criteri di Graham (Cap.14) a un singolo titolo, con mapping agli endpoint FMP stable e al [[value-investing-rule-engine]] della WebApp.

## Prerequisiti

- Account FMP con API key valida.
- Ticker del titolo da analizzare (es. `AAPL`).
- Accesso alla WebApp (`GET /api/analysis/{ticker}`) per i segnali automatici.
- Dati storici 10 anni disponibili per il titolo.

---

## Step 1 — Verifica Dimensioni Minime (Criterio 1)

**Obiettivo**: escludere micro-cap e small-cap non adatte al profilo difensivo.

**Soglia Graham**: Fatturato ≥ $100M annui (industriali); Attivo ≥ $50M (utility).

**FMP Endpoint**:
```
GET /stable/income-statement/{ticker}?limit=1
```
Campo: `revenue` (ultimo anno).

```
GET /stable/balance-sheet-statement/{ticker}?limit=1
```
Campo: `totalAssets` (per utility).

**Esito**: se `revenue < 100_000_000` → **ESCLUDI** (non idoneo al portafoglio difensivo).

---

## Step 2 — Verifica Current Ratio (Criterio 2a)

**Obiettivo**: liquidita' corrente sufficiente a coprire le obbligazioni a breve.

**Soglia Graham**: Current Ratio ≥ 2:1.

**FMP Endpoint**:
```
GET /stable/balance-sheet-statement/{ticker}?limit=1
```
Calcolo: `totalCurrentAssets / totalCurrentLiabilities`

**WebApp**: il segnale `CURRENT_RATIO_LATEST` nel response di `GET /api/analysis/{ticker}` esegue questo calcolo automaticamente.
- `GREEN`: Current Ratio > 2.0 — criterio soddisfatto.
- `YELLOW`: 1.5-2.0 — attenzione.
- `RED`: < 1.5 — **ESCLUDI** (per profilo difensivo).

**Esito**: se Current Ratio < 2 → **ESCLUDI**.

---

## Step 3 — Verifica Debito a Lungo Termine (Criterio 2b)

**Obiettivo**: il debito a lungo termine non deve superare il capitale circolante netto.

**Soglia Graham**: Long-Term Debt ≤ Net Current Assets (= Current Assets - Total Current Liabilities).

**FMP Endpoint**:
```
GET /stable/balance-sheet-statement/{ticker}?limit=1
```
Calcolo: `longTermDebt ≤ (totalCurrentAssets - totalCurrentLiabilities)`

**Nota**: il [[value-investing-rule-engine]] implementa la variante Buffett (`DEBT_TO_INCOME_LATEST`: LT Debt / Net Income < 4 anni). Per il criterio Graham puro, il calcolo manuale e' necessario.

**Esito**: se LT Debt > Net Current Assets → segnala come area di attenzione (non esclusione automatica, ma valutazione qualitativa richiesta).

---

## Step 4 — Verifica Stabilita' degli Utili (Criterio 3)

**Obiettivo**: nessun anno in perdita negli ultimi 10 anni.

**Soglia Graham**: Net Income > 0 in ciascuno degli ultimi 10 anni.

**FMP Endpoint**:
```
GET /stable/income-statement/{ticker}?limit=10
```
Campo: `netIncome` — verificare che tutti i 10 valori siano positivi.

**Esito**: se anche un solo anno ha `netIncome ≤ 0` → **ESCLUDI** (per profilo difensivo rigoroso).

---

## Step 5 — Verifica Storico Dividendi (Criterio 4)

**Obiettivo**: track record di pagamento dividendi ininterrotto da almeno 20 anni.

**Soglia Graham**: dividendi pagati ogni anno per ≥ 20 anni.

**FMP Endpoint**:
```
GET /stable/historical-price-full/stock_dividend/{ticker}
```
oppure:
```
GET /stable/key-metrics/{ticker}?limit=20
```
Campo: `dividendYield` o `dividendPerShare` — verificare continuita' per 20 anni.

**Nota**: questo criterio esclude automaticamente la maggior parte delle aziende growth (es. Amazon, Alphabet pre-2024, NVIDIA) che non distribuivano dividendi. Graham considera la continuita' dei dividendi come il segnale piu' affidabile di solidita' aziendale di lungo periodo.

**Esito**: se interruzioni nei dividendi negli ultimi 20 anni → segnala come area di attenzione.

---

## Step 6 — Verifica Crescita EPS (Criterio 5)

**Obiettivo**: l'EPS deve essere aumentato almeno del 33% in 10 anni (confronto medie triennali).

**Soglia Graham**: (Media EPS ultimi 3 anni) / (Media EPS 8-10 anni fa) ≥ 1.33

**FMP Endpoint**:
```
GET /stable/income-statement/{ticker}?limit=10
```
Campo: `eps`

**Calcolo**:
1. Media EPS anni 8, 9, 10 (periodo iniziale).
2. Media EPS anni 1, 2, 3 (periodo finale).
3. Ratio = Media finale / Media iniziale ≥ 1.33 → soddisfatto.

**Esito**: se la crescita EPS < 33% in 10 anni → ESCLUDI o valuta come "qualita' ridotta".

---

## Step 7 — Verifica P/E (Criterio 6)

**Obiettivo**: il prezzo corrente non deve essere piu' di 15 volte la media degli utili degli ultimi 3 anni.

**Soglia Graham**: Prezzo / (Media EPS 3 anni) ≤ 15.

**FMP Endpoint**:
```
GET /stable/quote/{ticker}
```
Campo: `price` (prezzo corrente).

```
GET /stable/income-statement/{ticker}?limit=3
```
Campo: `eps` (ultimi 3 anni).

**Calcolo**: `price / avg(eps[0], eps[1], eps[2]) ≤ 15`

**Alternativa WebApp**: `grahamNumber` in `GET /api/analysis/{ticker}` — se `prezzo > grahamNumber`, il titolo supera il vincolo combinato P/E × P/B.

**Esito**: se P/E > 15 → segnala come area di attenzione.

---

## Step 8 — Verifica P/Book (Criterio 7)

**Obiettivo**: il prezzo non deve superare 1.5 volte il valore contabile, o il prodotto P/E × P/B deve essere ≤ 22.5.

**Soglia Graham**: P/B ≤ 1.5; oppure P/E × P/B ≤ 22.5.

**FMP Endpoint**:
```
GET /stable/key-metrics/{ticker}?limit=1
```
Campo: `bookValuePerShare`, `priceToBookRatio`.

**Verifica combinata**: `priceToEarningsRatio × priceToBookRatio ≤ 22.5`

**Nota**: il [[graham-number]] = `sqrt(22.5 × EPS × BVPS)` e' il prezzo massimo compatibile con questo vincolo. Disponibile in `GET /api/analysis/{ticker}` → campo `grahamNumber`.

**Esito**: se `prezzo > grahamNumber` → criterio 7 non soddisfatto.

---

## Checklist Finale

```
[ ] Criterio 1: Fatturato ≥ $100M       → Fonte: Income Statement (revenue)
[ ] Criterio 2a: Current Ratio ≥ 2      → Fonte: Balance Sheet / WebApp CURRENT_RATIO_LATEST
[ ] Criterio 2b: LT Debt ≤ Net CA       → Fonte: Balance Sheet (calcolo manuale)
[ ] Criterio 3: Utili positivi 10 anni  → Fonte: Income Statement (netIncome, 10 anni)
[ ] Criterio 4: Dividendi 20 anni       → Fonte: Key Metrics / Stock Dividend History
[ ] Criterio 5: EPS +33% in 10 anni    → Fonte: Income Statement (eps, 10 anni)
[ ] Criterio 6: P/E ≤ 15 (media 3a)   → Fonte: Quote + Income Statement
[ ] Criterio 7: P/B ≤ 1.5 / GN        → Fonte: Key Metrics / WebApp grahamNumber
```

**Decisione finale**: solo se TUTTI e 8 i check sono positivi → il titolo e' idoneo al portafoglio difensivo secondo Graham.

---

## Integrazione WebApp

Per i criteri automatizzati, usa `GET /api/analysis/{ticker}`:

| ruleId | Criterio Graham corrispondente |
|---|---|
| `CURRENT_RATIO_LATEST` | Criterio 2a (Current Ratio ≥ 2) |
| `DEBT_TO_INCOME_LATEST` | Criterio 2b (LT Debt sostenibile) |
| `ROE_10Y_AVG` | Implicito nei Criteri 3, 5 (redditività stabile) |
| `NET_MARGIN_10Y_AVG` | Implicito nel Criterio 3 (no perdite) |
| `grahamNumber` | Criteri 6 + 7 combinati (prezzo massimo) |
| `mosSignal` | [[margin-of-safety]] — prezzo attuale vs DCF intrinsic value |

I Criteri 1 (dimensioni), 4 (dividendi 20 anni) e 5 (crescita EPS +33%) richiedono verifica manuale aggiuntiva. Non sono ancora implementati come `ruleId` nel MVP.

## Concetti correlati
[[seven-criteria-defensive-stock-selection]]
[[graham-number]]
[[value-investing-rule-engine]]
[[margin-of-safety]]
[[defensive-vs-enterprising-investor]]

## Pagine collegate
[[intelligent-investor]]
[[benjamin-graham]]
[[enterprising-investor-checklist]]
[[value-investing-rule-engine-runbook]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
