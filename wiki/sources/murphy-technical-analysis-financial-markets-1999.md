---
id: murphy-technical-analysis-financial-markets-1999
type: source
title: "Technical Analysis of the Financial Markets (Murphy, 1999)"
status: draft
created: 2026-06-05
updated: 2026-06-05
sources:
  - raw/2026-06-05-technical-analysis-financial-markets-1999.txt
tags: [technical-analysis, trading, chart-patterns, dow-theory, moving-averages, oscillators, intermarket, ta-domain, murphy]
domain: technical-analysis-trading
---

# Technical Analysis of the Financial Markets — John J. Murphy (1999)

**Dominio:** Analisi tecnica / trading attivo
**Avviso di contesto:** Questo testo appartiene al dominio `technical-analysis-trading`, distinto dal value investing Graham/Buffett che e il cuore dell'applicazione. Vedi [[technical-analysis-trading-domain]].

**Autore:** John J. Murphy (vedi [[john-murphy]])
**Edizione:** 1999, New York Institute of Finance / Prentice Hall (ISBN 0-7352-0066-1)
**Pagine originali:** 585 (revisione ed espansione di "Technical Analysis of the Futures Markets", 1986)
**Raw estratto:** `raw/2026-06-05-technical-analysis-financial-markets-1999.txt`
**Metodo estrazione:** OCR Tesseract 5.4.0, PyMuPDF render zoom=3.0
**Note OCR:** Pagine di copertina/dorso (Page 1, 2, 30) contengono rumore OCR da testo verticale del dorso — ignorare. Corpo dei capitoli pulito e accurato.

---

## Struttura del volume

Il libro e organizzato in 20 capitoli piu appendici e glossario.

### Capitolo 1 — Philosophy of Technical Analysis

Definisce la disciplina e i suoi tre assiomi fondamentali: [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 31]

1. **Market action discounts everything** — il prezzo riflette gia tutte le informazioni note (fondamentali, politiche, psicologiche). Lo studio del prezzo e quindi sufficiente.
2. **Prices move in trends** — il trend e il presupposto centrale; ogni tool del chartista serve a identificarlo e seguirlo. Corollario: "un trend in moto tende a continuare fino a che non si inverte" (adattamento della prima legge di Newton).
3. **History repeats itself** — i pattern di prezzo sono espressione della psicologia umana, che non cambia. I pattern funzionano perche rispecchiano comportamenti collettivi ripetibili nel tempo.

**Definizione canonica di AT:** "Technical analysis is the study of market action, primarily through the use of charts, for the purpose of forecasting future price trends." I tre input principali sono: prezzo, volume, open interest (solo futures/options). [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 31]

**AT vs Analisi Fondamentale:** Il fondamentalista studia le cause del movimento; il tecnico studia l'effetto. La AT include implicitamente il fondamentale (il prezzo incorpora i fondamentali). Il contrario non e vero. Timing e quasi puramente tecnico in natura. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 35]

**Critica alla Random Walk Theory:** La RWT (accademica) afferma che i cambiamenti di prezzo sono serialmente indipendenti e i trend non esistono. Murphy risponde che l'esperienza empirica contraddice questo assunto — basta guardare qualsiasi chart book per vedere trend evidenti. I trend-following systems profittevoli nel mondo reale confutano la teoria. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 49]

**Self-fulfilling prophecy:** Obiezione classica: se tutti vedono gli stessi pattern, li creano artificialmente. Murphy risponde che (a) i chart patterns sono soggettivi, quindi tutti non vedono la stessa cosa; (b) anche se lo facessero, il meccanismo sarebbe autocorrettivo; (c) i grandi bull/bear markets richiedono forze reali di domanda/offerta, non solo chartisti. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 46]

---

### Capitolo 2 — Dow Theory

Fondamenta storiche dell'analisi tecnica moderna. Charles Dow e il suo successore Hamilton hanno codificato 6 principi fondamentali. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 53]

I 6 principi (tenets) della Dow Theory:

1. **The averages discount everything** — le medie azionarie incorporano tutto il knowable (incluse catastrofi naturali).
2. **The market has three trends** — primary (> 1 anno, paragonabile alla marea), secondary/intermediate (3 settimane–3 mesi, le onde), minor/near-term (< 3 settimane, le increspature). Correzioni secondarie retraggono tipicamente 1/3–2/3 del movimento precedente, piu spesso il 50%.
3. **Major trends have three phases** — accumulation (smart money), public participation (trend-followers entrano), distribution (smart money vende mentre il pubblico e euforico).
4. **The averages must confirm each other** — nessun segnale bull/bear valido a meno che sia confermato sia da Dow Industrials che da Dow Transports.
5. **Volume must confirm the trend** — il volume deve espandersi nella direzione del trend primario e contrarsi nelle correzioni.
6. **A trend is assumed to be in effect until it gives definite reversal signals** — il principio fondamentale del trend-following: stare con il trend fino a prova contraria.

**Failure Swing vs Nonfailure Swing:** Due pattern di inversione identificati da Dow per segnali sell. Il Failure Swing (picco C < picco A) e piu debole e inequivocabile. Il Nonfailure Swing (C > A) richiede conferma ulteriore. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 59]

**Critiche alla Dow Theory:** In media manca il 20-25% di un movimento prima di generare il segnale. Murphy ricorda che Dow non voleva anticipare i trend, ma catturare la parte centrale delle grandi mosse. Dal 1920 al 1975, la Dow Theory ha catturato il 68% dei movimenti negli Industrial e Transportation Averages. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 61]

---

### Capitolo 3 — Chart Construction

Tipi di grafici disponibili: [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 65]

- **Bar chart** (piu usato): ogni barra = high/low del periodo; ticchetto a destra = close; ticchetto a sinistra = open.
- **Line chart**: solo prezzi di chiusura collegati; molti chartisti lo preferiscono perche la chiusura e il prezzo piu critico.
- **Point & Figure chart**: formato compresso, colonne X (rialzo) e O (ribasso); segnali buy/sell piu precisi.
- **Candlestick chart**: versione giapponese del bar chart; il corpo reale (real body) misura distanza open-close (bianco = bullish close > open; nero = bearish close < open); shadow (ombra) = high-low range.

**Scala aritmetica vs logaritmica:** La scala logaritmica mostra uguali distanze per variazioni percentuali uguali (utile per analisi di lungo periodo). La scala aritmetica mostra uguali distanze per variazioni assolute. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 68]

**Volume:** Registrato con barre verticali sotto il grafico prezzi; misura il totale delle posizioni scambiate nel giorno (azioni) o dei contratti trattati (futures).

**Open Interest (futures):** Numero totale di contratti futures aperti e in vita a fine giornata (solo lato long O short, non entrambi). Segnalato con linea solida sopra le barre di volume. Riportato con un giorno di ritardo nei futures. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 71]

**Weekly e Monthly charts:** Comprimono il price action per analisi di lungo periodo (weekly fino a 5 anni, monthly fino a 20 anni). Stessa costruzione del daily chart, ognibarra = settimana/mese.

---

### Capitolo 4 — Basic Concepts of Trend

**Definizione di trend:** Direzione della serie di picchi e minimi. Uptrend = serie di picchi e minimi crescenti. Downtrend = serie di picchi e minimi decrescenti. Sideways trend = picchi e minimi orizzontali (spesso detto "trendless"). [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 77]

**Tre direzioni:** Up, down, sideways. Il trading range orizzontale occupa almeno 1/3 del tempo per stima conservativa; i sistemi trend-following non funzionano in queste fasi.

**Tre classificazioni:** Primary/major (> 6 mesi), intermediate/secondary (3 settimane–mesi), near-term/minor (< 2-3 settimane). Ogni trend e parte del successivo e composto da trend piu piccoli.

**Support e Resistance:** [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 82]
- Support = livello sotto il mercato dove il buying interest supera la selling pressure; coincide tipicamente con un minimo di reazione precedente.
- Resistance = livello sopra il mercato dove la selling pressure supera il buying interest; coincide tipicamente con un picco precedente.
- **Role reversal:** Un livello di support violato diventa resistance e viceversa. Principio fondamentale del chart analysis.

**Trendlines:** Linea retta che connette minimi crescenti in un uptrend (o massimi decrescenti in un downtrend). Piu contatti con la linea = piu significativa. La violazione della trendline primaria e il primo segnale di cambio di trend.

**Channel line (return line):** Linea parallela alla trendline disegnata sul lato opposto; definisce il "canale" entro cui si muovono i prezzi.

**Percentage retracements:** Le correzioni tendono a retrarre frazioni prevedibili del move precedente: 33% (minimo), 50% (piu comune), 66% (massimo retracement tollerabile in un trend). Livelli Fibonacci (38.2%, 50%, 61.8%) largamente usati come equivalenti. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 207]

**Price Gaps:** Interruzioni nella serie continua dei prezzi. Tipi principali:
- Common gap (area gap): riempito rapidamente, poco significativo.
- Breakaway gap: segnala inizio di un nuovo trend su alto volume.
- Runaway gap (measuring gap): nel mezzo di un forte trend; usato per proiettare il target.
- Exhaustion gap: vicino alla fine di un trend; segnala esaurimento. Spesso seguito da Island Reversal (gap up + trading range + gap down = top significativo). [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 124]

---

### Capitolo 5 — Major Reversal Patterns

**Premesse comuni a tutti i reversal patterns:** [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 127]
1. Occorre un trend precedente da invertire.
2. Il primo segnale e spesso la rottura di una trendline importante.
3. Piu grande il pattern (altezza × larghezza), maggiore il potenziale move.
4. I top patterns sono piu volatili e brevi dei bottom patterns.
5. I bottom patterns hanno range di prezzo piu piccoli ma richiedono piu tempo.
6. Il volume e piu importante sul lato rialzista.

**Head and Shoulders Top:** Pattern piu affidabile. Tre picchi: spalla sinistra (A, alto volume), testa (C, picco assoluto, volume inferiore), spalla destra (E, volume ancora piu basso). Neckline = trendline orizzontale (leggermente inclinata) tra i due minimi (B, D). Il pattern si completa con la chiusura sotto la neckline. Target price = distanza testa-neckline proiettata verso il basso dal punto di rottura. Return move = rimbalzo fino alla neckline rotta (ora resistance) spesso segue la rottura. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 132]

**Inverse Head and Shoulders:** Pattern speculare al bottom; identica struttura ma invertita. Volume piu critico sul lato rialzista al breakout.

**Complex Head and Shoulders:** Con due teste o due paia di spalle.

**Double Top / Double Bottom:** Due picchi (o due minimi) allo stesso livello approssimativo con correzione intermedia. Completato con chiusura oltre il minimo (o massimo) intermedio. Spesso confuso con il semplice retest di resistance/support — attendere conferma decisiva.

**Triple Top / Triple Bottom:** Tre tentativi dello stesso livello; meno comune ma piu affidabile del double.

**Saucers (Rounding tops/bottoms):** Inversione lenta e graduale; tipica in mercati lenti con basso volume durante la transizione.

**Spikes (V tops/bottoms):** Inversione violenta e rapida; difficilissima da tradare; segnalata da reversal day con volume estremo.

---

### Capitolo 6 — Continuation Patterns

Pattern che indicano una pausa nel trend esistente (consolidazione) prima della ripresa. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 261]

**Triangoli:**
- *Symmetrical Triangle*: convergenza di upper trendline (discendente) e lower trendline (ascendente); coil prima del breakout nella direzione del trend primario; misura = altezza del triangolo alla base proiettata dal breakout.
- *Ascending Triangle*: upper trendline orizzontale + lower trendline crescente; bullish bias; breakout tipicamente verso l'alto.
- *Descending Triangle*: upper trendline decrescente + lower trendline orizzontale; bearish bias; breakout tipicamente verso il basso.
- *Broadening Formation*: pattern inverso del triangolo simmetrico (espansione verso l'esterno); indica mercato erratico, spesso vicino a top importanti.

**Flags e Pennants:** Pattern di breve durata (1-3 settimane) a meta di un move veloce. Il pattern e caratterizzato da un palo (flag pole, il move precedente) e da un corpo (flag = piccolo canale in controtendenza; pennant = piccolo triangolo simmetrico). Breakout nella direzione del trend precedente. Misura = lunghezza del palo. "Flags and pennants mark the half-way point of the move." [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 274]

**Wedge Formation:** Simile a un triangolo ma entrambe le trendlines inclinate nella stessa direzione (contro il trend). Bearish rising wedge in un uptrend; bullish falling wedge in un downtrend.

**Rectangle Formation:** Consolidazione orizzontale (trading range) tra support e resistance definiti; continuazione nella direzione del trend precedente al breakout.

**Measured Move (AB=CD):** Il secondo rally (CD) retracia la stessa distanza e percentuale del primo rally (AB) dopo la correzione intermedia (BC).

---

### Capitolo 7 — Volume and Open Interest

Volume = indicatore secondario ma importante di conferma. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 193]

**Regole generali del volume:**
- Volume dovrebbe espandersi nella direzione del trend primario.
- Volume precede il prezzo: la perdita di pressione si manifesta prima nel volume che nel prezzo.
- Volume elevato su breakout da pattern = segnale valido. Volume basso su breakout = sospettoso.
- Al bottom, il pickup di volume e assolutamente essenziale per confermare il reversal.
- Ai top, il volume elevato e utile ma non critico.

**On Balance Volume (OBV):** Sviluppato da Joseph Granville (1963). Linea cumulativa: giorno up = + volume totale; giorno down = -volume totale. La direzione della linea OBV e l'informazione rilevante, non il valore assoluto. Divergenza OBV/prezzo = warning di reversal. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 193]

**Regole dell'open interest (futures):**
1. Open interest crescente + prezzi in salita = bullish (nuovo denaro entra, nuovi long aggressivi).
2. Open interest decrescente + prezzi in salita = bearish (rally da short covering, non da nuovo interesse).
3. Open interest crescente + prezzi in calo = bearish (nuovo denaro entra, nuovi short aggressivi).
4. Open interest decrescente + prezzi in calo = bullish (calo da liquidazione forzata dei long, stanchezza del ribasso). [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 198]

**Commitments of Traders (COT) Report:** Report settimanale CFTC che suddivide open interest in commerciali (hedgers), grandi speculatori, piccoli trader. "Watch the commercials": i commerciali sono le "smart money" — quando sono net long in extremis, probabile bottom. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 302]

---

### Capitolo 8 — Long Term Charts

Prospettiva di lungo periodo tramite chart settimanali e mensili. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 316]

- I chart di lungo periodo disprovano la Random Walk Theory mostrando trend pluridecennali.
- Ogni analisi tecnica dovrebbe iniziare dal chart mensile/settimanale e poi drill-down al giornaliero.
- I chart di lungo periodo non sono progettati per il trading ma per la comprensione del trend primario.
- Scala logaritmica preferita per chart di lungo periodo (misura variazioni percentuali).
- **Continuation charts (futures):** Contratti concatenati per ottenere storia continua; il "Perpetual Contract" usa media ponderata dei contratti attivi.

---

### Capitolo 9 — Moving Averages

Le medie mobili sono lo strumento trend-following per eccellenza. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 341]

**Tipi principali:**
- *Simple Moving Average (SMA)*: media aritmetica semplice dei prezzi di chiusura degli ultimi N periodi.
- *Exponential Moving Average (EMA)*: peso maggiore ai prezzi recenti tramite fattore di smorzamento; reagisce piu velocemente.
- *Weighted Moving Average*: pesi crescenti verso i periodi recenti.

**Uso con due medie (dual crossover):**
- La media piu corta oscilla intorno alla piu lunga.
- Crossover della corta sopra la lunga = buy signal; crossover sotto = sell signal.
- Combinazioni comuni futures: 4/9, 9/18, 5/20, 10/40 giorni. Stocks: 50 giorni (o 10 settimane), 200 giorni (o 30-40 settimane). [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 220]

**Moving Average Envelopes:** Bande a percentuale fissa sopra/sotto la MA (es. +/- 3%); utili per identificare estremi di mercato.

**Bollinger Bands:** Bande a +/- 2 deviazioni standard intorno a una SMA di 20 periodi; la larghezza delle bande misura la volatilita. Prezzi che toccano la banda superiore = overbought relativo; banda inferiore = oversold relativo. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 347]

**Weekly Rule (4-week rule):** Sistema semplice e robusto: buy quando prezzi chiudono sopra il massimo delle ultime 4 settimane; sell quando chiudono sotto il minimo delle ultime 4 settimane. Basato sul ciclo mensile dominante. Funziona meglio in mercati in trend. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 248]

**Adaptive Moving Average (AMA):** Sviluppato da Perry Kaufman; si adatta automaticamente alla volatilita (efficiency ratio). Si muove piu lentamente in trading range, piu velocemente in trend.

**MA e cicli:** Le MA piu popolari (5, 10, 20, 40 giorni) derivano dal ciclo mensile (20 trading days) e dal principio degli armonici (ogni ciclo e il doppio o la meta del successivo).

---

### Capitolo 10 — Oscillators and Contrary Opinion

Gli oscillatori sono strumenti secondari, subordinati all'analisi del trend primario. Sono piu utili nei trading range e verso la fine dei movimenti di trend. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 257]

**Tre usi principali degli oscillatori:**
1. Lettura in area estrema (overbought/oversold) = warning di esaurimento.
2. Divergenza oscillatore/prezzo in area estrema = importante warning di reversal.
3. Crossing della zero line (o midpoint) = segnale di trading nella direzione del trend primario.

**Momentum:** Differenza tra prezzo corrente e prezzo N giorni fa. Formula: M = V − V*. Il momentum misura la velocita del cambiamento di prezzo (acceleration/deceleration). La linea di momentum precede il prezzo: si appiattisce prima che il prezzo si inverta. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 398]

**Rate of Change (ROC):** Ratio invece di differenza: ROC = 100 × (V/Vx). Midpoint = 100 invece di 0. Interpretazione identica al momentum.

**MACD (Moving Average Convergence/Divergence):** Oscillatore derivato dalla differenza tra due EMA (tipicamente 12 e 26 periodi). Signal line = EMA a 9 periodi del MACD. MACD Histogram = MACD - Signal line. Crossing signal line = buy/sell. Histogram divergence = importante early warning. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 399]

**RSI (Relative Strength Index):** Sviluppato da J. Welles Wilder Jr. (1978). Oscillatore normalizzato 0-100. Overbought sopra 70; oversold sotto 30. Divergenza RSI/prezzo in area estrema e il segnale piu affidabile. Periodi comuni: 9 o 14 giorni.

**Stochastics (K%D):** Sviluppato da George Lane. Misura dove si trova il prezzo di chiusura nel range recente high-low. %K = (Close - Lowest Low) / (Highest High - Lowest Low) × 100. %D = SMA di %K. Overbought > 80; oversold < 20. Fast Stochastics = %K grezzo; Slow Stochastics = smussato.

**Williams %R:** Simile agli Stochastics ma invertito (range da -100 a 0); overbought tra 0 e -20, oversold tra -80 e -100.

**Commodity Channel Index (CCI):** Misura la deviazione del prezzo dalla sua media statistica. Letture > +100 = overbought (uscita da trading range a upside); < -100 = oversold.

**Contrary Opinion (Bullish Consensus):** Quando l'opinion pubblica bullish supera l'80-85%, si avvicina un top; sotto il 20-25%, si avvicina un bottom. Misura l'eccessivo consenso come contrarian signal. Investors Intelligence Numbers: percentuale di newsletter bullish/bearish. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 404]

---

### Capitoli 11-12 — Point and Figure Charting / Japanese Candlesticks

**Point and Figure:** [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 535]
- Esclude il tempo; registra solo movimenti di prezzo significativi.
- Colonne X (rialzo) e O (ribasso); box size + reversal amount definiscono la sensibilita.
- 3-box reversal: il piu comune; richiede un movimento di 3 box nella direzione opposta per cambiare colonna.
- Trendlines a 45 gradi; misura orizzontale (count) per proiezione target.
- Vantaggi: filtro del rumore, segnali buy/sell precisi, nessuna distorsione temporale.

**Japanese Candlesticks (Capitolo 12, autore Greg Morris):** [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 12000]
- I pattern di candela sono rappresentazioni psicologiche del comportamento dei trader.
- Pattern di inversione: Dark Cloud Cover, Piercing Line, Evening Star, Morning Star, Engulfing Pattern, Harami, Hammer, Shooting Star.
- Pattern di continuazione: Rising Three Methods, Falling Three Methods.
- Il prerequisito fondamentale: il pattern di reversal richiede un trend da invertire. Non puo esistere un bullish reversal in un uptrend.
- Filtered Candle Patterns (Morris 1991): usare Stochastics %D o altro oscillatore come filtro — considerare solo i pattern quando %D e in area presignal (> 80 overbought o < 20 oversold).

---

### Capitolo 13 — Elliott Wave Theory

**Background:** R. N. Elliott (1930s) elaboro un sistema di analisi basato su sequenze ricorrenti di onde nel prezzo. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 563]

**Principio base:** I mercati si muovono in sequenze di 5 onde nella direzione del trend primario (onde 1-2-3-4-5) seguite da 3 onde correttive (A-B-C). Onde 1, 3, 5 = impulsive (nella direzione del trend); onde 2, 4 = correttive.

**Tre fasi del bull market (corrispondono a Dow Theory):** Accumulation → Public Participation → Distribution.

**Fibonacci come base:** I numeri della sequenza di Fibonacci (1, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89...) e i ratio derivati (38.2%, 50%, 61.8%, 161.8%) sono alla base dei conteggi di Elliott. Fibonacci ratios come retracements: 38.2%, 50%, 61.8% del wave precedente.

**Rule of Alternation:** Le onde 2 e 4 tendono ad alternarsi — se la 2 e semplice (sharp), la 4 sara complessa (flat) e viceversa.

---

### Capitolo 14 — Time Cycles

**Cicli dominanti nei mercati:** [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 592]
- Principio degli armonici: ogni ciclo e il doppio o la meta del successivo.
- Cicli comuni: 20 giorni (mensile, il piu affidabile), 40 giorni, 10 settimane, 20 settimane, 40 settimane (annuale).
- Seasonal cycles: pattern ricorrenti su base stagionale per molte commodity (es. grano, petrolio).
- Stock market cycles: January Barometer (performance gennaio predice l'anno), Presidential Cycle (quarto anno presidenziale tendenzialmente bullish).

**Translation:** Left translation (ciclo punta prima della meta = bearish bias); Right translation (picco dopo la meta = bullish bias).

**Come i cicli spiegano i tool tecnici:** Il ciclo dominante determina il miglior periodo per la MA (es. ciclo 20-day → MA 10-day per essere in anticipo di mezza lunghezza del ciclo).

---

### Capitolo 15 — Computers and Trading Systems

**Wilder's Parabolic SAR:** Sistema stop-and-reverse sempre nel mercato; i punti SAR si spostano progressivamente nella direzione del trend accelerando con esso (forma parabolica). Funziona bene in trend; whipsaw in trading range. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 14586]

**Directional Movement Index (DMI) e ADX:** ADX (Average Directional Index) misura l'intensita del trend su scala 0-100. ADX in salita = mercato in trend (piu alto, piu forte il trend). ADX in calo = mercato non-trending. +DI > -DI = bias rialzista. Uso: usare trend-following systems quando ADX e alto e sale; usare oscillatori quando ADX e basso e scende. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 14684]

**5-Step Plan per building a trading system:**
1. Start with a concept (idea).
2. Turn the idea into objective rules.
3. Visually check on the charts.
4. Formally test with a computer.
5. Evaluate results (out-of-sample testing essenziale).

**Pro e contro dei sistemi meccanici:** Eliminano l'emotivita; catturano i trend importanti. Contro: perdono denaro per ~70% del tempo in mercati non-trending (Wilder stima: solo il 30% del tempo i mercati trendano).

---

### Capitolo 16 — Money Management and Trading Tactics

**I tre elementi del trading di successo:** Direction analysis, money management, trading tactics. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 641]

**Money management principles:**
- Reward-to-risk ratio: mai prendere una trade con rapporto inferiore a 3:1.
- Position sizing: determinare quante unita comprare/vendere in base al capitale disponibile e al rischio per trade.
- Trending vs Trading units: separare posizioni core di lungo termine da posizioni tattiche di breve.
- Non aumentare le posizioni dopo perdite (evita di inseguire loss); considerare riduzione del size in fasi di avversita.

**Trading tactics:**
- Combinare analisi fondamentale con timing tecnico.
- Ordini: market order (immediato), limit order (prezzo specificato), stop order (protezione o entrata su breakout).
- Intraday pivot points: livelli calcolati da high/low/close del giorno precedente come supporti/resistenze intradailyGiornata.
- Dal daily al intraday: usare i daily chart per la direzione; passare agli intraday per il timing dell'entry.

**Application to stocks:** Asset allocation tra stocks, bonds, cash tramite analisi del trend relativo dei tre mercati; sector rotation via relative strength analysis.

---

### Capitolo 17 — Intermarket Analysis (The Link Between Stocks and Futures)

**Quattro mercati fondamentalmente interconnessi:** Commodities (CRB), Bonds (Treasury), Stocks (S&P 500), U.S. Dollar. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 16057]

**Relazioni fondamentali:**
1. **Dollar → Commodities (inversa):** Dollaro forte = commodities deboli (non inflazionario). Dollaro debole = commodities forti. Il gold e il mercato commodity piu sensibile al dollaro e agisce come leading indicator per le altre commodity.
2. **Commodities → Bonds (inversa):** Commodity forti = pressione inflazionistica = bond deboli. Il turn nei bond tende a precedere quello nelle commodity.
3. **Bonds → Stocks (diretta):** Bond forti (tassi in calo) = stocks forti. Bond deboli = headwinds per gli stocks.
4. **Schema completo:** Dollar → Commodities → Bonds → Stocks. Tutto collegato.

**Implicazioni per i settori azionari:**
- Bond forti + commodity deboli → outperformance di utilities, financials, consumer staples.
- Commodity forti + bond deboli → outperformance di gold, energy, cyclical stocks.

**Relative Strength Analysis:** Ratio tra due mercati (es. CRB/T-Bond); RS line in salita = il numeratore outperforma. Tool per identificare rotazione settoriale. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 16234]

**Dollar e large cap vs small cap:** Dollaro forte penalizza i grandi multinazionali (export piu caro); favorisce le small cap domestic. Dollaro debole favorisce i grandi multinazionali.

**Top-Down Market Approach:** Analisi top-down: macro intermarket → settori/industry groups → singolo titolo. Usare relative strength per identificare quali settori sovraperformano.

---

### Capitolo 18 — Stock Market Indicators

**Market Breadth:** [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 715]
- **Advance-Decline Line (AD Line):** Differenza cumulativa tra titoli in salita e in calo; divergenza AD/indice = warning che il mercato sta perdendo ampiezza.
- **McClellan Oscillator:** Oscillatore della AD line; 19-day EMA − 39-day EMA della differenza Advance-Decline. Overbought > +100; oversold < -100.
- **McClellan Summation Index:** Versione cumulativa del McClellan Oscillator; utile per identificare trend di lungo periodo della breadth.
- **New Highs vs New Lows:** NH-NL Index = differenza cumulativa tra nuovi massimi a 52 settimane e nuovi minimi. Strumento potente per salute del mercato.
- **Arms Index (TRIN):** (Advances/Declines) / (Advancing Volume/Declining Volume). TRIN < 1 = bullish; > 1 = bearish. Moving average del TRIN smoothed = miglior indicatore.
- **Equivolume charting:** Incorpora il volume nella larghezza delle barre di prezzo; box piu largo = giornata con volume piu alto.

---

### Capitoli 19-20 + Appendici — Checklist, Advanced Indicators, Market Profile, Building a System

**Technical Checklist (Capitolo 19):** [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 751]
- Determinare il trend primario (monthly chart).
- Determinare il trend intermedio (weekly chart).
- Identificare il trend di breve (daily chart).
- Identificare i livelli di support e resistance.
- Identificare trendlines e channels.
- Verificare i pattern di prezzo.
- Controllare le MA (50 e 200 giorni per stocks).
- Controllare gli oscillatori (overbought/oversold, divergenze).
- Verificare volume e open interest.
- Applicare l'analisi intermarket (dove si trovano bonds e commodities?).

**Market Profile (Appendice B):** Sistema sviluppato da J. Peter Steidlmayer; organizza il price action per valore (distribuzione statistica a campana) piuttosto che per tempo. Identifica Value Area (70% delle transazioni), POC (Point of Control = prezzo piu tradato), Initial Balance (prime ore di trading). [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 783]

**Demand Index e Herrick Payoff Index (Appendice A):** Indicatori avanzati che combinano prezzo e volume/open interest in modo non-lineare.

---

## Temi cross-capitolo

### Convergenza con Elder
Murphy e Elder (vedi [[elder-new-trading-living-2014]]) convergono su:
- Il trend e il concetto centrale (Elder: "Trade in the direction of the Triple Screen trend").
- Gli oscillatori come strumenti secondari da usare in confluenza con il trend (Elder: usare MACD-Histogram sul timeframe intermedio per conferma).
- La psicologia delle folle come fondamento dei pattern di prezzo (Elder: psicologia del trading come requisito critico).
- L'importanza del volume per confermare i movimenti di prezzo.

### Divergenze metodologiche Elder/Murphy
- Murphy e piu "classico" e didattico: copre l'intera AT da Dow Theory a Elliott Wave.
- Elder e piu operativo: si concentra su Triple Screen System come framework pratico di trading.
- Murphy dedica un capitolo completo all'analisi intermarket (Ch. 17) — assente in Elder.
- Elder dedica molto spazio alla psicologia del trader individuale — Murphy e piu market-centric.

### Separazione dal value investing
Vedi [[technical-analysis-trading-domain]] per la mappa completa di separazione. Murphy stesso riconosce il ruolo complementare: "Fundamental analysis can help you find a stock that may be worth buying. Use technical analysis to time your entries and exits." Il value investing usa la valutazione fondamentale come filtro primario; l'AT puo avere ruolo ausiliario nel timing dell'entry.

---

## Relazione con altri concetti wiki

- [[john-murphy]] — entita autore
- [[technical-analysis-trading-domain]] — mappa di separazione domini TA/value investing
- [[dow-theory]] — concept capitolo 2
- [[trend-trendlines-support-resistance]] — concept capitoli 4 e parte del 5
- [[chart-patterns-reversal-continuation]] — concept capitoli 5 e 6
- [[moving-averages-ta]] — concept capitolo 9
- [[oscillators-momentum-rsi]] — concept capitolo 10
- [[volume-open-interest]] — concept capitolo 7
- [[intermarket-analysis-murphy]] — concept capitolo 17
- [[elder-new-trading-living-2014]] — sorgente TA parallela (Elder 2014)
- [[elder-triple-screen-impulse-system]] — concept Elder: convergenze con Murphy Ch. 10/9
- [[elder-risk-management-2pct-6pct]] — concept Elder: convergenze con Murphy Ch. 16 money management
- [[investment-vs-speculation]] — confine Graham tra speculazione (AT pura) e investimento

---

## Storie collegate

<!-- Sezione proprieta PM — non modificare -->
