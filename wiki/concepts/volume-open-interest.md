---
id: volume-open-interest
type: concept
title: "Volume e Open Interest — Indicatori di Conferma"
status: draft
created: 2026-06-05
updated: 2026-06-05
sources:
  - raw/2026-06-05-technical-analysis-financial-markets-1999.txt
tags: [technical-analysis, volume, open-interest, obv, cot-report, ta-domain]
domain: technical-analysis-trading
---

# Volume e Open Interest — Indicatori di Conferma

**Dominio:** Analisi tecnica / trading attivo. Vedi [[technical-analysis-trading-domain]].

Volume e open interest sono indicatori **secondari** che confermano o mettono in dubbio i segnali di prezzo. "Volume precede il prezzo": la perdita di pressione si manifesta prima nel volume che nel prezzo stesso. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 157]

---

## Volume

### Definizione

Volume = numero totale di azioni (stocks) o contratti (futures) scambiati in un determinato periodo. Representato come barre verticali sotto il grafico prezzi. Fornisce una misura dell'intensita del mercato. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 69]

**Differenza stocks vs futures:**
- Stocks: volume disponibile immediatamente.
- Futures: volume riportato con un giorno di ritardo (solo le stime sono disponibili il giorno stesso).

### Regole Interpretative del Volume

**Regola fondamentale:** Il volume dovrebbe espandersi nella direzione del trend primario e contrarsi nelle correzioni. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 162]

| Prezzo | Volume | Interpretazione |
|---|---|---|
| In salita | Crescente | Bullish — nuovi acquirenti entrano nel mercato |
| In salita | Decrescente | Warning bearish — rally su volume calante, possibile esaurimento |
| In calo | Crescente | Bearish — venditori aggressivi; probabile continuazione del calo |
| In calo | Decrescente | Potenziale bottom — il calo sta esaurendo i venditori |

**Volume ai breakout:** Un breakout da un pattern o da un livello di support/resistance deve essere accompagnato da incremento notevole di volume. Breakout su volume basso = sospetto, alta probabilita di falso segnale. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 162]

**Volume ai reversal:**
- Ai top: il volume elevato e utile ma non critico. I mercati possono cadere "per loro peso".
- Ai bottom: il pickup di volume e **assolutamente essenziale** per confermare il reversal. Senza incremento di volume, il rimbalzo e probabilmente un "dead cat bounce".

**Volume e psicologia:** "Volume precede il prezzo." La perdita di pressione acquisto (o vendita) si manifesta prima nel volume declinante che nel prezzo stesso — il volume e un leading indicator di reversal.

---

## On Balance Volume (OBV)

Sviluppato e popolarizzato da Joseph Granville nel 1963. Prima versione moderna di volume indicator cumulativo. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 193]

**Costruzione:**
- Giorno up (chiusura > chiusura precedente): aggiunge il volume totale a un running total.
- Giorno down (chiusura < chiusura precedente): sottrae il volume totale.

**Interpretazione:** La **direzione della linea OBV** (non il valore assoluto) e l'informazione rilevante. I valori assoluti cambiano a seconda di quando si inizia a calcolare.

- OBV e prezzo in salita insieme = uptrend confermato.
- OBV in calo mentre il prezzo sale = bearish divergence, warning di possibile top.
- OBV in salita mentre il prezzo scende = bullish divergence, warning di possibile bottom.
- OBV che fa nuovi massimi prima del prezzo = leading indicator bullish.

**Limitazione:** Assegna l'intero volume del giorno a un segno + o -, indipendentemente dall'entita del movimento. Una chiusura up di 1 cent e una di 10 punti ricevono lo stesso peso. Varianti piu sofisticate pesano il volume in proporzione al movimento.

---

## Open Interest (Futures)

Open interest = numero totale di contratti futures aperti e in vita alla fine della giornata. E il totale sul lato long (o equivalentemente sul lato short — per ogni long c'e sempre uno short). NON e la somma di long + short. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 70]

**Riportato:** Con un giorno di ritardo nei mercati futures. Non applicabile agli stocks (le azioni non hanno "scadenza").

### Regole dell'Open Interest

Le quattro combinazioni fondamentali: [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 198]

| Prezzo | Open Interest | Interpretazione |
|---|---|---|
| In salita | Crescente | **Bullish** — nuovo denaro entra, nuovi long aggressivi |
| In salita | Decrescente | **Bearish** — rally da short covering (losers chiudono shorts), non da nuova convinzione |
| In calo | Crescente | **Bearish** — nuovo denaro entra short aggressivi, downtrend confermato |
| In calo | Decrescente | **Bullish** — calo da liquidazione forzata dei long (perdenti), esaurimento imminente |

### Situazioni Speciali con Open Interest

**Fine del trend:** Verso la fine di un importante trend, open interest elevato poi livellamento/calo = early warning di cambio di trend. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 200]

**High open interest ai top:** Se open interest e molto alto a un top e il prezzo scende improvvisamente, tutti i nuovi long stabiliti vicino al top sono "trapped" con perdite. La loro liquidazione forzata mantiene pressione ribassista → calo prolungato.

**Open interest in trading range:** Open interest che cresce durante una consolidazione orizzontale intensifica il breakout quando avviene. I trader "sorpresi" dalla parte sbagliata amplificano il move con le loro coperture forzate.

**Open interest a completamento di pattern:** Breakout confermato da rising open interest + rising volume = segnale molto forte.

---

## Commitments of Traders (COT) Report

Rapporto settimanale della CFTC (Commodity Futures Trading Commission) che suddivide il totale dell'open interest in tre categorie: [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 302]

1. **Commercials (hedgers):** Produttori, processori, merchant che usano il futures per coprire il rischio operativo. Sono considerati le "smart money" del mercato perche conoscono le proprie industry meglio di chiunque altro.
2. **Large speculators:** Hedge funds, CTAs (Commodity Trading Advisors), grandi fondi. Tipicamente trend-following.
3. **Small traders (public):** Piccoli speculatori; spesso dalla parte sbagliata nei punti di svolta.

**"Watch the commercials":** Quando i commercials sono net long in modo inusuale (o net short), e un segnale contrarian significativo. I commercials vendono in eccesso quando sono certi che il mercato sia "sopravvalutato" rispetto ai fondamentali del loro settore. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 176]

**Large speculators vs public:** I large speculators hanno quasi sempre torto nei punti di svolta (trend-followers che arrivano tardi); il pubblico e cronicamente dalla parte sbagliata. Quando entrambi sono estremi nella stessa direzione (es. massimamente long), e un segnale contrarian forte.

---

## Blowoffs e Selling Climaxes

**Blowoff top:** Il prezzo sale parabolicament su volume e open interest in forte crescita, poi crolla improvvisamente su volume altissimo. La caduta dopo il blowoff e spesso rapida e violenta. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 302]

**Selling climax (bottom):** Il prezzo crolla verticalmente su volume estremo. Il climax si esaurisce quando tutti i venditori forzati hanno venduto. Il segnale: il prezzo rimbalza su volume ancora molto alto ma in calo. Spesso segnala un bottom importante.

---

## Volume e Patterns

Il volume e il principale fattore di conferma di tutti i chart patterns (vedi [[chart-patterns-reversal-continuation]]):

- **Head and Shoulders:** Volume decrescente sulla testa e sulla spalla destra; volume crescente alla rottura della neckline.
- **Triangoli:** Volume in contrazione durante la formazione; spike al breakout.
- **Flags/Pennants:** Volume molto basso durante la pausa; forte incremento al breakout.
- **Double Top/Bottom:** Volume piu basso al secondo picco/minimo; volume elevato al breakout della conferma.

---

## Relazione con altri concetti wiki

- [[murphy-technical-analysis-financial-markets-1999]] — sorgente principale (Ch. 7)
- [[john-murphy]] — autore
- [[dow-theory]] — il 5o principio Dow: "Volume must confirm the trend"
- [[trend-trendlines-support-resistance]] — il volume conferma i breakout di trendline e livelli
- [[chart-patterns-reversal-continuation]] — volume come principale fattore di conferma di tutti i pattern
- [[moving-averages-ta]] — volume conferma i crossover di MA
- [[oscillators-momentum-rsi]] — OBV e i volume-based oscillators come alternative agli oscillators momentum-based
- [[technical-analysis-trading-domain]] — separazione dominio
