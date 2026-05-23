---
type: synthesis
sources: ["raw/agent.py", "raw/09_agent_py_method_analysis.md", "raw/investitore intelligente.txt"]
status: draft
created: 2026-05-23
updated: 2026-05-23
tags: [value-investing, graham, buffett, methodology, synthesis, agent-py, rule-engine, dcf, owner-earnings, seven-criteria]
---
# Graham 1973 ↔ Pratiche Moderne 2026 ↔ agent.py ↔ Rule Engine Kotlin

> Sintesi cross-domain delle metodologie di value investing: dove i quattro sistemi convergono, dove divergono, e quali scelte metodologiche hanno implicazioni per la WebApp. La divergenza piu' critica e' il discount rate (4.5% vs 9.5%); la convergenza piu' solida e' sulla qualita' del business (ROE, margini, debito).

## Contesto

Questa synthesis mette a confronto quattro "versioni" del value investing:
1. **Graham 1973** — i 7 criteri del Cap.14 de L'Investitore Intelligente (edizione italiana 2020).
2. **Pratiche moderne 2026** — stato dell'arte accademico e pratico (fonti web maggio 2026).
3. **agent.py v2.6.1** — Value Investor Bot, prototipo Python LangGraph.
4. **Rule engine Kotlin** — implementazione MVP della WebApp (Sprint 1-5).

[^src: raw/09_agent_py_method_analysis.md §2] [^src: raw/investitore intelligente.txt §Cap.14]

[^web: Benjamin Graham's 7 Stock Criteria for Defensive Investors (Yahoo Finance) — https://finance.yahoo.com/news/benjamin-grahams-7-stock-criteria-150242223.html]
[^web: Lessons and Ideas from Benjamin Graham — Jason Zweig — https://jasonzweig.com/lessons-and-ideas-from-benjamin-graham-2/]
[^web: Chapter 14: Stock Selection for the Defensive Investor (Medium / David Cappelucci) — https://medium.com/the-intelligent-investor-series/chapter-14-stock-selection-for-the-defensive-investor-b50f847a2783]
[^web: Value Investing — CFA Level III Study Notes (AnalystPrep) — https://analystprep.com/study-notes/cfa-level-iii/value-investing/]

---

## 1. Discount Rate DCF

| Sistema | Valore | Razionale |
|---|---|---|
| Graham 1973 | ~7-8% (yield AAA corporate, implicito Cap.10) | Costo opportunita' obbligazionario; approccio conservativo |
| Pratiche moderne 2026 | 8-10% WACC (risk-free ~4.5% + MRP ~4-5%) | Standard CFA; applicabile a universo ampio |
| agent.py v2.6.1 | **4.5%** (risk-free only, hardcoded) | Approccio Buffett puro: pre-screening severo giustifica l'assenza di MRP |
| Rule engine Kotlin | **9.5%** (WACC standard) | CFA standard; nessuna assunzione su qualita' pre-screening |

**Convergenza**: Graham e pratiche moderne convergono su un range 7-10%. Il rule engine Kotlin (9.5%) e' in questo range.

**Divergenza critica**: agent.py usa 4.5% che e' fuori dal range standard. Giustificato solo dal pre-screening severo (ROE>15%, D/E<0.5, settori Buffett). Su universo NASDAQ+NYSE allargato, 4.5% sovrastima sistematicamente il valore intrinseco.

**Raccomandazione WebApp**: mantenere 9.5% come default. Esporre parametro configurabile in Deep Analysis (EP-011).

[^src: raw/09_agent_py_method_analysis.md §2.1 §6]

---

## 2. Owner Earnings (Formula DCF)

| Sistema | Formula | ΔWC incluso | Maint.CapEx |
|---|---|---|---|
| Buffett 1986 originale | NI + D&A +/- NonCash - MaintCapEx +/- ΔWC | Si | Solo maintenance |
| Pratiche moderne 2026 | Buffett 1986 + capitalizzazione quota R&D (intangibili) | Si | Solo maintenance |
| agent.py v2.6.1 | NI + D&A - \|TotalCapEx\| | No | Total CapEx (over-stima investimento) |
| Rule engine Kotlin | OCF - MaintCapEx (stima Greenwald PPE/Sales) | Implicito in OCF | Stima Greenwald |

**Convergenza**: tutti i sistemi usano D&A come addback e sottraggono CapEx. La struttura di base e' condivisa.

**Divergenza**: agent.py usa Total CapEx semplificato; Greenwald (rule engine Kotlin) e' piu' preciso nella distinzione maintenance vs growth. Per business in forte crescita (alto growth CapEx), la formula agent.py sovrastima l'investimento necessario e quindi sottostima gli Owner Earnings.

**Raccomandazione WebApp**: il rule engine Kotlin (Greenwald) e' metodologicamente superiore. Non degradare.

[^src: raw/09_agent_py_method_analysis.md §2.2]

---

## 3. I 7 Criteri Graham (Cap.14) — Confronto a 4 Colonne

| # | Criterio Graham | Soglia 1973 | Zweig 2003 | agent.py v2.6.1 | Rule engine Kotlin | EP-010 (gap colmato) |
|---|---|---|---|---|---|---|
| 1 | Dimensioni adeguate | Fatturato ≥ $100M (industriali) | Invariato (aggiustato inflazione) | Implicito: marketCap > $5B in screener | Non implementato | US-032 |
| 2a | Current Ratio | ≥ 2:1 | Invariato | Calcolato ma NON usato nel routing | `CURRENT_RATIO_LATEST` (≥2 GREEN) | Gia' in Kotlin |
| 2b | LT Debt | ≤ Net Current Assets (NCAV) | Invariato | D/E < 0.5 (variante Buffett) | `DEBT_TO_INCOME_LATEST` (<4 anni) | Variante Buffett in entrambi |
| 3 | Stabilita' Utili | NI > 0 ogni anno per 10y | Invariato | ROE 5y check (no stabilita' annuale) | Implicito in ROE/Margin 10y | US-033 |
| 4 | Dividendi | Continui ≥ 20 anni | Invariato (rilassato per growth) | Non implementato | Non implementato | US-037 |
| 5 | Crescita EPS | ≥ +33% in 10y (medie 3y) | ≥ +50% (inflazione Zweig) | EPS CAGR calcolato ma senza soglia | Implicito in ROE/ROIC | US-034 (con Zweig +50%) |
| 6 | P/E | ≤ 15 (media EPS 3y) | Invariato | Non implementato | Solo via grahamNumber scalare | US-035 |
| 7 | P/B | ≤ 1.5; P/E×P/B ≤ 22.5 | Invariato | Non implementato | Solo via grahamNumber scalare | US-036 |

**Criteri implementati nel Rule Engine MVP (pre-EP-010)**: 2a, 2b (variante Buffett), 3 (parziale), piu' aggiunte Buffett (ROE, ROIC, Gross Margin, CapEx intensity).

**Criteri da aggiungere con EP-010**: 1 (size), 4 (dividendi 20y), 5 (EPS CAGR con soglia), 6 (P/E), 7 (P/B).

[^src: raw/09_agent_py_method_analysis.md §2.3] [^src: raw/investitore intelligente.txt §Cap.14]

---

## 4. Aggiunte Moderne Non Presenti in Graham

| Tecnica | Sistemi | Origine | Rationale |
|---|---|---|---|
| 13-F overlay (clone investing) | agent.py | Pabrai/Spier | Segnale di business pre-approvato da investitori con track record decennale |
| News sentiment LLM | agent.py | Behavioral finance moderna | Distingue panico temporaneo da deterioramento strutturale |
| Panic-buy detection (drawdown 52w) | agent.py | Cap.8 Graham (Mr. Market) + behavioral 2010+ | Operativizza "Be greedy when others are fearful" |
| Munger inversion RAG (10-K/10-Q) | agent.py | Charlie Munger "Invert, always invert" | Pre-empts catastrophic losses; complementa analisi quantitativa |
| Sector blacklist (biotech, mining, airlines) | agent.py | Buffett circle of competence | Esclude business non-prevedibili su orizzonte 10 anni |
| Position sizing scalato con MoS | agent.py | Kelly Criterion variants | Max 7 posizioni, riserva 15% liquidita' |
| ROIC > 12% | Rule engine Kotlin | Buffett/Munger | Non in Graham 1973; misura efficienza allocazione capitale |
| Gross Margin > 40% | Rule engine Kotlin | Buffett pricing power | Non in Graham 1973; proxy per moat durevole |

[^src: raw/09_agent_py_method_analysis.md §2.4]

---

## 5. Position Sizing e Portfolio Construction

| Sistema | Approccio |
|---|---|
| Graham 1973 | Diversificazione difensiva (10-30 titoli); ribilancio annuale; no position sizing esplicita |
| Pratiche moderne 2026 | Equal weight vs Kelly; max 5-10% per posizione; diversificazione per settore |
| agent.py v2.6.1 | Max 7 posizioni; riserva 15% liquidita'; position scalata con MoS (mult 0.7-1.3x); bonus 1.2x per panic_buy |
| Rule engine Kotlin MVP | No position sizing — solo segnale buy/hold/sell |

**Gap rule engine**: il rule engine Kotlin non implementa position sizing. Questa funzionalita' potrebbe essere aggiunta come feature post-MVP. [^src: raw/09_agent_py_method_analysis.md §2.4] [^src: raw/agent.py:1829-1846]

---

## 6. Filtri Comportamentali (Behavioral Finance)

| Filtro | Graham | Pratiche 2026 | agent.py | Rule engine Kotlin |
|---|---|---|---|---|
| Anti-panico (non vendere in crash) | Cap.8 Mr. Market (narrativo) | Behavioral finance; momentum contrarian | panic_discount flag + MoS ridotto | No |
| Anti-moda (evitare trend) | Cap.1-5 (difensivo) | Factor investing (qualita' > momentum) | Sector blacklist SPAC/crypto/biotech | No |
| Anti-value-trap | Non esplicito | Distress scoring, Z-score Altman | deterioration_warning + STRUCTURAL_DAMAGE | No |
| Moat check qualitativo | Cap.15 (intraprendente) | Competitive advantage period (CAP) | Munger inversion RAG | No (economic moat narrativo) |

**Convergenza**: Graham introduce i filtri comportamentali in forma narrativa (il "giusto atteggiamento mentale"); agent.py li operativizza algoritmicamente. Le pratiche moderne 2026 li formalizzano nel framework behavioral finance.

[^src: raw/09_agent_py_method_analysis.md §2.4]

---

## 7. Decisioni Metodologiche Discutibili (Input per ADR Futuri)

Le seguenti decisioni in agent.py divergono dalla pratica standard e meritano un ADR esplicito nel porting Kotlin:

| # | Decisione | Divergenza | Rischio | ADR consigliato |
|---|---|---|---|---|
| 1 | r=4.5% (risk-free only) | vs 9.5% standard | Sovrastima valore intrinseco fuori dal pre-screening | ADR-DCF-001: discount rate policy |
| 2 | Owner Earnings senza ΔWC | vs formula Buffett 1986 completa | Errore per business stagionali (retailer, costruzioni) | ADR-DCF-002: OE formula (gia' parzialmente in ADR-005) |
| 3 | ROE 5y vs 10y | agent.py 5y; rule engine 10y | 5y favorisce turnaround; 10y favorisce stabilita' | ADR-DCF-003: ROE lookback policy |
| 4 | EPS CAGR senza soglia | Graham: +33%; Zweig: +50% | Segnale calcolato ma non usato come gate | EP-010 US-034 (gia' pianificato) |
| 5 | News sentiment 3-way coarse | FinBERT supporta 7-10 dimensioni | Meno fine-grained | Trade-off costo/qualita'; da decidere in EP-011 |
| 6 | Panic threshold 35% hardcoded | Range empirico 25-55% | Troppo rigido per settori diversi | Configurabile per settore in EP-011 |
| 7 | 13-F lookback fisso | Lookback variabile per fondo sarebbe migliore | Segnale piu' debole per fondi attivi | Da considerare in EP-012 |

[^src: raw/09_agent_py_method_analysis.md §6]

---

## 8. Conclusioni per la WebApp

**Convergenze solide** (mantenere invariate):
- Soglie qualita' business (ROE>15%, ROIC>12%, Gross Margin>40%, D/E basso, CapEx intensity)
- Current Ratio ≥ 2 (Graham puro, gia' implementato)
- Owner Earnings Greenwald (metodologicamente superiore a formula semplificata agent.py)
- Margin of Safety 30% come soglia standard

**Divergenze da gestire con ADR**:
- Discount rate: adottare 9.5% come default; esporre configurabile in Deep Analysis
- EPS CAGR: aggiungere soglia Zweig +50% con EP-010 US-034
- Criteri Graham mancanti: completare con EP-010 (dimensioni, dividendi, P/E, P/B)

**Tecniche moderne da portare in EP-011/EP-012**:
- Munger inversion RAG (10-K/10-Q narrative)
- News sentiment classification
- Panic-buy vs value-trap detection
- 13-F overlay (clone investing)

## Concetti correlati
[[value-investor-bot-architecture]]
[[dcf-discount-rate-policy]]
[[owner-earnings-formula-variants]]
[[panic-buy-vs-value-trap-detection]]
[[clone-investing-13f-overlay]]
[[munger-inversion-rag]]
[[seven-criteria-defensive-stock-selection]]

## Pagine collegate
[[value-investing-rule-engine]]
[[warren-buffett]]
[[benjamin-graham]]
[[intelligent-investor]]
[[value-investing-fmp-integration]]
[[superinvestors-graham-doddsville]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
