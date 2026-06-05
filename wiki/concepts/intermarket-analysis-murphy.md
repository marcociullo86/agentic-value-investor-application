---
id: intermarket-analysis-murphy
type: concept
title: "Intermarket Analysis — Correlazioni tra Asset Class"
status: draft
created: 2026-06-05
updated: 2026-06-05
sources:
  - raw/2026-06-05-technical-analysis-financial-markets-1999.txt
tags: [technical-analysis, intermarket, bonds, commodities, stocks, dollar, sector-rotation, relative-strength, ta-domain]
domain: technical-analysis-trading
---

# Intermarket Analysis — Correlazioni tra Asset Class

**Dominio:** Analisi tecnica / trading attivo. Vedi [[technical-analysis-trading-domain]].

L'analisi intermarket e una branca della TA introdotta da Murphy con il suo libro omonimo (1991). Il principio fondamentale: i quattro mercati principali (commodities, bonds, stocks, dollar) sono interconnessi in modo sistematico tramite l'inflation/deflation cycle. Capire un mercato richiede guardare agli altri tre. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 16057]

"All four markets are linked—the dollar influences commodities, which influence bonds, which influence stocks. To fully comprehend what's happening in any one asset class, it's necessary to know what's happening in the other three."

---

## Le Quattro Relazioni Fondamentali

### 1. Dollar → Commodities (relazione inversa)

Un dollaro forte ha un effetto depressivo sui prezzi delle commodity. Un dollaro debole e inflazionario, spinge le commodity al rialzo. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 16009]

- **Gold come barometro:** Il gold e la commodity piu sensibile al dollaro e reagisce per primo. Il gold agisce come leading indicator per le altre commodity.
- Un bottom del dollaro tende a precedere un top nelle commodity (e viceversa).
- Esempi storici: il bottom del dollaro nel 1980 coincide con un major peak nelle commodity; il bottom del 1995 contribuisce al calo delle commodity un anno dopo.

### 2. Commodities → Bonds (relazione inversa)

Le commodity sono un proxy dell'inflazione. Commodity forti = pressione inflazionistica = bonds deboli (tassi in salita). [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 16000]

- Quando le commodity salgono fortemente, i bond traders scontano inflazione futura → bond prices scendono (rendimenti salgono).
- Il turn nei bond tende a precedere quello nelle commodity (i mercati finanziari anticipano l'economia reale).
- I bond bottom in primavera 1996 e 1997 coincidono con major peak delle commodity.

### 3. Bonds → Stocks (relazione diretta)

Bond forti (rendimenti in calo) = costo del denaro basso = positivo per le stocks. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 16000]

- Rising bond prices → positivo per stocks.
- Falling bond prices (rising rates) → headwinds per stocks.
- I bond tendono a fare il top/bottom prima degli stocks (leading indicator).
- **Exceptions:** In deflazione severa, stocks e bonds possono crollare insieme; in economia molto forte, stocks possono salire nonostante bond in calo.

### 4. Schema Completo della Catena

**Dollar ↑ → Commodities ↓ → Inflation concerns ↓ → Bonds ↑ → Stocks ↑** (scenario disinflazionario favorevole)

**Dollar ↓ → Commodities ↑ → Inflation concerns ↑ → Bonds ↓ → Stocks under pressure** (scenario inflazionario)

**Deflation scenario:** In deflazione severa, tutti i mercati si comportano in modo anomalo. I commodity e stock prices scendono insieme; i bond possono salire (flight to safety) ma poi anche crollare se la deflation deteriora la qualita del credito. Murphy nota che il risk di deflation (asiatica 1997) ha influenzato i mercati significativamente verso la fine degli anni '90.

---

## Impatto sui Settori Azionari

Le relazioni intermarket determinano quale settore azionario sovraperforma in un determinato contesto. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 16063]

| Scenario Intermarket | Settori Outperformer | Settori Underperformer |
|---|---|---|
| Bonds forti + Commodity deboli (low inflation) | Utilities, Financials, Consumer Staples, Real Estate | Gold mining, Energy, Cyclicals |
| Commodity forti + Bonds deboli (high inflation) | Gold, Energy, Materials, Industrials ciclici | Utilities, Financials, Consumer Staples |

**Meccanismi specifici:**
- **Utility stocks ↔ Treasury Bonds:** Correlazione molto stretta. Le utilities sono finanziate con debito — tassi bassi le avvantaggiano. Le utilities spesso fanno il turn prima dei bond (leading indicator del bond market).
- **Gold mining shares ↔ Gold:** Le azioni delle mining companies di oro anticipano spesso il movimento del gold fisico.
- **Oil ↔ Energy stocks / Airlines:** Petrolio in salita → energy stocks up, airline stocks down (fuel costs). Petrolio in calo → inverso.
- **Dollar ↔ Large cap / Small cap:** Dollaro forte penalizza i grandi multinazionali (export piu caro). Small cap domestici meno sensibili al dollar. Un dollaro forte tende a favorire Russell 2000 vs Dow/S&P. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 16119]

---

## Relative Strength Analysis

Strumento operativo principale dell'analisi intermarket: dividere il prezzo di un mercato per un altro (ratio chart). [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 16234]

**Formula:** RS Ratio = Prezzo mercato A / Prezzo mercato B

- RS ratio in salita = A outperforma B.
- RS ratio in calo = B outperforma A.

**Applicazioni pratiche:**
1. **CRB / T-Bond ratio:** Indica se le commodity outperformano i bond. Ratio in salita = inflation regime (favorire commodities e settori inflation-sensitive). Ratio in calo = disflation/deflation regime (favorire bonds e settori interest-rate sensitive).
2. **Settore / S&P 500 ratio:** Identifica i settori in outperformance relativa. Applicare trendlines e MA alla RS line stessa per identificare cambi di tendenza settoriale.
3. **Singolo titolo / settore ratio:** Identifica i leader del settore.

**Tactiche di sector rotation:** Ruotare i fondi verso i settori con RS ratio che stanno girando al rialzo; uscire dai settori con RS ratio in calo. Implementabile tramite options su indici settoriali o fondi comuni settoriali.

---

## Program Trading e Arbitraggio

L'arbitraggio informatico (program trading) ha creato un legame immediato tra il S&P 500 futures e le singole azioni del basket. I grandi istituzionali comprano o vendono il basket completo in pochi secondi quando il prezzo del futures si discosta dal "fair value" teorico. Questo crea movimenti sincronizzati tra futures e cash markets e amplifica la volatilita. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 16000]

---

## Stock Market Indicators come Context

Murphy dedica un capitolo (Ch. 18) agli indicatori di breadth del mercato azionario che forniscono contesto all'analisi intermarket: [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 715]

- **Advance-Decline (AD) Line:** Misura la salute del mercato generale. Divergenza AD/indice (indice fa nuovi massimi ma AD no) = warning che il rally e limitato a pochi titoli e non e sostenuto dalla breadth.
- **New Highs vs New Lows:** NH-NL Index cumulativo. Top di mercato tipicamente preceduti da calo dei NH e aumento dei NL settimane/mesi prima del top dell'indice.
- **Arms Index (TRIN):** (Advance Issues/Decline Issues) / (Advance Volume/Decline Volume). Smoothed con MA → indicatore di overbought/oversold di mercato.

---

## Convergenza con il Value Investing (punto di contatto limitato)

L'analisi intermarket ha applicazioni interessanti anche in un contesto di value investing, con cautela:

1. **Settori ciclici vs difensivi:** La comprensione del ciclo inflazionario (bonds/commodities) aiuta a valutare il contesto per i settori GICS. Un value investor che screening le utility (Graham: settori con pricing power) beneficia di sapere che bonds forti creano un contesto favorevole.

2. **Commodity come segnale macro:** Il trend delle commodity (CRB Index) come proxy dell'inflazione e rilevante per valutare la protezione dall'inflazione che Graham discute nel Capitolo 2 (vedi [[inflation-investing-graham]]).

3. **Sector relative strength:** Il concetto di sector rotation non e centrale nel value investing classico (Buffett: "Our favorite holding period is forever"), ma puo fornire contesto per la valutazione di titoli ciclici.

**Il confine:** L'analisi intermarket rimane nel dominio TA/trading quando usata per timing di breve termine. Il suo valore per il value investing e principalmente come contesto macro strutturale, non come segnale di trading.

---

## Relazione con altri concetti wiki

- [[murphy-technical-analysis-financial-markets-1999]] — sorgente principale (Ch. 17, 18)
- [[john-murphy]] — autore; ha fondato questa branca nel 1991
- [[technical-analysis-trading-domain]] — separazione dominio TA/value investing
- [[trend-trendlines-support-resistance]] — le correlazioni intermarket si analizzano con gli stessi tool tecnici (trendlines, RS analysis)
- [[moving-averages-ta]] — MA applicate alle RS ratio per identificare cambi di tendenza
- [[inflation-investing-graham]] — punto di contatto limitato: la relazione commodity/inflazione ha rilevanza anche per il value investing
- [[fmp-commodities]] — endpoint FMP per dati su commodity (contesto intermarket per analisi macro)
- [[fmp-market-performance]] — endpoint FMP per sector performance relativa
