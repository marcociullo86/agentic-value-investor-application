---
type: source
sources: ["raw/07_Risoluzione_Q002_Q003.md"]
status: draft
created: 2026-05-20
updated: 2026-05-20
tags: [product-spec, frontend, spa, react, nextjs, screener, market-cap, gics, q002, q003]
---
# Risoluzione Q_002 e Q_003 — ADR Frontend e Criteri Screener

> Documento di decisione tecnica che formalizza la scelta del framework SPA (Q_002: React + Next.js) e i criteri operativi del screener parametrico (Q_003: fasce market cap GICS, filtro Circle of Competence).

## Contesto

Il documento FSD iniziale lasciava aperte due questioni bloccanti: la scelta definitiva del framework frontend SPA tra React, Vue.js e Angular (Q_002) e la definizione delle soglie di capitalizzazione e lista settoriale del screener parametrico RF1 (Q_003). Questo raw risolve entrambe formalmente. [^src: raw/07_Risoluzione_Q002_Q003.md §Risoluzione Q_002: Architectural Decision Record (ADR) - Scelta Framework Frontend]

## Dettaglio

### ADR Q_002: Scelta Framework Frontend

**Decisione:** Il framework adottato per il frontend e' **React (con Next.js in modalita' SPA/SSG)**. [^src: raw/07_Risoluzione_Q002_Q003.md §Risoluzione Q_002: Architectural Decision Record (ADR) - Scelta Framework Frontend]

**Motivazioni:**

1. **Ecosistema librerie data-grid e charting:** L'interfaccia richiede tabelle di bilanci decennali, grafici dinamici e il pannello Traffic Light. React offre l'ecosistema piu' ampio di librerie pronte (es. Recharts, Ag-Grid), riducendo i tempi di sviluppo. [^src: raw/07_Risoluzione_Q002_Q003.md §Risoluzione Q_002: Architectural Decision Record (ADR) - Scelta Framework Frontend]
2. **Manutenibilita' a componenti:** La filosofia component-based di React e' allineata alla scomposizione modulare delle metriche finanziarie (un componente per ROE, uno per DCF, ecc.). [^src: raw/07_Risoluzione_Q002_Q003.md §Risoluzione Q_002: Architectural Decision Record (ADR) - Scelta Framework Frontend]
3. **Community e longevita':** React, supportato da Meta, garantisce disponibilita' di sviluppatori e risoluzione rapida dei bug (analogo a un investimento value per "moat" tecnologico). [^src: raw/07_Risoluzione_Q002_Q003.md §Risoluzione Q_002: Architectural Decision Record (ADR) - Scelta Framework Frontend]

**State management:** da definire in base alle preferenze del team (candidati: Zustand o Redux Toolkit).

**Storie sbloccate:** US-014, US-015, US-016 (EP-005 Dashboard). [^src: raw/07_Risoluzione_Q002_Q003.md §Risoluzione Q_002: Architectural Decision Record (ADR) - Scelta Framework Frontend]

---

### Criteri Q_003: Screener Parametrico Buffett/Graham

#### Fasce di Capitalizzazione di Mercato

Lo screener espone cinque fasce, allineate alla progressione storica di Buffett: [^src: raw/07_Risoluzione_Q002_Q003.md §Risoluzione Q_003: Criteri Esatti dello Screener Parametrico (Approccio Buffett/Graham)]

| Fascia | Range | Note |
|--------|-------|------|
| Micro Cap | $50M – $300M | Terreno di caccia originale Buffett; massima inefficienza di prezzo |
| Small Cap | $300M – $2B | |
| Mid Cap | $2B – $10B | |
| Large Cap | $10B – $200B | Terreno di gioco attuale per investimenti significativi |
| Mega Cap | > $200B | |

Soglia minima hardcoded: **$50M**. Le Nano Cap sono escluse per illiquidade e rischio frode eccessivo. [^src: raw/07_Risoluzione_Q002_Q003.md §Risoluzione Q_003: Criteri Esatti dello Screener Parametrico (Approccio Buffett/Graham)]

#### Classificazione Settoriale (GICS)

Lo screener utilizza i **settori GICS standard** supportati da FMP: [^src: raw/07_Risoluzione_Q002_Q003.md §Risoluzione Q_003: Criteri Esatti dello Screener Parametrico (Approccio Buffett/Graham)]

1. Information Technology
2. Financials (Banche, Assicurazioni)
3. Health Care
4. Consumer Discretionary
5. Consumer Staples (settore preferito Buffett per prevedibilita', es. Coca-Cola)
6. Communication Services
7. Industrials
8. Energy
9. Materials
10. Real Estate
11. Utilities

#### Filtro "Circle of Competence"

Aggiunta ai requisiti: checkbox/preset **"Exclude Hard-to-Predict Sectors"**. Se attivato, esclude automaticamente dalla ricerca settori a elevata imprevedibilita' (biotecnologie pre-approvazione, startup tech non redditizie, societa' di estrazione mineraria speculativa), restringendo il campo ad aziende con modelli di business storicamente stabili. [^src: raw/07_Risoluzione_Q002_Q003.md §Risoluzione Q_003: Criteri Esatti dello Screener Parametrico (Approccio Buffett/Graham)]

**Impatto:** I payload esatti sono ora definiti per le chiamate agli endpoint di screening FMP. EP-001 e' pienamente sbloccata. [^src: raw/07_Risoluzione_Q002_Q003.md §Risoluzione Q_003: Criteri Esatti dello Screener Parametrico (Approccio Buffett/Graham)]

## Concetti correlati
[[webapp-architecture-vi]]
[[value-investing-rule-engine]]
[[fmp-search]]
[[warren-buffett]]
[[economic-moat]]

## Pagine collegate
[[vi-06-webapp-value-investing-fsd]]
[[webapp-value-investing-spec]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
