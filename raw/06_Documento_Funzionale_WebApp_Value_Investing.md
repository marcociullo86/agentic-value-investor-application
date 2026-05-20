# Documento di Specifica Funzionale (FSD)
## Piattaforma Web per lo Screening di Titoli in ottica Value Investing

---

## 1. Scopo del Progetto
Il presente documento descrive i requisiti funzionali e architetturali per lo sviluppo di un'applicazione web mirata all'identificazione di titoli azionari sottovalutati. L'obiettivo è digitalizzare e automatizzare i processi analitici derivati dalla filosofia di Benjamin Graham e Warren Buffett. La piattaforma si interfaccerà con le API di **Financial Modeling Prep (FMP)** per l'estrazione in tempo reale e storica dei dati di bilancio, elaborandoli attraverso un motore di regole personalizzato (Rule Engine).

## 2. Architettura di Sistema Raccomandata
Per garantire scalabilità, manutenibilità e prestazioni ottimali nell'elaborazione dei dati finanziari, il sistema sarà suddiviso in tre livelli:
* **Frontend (Client):** Single Page Application (es. React, Vue.js o Angular) per una fruizione dinamica delle dashboard e dei risultati dello screening.
* **Backend (Server):** Applicazione basata su **Kotlin e Spring Framework** (Spring Boot, Spring Data). Questo livello agirà da orchestratore: gestirà il routing, il caching delle chiamate API per ottimizzare i costi, l'implementazione della logica di validazione finanziaria e l'esposizione di endpoint REST/GraphQL per il frontend.
* **Data Provider (External API):** Integrazione con *financialmodelingprep.com* per il recupero di Income Statement, Balance Sheet, Cash Flow Statement e Key Metrics.
* **Database (Storage):** Relazionale (es. PostgreSQL via Spring Data JPA) per salvare configurazioni utente, watchlist, e fare caching dei dati di bilancio giornalieri.

## 3. Flusso dei Dati (Data Flow)
1. L'utente richiede l'analisi di un singolo *ticker* o avvia uno *screener* basato su parametri quantitativi.
2. Il backend in Kotlin intercetta la richiesta, verifica se i dati sono presenti in cache (valida per 24h) per evitare chiamate ridondanti a FMP.
3. Se non in cache, il backend interroga gli endpoint FMP necessari.
4. I dati JSON grezzi vengono mappati in oggetti di dominio e passati al "Value Investing Rule Engine".
5. Il Rule Engine calcola il Valore Intrinseco, il Margine di Sicurezza e valuta il superamento dei check finanziari (ROE, Margini, Debito).
6. Il risultato strutturato viene inviato al frontend per il rendering.

## 4. Requisiti Funzionali (RF)

### RF1: Motore di Ricerca e Screening
* **Ricerca per Ticker:** L'utente deve poter inserire un simbolo (es. AAPL, MSFT, BYIT) e ottenere l'analisi istantanea.
* **Screener di Mercato:** Possibilità di filtrare l'intero database FMP impostando soglie minime per capitalizzazione di mercato e settore.

### RF2: Integrazione API (Financial Modeling Prep)
Il backend dovrà implementare i client per i seguenti endpoint FMP:
* `GET /api/v3/income-statement/{ticker}?limit=10` (Storico 10 anni per la stabilità degli utili e margini).
* `GET /api/v3/balance-sheet-statement/{ticker}?limit=10` (Per debito, asset e current ratio).
* `GET /api/v3/cash-flow-statement/{ticker}?limit=10` (Per CapEx, Free Cash Flow, Owner Earnings).
* `GET /api/v3/key-metrics/{ticker}?limit=10` (Metriche pre-calcolate come ROE, ROIC).

### RF3: Il "Value Investing Rule Engine" (Logica di Business)
Il cuore dell'applicazione. Il sistema deve validare automaticamente le seguenti regole stringenti:
* **Redditività:** ROE > 15% e ROIC > 12-15% costanti negli ultimi 5-10 anni.
* **Pricing Power:** Gross Margin > 40% e Net Margin > 10%.
* **Solidità Finanziaria:** * Current Ratio > 2 (o > 1.5 per business molto stabili).
    * *Debito a Lungo Termine / Utile Netto* < 4 (Il debito deve poter essere estinto con max 4 anni di utili).
* **Capitale Intensivo:** *CapEx / Utile Netto* < 25-30% (Identificazione di business a basso assorbimento di capitali).

### RF4: Calcolo del Valore Intrinseco e Margin of Safety
Il sistema deve calcolare autonomamente due metriche di prezzo:
1.  **Indice di Graham:** `Sqrt(22.5 * EPS * BVPS)` calcolato con i dati correnti.
2.  **Discounted Cash Flow (DCF):** Proiezione del Free Cash Flow (o Owner Earnings) basata sulla media storica di crescita (limitata precauzionalmente al max 5-7%), attualizzata con un discount rate (es. 9-10%) e tasso terminale (2-3%).
3.  **Margin of Safety (MoS):** Segnalazione visiva immediata se il `Prezzo di Mercato Attuale < (Valore Intrinseco DCF * 0.70)` (Sconto del 30%).

### RF5: Dashboard e Interfaccia Utente (UI)
* **Pannello di Sintesi ("Traffic Light"):** Un sistema visivo a semaforo (Verde, Giallo, Rosso) per ogni regola (es. ROE verde se >15%, rosso se <10%).
* **Grafici Storici:** Visualizzazione trend dei ricavi e dell'utile netto per valutare la consistenza del business.
* **Sezione Qualitativa (Moat):** Un'area annotazioni o un form di checklist in cui l'analista può inserire considerazioni soggettive sull'Economic Moat (Asset Immateriali, Switching Costs, Network Effect).

## 5. Requisiti Non Funzionali
* **Rate Limiting API:** Implementazione di un sistema di throttling nel backend Spring per rispettare i limiti di chiamate della licenza FMP.
* **Resilienza:** Gestione dei fallimenti delle API esterne con meccanismi di Retry e fallback sui dati in cache.
* **Tipizzazione:** Utilizzo estensivo di data classes in Kotlin per mappare in modo sicuro le risposte JSON delle API di FMP, prevenendo Null Pointer Exception in caso di dati contabili mancanti.
