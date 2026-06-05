---
id: ta-entry-timing-stock-detail
type: synthesis
title: "Timing dell'Entry su un Titolo Promosso dal Verdetto VI — Applicazione alla Pagina Dettaglio"
status: draft
created: 2026-06-05
updated: 2026-06-05
sources:
  - raw/2026-06-05-alexander-elder-new-trading-living-2014.txt
  - raw/2026-06-05-technical-analysis-financial-markets-1999.txt
tags: [technical-analysis, trading, timing, entry, triple-screen, rsi, sma200, ta-domain]
domain: technical-analysis-trading
---

# Timing dell'Entry su un Titolo Promosso dal Verdetto VI

**Dominio:** Analisi tecnica / trading — layer advisory sul dettaglio titolo. Vedi [[technical-analysis-trading-domain]] per il confine tra TA e value investing. Vedi [[ta-vs-vi-decision-layer]] per la relazione decisionale tra i due layer.

Il verdetto value investing risponde alla domanda **"Cosa comprare?"** — il layer TA risponde alla domanda **"Quando comprare?"** Le due domande sono ortogonali e devono essere tenute separate. Questa sintesi documenta come usare i concetti TA di Elder e Murphy per rispondere alla seconda, a partire dai segnali advisory già presenti sulla pagina `/analysis` dell'applicazione.

---

## Il punto di partenza: i due badge advisory esistenti

La pagina `/analysis` espone già due segnali tecnici nella "Context Flags Section (advisory)", distinta dal `TrafficLightPanel` del verdetto fondamentale:

| Badge | Indicatore | Valori possibili | File |
|---|---|---|---|
| `MrMarketSentimentBadge` | RSI 14-day | Oversold / Neutral / Overbought | `src/frontend/components/analysis/MrMarketSentimentBadge.tsx` |
| `LongTermTrendBadge` | SMA200 trend | Below / Near / Above trend | `src/frontend/components/analysis/LongTermTrendBadge.tsx` |

Questi due badge costituiscono una **prima approssimazione di timing** — non un sistema completo. Interpretarli correttamente richiede il framework TA documentato qui.

---

## Il framework: Triple Screen di Elder come struttura mentale

[[elder-triple-screen-impulse-system]] formalizza l'approccio multi-timeframe che ogni investitore dovrebbe applicare prima di entrare in un titolo. Il sistema originale è pensato per trader attivi, ma la sua struttura si trasferisce bene all'investitore value che vuole ottimizzare il timing di un'entry su un titolo già valutato positivamente dal verdetto fondamentale.

La logica del Triple Screen in tre frasi: [^src: raw/2026-06-05-alexander-elder-new-trading-living-2014.txt §39. Triple Screen Trading System]

> "Triple Screen demands that you examine the long-term chart first. It allows you to trade only in the direction of the tide."

**Tre schermate, tre domande:**

| Schermata | Timeframe | Strumento | Domanda |
|---|---|---|---|
| Screen 1 — Tide | Lungo (settimanale o mensile) | Trend primario (MA200, EMA weekly) | Il trend di lungo periodo è favorevole? |
| Screen 2 — Wave | Intermedio (giornaliero) | Oscillatore (RSI, Force Index) | C'è un pullback che offre un entry a prezzi migliori? |
| Screen 3 — Entry | Breve | Livello preciso | Dove piazzare l'ordine e lo stop? |

Il valore della struttura è **sequenziale**: Screen 2 si legge solo se Screen 1 è favorevole; Screen 3 si esegue solo se Screen 2 indica il momento giusto. Ogni schermata funziona come "censore" di quella successiva.

---

## Screen 1 — Trend di lungo: SMA200 (LongTermTrendBadge)

Il `LongTermTrendBadge` espone direttamente l'informazione di Screen 1: la relazione tra il prezzo corrente e la SMA200.

Murphy identifica la SMA200 (equivalente alla MA a 40 settimane) come il **benchmark di riferimento per il trend primario** delle azioni: [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 341]

- **Above trend** (prezzo > SMA200): il titolo è in uptrend primario. Screen 1 favorevole per i long. Non è il momento di cercare opportunità short o di "aspettare un calo ulteriore" che potrebbe non arrivare.
- **Near trend** (prezzo ≈ SMA200): zona di decisione. Il trend potrebbe stare cambiando direzione. Richiede conferma da Screen 2 prima di agire.
- **Below trend** (prezzo < SMA200): il titolo è in downtrend primario. Screen 1 sfavorevole per i long. Acquistare qui significa andare contro la marea — tecnicamente ammissibile solo con segnali molto forti di Screen 2 e con uno stop definito (vedi [[ta-stop-placement-position-sizing]]).

**Regola pratica per l'investitore value che usa l'app:**

Acquistare un titolo con verdetto VI positivo ma `LongTermTrendBadge = Below trend` significa accettare di navigare contro la marea. Non è vietato — il verdetto fondamentale dà il diritto di entrare — ma impone una posizione più piccola e uno stop ben definito. Il caso COPART illustra esattamente questo rischio (vedi [[ta-vs-vi-decision-layer]]).

---

## Screen 2 — Pullback nel trend: RSI (MrMarketSentimentBadge)

Il `MrMarketSentimentBadge` espone l'RSI 14-day, che è lo strumento principale di Screen 2 in questo contesto: identifica i momenti in cui il prezzo si è temporaneamente allontanato dal trend in modo statisticamente favorevole all'entry.

Murphy descrive le tre modalità d'uso dell'RSI: [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 239]

1. **Crossover delle aree estreme (70/30):** RSI > 70 = overbought; RSI < 30 = oversold.
2. **Divergenza RSI/prezzo:** segnale più affidabile di imminente reversal.
3. **Failure swing:** RSI non supera il precedente massimo in area overbought, poi cade.

Il `MrMarketSentimentBadge` mappa queste aree nelle etichette `Oversold / Neutral / Overbought`.

**Regola di timing derivata dai due badge combinati:**

| LongTermTrendBadge | MrMarketSentimentBadge | Interpretazione timing |
|---|---|---|
| Above trend | Oversold | Entry ideale: trend favorevole + pullback che crea prezzo scontato. Priorita alta. |
| Above trend | Neutral | Entry accettabile: trend favorevole, prezzo non in eccesso. Nessun segnale di attesa. |
| Above trend | Overbought | Entry sfavorevole: tendenza a comprare in cima a un rally. Attendere il prossimo pullback (RSI scenda sotto 50). |
| Near trend | Oversold | Potenziale opportunita, ma Screen 1 ambiguo: dimensione posizione ridotta. |
| Below trend | Oversold | Trend sfavorevole + estremo a ribasso: possibile rimbalzo tecnico. Non compatibile con entry per investitore value a meno di condizioni di panico (vedi [[panic-buy-vs-value-trap-detection]]). |
| Below trend | Overbought | Segnale di alert forte: titolo in downtrend primario + ipercomprato a breve = momento peggiore per acquistare. |

**Nota critica sull'RSI in trend forti:** In un uptrend sostenuto, l'RSI può rimanere in area "overbought" (>70) per periodi estesi senza reversal immediato. Murphy avverte che in trend forti il livello overbought non è un segnale di vendita autonomo. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 239] La combinazione con Screen 1 (trend primario) è essenziale per contestualizzare il segnale.

---

## Screen 3 — Livello d'entry: Support/Resistance e Pullback (Murphy)

Una volta che Screen 1 e Screen 2 sono favorevoli, Murphy indica come scegliere il livello preciso di entry: acquistare vicino ai livelli di support, non sul breakout o nel mezzo di un rally. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 82]

**Support in un uptrend:** area sotto il mercato dove il buying interest è sufficientemente forte da assorbire la selling pressure. In un uptrend, le aree di support sono i precedenti massimi superati (role reversal: la vecchia resistance diventa support). [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 82]

**Retracements come zone d'entry:** Murphy documenta i retracement del 33-50-66% come aree prevedibili di pullback: [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 85]

- **33% di retracement:** trend molto forte; entry aggressiva.
- **50% di retracement:** zona classica; entry standard in uptrend sano.
- **66% di retracement:** massimo retracement tollerabile; entry con stop stretto sotto il 66%. Violazione del 66% mette in discussione il trend.

**Applicazione pratica:**

Un titolo con verdetto VI positivo e `LongTermTrendBadge = Above trend` ha recentemente completato un rally dal bottom di 6 mesi. Il prezzo si ritrae del 40% di quel rally. `MrMarketSentimentBadge` scende verso Oversold. Questa è la combinazione ottimale per Screen 3: entry nel retracement del 33-50%, con stop sotto il livello del 66% (vedi [[ta-stop-placement-position-sizing]] per il calcolo dello stop).

---

## Come Elder integra il timing con l'Impulse System

Il complemento al Triple Screen è l'**Impulse System**: [^src: raw/2026-06-05-alexander-elder-new-trading-living-2014.txt §40. The Impulse System]

- **Barra verde** (EMA rising + MACD-H rising): entrambe le forze puntano al rialzo — il mercato ha impulso upside.
- **Barra rossa** (EMA falling + MACD-H falling): entrambe le forze puntano al ribasso — acquisto vietato.
- **Barra blu** (direzioni opposte): mercato neutrale — entry possibile con cautela.

Per l'investitore value che usa il dettaglio titolo senza accesso a grafici MACD-H: i due badge dell'app approssimano questo sistema in modo semplificato (SMA200 ≈ inerzia di lungo; RSI ≈ momentum di breve). L'Impulse System completo richiede un grafico con EMA e MACD-H — strumenti disponibili su qualunque piattaforma TA (TradingView, Bloomberg, etc.).

---

## Sintesi operativa: checklist di timing per la pagina dettaglio

Prima di eseguire un ordine di acquisto su un titolo promosso dal verdetto VI:

1. **Screen 1 — LongTermTrendBadge:** Is it "Above trend" or at minimum "Near trend"? If "Below trend", reduce position size and define stop before proceeding.
2. **Screen 2 — MrMarketSentimentBadge:** Is RSI in "Oversold" or "Neutral"? If "Overbought", wait for the next pullback.
3. **Screen 3 — Support level:** Is the current price near a documented support level (previous high, 50% retracement, SMA200 itself)? If yes, entry is structurally sound.
4. **Stop defined:** Can a logical stop be placed below the support (not at arbitrary percentage)? If yes, proceed. For position sizing, vedi [[ta-stop-placement-position-sizing]].

La combinazione ottimale: `LongTermTrendBadge = Above trend` + `MrMarketSentimentBadge = Oversold` + prezzo vicino a un livello di support identificato su grafico.

---

## Relazione con altri concetti wiki

- [[technical-analysis-trading-domain]] — confine di dominio TA/VI
- [[ta-vs-vi-decision-layer]] — come il layer TA si integra con il verdetto VI senza sostituirlo
- [[ta-stop-placement-position-sizing]] — dove mettere lo stop e quanto rischiare dopo l'entry
- [[elder-triple-screen-impulse-system]] — framework originale Elder (source primario)
- [[elder-risk-management-2pct-6pct]] — regole di sizing associate all'entry
- [[elder-trading-psychology]] — psicologia del timing (aspettare il momento giusto invece di comprare per FOMO)
- [[trend-trendlines-support-resistance]] — support/resistance come strumento di Screen 3 (Murphy)
- [[oscillators-momentum-rsi]] — RSI: teoria completa e limitazioni dell'indicatore
- [[moving-averages-ta]] — SMA200 come benchmark trend primario (Murphy Ch.9)
- [[volume-open-interest]] — il volume come conferma dei segnali di entry (OBV, regole volume/trend)
- [[chart-patterns-reversal-continuation]] — pattern di continuation come contesto per il timing
- [[panic-buy-vs-value-trap-detection]] — distinzione tra panic buy (entry opportunistica) e value trap
- [[mr-market]] — connessione comportamentale tra RSI oversold e la metafora Graham del mercato depresso

---

## Storie collegate

<!-- Sezione proprieta PM — non modificare -->

- [EP-024](../../management/kanban/EP-024-riepilogo-e-technical-analysis-tab/EP-024.md) — Tab Technical Analysis (porta il framework Triple Screen sul dettaglio ticker)
- [US-098](../../management/kanban/EP-024-riepilogo-e-technical-analysis-tab/US-098-pipeline-ta-payload-be/US-098.md) — BE Pipeline TA: payload trend + RSI + MACD + livelli (Screen 1/2/3)
- [US-099](../../management/kanban/EP-024-riepilogo-e-technical-analysis-tab/US-099-entry-timing-advisor-be/US-099.md) — BE Entry-timing advisor Triple-Screen-like (verdetto ENTRY_FAVORABLE/NEUTRAL/UNFAVORABLE/WAIT)
- [US-101](../../management/kanban/EP-024-riepilogo-e-technical-analysis-tab/US-101-tab-technical-analysis-fe/US-101.md) — FE Tab Technical Analysis
