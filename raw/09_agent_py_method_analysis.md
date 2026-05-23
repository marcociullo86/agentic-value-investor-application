# Analisi metodologica di `agent.py` — Value Investor Bot v2.6.1

> **Tipo**: raw analitico (non documento sorgente del libro, ma sintesi di codice + ricerca web di terze parti).
> **Fonte primaria**: `raw/agent.py` (Value Investor Bot v2.6.1 — LangGraph multi-agent, 2350 righe).
> **Fonti web (maggio 2026)**: vedi sezione `## Fonti esterne` in coda.
> **Scopo**: fornire al `wiki-keeper` materiale strutturato per ingest in `wiki/` con confronto Graham 1973 ↔ pratiche moderne 2026 ↔ implementazione agent.py ↔ rule engine Kotlin esistente.

---

## 1. Architettura LangGraph multi-agente

`agent.py` è un sistema **multi-agente orchestrato via LangGraph** (StateGraph) che simula il processo decisionale del "Team Buffett" su un universo dinamico di ticker NASDAQ + NYSE. Il flusso (line refs: `agent.py:2229-2286` `build_graph`):

```
screener (4 segnali) → [LOOP per ogni ticker]:
  estrai_dati → leggi_10k (RAG su 10-K + 10-Q) →
  news_sentiment → check_price_action → calcola_valore →
  munger_decision (cascade) → verdetto → next
genera_report HTML
```

Nodi e responsabilità:

| Nodo | Funzione | Line refs |
|---|---|---|
| `node_screener` | Costruisce universo dinamico combinando 4 segnali (13-F + quant FMP + news LLM + settori Buffett) | `agent.py:875-987` |
| `node_estrai_dati` | Estrae 5y di income/balance/cashflow da FMP, calcola ROE/Owner Earnings/CAGR/Current Ratio | `agent.py:1029-1116` |
| `node_leggi_report_10k` | Scarica 10-K + 10-Q da SEC EDGAR, FAISS RAG, 10 query inversione Munger, Claude Opus 4.7 | `agent.py:1304-1494` |
| `node_news_sentiment` | News 90gg via FMP, classificazione TEMPORARY_PANIC / STRUCTURAL_DAMAGE / NEUTRAL via Opus | `agent.py:1504-1631` |
| `node_check_price_action` | Storico 12 mesi via FMP, drawdown 52w, flag panic_discount + deterioration_warning | `agent.py:1641-1767` |
| `node_calcola_valore_buffett` | DCF 2-stadi con Owner Earnings, MoS calc | `agent.py:1774-1822` |
| `munger_decision` | Cascade verdetto: 6 outcomes (APPROVATO_PANIC_BUY / APPROVATO / WATCHLIST / BOCCIATO_NUMERICO / BOCCIATO_QUALITATIVO / BOCCIATO_VALUE_TRAP) | `agent.py:1901-1936` |
| `_calcola_position_size` | Position sizing scalato con MoS, max 7 posizioni, riserva 15% liquidità | `agent.py:1829-1846` |

LLM strategy ibrida:
- **Claude Opus 4.7** (`claude-opus-4-7`) per analisi finanziaria profonda (10-K narrative, news sentiment)
- **Gemini 2.5 Flash** per screener news scout (alto volume, costo basso)
- **Embeddings**: `BAAI/bge-large-en-v1.5` locale (HuggingFace) o `gemini-embedding-001` (cloud, slow su free tier)

---

## 2. Metodologia di valutazione: confronto Graham ↔ Moderno ↔ agent.py ↔ Rule Engine Kotlin

### 2.1 DCF Discount Rate — la decisione più critica

| Sistema | Discount Rate | Razionalia |
|---|---|---|
| **Graham 1973** | Non specifica r esatto (Cap.10 cita yield AAA corporate ~7-8%) | Conservativo, ancorato a yield obbligazioni |
| **Finanza moderna 2026** | WACC large-cap 8-10%; risk-free 10Y Treasury ~4.2-4.5%; MRP forward-looking 4-5% | Buffett historically uses risk-free only (since "no premium needed for predictable businesses") |
| **agent.py** | `r = 0.045` (4.5%) hardcoded — solo risk-free 10Y Treasury, **nessun premio rischio** | Approccio Buffett aggressivo: presume cherry-picking di business prevedibili dallo screener |
| **Rule engine Kotlin** | `r = 0.095` (9.5%) — WACC standard large-cap | Approccio CFA standard: applicabile a qualsiasi business senza assunzioni di pre-screening |

**Decisione metodologica discutibile in agent.py**: r=4.5% **sovrastima sistematicamente** il valore intrinseco se applicato a business non perfettamente prevedibili. È giustificato SOLO perché lo screener fa pre-filtering (ROE>15% + D/E<0.5 + settori Buffett OK + esclude biotech/SPAC/crypto). Se i criteri di pre-screening si allentano, il DCF diventa troppo ottimista.

**Raccomandazione per la WebApp**: mantenere il rule engine Kotlin con r=9.5% come default conservativo. Nella `Deep Analysis` (EP-011) esporre una soglia configurabile per utenti che vogliono replicare l'approccio Buffett puro.

### 2.2 Owner Earnings formula

| Versione | Formula | Note |
|---|---|---|
| **Buffett 1986 originale** | `Net Income + D&A ± Other Non-Cash Charges − Maintenance CapEx ± Δ Working Capital` | Inclusione `ΔWC` essenziale per business stagionali / lavorazione su commessa |
| **Moderna 2025 (intangibili)** | Buffett 1986 + capitalizzazione di una quota di R&D / SG&A (asset di sviluppo) | Riflette economia intangibile post-2010 (software, biotech, brand) |
| **agent.py** (`node_estrai_dati`) | `oe = Net Income + D&A − abs(CapEx)` — **manca ΔWC, manca capitalizzazione R&D** | Formula semplificata; ok per first approximation, sub-ottimale per business con WC volatile |
| **Rule engine Kotlin** (`GreenwaldMaintenanceCapexEstimator`) | Greenwald method: `OCF − Maintenance CapEx`, dove Maintenance CapEx = `Total CapEx − (PPE/Sales × ΔSales)` | Metodo Greenwald più rigoroso; cattura distinzione maintenance vs growth CapEx |

**Decisione metodologica**: il rule engine Kotlin (Greenwald) è metodologicamente superiore. agent.py usa Owner Earnings semplificato perché il foco è sull'analisi qualitativa (Munger inversion), non sulla precisione DCF.

### 2.3 Confronto con i 7 criteri Graham (Cap.14)

| Criterio Graham | Soglia 1973 | Zweig 2003 update | agent.py | Rule engine Kotlin | Gap |
|---|---|---|---|---|---|
| **1. Size (revenue)** | ≥ $100M (industriali); ≥ $50M (utility) | Invariato | Implicito (marketCap > $5B nello screener) | ❌ Non implementato | EP-010 US-032 colma il gap |
| **2a. Current Ratio** | ≥ 2.0 | Invariato | Calcolato in `current_ratio` ma NON usato in `munger_decision` | ✅ `CURRENT_RATIO_LATEST` | Rule engine allineato |
| **2b. LT Debt ≤ Net Current Assets** | LT Debt ≤ NCAV | Invariato | Sostituito da D/E < 0.5 (Buffett variant) | Sostituito da Debt/Income < 4 (Buffett variant) | Entrambi usano variante Buffett, non Graham puro |
| **3. Earnings Stability** | NetIncome > 0 ogni anno per 10y | Invariato | ❌ Solo ROE 5y check | ❌ Solo implicito in ROE 10y | EP-010 US-033 colma |
| **4. Dividend History** | Continui ≥ 20 anni | Invariato (rilassato per growth companies) | ❌ Mancante | ❌ Mancante | EP-010 US-037 colma |
| **5. EPS Growth** | ≥ +33% in 10y (medie 3y) | +50% (4% annuo) per inflazione | ⚠️ Calcola EPS CAGR ma senza soglia | ❌ Solo implicito | EP-010 US-034 colma (con Zweig +50%) |
| **6. P/E ≤ 15** | ≤ 15 (media EPS 3y) | Invariato | ❌ Mancante | ⚠️ Solo via grahamNumber esterno | EP-010 US-035 colma |
| **7. P/B ≤ 1.5** | ≤ 1.5 (oppure P/E × P/B ≤ 22.5) | Invariato | ❌ Mancante | ⚠️ Solo via grahamNumber esterno | EP-010 US-036 colma |

### 2.4 Aggiunte di agent.py NON in Graham (tecniche moderne)

| Tecnica agent.py | Origine | Rationale moderno |
|---|---|---|
| **13-F overlay via SEC EDGAR** (`_segnale_1_holdings_value`, `agent.py:684-734`) | Mohnish Pabrai / Guy Spier — "Clone Investing" | Indagare cosa tengono Berkshire, Pershing Square, Akre, Markel, Sequoia. Validato in studi accademici (CoStar 2018-2024) |
| **News sentiment LLM** (`node_news_sentiment`) | Behavioral finance moderna; informational edge | Classifica panico temporaneo (AmEx 1963, KO 1988, WFC 1990) vs deterioramento strutturale (Kodak vs digitale, Blockbuster vs Netflix) |
| **Panic-buy detection** (drawdown 52w + fondamentali solidi + news non strutturali) | Cap.8 Mr. Market (Graham) + behavioral 2010+ | "Be greedy when others are fearful". Threshold canonico: drawdown ≥ 35% (`PANIC_DRAWDOWN_THRESHOLD`) |
| **Munger inversion 10-K/10-Q** (10 query RAG su rischi, cause legali, fraud, customer concentration) | Charlie Munger "Invert, always invert" + Cap.15 Graham (intraprendente qualitativo) | Anti-survivorship-bias; pre-empts catastrophic losses |
| **Sector blacklist** (`SOTTOINDUSTRIE_BLACKLIST`: biotech, mining, airlines, tobacco, gambling, SPAC) | Buffett "circle of competence" + lessons learned | Esclude business non-prevedibili a 10y; Buffett ha venduto tutte le aviolinee 2020 |
| **Position sizing scalato con MoS** | Kelly Criterion variants; modern portfolio theory | Max 7 posizioni, riserva 15% liquidità, multiplier 0.7-1.3 in base a MoS, bonus 1.2x per panic_buy |

---

## 3. Endpoint FMP utilizzati da agent.py (oltre quelli del rule engine attuale)

Il rule engine Kotlin attuale usa: `/stable/income-statement`, `/stable/balance-sheet-statement`, `/stable/cash-flow-statement`, `/stable/key-metrics`, `/stable/profile`, `/stable/company-screener`, `/stable/search-symbol`.

**agent.py aggiunge** (utili per EP-011 e EP-012):

| Endpoint | Uso | Line refs |
|---|---|---|
| `/stable/sec-filings-search/symbol?symbol=X&from=YYYY-MM-DD&to=YYYY-MM-DD` | Lista filing SEC con `formType` (10-K, 10-Q, 8-K) + `finalLink` URL | `agent.py:1140-1182` |
| `/stable/quote?symbol=X` | Prezzo corrente + sharesOutstanding + marketCap (alternativa a `/profile`) | `agent.py:1801` |
| `/stable/historical-price-eod/full?symbol=X&from=...&to=...` | Storico OHLCV 12 mesi per drawdown analysis | `agent.py:1661-1669` |
| `/stable/news/stock?symbols=X&page=0&limit=N` | News per ticker (filtro 90gg post-hoc) | `agent.py:1521-1525` |
| `/stable/news/stock-latest?page=0&limit=200` | News mercato (per screener news scout) | `agent.py:790` |
| `/stable/company-screener?marketCapMoreThan=X&returnOnEquityMoreThan=0.15&debtToEquityLessThan=0.5&betaLessThan=1.3&volumeMoreThan=300000&exchange=NYSE,NASDAQ&country=US&limit=100` | Pre-filtro fondamentale quant | `agent.py:744-752` |

---

## 4. Integrazione SEC EDGAR diretta (non-FMP)

agent.py usa SEC EDGAR direttamente per il segnale 13-F (i piani FMP Ultimate $149/mo non sono più necessari grazie a questa integrazione):

| Endpoint SEC EDGAR | Uso | Line refs |
|---|---|---|
| `https://www.sec.gov/files/company_tickers.json` | Mappa CIK ↔ ticker (cached 30gg in `./cache/sec_tickers.json`) | `agent.py:164` `SEC_TICKERS_URL`, `agent.py:427-491` |
| `https://data.sec.gov/submissions/CIK{cik:010d}.json` | Lista submissions per CIK (filings 13-F, 10-K, 10-Q) | `agent.py:165` `SEC_SUBMISSIONS_URL`, `agent.py:567-580` |
| `https://www.sec.gov/Archives/edgar/data/{cik}/{accession}/index.json` | Index del filing → trovare l'Information Table XML | `agent.py:166` `SEC_ARCHIVES_BASE`, `agent.py:596-619` |

Requisiti SEC fair-access:
- **User-Agent obbligatorio**: `"ValueInvestorBot research@valueinvestorbot.com"` (`agent.py:163`)
- **Rate limit**: 6-7 req/sec (sotto la soglia SEC di 10 req/sec) → `SEC_RATE_LIMIT_S = 0.15` (`agent.py:167`)
- **Caching aggressivo**: TTL 30gg per la mappa CIK + fallback cache scaduta se SEC unreachable (`agent.py:441-472`)
- **Lista emergency Berkshire** (`SEC_EMERGENCY_HOLDINGS`, `agent.py:190-193`) come ultima ancora

Algoritmo di normalizzazione `nameOfIssuer` → ticker (validato 97.4% accuratezza su 76 casi reali):
1. Match esatto + classe azionaria corrispondente (CL A / CL B)
2. Match esatto + qualsiasi classe (preferisce A o nessuna)
3. Match per primi 2 token (issuer key)
4. Fuzzy match ratio ≥ 0.92 (`SEC_FUZZY_THRESHOLD`)

Suffissi noisy rimossi durante normalizzazione: `_CORP_SUFFIXES` (INC, CORP, COMPANY, LTD, LLC, ...), `_STATE_SUFFIXES` (DE, CA, NY, ...), `_NOISE_TOKENS` (NEW, OLD, II, III, IV).

---

## 5. Cascade decisionale Munger (`munger_decision`, agent.py:1901-1936`)

Priorità top-down (primo hit vince):

```
1. RISCHIO_ESTREMO Munger (10-K/10-Q narrative)     → BOCCIATO_QUALITATIVO
2. deterioration_warning AND news_sentiment=STRUCTURAL  → BOCCIATO_VALUE_TRAP
3. NOT passa_test_qualita (ROE<15% OR D/E>0.5)      → BOCCIATO_NUMERICO
4. panic_discount AND news_sentiment IN [TEMPORARY_PANIC, NEUTRAL] AND MoS>10%  → APPROVATO_PANIC_BUY
5. MoS > 30%                                         → APPROVATO
6. default                                           → WATCHLIST
```

Note metodologiche:
- Il priorità (1) > (2) > (3) > (4) > (5) > (6) implica che la **qualità qualitativa veta** sulla quantità, ma la **value-trap detection veta sul panic-buy** (impedisce di "comprare il sangue per le strade" quando le strade sono insanguinate per ragioni strutturali).
- (4) APPROVATO_PANIC_BUY ha priorità su (5) APPROVATO **anche quando MoS è solo > 10%** (vs > 30% per APPROVATO standard) — riflette la convinzione Buffett che le occasioni Mr.Market sono rare.
- Il position size raddoppia per panic_buy via `mult *= 1.2` (`agent.py:1845`).

---

## 6. Decisioni metodologiche discutibili identificate (per ADR futuri)

1. **`r=4.5%` in DCF**: troppo aggressivo se applicato fuori dal pre-screening Buffett. Per la WebApp che processa universo NASDAQ+NYSE (più ampio del pre-screening agent.py), mantenere il `r=9.5%` del rule engine Kotlin come default.

2. **Owner Earnings senza ΔWC**: per business stagionali (es. retailer, costruzioni, ingegneria su commessa) la formula `NI + D&A − CapEx` può divergere significativamente dai cash flow reali. Greenwald + maintenance CapEx (rule engine Kotlin) è metodologicamente superiore.

3. **ROE 5y vs 10y**: agent.py usa 5y (più reattivo, cattura business in turnaround); rule engine Kotlin usa 10y (più conservativo, allineato Graham). **Trade-off**: 5y favorisce growth-value; 10y favorisce business stabili. La Deep Analysis (EP-011) potrebbe esporre entrambi.

4. **EPS CAGR senza soglia**: agent.py calcola `eps_cagr_pct` ma non lo usa come gate. Graham + Zweig: soglia +33% (1973) o +50% (2003) in 10y. EP-010 US-034 colma.

5. **News sentiment classifier 3-way**: TEMPORARY_PANIC / STRUCTURAL_DAMAGE / NEUTRAL — categorizzazione coarse-grained. Modelli moderni 2025 (FinBERT, multi-label) supportano analisi a 7-10 dimensioni (litigation, regulatory, macro, competition, ...). Trade-off: more fine-grained = more LLM tokens = costo.

6. **Panic-buy threshold 35% drawdown**: hardcoded. Empiricamente AmEx 1963 -50%, KO 1988 -25%, WFC 1990 -55%. Range 25-55%. Potrebbe essere configurabile per settore.

7. **13-F lookback fissato a "ultimo trimestre disponibile" (`_ultimo_trimestre_13f`, agent.py:335-350)**: i fund top-holdings cambiano con velocità diverse (Berkshire raramente, Pershing Square più spesso). Lookback variabile per fund potrebbe migliorare la qualità del segnale.

---

## 7. Mapping ai gap già aperti in `wiki/gaps.md`

| Gap aperto | Indirizzato da agent.py? |
|---|---|
| `vi-sec-narrative-gap` (FMP non espone testo SEC) | ✅ SÌ — agent.py risolve via download HTML diretto da SEC e FAISS RAG. EP-011 ne è il porting Kotlin. |
| `fmp-stable-rate-limiting` (limiti FMP non documentati) | ⚠️ Parziale — agent.py applica `time.sleep(SEC_RATE_LIMIT_S)` solo per SEC, non per FMP. Resta gap aperto. |
| `tpm-llm-cost-budget-r2` ($110-175/mese stimato) | ⚠️ Conferma necessaria — agent.py usa modello ibrido (Opus solo dove serve, Gemini Flash per task leggeri) per ridurre costi. Pattern replicabile in EP-011. |
| `wiki-promote-sec-edgar-adapter-spec` (PM TPM, ` 2026-05-23`) | ✅ Spec implementativa = `agent.py:355-547` (helper SEC EDGAR completi) |
| `wiki-promote-universe-screener-spec` | ✅ Spec implementativa = `agent.py:744-988` (4 segnali aggregati) |

---

## 8. Pagine wiki proposte per il `wiki-keeper`

### Nuove concept (6):

1. **`wiki/concepts/value-investor-bot-architecture.md`** — Architettura LangGraph multi-agente di agent.py: 14 nodi, state machine, LLM strategy ibrida (Opus + Gemini Flash), endpoint integrati (FMP stable + SEC EDGAR).
2. **`wiki/concepts/dcf-discount-rate-policy.md`** — Policy di scelta del discount rate: risk-free vs WACC vs CAPM, rationale agent.py (4.5%) vs rule engine Kotlin (9.5%), raccomandazione per la WebApp.
3. **`wiki/concepts/owner-earnings-formula-variants.md`** — 3 varianti: Buffett 1986 originale, Greenwald maintenance capex, agent.py semplificato. Pro/contro.
4. **`wiki/concepts/panic-buy-vs-value-trap-detection.md`** — Algoritmo combinato: drawdown 52w + fondamentali + news sentiment. Soglie e casi storici (AmEx 1963, KO 1988, WFC 1990, Kodak, Blockbuster).
5. **`wiki/concepts/clone-investing-13f-overlay.md`** — Tecnica 13-F overlay (Mohnish Pabrai / Guy Spier): scarica top holdings di Berkshire + Pershing + Akre + Markel + Sequoia da SEC EDGAR. Algoritmo `nameOfIssuer → ticker` con normalizzazione 4-step (validato 97.4%).
6. **`wiki/concepts/munger-inversion-rag.md`** — Metodologia di RAG su 10-K/10-Q con 10 query Munger-style: rischi, cause legali, debiti, customer concentration, going concern, fraud, technology obsolescence, regulatory, guidance lowered, subsequent events.

### Nuove synthesis (1):

7. **`wiki/syntheses/graham-modern-bot-methodologies.md`** — Cross-domain: Graham 1973 (libro) ↔ Pratiche moderne 2026 (fonti web) ↔ agent.py v2.6.1 ↔ Rule engine Kotlin. Tabelle comparate per discount rate, owner earnings, 7 criteri, position sizing, behavioral filters.

### Aggiornamenti append-only a pagine esistenti (4):

8. **`wiki/concepts/value-investing-rule-engine.md`** — Aggiungere sezione `## Confronto con agent.py (v2.6.1)` con mapping ruleId Kotlin ↔ check agent.py + delta metodologici.
9. **`wiki/syntheses/value-investing-fmp-integration.md`** — Aggiungere endpoint FMP usati da agent.py non ancora wrappati nel rule engine: `/stable/sec-filings-search/symbol`, `/stable/historical-price-eod/full`, `/stable/news/stock`, `/stable/news/stock-latest`.
10. **`wiki/entities/warren-buffett.md`** — Aggiornare con riferimento a discount rate Buffett-style (risk-free only, justified by pre-screening); cita owner earnings 1986 con formula completa.
11. **`wiki/gaps.md`** — Chiusura parziale di `wiki-promote-sec-edgar-adapter-spec` e `wiki-promote-universe-screener-spec` (la nuova ingest fornisce le spec).

---

## 9. Note di costo LLM (rilevanti per EP-011/012 budget)

Stime maggio 2026 (Claude Opus 4.7, pricing pubblico):
- Input: $15/1M token; Output: $75/1M token
- Per analisi 10-K + 10-Q completa: input ~8000 token + output ~2000 token = $0.12 + $0.15 = **~$0.27/ticker**
- Per news sentiment (input ~7000 + output ~1500): **~$0.22/ticker**
- Per news scout screener (input ~8000 + output ~500): **~$0.16/run**

Batch notturno EP-012 (30 ticker): **~$15-20/run** = ~$450-600/mese (se cache invalidata ogni notte).

Con cache 90gg per 10-K/10-Q (TSK-095 V011__filing_blob): solo trimestrali producono refresh → **costo reale ~$50-100/mese**.

Senza cache: **costo alto**, da rifiutare in produzione.

---

## 10. Fonti esterne (consultate maggio 2026)

- [What is Owner Earnings? (The Warren Buffett Guide) – Old School Value](https://www.oldschoolvalue.com/what-is-owner-earnings/)
- [Owner earnings - Wikipedia](https://en.wikipedia.org/wiki/Owner_earnings)
- [Mind the Gap: An Intangible Twist on Warren Buffett's Owner Earnings (substack 2025)](https://compcap.substack.com/p/mind-the-gap-an-intangible-twist-8ea)
- [The Complete Guide to Calculating Discount Rates for DCF Valuation (2025)](https://financialmodeling.tech/learnings/discounted-cash-flow/discount-rate)
- [DCF Calculator 2026 | FREE Discounted Cash Flow Valuation Tool](https://cdcalculators.com/discounted-cash-flow-calculator/)
- [Benjamin Graham's 7 Stock Criteria for Defensive Investors (Yahoo Finance)](https://finance.yahoo.com/news/benjamin-grahams-7-stock-criteria-150242223.html)
- [Lessons and Ideas from Benjamin Graham – Jason Zweig](https://jasonzweig.com/lessons-and-ideas-from-benjamin-graham-2/)
- [Chapter 14: Stock Selection for the Defensive Investor (Medium / David Cappelucci)](https://medium.com/the-intelligent-investor-series/chapter-14-stock-selection-for-the-defensive-investor-b50f847a2783)
- [Fundamental Analysis 101: A Complete Beginner's Guide 2026 (Winvesta)](https://www.winvesta.in/blog/investors/fundamental-analysis-101-a-complete-beginners-guide-for-2026)
- [What is Value Investing? A Complete Guide for 2026 (Emeritus)](https://emeritus.org/blog/what-is-value-investing-a-complete-guide-for-2026/)
- [Value Investing — CFA Level III Study Notes (AnalystPrep)](https://analystprep.com/study-notes/cfa-level-iii/value-investing/)

---

**Fine analisi**. Il wiki-keeper può procedere con l'ingest seguendo lo schema §8.
