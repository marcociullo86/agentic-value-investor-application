---
id: moving-averages-ta
type: concept
title: "Moving Averages — Strumenti Trend-Following"
status: draft
created: 2026-06-05
updated: 2026-06-05
sources:
  - raw/2026-06-05-technical-analysis-financial-markets-1999.txt
tags: [technical-analysis, moving-averages, bollinger-bands, trend-following, ta-domain]
domain: technical-analysis-trading
---

# Moving Averages — Strumenti Trend-Following

**Dominio:** Analisi tecnica / trading attivo. Vedi [[technical-analysis-trading-domain]].

Le medie mobili sono i tool trend-following per eccellenza. Lisciando le fluttuazioni casuali del prezzo, aiutano a identificare la direzione del trend sottostante. Funzionano meglio in mercati in trend; generano whipsaw in mercati laterali. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 341]

"A moving average is essentially a following device. Because it is based on past prices, it always lags the market. This lag is a necessary tradeoff for the smoothing effect."

---

## Tipi di Moving Average

### Simple Moving Average (SMA)

Media aritmetica dei prezzi di chiusura degli ultimi N periodi. Ogni periodo ha lo stesso peso. Il valore cambia ogni giorno: si aggiunge il prezzo piu recente e si elimina il piu vecchio.

**Uso:** La SMA e ancora il tipo piu usato nonostante varianti piu sofisticate. Non ci sono prove empiriche convincenti che le varianti piu complesse funzionino meglio. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 341]

### Exponential Moving Average (EMA)

Attribuisce peso maggiore ai prezzi piu recenti tramite un fattore di smorzamento (smoothing factor). Reagisce piu velocemente ai cambiamenti di prezzo rispetto alla SMA equivalente.

Formula: EMA(t) = α × Prezzo(t) + (1 − α) × EMA(t-1), dove α = 2/(N+1) per un periodo N.

Uso: Molto popolare come Signal line nel MACD (9-period EMA del MACD). Anche l'Elder Impulse System usa EMA (vedi [[elder-triple-screen-impulse-system]]).

### Weighted Moving Average

Peso crescente verso i periodi piu recenti in modo lineare. Meno usato della EMA.

---

## Uso Principale: Crossover System

Il modo piu classico di usare le MA e il sistema a doppia media: [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 341]

1. **Segnale buy:** La MA piu corta (veloce) incrocia verso l'alto la MA piu lunga (lenta).
2. **Segnale sell:** La MA corta incrocia verso il basso la MA lunga.

Le combinazioni piu comuni:
- **Futures:** 4/9 giorni, 9/18 giorni, 5/20 giorni, 10/40 giorni.
- **Stocks (daily):** 50/200 giorni (la "golden cross" = 50-day incrocia sopra 200-day; "death cross" = 50-day incrocia sotto 200-day).
- **Stocks (weekly):** 30/40 settimane; la 40-week (200-day) MA e il benchmark di riferimento per il trend primario.

**Limitazione:** In mercati laterali (trendless), il crossover system genera whipsaw ripetuti con perdite continue. La MA e uno strumento di trend-following — non funziona senza un trend.

---

## Moving Average Envelopes

Bande tracciate a una percentuale fissa sopra e sotto la MA. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 347]

- Se i prezzi si allontanano troppo dalla MA (toccano le bande), sono in condizione di "overbought" o "oversold" relativo rispetto alla MA stessa.
- Le percentuali variano per mercato: per azioni tipicamente 3-5%; per indici 3%; per futures 1-3%.

---

## Bollinger Bands

Sviluppate da John Bollinger. Bande tracciate a +/- 2 deviazioni standard attorno a una SMA di 20 periodi (default). [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 347]

**Caratteristiche distintive:**
- Larghezza variabile: le bande si espandono in periodi di alta volatilita e si contraggono in periodi di bassa volatilita.
- Un "squeeze" (bande molto strette) e spesso seguito da un movimento esplosivo di prezzo.
- Prezzi che toccano la banda superiore = overbought relativo; banda inferiore = oversold relativo.
- I prezzi possono camminare lungo le bande in trend molto forti — il semplice tocco della banda non e un segnale autonomo.

**Uso come target:** In un trading range, i prezzi rimbalzano tra le due bande; la banda opposta e il target. Murphy suggerisce di usarle come target piuttosto che come trigger.

---

## MA e Cicli di Mercato

I periodi piu popolari per le MA rispecchiano i cicli dominanti dei mercati: [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 212]

- Il ciclo mensile (trading) = ~20 giorni di mercato.
- Il principio degli armonici: ogni ciclo e il doppio o la meta del precedente.
- Quindi: 5, 10, 20, 40 giorni (raddoppio progressivo dal ciclo base).
- Per stocks: 50 giorni (2.5 mesi) e 200 giorni (10 mesi o ~40 settimane).

**La MA come half-cycle:** Una MA di N periodi funziona meglio quando N e la meta del ciclo dominante (N = ciclo/2). Esempio: ciclo 40 giorni → MA 20 giorni ottimale.

---

## The Weekly Rule (4-Week Rule)

Sistema semplice e robusto basato sul breakout: [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 248]

- **Buy:** Prezzo chiude sopra il massimo delle ultime 4 settimane (20 trading days).
- **Sell (o short):** Prezzo chiude sotto il minimo delle ultime 4 settimane.

Il sistema e continuo (sempre nel mercato). Puo essere reso non-continuo usando un segnale piu corto (1-2 settimane) per la liquidazione — si esce dal mercato e si attende il prossimo 4-week breakout.

**Perche funziona:** Il ciclo dominante a 4 settimane (20 giorni) e uno dei cicli piu affidabili in molti mercati. Il sistema cattura i trend importanti e limita il whipsaw.

**Aggiustamenti:** Timeframe piu breve (2 settimane) per mercati piu veloci; piu lungo (8 settimane) per mercati piu lenti o in trading range.

---

## Adaptive Moving Average (AMA)

Sviluppato da Perry Kaufman (libro "Smarter Trading"). L'AMA si adatta automaticamente alla volatilita tramite un Efficiency Ratio: [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 222]

- Efficiency Ratio alto (alta direzionalita, bassa volatilita relativa) → AMA si muove piu velocemente (come una MA corta).
- Efficiency Ratio basso (alta volatilita senza direzione) → AMA si muove piu lentamente (come una MA lunga).

Risolve il problema del trade-off tra sensibilita e stabilita.

---

## Confronto con il Framework Elder

Murphy e Elder concordano sull'uso fondamentale delle MA ma con differenze metodologiche:

| Dimensione | Murphy | Elder |
|---|---|---|
| MA preferita | SMA o EMA, entrambe valide | EMA (peso al recente) come sistema Impulse |
| Uso principale | Crossover system, singola MA come support/resistance | EMA lenta (trend timeframe) + MACD-Histogram (momentum) nel Triple Screen |
| Bollinger Bands | Target zone, volatility squeeze | Non centrale nel framework Elder |
| Weekly Rule | Sistema autonomo affidabile | Non citato esplicitamente |

Vedi [[elder-triple-screen-impulse-system]] per il framework Elder che usa EMA + MACD-Histogram in modo coordinato.

---

## Relazione con altri concetti wiki

- [[murphy-technical-analysis-financial-markets-1999]] — sorgente principale (Ch. 9)
- [[john-murphy]] — autore
- [[dow-theory]] — le MA operativizzano il concetto Dow di trend
- [[trend-trendlines-support-resistance]] — le MA fungono come support/resistance dinamici
- [[oscillators-momentum-rsi]] — MACD combina due MA in un oscillatore; Murphy: quando le MA non bastano, usare gli oscillatori
- [[volume-open-interest]] — il volume conferma i crossover di MA
- [[elder-triple-screen-impulse-system]] — Elder usa EMA + MACD-Histogram come sistema integrato
- [[technical-analysis-trading-domain]] — separazione dominio
