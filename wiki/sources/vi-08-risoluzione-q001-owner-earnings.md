---
type: source
sources: ["raw/08_Risoluzione_Q001_Owner_Earnings.md"]
status: draft
created: 2026-05-20
updated: 2026-05-20
tags: [product-spec, value-investing, dcf, owner-earnings, buffett, capex, greenwald, kotlin, q001, vi-domain]
domain: value-investing
---
# Risoluzione Q_001 — Formula Owner Earnings per il DCF

> Documento tecnico che formalizza la formula degli Owner Earnings da implementare nel Rule Engine DCF (RF4), con tre metodi di stima della Maintenance CapEx e indicazioni di implementazione Kotlin.

## Contesto

La FSD cita "Free Cash Flow o Owner Earnings" come base del DCF senza specificare la composizione esatta. Poiche' le API FMP non espongono nativamente la distinzione tra Maintenance CapEx e Growth CapEx, il documento risolve il blocco definendo un algoritmo di stima basato sui principi di Warren Buffett e il metodo accademico di Bruce Greenwald. [^src: raw/08_Risoluzione_Q001_Owner_Earnings.md §Risoluzione del Blocco (API FMP e Maintenance CapEx)]

## Dettaglio

### 1. Definizione Canonica (Buffett 1986)

La formula ufficiale degli Owner Earnings, derivata dalla Lettera agli Azionisti Berkshire Hathaway del 1986: [^src: raw/08_Risoluzione_Q001_Owner_Earnings.md §1. La Definizione Ufficiale di Warren Buffett]

```
Owner Earnings = Net Income + D&A (Depreciation & Amortization)
                 +/- Altre voci non monetarie (Non-Cash Charges)
                 - Maintenance CapEx
```

**Nota:** A differenza del Free Cash Flow standard, Buffett non sottrae la variazione del Capitale Circolante Netto se il business non richiede iniezioni continue di liquidita' per mantenere i volumi correnti. Per le aziende ad alta intensita' di capitale, tuttavia, va sottratta l'aggiunta al capitale circolante. [^src: raw/08_Risoluzione_Q001_Owner_Earnings.md §1. La Definizione Ufficiale di Warren Buffett]

### 2. Tre Metodi di Stima della Maintenance CapEx

#### Metodo 1: Modello di Bruce Greenwald (Raccomandato — calcolo primario)

Metodo accademico per estrarre la CapEx di mantenimento dai dati standard di bilancio: [^src: raw/08_Risoluzione_Q001_Owner_Earnings.md §Metodo 1: Il Modello di Bruce Greenwald (Raccomandato per il Rule Engine)]

```
PPE_Ratio      = Gross Property Plant & Equipment / Revenue
Growth CapEx   = PPE_Ratio × (Revenue_Anno_Corrente - Revenue_Anno_Precedente)
Maint. CapEx   = Total CapEx - Growth CapEx
```

Regola di fallback: se le vendite diminuiscono, `Growth CapEx = 0` e `Maintenance CapEx = Total CapEx`.

#### Metodo 2: Proxy Conservativa dell'Ammortamento (Semplificato)

```
Maintenance CapEx ≈ D&A
```

Funziona bene per aziende mature con crescita stabile. Sottostima il fabbisogno di capitale in scenari inflattivi. L'Owner Earnings risultante tende a convergere verso l'Utile Netto. [^src: raw/08_Risoluzione_Q001_Owner_Earnings.md §Metodo 2: Proxy Conservativa dell'Ammortamento (Semplificato)]

#### Metodo 3: Approccio "Peggior Scenario" di Buffett

```
Owner Earnings = Operating Cash Flow - Total CapEx
               (= Free Cash Flow tradizionale)
```

Applicato quando l'azienda non dimostra rendimenti superiori (ROIC elevato) sui capitali reinvestiti; Buffett considera tutta la spesa in conto capitale come spesa di mantenimento. [^src: raw/08_Risoluzione_Q001_Owner_Earnings.md §Metodo 3: L'Approccio "Peggior Scenario" di Buffett]

### 3. Implementazione Pratica nel Rule Engine (Kotlin)

Il Rule Engine usera' il **Metodo 1 (Greenwald)** come calcolo primario per il modello DCF. [^src: raw/08_Risoluzione_Q001_Owner_Earnings.md §3. Implementazione Pratica (Aggiornamento US-012)]

Viene aggiunto un **flag nel database**, sovrascrivibile dall'utente/analista nella UI, per forzare il **Metodo 3** sui settori ad altissima intensita' di capitale (Utilities, Telecomunicazioni) dove la distinzione tra Growth CapEx e Maintenance CapEx e' sfocata e il rischio di sovrastimare i flussi di cassa e' alto. [^src: raw/08_Risoluzione_Q001_Owner_Earnings.md §3. Implementazione Pratica (Aggiornamento US-012)]

**Storia sbloccata:** US-012 (calcolo DCF Owner Earnings). [^src: raw/08_Risoluzione_Q001_Owner_Earnings.md §3. Implementazione Pratica (Aggiornamento US-012)]

## Concetti correlati
[[value-investing-rule-engine]]
[[intrinsic-value]]
[[warren-buffett]]
[[fmp-financial-statements-stable]]
[[fmp-key-metrics-ratios]]

## Pagine collegate
[[vi-06-webapp-value-investing-fsd]]
[[webapp-value-investing-spec]]
[[value-investing-fmp-integration]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
