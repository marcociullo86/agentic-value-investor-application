---
id: oscillators-momentum-rsi
type: concept
title: "Oscillatori, Momentum e RSI — Indicatori Secondari"
status: draft
created: 2026-06-05
updated: 2026-06-05
sources:
  - raw/2026-06-05-technical-analysis-financial-markets-1999.txt
tags: [technical-analysis, oscillators, rsi, macd, stochastics, momentum, contrary-opinion, ta-domain]
domain: technical-analysis-trading
---

# Oscillatori, Momentum e RSI — Indicatori Secondari

**Dominio:** Analisi tecnica / trading attivo. Vedi [[technical-analysis-trading-domain]].

Gli oscillatori sono strumenti **secondari**, sempre subordinati all'analisi del trend primario. Diventano particolarmente utili in due contesti: (1) mercati in trading range dove le MA non funzionano; (2) verso la fine di un trend per identificare divergenze e perdita di momentum. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 257]

"The oscillator is only a secondary indicator in the sense that it must be subordinated to basic trend analysis."

---

## Tre Usi Principali degli Oscillatori

1. **Lettura in area estrema:** Quando l'oscillatore raggiunge livelli estremi (overbought/oversold), il move di prezzo potrebbe essere "andato troppo lontano troppo in fretta" — probabile consolidazione o correzione. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 360]

2. **Divergenza oscillatore/prezzo in area estrema:** Il segnale piu importante. Se il prezzo fa nuovi massimi ma l'oscillatore non li conferma (bearish divergence), o se il prezzo fa nuovi minimi ma l'oscillatore no (bullish divergence) → warning di imminente reversal.

3. **Crossing della zero line (o midpoint):** Segnali di trading nella direzione del trend primario. Comprare solo su crossing rialzista se il trend primario e up; vendere/short solo su crossing ribassista se il trend primario e down.

---

## Momentum

Il concetto base dell'analisi oscillatoria. Misura la **velocita** del cambiamento di prezzo (non il livello assoluto). [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 398]

Formula: **M = V − V*** (prezzo corrente meno prezzo N giorni fa)

- M > 0 = i prezzi sono piu alti rispetto a N giorni fa (upward momentum).
- M < 0 = i prezzi sono piu bassi rispetto a N giorni fa (downward momentum).
- M in calo ma ancora positivo = trend in salita ma decelera.

**Proprietà fondamentale:** La linea di momentum e sempre un passo avanti al prezzo. Il momentum si appiattisce prima che il prezzo raggiunga il picco, poi scende mentre il prezzo e ancora in salita — anticipa il reversal.

**Timeframe:** Periodi piu brevi (5 giorni) = piu sensibile ma piu rumoroso. Periodi piu lunghi (40 giorni) = piu smussato, cattura i grandi turning points.

**Rate of Change (ROC):** Variante in forma di ratio: ROC = 100 × (V/Vx). Midpoint = 100 invece di 0. Interpretazione identica al momentum ma in forma percentuale.

---

## MACD (Moving Average Convergence/Divergence)

Sviluppato da Gerald Appel. Combina due EMA in un oscillatore che misura il momentum tramite la differenza tra di esse. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 399]

**Costruzione:**
- MACD line = EMA a 12 periodi − EMA a 26 periodi.
- Signal line = EMA a 9 periodi del MACD (linea piu lenta).
- MACD Histogram = MACD − Signal line (misura la distanza tra le due linee).

**Segnali principali:**
- MACD incrocia Signal line verso l'alto = buy signal.
- MACD incrocia Signal line verso il basso = sell signal.
- MACD > 0 = la EMA veloce e sopra la lenta = trend bullish.
- MACD < 0 = trend bearish.

**MACD Histogram:** Barre positive quando MACD > Signal; barre negative quando MACD < Signal. L'histogram "gira" prima del crossover delle linee — anticipa il segnale. La divergenza dell'histogram rispetto al prezzo e il segnale piu precoce disponibile. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 399]

**Uso combinato weekly/daily:** Usare il MACD settimanale per la direzione del trend primario; il MACD giornaliero per il timing dell'entry. Questo approccio multi-timeframe e analogo al Triple Screen di Elder (vedi [[elder-triple-screen-impulse-system]]).

---

## RSI (Relative Strength Index)

Sviluppato da J. Welles Wilder Jr. (1978, "New Concepts in Technical Trading Systems"). Oscillatore normalizzato tra 0 e 100. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 239]

**Formula:** RSI = 100 − [100 / (1 + RS)], dove RS = media dei guadagni degli ultimi N giorni / media delle perdite degli ultimi N giorni.

**Interpretazione:**
- RSI > 70 = overbought; RSI < 30 = oversold.
- Periodi comuni: 9 o 14 giorni (Wilder usava 14).

**Segnali:**
1. **Crossover delle linee 70/30:** La linea RSI attraversa 70 verso il basso = sell signal; attraversa 30 verso l'alto = buy signal.
2. **Divergenza:** RSI fa nuovo massimo mentre il prezzo non lo conferma (bearish divergence). O RSI fa nuovo minimo mentre il prezzo non lo fa (bullish divergence). Il segnale piu affidabile dell'RSI.
3. **Failure swing:** RSI torna in area overbought ma non supera il precedente picco RSI, poi scende sotto quel trough — segnale forte.

**RSI e trend:** In un forte uptrend, RSI tende a restare tra 40 e 80 (overbought non e il livello assoluto 70; il livello va contestualizzato al trend). In un downtrend, RSI resta tra 20 e 60.

---

## Stochastics (K%D)

Sviluppati da George Lane. Misurano dove si trova il prezzo di chiusura rispetto al range high-low degli ultimi N periodi. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 246]

**Formula:**
- %K = [(Close − Lowest Low) / (Highest High − Lowest Low)] × 100
- %D = Media mobile a 3 periodi di %K (signal line)

**Interpretazione:**
- %K > 80 = overbought; %K < 20 = oversold.
- **Fast Stochastics:** %K grezzo + %D; molto sensibile.
- **Slow Stochastics:** il %D di Fast diventa il nuovo %K, poi si calcola un nuovo %D. Piu smussato, meno whipsaw.
- Segnale: %K incrocia %D verso il basso in area overbought = sell; %K incrocia %D verso l'alto in area oversold = buy.
- Divergenza stochastica/prezzo in aree estreme = warning di reversal.

---

## Williams %R

Sviluppato da Larry Williams. Praticamente identico agli Stochastics ma invertito. Range da -100 a 0: [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 249]
- Overbought tra 0 e -20.
- Oversold tra -80 e -100.
- Segnali analoghi agli Stochastics.

---

## Commodity Channel Index (CCI)

Sviluppato da Donald Lambert. Misura la deviazione del prezzo dalla sua media statistica (su N periodi). [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 237]

- CCI > +100 = overbought (il prezzo si discosta significativamente sopra la media → possibile inizio di un trend forte, non necessariamente reversal).
- CCI < -100 = oversold.
- Uso primario: identificare nuovi trend quando il CCI sale sopra +100 (bullish) o scende sotto -100 (bearish) per la prima volta dopo un periodo laterale.

---

## Contrary Opinion

Basato sul principio che la maggioranza del mercato ha quasi sempre torto nei punti di svolta estremi. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 404]

**Bullish Consensus (Hadady):** Percentuale di operatori del mercato futures classificati come bullish. Letture > 80-85% = consenso eccessivamente bullish → probabile top. Letture < 15-20% = consenso eccessivamente bearish → probabile bottom.

**Investors Intelligence (Chartcraft):** Percentuale di newsletter bullish vs bearish. Strumento classico per il sentiment del mercato azionario. Funziona meglio come indicatore contrarian nei punti estremi.

**Interpretazione pratica:** Murphy avverte che il sentiment estremo puo mantenersi a lungo in trend forti — il contrary opinion non fornisce timing preciso, ma contesto. Va usato in confluenza con altri segnali tecnici.

---

## Regole Generali per l'Uso degli Oscillatori

1. **Determinare prima il trend primario.** Mai usare gli oscillatori per tradare contro il trend dominante.
2. **In trend forte, le letture overbought/oversold possono mantenersi a lungo.** Overbought in uptrend = normale, non automaticamente sell.
3. **Le divergenze in aree estreme sono i segnali piu affidabili.** Attendere la divergenza; il semplice tocco del livello non e sufficiente.
4. **Usare la crossing della zero line come conferma** nella direzione del trend, non come segnale autonomo.
5. **Scegliere 1-2 oscillatori** con cui si e a proprio agio. L'over-analysis di troppi oscillatori porta a paralisi decisionale.

---

## Confronto con il Framework Elder

Elder usa questi stessi oscillatori nel suo Triple Screen System (vedi [[elder-triple-screen-impulse-system]]):

| Oscillatore | Murphy | Elder |
|---|---|---|
| MACD | Crossover signal line + histogram divergence | MACD-Histogram come secondo schermo (momentum del timeframe intermedio) |
| Force Index | Non trattato in Murphy | Terzo schermo Elder per precisare entry/exit |
| Stochastics | Crossing %K/%D in area overbought/oversold | Oscillatore alternativo al terzo schermo |
| RSI | Divergenza come segnale principale | Meno centrale nel framework Elder rispetto a MACD |

---

## Relazione con altri concetti wiki

- [[murphy-technical-analysis-financial-markets-1999]] — sorgente principale (Ch. 10)
- [[john-murphy]] — autore
- [[moving-averages-ta]] — MACD deriva da due EMA; la MA come riferimento di trend primario per contestualizzare gli oscillatori
- [[trend-trendlines-support-resistance]] — il trend primario determina come interpretare i segnali oscillatore
- [[chart-patterns-reversal-continuation]] — gli oscillatori come filtro per i candlestick patterns (Morris)
- [[volume-open-interest]] — volume come conferma dei segnali oscillatore
- [[elder-triple-screen-impulse-system]] — framework Elder che integra MACD-Histogram in modo sistematico
- [[elder-trading-psychology]] — il contrary opinion ha radici nella psicologia delle folle
- [[technical-analysis-trading-domain]] — separazione dominio
- [[behavioral-finance]] — il contrary opinion ha convergenze con i bias cognitivi del value investing (herding, overconfidence)
