# Risoluzione Quesiti Tecnici e di Dominio: WebApp Value Investing

---

## Risoluzione Q_002: Architectural Decision Record (ADR) - Scelta Framework Frontend

**Contesto:** Il documento FSD iniziale proponeva React, Vue.js o Angular per il livello Client (Frontend). È necessaria una scelta definitiva per sbloccare l'Epica EP-005 (Dashboard).

**Decisione:** Il framework scelto per l'implementazione del frontend è **React (con Next.js in modalità SPA/SSG)**.

**Motivazioni (Allineamento con la filosofia di efficienza):**
1.  **Velocità di Esecuzione e Componentizzazione:** L'interfaccia dell'applicazione richiederà molte tabelle dati complesse (bilanci decennali), grafici dinamici e il pannello a "Semaforo" (Traffic Light). React offre il più vasto ecosistema di librerie di data-grid e charting (es. Recharts, Ag-Grid) pronte all'uso, accelerando i tempi di sviluppo.
2.  **Manutenibilità:** La filosofia a componenti di React si sposa perfettamente con la scomposizione modulare delle metriche finanziarie (es. un componente indipendente per il calcolo del ROE, uno per il DCF).
3.  **Community e Longevità:** Come in un investimento "value", scegliamo un asset solido. React, supportato da Meta, garantisce un "moat" tecnologico (community enorme, risoluzione rapida dei bug, facile reperibilità di sviluppatori).

**Impatto sulle Storie:** Le storie US-014, US-015, US-016 vengono sbloccate e lo stack frontend è confermato (React + libreria di state management da definire in base alle preferenze del team di sviluppo, es. Zustand o Redux Toolkit).

---

## Risoluzione Q_003: Criteri Esatti dello Screener Parametrico (Approccio Buffett/Graham)

**Contesto:** Il RF1 del FSD menziona "capitalizzazione e settore" senza fornire le metriche esatte. Dobbiamo definire le enumerazioni e le soglie, allineandole all'approccio di selezione di Berkshire Hathaway.

**Filtri di Capitalizzazione di Mercato (Market Cap):**
In ottica Graham/Buffett, la dimensione conta per la stabilità, ma Buffett stesso ha iniziato con le micro/small cap prima che i capitali di Berkshire diventassero troppo grandi. Lo screener offrirà queste fasce:
1.  **Micro Cap:** $50M - $300M (Il terreno di caccia originale di Buffett, massima inefficienza di prezzo).
2.  **Small Cap:** $300M - $2B
3.  **Mid Cap:** $2B - $10B
4.  **Large Cap:** $10B - $200B (Il terreno di gioco attuale per investimenti significativi).
5.  **Mega Cap:** > $200B
*(Soglia minima hardcoded: $50M. Si escludono le "Nano Cap" per illiquidità e rischio frode eccessivo).*

**Classificazione Settoriale (Settori GICS - Global Industry Classification Standard):**
Lo screener utilizzerà i settori standard GICS supportati da Financial Modeling Prep, ma con la possibilità di applicare filtri di esclusione "alla Buffett":
1.  Information Technology
2.  Financials (Banche, Assicurazioni)
3.  Health Care
4.  Consumer Discretionary
5.  Consumer Staples (Il settore preferito storicamente per prevedibilità, es. Coca-Cola)
6.  Communication Services
7.  Industrials
8.  Energy
9.  Materials
10. Real Estate
11. Utilities

**Filtro "Circle of Competence" (Aggiunta ai requisiti):**
Per aderire strettamente alla filosofia di Berkshire Hathaway, lo screener deve includere una checkbox o un preset chiamato *"Exclude Hard-to-Predict Sectors"*. Se attivato, questo filtro escluderà automaticamente dalla ricerca settori come biotecnologie in fase pre-approvazione, startup tech non redditizie e società di estrazione mineraria speculativa, restringendo il campo ad aziende con modelli di business storicamente stabili e prevedibili.

**Impatto sulle Storie:** L'Epica EP-001 è sbloccata. Le soglie e le categorie fornite costituiscono i payload esatti da richiedere agli endpoint di screening di FMP.
