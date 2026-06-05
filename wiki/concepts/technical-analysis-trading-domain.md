---
id: technical-analysis-trading-domain
type: concept
title: "Technical Analysis / Trading — Mappa di dominio e separazione da Value Investing"
status: draft
created: 2026-06-05
updated: 2026-06-05
sources:
  - raw/2026-06-05-alexander-elder-new-trading-living-2014.txt
tags: [technical-analysis, trading, domain-boundary, value-investing, ta-domain]
domain: technical-analysis-trading
---

# Technical Analysis / Trading — Separazione di dominio

Questo concept definisce il confine tra il dominio **analisi tecnica / trading attivo** e il dominio **value investing** che e il cuore dell'applicazione. Il materiale bibliografico TA/trading presente nel wiki (Elder 2014, Murphy 1999) appartiene a un dominio distinto e NON deve essere mescolato con i principi Graham/Buffett nelle pagine applicative.

---

## Differenze fondamentali tra i due domini

| Dimensione | Value Investing (core app) | Technical Analysis / Trading |
|---|---|---|
| Orizzonte | Anni / decenni | Giorni / settimane / mesi |
| Base decisionale | Analisi fondamentale (bilanci, valutazione intrinseca, moat) | Prezzo e volume (pattern, indicatori, psicologia di massa) |
| Presupposto teorico | Il prezzo diverge dal valore nel breve termine ma converge nel lungo | Il prezzo riflette tutto il conoscibile; il passato predice il futuro (pattern ripetibili) |
| Graham su TA | "Speculazione", non investimento (cfr. [[investment-vs-speculation]]) | — |
| Buffett su EMH | "Playing bridge with someone told it doesn't pay to look at the cards" (citato da Elder, §17) | Efficienza debole del mercato sfruttabile |
| Holding period | Permanente o pluriennale | Trade a termine definito |
| Stop-loss | Non concettualmente presente (MoS e il buffer) | Elemento fondamentale di sopravvivenza |
| Concentrazione | 5-20 posizioni concentrate per conviction | Diversificazione di sistema, position sizing fisso |

---

## Ruolo del TA nell'applicazione Value Investor

L'applicazione e un **value investor tool** (screening Buffett/Graham, rule engine, analisi fondamentale FMP, 13-F clone investing). I concetti TA/trading documentati nel wiki hanno un ruolo **ausiliario limitato** e distinto:

### Usi potenzialmente compatibili (con cautela)

1. **Timing dell'entry**: Graham ammetteva che il timing e lecito purche non sostituisca la valutazione fondamentale. Elder stesso (§33 Trading Timeframes) scrive: "Fundamental analysis can help you find a stock that may be worth buying. Use technical analysis to time your entries and exits." Questo e compatibile con l'approccio value se la valutazione fondamentale viene prima.

2. **Lettura del sentiment di mercato (Mr. Market)**: i concetti di psicologia delle folle di Elder (§12-15) sono strettamente correlati al concetto di Mr. Market di Graham ([[mr-market]]). I pattern di massa (euphoria → crash) sono la radice comportamentale del fenomeno che Graham descrive metaforicamente.

3. **Indicatori di mercato generale** (Parte 6 Elder): NH-NL Index, Stocks above 50-day MA, ecc. possono essere usati per contestualizzare le condizioni di mercato nell'analisi value (es. in un mercato in forte downtrend, il valore intrinseco rimane invariato ma il timing del deploy del capitale puo variare).

### Usi NON compatibili con il dominio value investing

- **Trading attivo di breve termine** basato su pattern tecnici senza analisi fondamentale
- **Short selling** basato su analisi tecnica (il value investing non usa il short selling come pratica sistematica)
- **Risk management 2%/6% rule** di Elder: il value investor non usa stop-loss meccanici; la protezione dal rischio viene dal margine di sicurezza e dalla diversificazione
- **Indicatori come MACD, Stochastic, RSI** come segnali primari di acquisto/vendita (nel value investing il segnale e il prezzo vs valore intrinseco)

---

## Punti di contatto concettuale

Alcuni concetti Elder hanno equivalenti nel value investing, ma con implementazione diversa:

| Concetto Elder | Concetto equivalente Value Investing | Nota differenza |
|---|---|---|
| Support/Resistance | Prezzo di acquisto / target di vendita Graham | TA: basato su memoria di prezzo; VI: basato su valutazione |
| Value Zone (tra EMA veloce e lenta) | Margine di sicurezza | TA: range di prezzo relativo; VI: sconto al valore intrinseco |
| Businessman's risk (2% Rule) | Margine di sicurezza (MoS) come buffer | TA: stop meccanico; VI: cushion qualitativo sulla valutazione |
| Guru skepticism (Elder §5) | Mr. Market skepticism (Graham) | Entrambi avvertono contro i leader di opinione |
| Record-keeping (Elder §57-59) | Diario di investimento Graham | Pratiche analoghe, timeframe diverso |

---

## Avviso per il wiki-keeper e i dev-agent

Quando si leggono o citano pagine wiki del dominio TA/trading (tag `ta-domain`):

1. **Non importare** regole TA/trading nelle pagine di value investing esistenti senza nota esplicita di dominio
2. **Non usare** indicatori tecnici come criteri del rule engine (i 13+2 ruleId sono tutti fondamentali)
3. **Citare il dominio** esplicitamente quando si fa riferimento incrociato tra i due domini
4. Il **confine decisionale** resta: se un'azione non passa lo screening fondamentale (ruleId Verde/Giallo), nessun segnale tecnico giustifica l'acquisto nell'applicazione

---

## Relazione con altri concetti wiki

- [[investment-vs-speculation]] — concept esistente con la distinzione Graham
- [[mr-market]] — concept esistente con la metafora Graham (ponte comportamentale verso TA)
- [[elder-new-trading-living-2014]] — sorgente TA principale
- [[murphy-technical-analysis-financial-markets-1999]] — sorgente TA Murphy (OCR 2026-06-05, ingest completo)
- [[john-murphy]] — entita autore Murphy
- [[dow-theory]] — 6 principi Dow (Murphy Ch. 2)
- [[trend-trendlines-support-resistance]] — trend/support/resistance/trendlines (Murphy Ch. 4)
- [[chart-patterns-reversal-continuation]] — pattern reversal e continuation (Murphy Ch. 5-6-12)
- [[moving-averages-ta]] — medie mobili e Bollinger Bands (Murphy Ch. 9)
- [[oscillators-momentum-rsi]] — oscillatori, RSI, MACD, Stochastics (Murphy Ch. 10)
- [[volume-open-interest]] — volume, OBV, open interest, COT (Murphy Ch. 7)
- [[intermarket-analysis-murphy]] — analisi intermarket Dollar/Commodities/Bonds/Stocks (Murphy Ch. 17)
- [[elder-trading-psychology]] — psicologia del trading (Elder)
- [[elder-triple-screen-impulse-system]] — sistemi di trading (Elder)
- [[elder-risk-management-2pct-6pct]] — risk management (Elder)
- [[value-investing-rule-engine]] — rule engine fondamentale (dominio core app)
- [[graham-investing-philosophy]] — sintesi value investing

---

## Storie collegate

<!-- Sezione proprieta PM — non modificare -->

- [EP-024](../../management/kanban/EP-024-riepilogo-e-technical-analysis-tab/EP-024.md) — Riepilogo + Tab Technical Analysis (introduce TA come layer advisory di timing, separato e governato dai confini di dominio qui formalizzati)
