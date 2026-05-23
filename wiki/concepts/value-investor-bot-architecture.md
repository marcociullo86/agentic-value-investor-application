---
type: concept
sources: ["raw/agent.py", "raw/09_agent_py_method_analysis.md"]
status: draft
created: 2026-05-23
updated: 2026-05-23
tags: [value-investing, langgraph, multi-agent, architecture, llm, fmp, sec-edgar, screener]
---
# Value Investor Bot — Architettura LangGraph Multi-Agente

> Agent.py v2.6.1 e' un sistema multi-agente orchestrato via LangGraph (StateGraph) che simula il processo decisionale del "Team Buffett" su un universo dinamico di ticker NASDAQ + NYSE, combinando screener quantitativo, analisi SEC narrativa (RAG), news sentiment e DCF Owner Earnings.

## Contesto

Il Value Investor Bot (agent.py) e' il prototipo Python che anticipa le funzionalita' di Fase 2 della WebApp (EP-010, EP-011, EP-012). Documenta le scelte metodologiche che il porting Kotlin dovra' replicare o superare. [^src: raw/09_agent_py_method_analysis.md §1]

## Flusso Principale

```
node_screener (4 segnali)
  → [LOOP per ogni ticker nel universo]:
      node_estrai_dati
      → node_leggi_report_10k   (RAG FAISS + Munger inversion)
      → node_news_sentiment
      → node_check_price_action
      → node_calcola_valore_buffett
      → munger_decision (cascade routing)
      → verdetto (6 outcomes)
      → [prossimo ticker o node_genera_report]
node_genera_report → HTML
```

[^src: raw/agent.py:1-43] [^src: raw/09_agent_py_method_analysis.md §1]

## Nodi e Responsabilita'

| Nodo | Funzione | Line refs |
|---|---|---|
| `node_screener` | Universo dinamico da 4 segnali aggregati: 13-F SEC EDGAR, quant FMP, news LLM scout, settori Buffett | `agent.py:875-987` |
| `node_estrai_dati` | 5 anni income/balance/cashflow FMP, calcola ROE/Owner Earnings/CAGR/Current Ratio | `agent.py:1029-1116` |
| `node_leggi_report_10k` | Download 10-K + 10-Q da SEC EDGAR, FAISS RAG, 10 query inversione Munger, Claude Opus 4.7 | `agent.py:1304-1494` |
| `node_news_sentiment` | News 90gg via FMP, classificazione TEMPORARY_PANIC / STRUCTURAL_DAMAGE / NEUTRAL | `agent.py:1504-1631` |
| `node_check_price_action` | Storico 12 mesi via FMP, drawdown 52w, flag `panic_discount` + `deterioration_warning` | `agent.py:1641-1767` |
| `node_calcola_valore_buffett` | DCF 2-stadi con Owner Earnings, calcolo MoS | `agent.py:1774-1822` |
| `munger_decision` | Cascade routing: 6 outcomes (vedi sezione dedicata) | `agent.py:1901-1936` |
| `_calcola_position_size` | Position sizing scalato con MoS, max 7 posizioni, riserva 15% liquidita' | `agent.py:1829-1846` |

[^src: raw/09_agent_py_method_analysis.md §1]

## Strategia LLM Ibrida

La scelta dei modelli e' ottimizzata per qualita'/costo: [^src: raw/agent.py:140-154]

| Task | Modello | Razionale |
|---|---|---|
| Analisi 10-K/10-Q narrative (Munger inversion) | Claude Opus 4.7 (`claude-opus-4-7`) | Finance Agent v1.1 leader (64.4%), FinanceBench leader (82.7%), minor tasso allucinazioni |
| News sentiment classification | Claude Opus 4.7 | Richiede comprensione contesto settoriale profondo |
| Screener news scout (alto volume) | Gemini 2.5 Flash (`gemini-2.5-flash`) | Economico, GA 2026, sufficiente per task di screening |
| Embeddings FAISS RAG | `BAAI/bge-large-en-v1.5` (locale) o `gemini-embedding-001` (cloud) | Locale = gratis + istantaneo; cloud = soggetto a rate limit |

[^src: raw/agent.py:150-154] [^src: raw/09_agent_py_method_analysis.md §1]

## Endpoint Integrati

### FMP Stable

| Endpoint | Nodo | Uso |
|---|---|---|
| `/stable/income-statement` | `node_estrai_dati` | 5 anni dati reddituali |
| `/stable/balance-sheet-statement` | `node_estrai_dati` | Patrimonio netto, debiti |
| `/stable/cash-flow-statement` | `node_estrai_dati` | CapEx, D&A, OCF |
| `/stable/company-screener` | `node_screener` | Pre-filtro quant (ROE>15%, D/E<0.5, beta<1.3) |
| `/stable/sec-filings-search/symbol` | `node_leggi_report_10k` | Localizza 10-K/10-Q su SEC |
| `/stable/historical-price-eod/full` | `node_check_price_action` | Storico OHLCV 12 mesi |
| `/stable/news/stock` | `node_news_sentiment` | News per ticker (90gg) |
| `/stable/news/stock-latest` | `node_screener` | News mercato per screener |
| `/stable/quote` | `node_calcola_valore_buffett` | Prezzo corrente + sharesOutstanding |

[^src: raw/09_agent_py_method_analysis.md §3]

### SEC EDGAR (diretta, gratuita)

| Endpoint | Uso |
|---|---|
| `https://www.sec.gov/files/company_tickers.json` | Mappa CIK ↔ ticker (cache 30gg) |
| `https://data.sec.gov/submissions/CIK{cik:010d}.json` | Lista filing per CIK |
| `https://www.sec.gov/Archives/edgar/data/{cik}/{accession}/index.json` | Index filing → XML Information Table |

Rate limit: 6-7 req/sec (`SEC_RATE_LIMIT_S = 0.15`), sotto la soglia SEC fair-access di 10 req/sec. User-Agent obbligatorio: `"ValueInvestorBot research@valueinvestorbot.com"`. [^src: raw/agent.py:163-167]

## Costanti Configurabili

```python
UNIVERSO_FINALE_MAX_TICKET_NUMBER = 30       # max ticker per run screener
PANIC_DRAWDOWN_THRESHOLD  = 35.0             # % drawdown per "panic discount"
WARNING_DRAWDOWN_THRESHOLD = 25.0            # % drawdown per deterioration warning
MARGINE_SICUREZZA_STANDARD = 30.0            # % MoS soglia approvazione standard
NEWS_LOOKBACK_DAYS = 90                      # finestra temporale news
NEWS_MAX_ITEMS     = 30                      # max news da classificare
```

[^src: raw/agent.py:88-137]

## Modalita' Ibrida Ticker (v2.6)

L'agente supporta due modalita' di selezione universo: [^src: raw/agent.py:87-108]

- **Solo screener** (`TICKER_MANUALI = []`, `INCLUDI_SCREENER = True`): universo dinamico dai 4 segnali.
- **Manuali + screener** (`TICKER_MANUALI = ["AAPL", ...]`, `INCLUDI_SCREENER = True`): i ticker manuali bypassano il filtro settoriale (Trust mode) ma subiscono tutte le analisi profonde.
- **Solo manuali** (`INCLUDI_SCREENER = False`): screener saltato, solo analisi dei ticker specificati.

## Relazione con la WebApp (Porting Kotlin)

Il porting in produzione e' distribuito su tre Epic:
- **EP-010** — criteri Graham mancanti (7 criteri Cap.14)
- **EP-011** — deep analysis (SEC narrative RAG, Munger inversion, news sentiment)
- **EP-012** — batch top-value-picks (screener universe + report notturno)

Vedi [[value-investing-rule-engine]] per il rule engine MVP gia' implementato in Kotlin.
Vedi [[clone-investing-13f-overlay]] per il dettaglio del Segnale 1 (13-F SEC EDGAR).
Vedi [[munger-inversion-rag]] per la metodologia RAG 10-K/10-Q.
Vedi [[panic-buy-vs-value-trap-detection]] per l'algoritmo drawdown + news.
Vedi [[dcf-discount-rate-policy]] per la policy del discount rate (r=4.5% vs r=9.5%).

## Concetti correlati
[[value-investing-rule-engine]]
[[dcf-discount-rate-policy]]
[[owner-earnings-formula-variants]]
[[panic-buy-vs-value-trap-detection]]
[[clone-investing-13f-overlay]]
[[munger-inversion-rag]]
[[graham-modern-bot-methodologies]]

## Pagine collegate
[[warren-buffett]]
[[benjamin-graham]]
[[seven-criteria-defensive-stock-selection]]
[[fmp-financial-statements-stable]]
[[sec-filings-analysis]]
[[analysis-api-pipeline]]
[ADR-018 — Embeddings inference architecture](../../design_&_architecture/decisions/ADR-018-embeddings-inference-architecture.md)

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
