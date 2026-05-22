---
type: concept
sources: ["raw/03_Analisi_Fondamentale_e_Valutazione.md"]
status: draft
created: 2026-05-20
updated: 2026-05-21
tags: [value-investing, graham, graham-number, pe-ratio, pb-ratio, eps, book-value, defensive-investor]
---
# Numero di Graham (Graham Number)

> Formula di valutazione rapida che calcola il prezzo massimo accettabile per un titolo difensivo come radice quadrata di 22.5 x EPS x Book Value per Share.

## Contesto

Il Numero di Graham (o Indice di Graham) e' la metrica quantitativa centrale per l'investitore difensivo secondo Benjamin Graham. Sintetizza il vincolo combinato P/E e P/B in un unico valore soglia. [^src: raw/03_Analisi_Fondamentale_e_Valutazione.md §2. Il Numero di Graham e Metriche Avanzate]

## Dettaglio

### Formula

```
Numero di Graham = sqrt(22.5 x Utile per Azione (EPS) x Valore Contabile per Azione)
```

Il coefficiente 22.5 deriva dalla regola empirica di Graham: P/E max 15 x P/B max 1.5 = 22.5. [^src: raw/03_Analisi_Fondamentale_e_Valutazione.md §2. Il Numero di Graham e Metriche Avanzate]

### I Sette Criteri Graham per il Portafoglio Difensivo

Prima di calcolare il Numero di Graham, il titolo deve superare tutti e sette i filtri: [^src: raw/03_Analisi_Fondamentale_e_Valutazione.md §1. Criteri di Selezione per l'Investitore Difensivo]

| # | Criterio | Soglia |
|---|---|---|
| 1 | Dimensioni adeguate | Escludere micro-cap volatili |
| 2 | Solidita' Finanziaria | Current Ratio > 2 |
| 3 | Stabilita' degli Utili | Utili positivi negli ultimi 10 anni |
| 4 | Storico Dividendi | Pagamento ininterrotto >= 20 anni |
| 5 | Crescita degli Utili (EPS) | Aumento >= 33% negli ultimi 10 anni |
| 6 | P/E | < 15 (calcolato su utili medi 3 anni) |
| 7 | P/B | < 1.5 (con P/E x P/B < 22.5) |

### Metriche FMP correlate

I dati necessari per il calcolo del Numero di Graham sono disponibili tramite FMP: [^src: raw/FMP_Docs_5_Metrics_and_Ratios.txt §Key Metrics & TTM Key Metrics API]

- **EPS**: via Key Metrics o Income Statement ([[fmp-financial-statements-stable]]).
- **Book Value per Share**: via Key Metrics o Balance Sheet.
- **Current Ratio**: via Financial Ratios ([[fmp-key-metrics-ratios]]).
- **P/E e P/B**: via Key Metrics o Quotes ([[fmp-quotes-stable]]).

### Estensione moderna: ROE

Il ROE costantemente elevato (> 15%) affianca il Numero di Graham come segnale di vantaggio competitivo e allocazione efficiente del capitale, non catturato dalla formula statica. [^src: raw/03_Analisi_Fondamentale_e_Valutazione.md §2. Il Numero di Graham e Metriche Avanzate]

## Concetti correlati
[[intrinsic-value]]
[[margin-of-safety]]
[[defensive-vs-enterprising-investor]]
[[fmp-key-metrics-ratios]]
[[fmp-financial-statements-stable]]
[[fmp-quotes-stable]]

## Pagine collegate
[[vi-03-analisi-fondamentale-valutazione]]
[[benjamin-graham]]

## Aggiornamenti (v2026-05-21)

**Backend:** `GrahamNumberCalculator` (package `ruleengine/calculators`) espone `calculate(eps, bvps)` e `calculateFromDataset`; risultato incluso in `GET /api/analysis/{ticker}` come campo `grahamNumber`. Valori null o ≤ 0 → non applicabile (campo omesso). EPS da income statement latest; BVPS da key metrics `bookValuePerShare`. [^src: design_&_architecture/api/openapi.yaml §RuleEngineResult]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
