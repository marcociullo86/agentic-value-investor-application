---
id: elder-triple-screen-impulse-system
type: concept
title: "Triple Screen Trading System e Impulse System (Elder)"
status: draft
created: 2026-06-05
updated: 2026-06-05
sources:
  - raw/2026-06-05-alexander-elder-new-trading-living-2014.txt
tags: [technical-analysis, trading, triple-screen, impulse-system, indicators, ta-domain]
domain: technical-analysis-trading
---

# Triple Screen Trading System e Impulse System

**Dominio:** Analisi tecnica / trading attivo. Vedi [[technical-analysis-trading-domain]] per il confine con il dominio value investing dell'applicazione.

---

## Il problema dei timeframe multipli

I mercati si muovono simultaneamente su timeframe mensili, settimanali, giornalieri e infraday — spesso in direzioni opposte. Un trader che guarda solo il suo timeframe preferito viene colpito dai movimenti provenienti da timeframe piu lunghi.

**Soluzione**: analizzare sistematicamente almeno due timeframe vicini (legati da un fattore di ~5) con ruoli distinti:
- **Timeframe lungo**: decisione strategica (bull o bear)
- **Timeframe intermedio**: timing tattico (quando entrare nel senso del trend)
- **Timeframe corto** (opzionale): entry di precisione

[^src: raw/2026-06-05-alexander-elder-new-trading-living-2014.txt §32. Time]

---

## Il fattore 5 tra timeframe

| Timeframe lungo | Timeframe intermedio | Timeframe corto |
|---|---|---|
| Mensile | Settimanale | Giornaliero |
| Settimanale | Giornaliero | Orario |
| Giornaliero | 1h | 10-12 min |
| 25-30 min | 5-8 min | 2 min |

Per lo swing trader: **settimanale** (strategico) → **giornaliero** (tattico) → **infraday** (entry).  
Per il day trader: **25-30 min** (strategico) → **5 min** (tattico) → **2 min** (entry).

[^src: raw/2026-06-05-alexander-elder-new-trading-living-2014.txt §33. Trading Timeframes]

---

## Triple Screen: i tre screen

Sistema sviluppato da Elder nel 1985, pubblicato su Futures magazine nell'aprile 1986. Combina il meglio dei trend-following indicators con il meglio degli oscillatori, eliminando i difetti di entrambi se usati isolatamente.

**Il problema che risolve:**
- I trend-following indicators (MA, MACD) generano segnali buy in uptrend MA anche sell in uptrend quando girano → efficaci in trend, danno whipsaw nei range
- Gli oscillatori generano segnali contrari efficaci nei range MA segnali pericolosi in trend forti
- Usarli insieme senza struttura produce segnali contraddittori e "voting rigging" (si trovano sempre indicatori che confermano il bias desiderato)

**Soluzione Triple Screen**: i due tipi di indicatori vengono assegnati a timeframe diversi con ruoli diversi.

---

### Screen 1 — Market Tide (timeframe lungo)

Obiettivo: identificare il bias strategico (bull/bear).  
Strumento: indicatore trend-following sul timeframe piu lungo.

**Strumenti raccomandati per Screen 1:**
- Slope del weekly MACD-Histogram (segnale originale del sistema 1986)
- Slope della weekly EMA (alternativa piu stabile, meno jumpy)
- **Impulse System** (preferito da Elder dopo l'invenzione negli anni '90): combina EMA + MACD-H

**Logica dello Screen 1 come censore:**
- Se Screen 1 e bullish → si puo SOLO comprare o stare fuori
- Se Screen 1 e bearish → si puo SOLO vendere allo scoperto o stare fuori
- Screen 1 non dice cosa fare — dice cosa e VIETATO fare

> "Triple Screen demands that you examine the long-term chart first. It allows you to trade only in the direction of the tide."

I migliori segnali di MACD-Histogram sul settimanale:
- Buy piu forte: slope che sale da sotto lo zero (spring → bull move)
- Buy piu debole: slope che sale da sopra lo zero (summer → uptrend maturo)
- Sell piu forte: slope che scende da sopra lo zero (autumn → bear move)
- Sell piu debole: slope che scende da sotto lo zero (winter → downtrend maturo)

[^src: raw/2026-06-05-alexander-elder-new-trading-living-2014.txt §39. Triple Screen Trading System]

---

### Screen 2 — Market Wave (timeframe intermedio)

Obiettivo: trovare la wave che va contro il tide per ottenere entry a prezzi migliori.  
Strumento: oscillatore sul timeframe intermedio (giornaliero per swing trader).

**Logica**: quando il trend settimanale e al rialzo, le correzioni giornaliere (waves al ribasso) creano opportunita di acquisto a prezzi scontati rispetto al momentum principale.

**Strumenti preferiti per Screen 2:**
- **2-day EMA of Force Index** (preferito da Elder): negativo = pullback → opportunita long; positivo = bounce → opportunita short
- Force Index 2gg non deve scendere a un nuovo minimo multi-settimana (segnalerebbe debolezza del trend)
- **Stochastic** (alternativa): sotto 30 in uptrend settimanale = oversold → buy; sopra 70 in downtrend settimanale = overbought → short
- **RSI**: logica analoga a Stochastic

| Trend settimanale | Screen 2 segnale | Azione consentita |
|---|---|---|
| Up | Force Index 2gg negativo | Buy |
| Up | Force Index 2gg positivo | Ignorare (o uscire da long esistenti) |
| Down | Force Index 2gg positivo | Short |
| Down | Force Index 2gg negativo | Ignorare (o chiudere short) |

[^src: raw/2026-06-05-alexander-elder-new-trading-living-2014.txt §39. Triple Screen Trading System]

---

### Screen 3 — Entry Technique

Obiettivo: entry preciso quando Screen 1 e 2 sono allineati.

**Tecniche di entry:**

**A. Average EMA Penetration** (consigliato quando non si ha accesso a dati infraday)
1. Misurare quanto i pullback normali penetrano sotto la fast EMA nelle ultime 4-6 settimane
2. Calcolare la penetrazione media
3. Stimare dove sara l'EMA domani (livello attuale + slope di ieri)
4. Piazzare un buy order = EMA stimata - penetrazione media
5. Abbassare il buy order ogni giorno finche: order viene eseguito, o Screen 1 cambia direzione

**B. Upside Breakout** (alternativa classica)
- Long: buy order un tick sopra il massimo del giorno precedente
- Short: sell order un tick sotto il minimo del giorno precedente

**Target e stop:**
- Target: impostato sul timeframe piu lungo (es. settimanale) — guardare support/resistance settimanali, value zone weekly
- Stop: impostato sul timeframe intermedio (es. giornaliero) — sotto il recente minor low per i long

[^src: raw/2026-06-05-alexander-elder-new-trading-living-2014.txt §39. Triple Screen Trading System]

---

## Tabella riassuntiva Triple Screen

| Trend settimanale | Trend giornaliero | Azione | Ordine |
|---|---|---|---|
| Up | Up | Stand aside | Nessuno |
| Up | Down (pullback) | Go long | EMA penetration o prev-day-high breakout |
| Down | Down | Stand aside | Nessuno |
| Down | Up (bounce) | Go short | EMA penetration o prev-day-low breakdown |

---

## Impulse System

Sviluppato da Elder a meta anni '90, presentato pubblicamente in "Come into My Trading Room" (2002, libro dell'anno Barron's).

### Concetto

Ogni barra di mercato puo essere descritta da due fattori:
- **Inerzia** (momentum): direzione dell'EMA veloce
- **Potere** (momentum accelerazione): slope del MACD-Histogram

Quando entrambi puntano nella stessa direzione, il mercato ha "impulso":
- Entrambi al rialzo → barra **verde** (bull impulse)
- Entrambi al ribasso → barra **rossa** (bear impulse)
- In direzioni opposte → barra **blu** (neutrale)

### Sistema di censura, non di segnali

L'Impulse System NON dice cosa fare — dice cosa e VIETATO:
- Barra rossa su qualunque timeframe → acquisto VIETATO
- Barra verde su qualunque timeframe → short VIETATO
- Barra blu → nessun divieto

**Regola d'oro**: se anche UNO dei due timeframe (settimanale o giornaliero) mostra il colore contrario al trade pianificato, il trade e vietato.

### Entry e exit con Impulse

**Entry long** (bottom fishing): monitorare quando il giornaliero smette di essere rosso (diventa blu o verde) mentre il settimanale e ancora rosso. Non appena il settimanale smette di essere rosso (diventa blu) → entry consentita.

**Entry short** (top fishing): monitorare quando il giornaliero smette di essere verde mentre il settimanale e ancora verde. Non appena il settimanale smette di essere verde → short consentito.

**Exit**: per i momentum trader, uscire non appena il colore del timeframe piu corto smette di supportare la direzione del trade (es. un giornaliero che da verde diventa blu durante un long).

### Colori e logica

| EMA | MACD-H | Colore | Significato | Divieto |
|---|---|---|---|---|
| Rising | Rising | Verde | Uptrend accelerante | Short vietato |
| Falling | Falling | Rosso | Downtrend accelerante | Buy vietato |
| Rising | Falling | Blu | Neutrale | Nessuno |
| Falling | Rising | Blu | Neutrale | Nessuno |

[^src: raw/2026-06-05-alexander-elder-new-trading-living-2014.txt §40. The Impulse System]

---

## Stagioni degli indicatori (MACD-Histogram)

Il ciclo di mercato applicato al MACD-Histogram produce quattro "stagioni":

| Slope MACD-H | Posizione vs zero | Stagione | Azione preferita |
|---|---|---|---|
| Rising | Below zero | Primavera | Go long (migliore risk/reward) |
| Rising | Above zero | Estate | Iniziare a uscire dai long |
| Falling | Above zero | Autunno | Go short (migliore risk/reward) |
| Falling | Below zero | Inverno | Iniziare a coprire gli short |

La primavera e emotivamente difficile (la memoria del downtrend e fresca) ma offre il miglior risk/reward per i long. L'autunno e emotivamente difficile (la folla continua ad essere bullish) ma offre il miglior risk/reward per gli short.

[^src: raw/2026-06-05-alexander-elder-new-trading-living-2014.txt §32. Time]

---

## Divergenze MACD-Histogram: i segnali piu potenti

Una divergenza valida richiede obbligatoriamente:
1. Due bottom (bullish) o due top (bearish) di MACD-Histogram
2. Il MACD-Histogram deve attraversare il proprio zero-line tra i due bottom/top (se non lo attraversa, non e una divergenza valida)
3. Il secondo bottom deve essere piu alto del primo (bullish) o il secondo top piu basso (bearish)
4. La distanza ottimale tra i due bottom/top: 20-40 barre (piu vicini a 20 = piu affidabile)
5. Il secondo bottom/top deve essere al massimo meta dell'altezza/profondita del primo

**Divergenze triple** (tre bottom/top): ancora piu forti; un'ordinaria divergenza deve prima abortire, poi riforme.

**Hound of the Baskervilles**: quando una divergenza segnala una reversal MA il mercato non la rispetta (continua nella direzione opposta) → segnale che qualcosa e fondamentalmente diverso; considerare di entrare nella nuova direzione.

[^src: raw/2026-06-05-alexander-elder-new-trading-living-2014.txt §23. Moving Average Convergence-Divergence]

---

## Collegamento con altri concetti wiki

- [[technical-analysis-trading-domain]] — confine di dominio
- [[elder-trading-psychology]] — base psicologica dei sistemi Elder
- [[elder-risk-management-2pct-6pct]] — risk management complementare ai sistemi
- [[elder-new-trading-living-2014]] — sorgente completa
- [[alexander-elder]] — entita autore

---

## Storie collegate

<!-- Sezione proprieta PM — non modificare -->

- [EP-024](../../management/kanban/EP-024-riepilogo-e-technical-analysis-tab/EP-024.md) — Tab Technical Analysis (entry-timing advisor Triple-Screen-like + Impulse-aware)
- [US-099](../../management/kanban/EP-024-riepilogo-e-technical-analysis-tab/US-099-entry-timing-advisor-be/US-099.md) — BE Entry-timing advisor che implementa la tabella Screen 1 × Screen 2 × Screen 3 di Elder
