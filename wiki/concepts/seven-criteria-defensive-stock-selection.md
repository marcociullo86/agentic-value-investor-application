---
type: concept
sources: ["raw/investitore intelligente.txt"]
status: draft
created: 2026-05-22
updated: 2026-05-22
tags: [value-investing, graham, defensive-investor, seven-criteria, stock-selection, pe-ratio, current-ratio, dividends, earnings]
---
# Sette Criteri di Selezione per l'Investitore Difensivo

> I sette filtri quantitativi del Capitolo 14 de L'Investitore Intelligente: la griglia operativa canonica che Graham stabilisce per la selezione dei titoli nel portafoglio difensivo.

## Contesto

Il Capitolo 14 de L'Investitore Intelligente presenta i criteri di selezione per il portafoglio difensivo come un sistema di filtri binari (superato/non superato). Graham li chiama "standard minimi per l'acquisto". Un titolo che non supera tutti e sette i criteri non e' idoneo al portafoglio difensivo, indipendentemente da quanto sembri attraente per altri motivi. [^src: raw/investitore intelligente.txt §Cap.14 — Selezione Titoli per l'Investitore Difensivo]

## I Sette Criteri

### Criterio 1 — Dimensioni Adeguate dell'Azienda

La dimensione minima serve a escludere le piccole aziende soggette a vulnerabilita' sproporzionata. Graham fissa la soglia in:

- **Aziende industriali**: fatturato non inferiore a **100 milioni di dollari** annui.
- **Public utilities**: attivo totale non inferiore a **50 milioni di dollari**.

La motivazione e' che le aziende piu' piccole sono troppo volatili e soggette a variazioni competitive imprevedibili per un investitore che vuole sicurezza del capitale. [^src: raw/investitore intelligente.txt §Cap.14 — Selezione Titoli per l'Investitore Difensivo]

### Criterio 2 — Situazione Finanziaria Solida

Doppia condizione di liquidita' e solidita' patrimoniale:

- **Current Ratio ≥ 2:1** — le attivita' correnti devono essere almeno il doppio delle passivita' correnti.
- **Debito a Lungo Termine ≤ Net Current Assets** — il debito a lungo termine non deve superare il capitale circolante netto (attivita' correnti meno passivita' correnti totali).

Questa coppia di vincoli garantisce sia la liquidita' operativa che la sostenibilita' strutturale del debito. [^src: raw/investitore intelligente.txt §Cap.14 — Selezione Titoli per l'Investitore Difensivo]

### Criterio 3 — Stabilita' degli Utili

L'azienda deve aver registrato **utili positivi in ciascuno degli ultimi dieci anni**. Nessun anno in perdita nell'ultimo decennio. Questo criterio esclude i business ciclici non redditizi e quelli in fase di turnaround incerta. [^src: raw/investitore intelligente.txt §Cap.14 — Selezione Titoli per l'Investitore Difensivo]

### Criterio 4 — Regolarita' dei Dividendi

L'azienda deve aver pagato **dividendi ininterrottamente per almeno 20 anni**. Graham considera il track record sui dividendi come il segnale piu' affidabile di stabilita' aziendale e disciplina del management nell'allocazione del capitale. [^src: raw/investitore intelligente.txt §Cap.14 — Selezione Titoli per l'Investitore Difensivo]

### Criterio 5 — Crescita degli Utili

**Incremento minimo del 33% degli utili per azione (EPS) negli ultimi dieci anni**, calcolato come confronto tra la media triennale di inizio periodo e la media triennale di fine periodo. Questo metodo delle medie riduce l'effetto dei picchi e dei minimi ciclici annuali. [^src: raw/investitore intelligente.txt §Cap.14 — Selezione Titoli per l'Investitore Difensivo]

### Criterio 6 — Rapporto P/E Moderato

**P/E ≤ 15** calcolato sul prezzo corrente diviso la media degli utili degli **ultimi tre anni** (non l'EPS dell'ultimo anno, per attenuare la ciclicita'). Graham usa la media triennale degli utili come denominatore piu' stabile. [^src: raw/investitore intelligente.txt §Cap.14 — Selezione Titoli per l'Investitore Difensivo]

### Criterio 7 — Rapporto P/Book Moderato

**P/B ≤ 1.5** sul valore contabile per azione (Book Value Per Share). Graham ammette pero' un trade-off con il Criterio 6:

> Il prodotto P/E × P/B non deve superare **22.5** (= 15 × 1.5).

Questa condizione combinata consente ad esempio P/E = 9 con P/B = 2.5 (9 × 2.5 = 22.5), oppure P/E = 12 con P/B = 1.875 (12 × 1.875 = 22.5). La formula del [[graham-number]] e' la radice quadrata di questo prodotto: `sqrt(22.5 × EPS × BVPS)`. [^src: raw/investitore intelligente.txt §Cap.14 — Selezione Titoli per l'Investitore Difensivo]

## Tabella Sinottica

| # | Criterio | Soglia | Fonte Dati FMP |
|---|---|---|---|
| 1 | Dimensioni adeguate | Fatturato ≥ $100M (industriali); Attivo ≥ $50M (utility) | Income Statement (revenue) |
| 2 | Current Ratio | ≥ 2:1; LT Debt ≤ Net Current Assets | Balance Sheet |
| 3 | Stabilita' Utili | Positivi ogni anno ultimi 10 anni | Income Statement (10 anni) |
| 4 | Dividendi | Ininterrotti ≥ 20 anni | Key Metrics (dividendYield/dividendPerShare storico) |
| 5 | Crescita EPS | ≥ +33% in 10 anni (medie triennali) | Income Statement (EPS, 10 anni) |
| 6 | P/E | ≤ 15 (su media utili 3 anni) | Key Metrics / Quotes |
| 7 | P/Book | ≤ 1.5; P/E × P/B ≤ 22.5 | Key Metrics (bookValuePerShare) |

## Relazione con il Rule Engine

Il [[value-investing-rule-engine]] della WebApp implementa una selezione dei criteri Graham adattata alla metodologia Buffett. La seguente tabella mappa i 7 criteri originali ai 7 `ruleId` dell'engine:

| Criterio Graham (Cap.14) | ruleId WebApp | Note |
|---|---|---|
| Criterio 2 (Current Ratio ≥ 2) | `CURRENT_RATIO_LATEST` | Soglia invariata |
| Criterio 2 (LT Debt) | `DEBT_TO_INCOME_LATEST` | Graham usa "≤ net current assets"; Buffett usa "estinguibile in <4 anni utili" — entrambi misurano sostenibilita' del debito |
| Criterio 3 (Stabilita' Utili) | implicito in `ROE_10Y_AVG` / `NET_MARGIN_10Y_AVG` | ROE/margin costanti presuppongono utili non negativi su 10 anni |
| Criterio 5 (Crescita EPS) | parzialmente in `ROE_10Y_AVG` / `ROIC_10Y_AVG` | ROE/ROIC costanti implicano crescita della base di utili |
| Criterio 6 (P/E ≤ 15) | fuori scope (valutazione esterna) | `grahamNumber` restituisce il prezzo massimo equivalente |
| Criterio 7 (P/B ≤ 1.5) | fuori scope (valutazione esterna) | `grahamNumber` incorpora BVPS per il check P/E × P/B |
| Criteri Buffett aggiuntivi | `GROSS_MARGIN_10Y_AVG`, `CAPEX_INTENSITY_10Y_AVG` | Non presenti in Graham 1973; aggiunti dalla metodologia Buffett |

Vedi [[value-investing-rule-engine]] per la genealogia completa del mapping.

## Note Metodologiche

- Graham raccomanda di **rivalutare il portafoglio ogni anno** e di sostituire i titoli che non superano piu' i criteri, non di venderli con panico.
- I criteri sono **cumulativi**: un titolo che supera 6/7 non e' "quasi" idoneo — non e' idoneo.
- La versione moderna (commentata da Zweig, 2003) aggiorna le soglie dimensionali per l'inflazione, mantenendo pero' invariata la struttura logica.

## Concetti correlati
[[graham-number]]
[[defensive-vs-enterprising-investor]]
[[margin-of-safety]]
[[net-net-stocks]]
[[value-investing-rule-engine]]

## Pagine collegate
[[intelligent-investor]]
[[benjamin-graham]]
[[defensive-investor-checklist]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
