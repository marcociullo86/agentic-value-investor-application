---
type: concept
sources: ["raw/agent.py", "raw/09_agent_py_method_analysis.md"]
status: draft
created: 2026-05-23
updated: 2026-05-23
tags: [value-investing, behavioral-finance, panic-buy, value-trap, drawdown, news-sentiment, buffett, munger, vi-domain]
domain: value-investing
---
# Panic Buy vs Value Trap Detection

> L'algoritmo combinato di agent.py distingue i "panic buy" (business solidi colpiti da panico temporaneo del mercato — AmEx 1963, KO 1988, WFC 1990) dalle "value trap" (business in declino strutturale — Kodak vs digitale, Blockbuster vs Netflix). Il discriminante e' la combinazione di: drawdown 52-settimane + test fondamentali + classificazione news sentiment LLM.

## Contesto

Il Capitolo 8 de L'Investitore Intelligente introduce "Mr. Market" — la metafora della borsa come partner irrazionale che ogni giorno offre prezzi soggettivi. Buffett ha operativizzato questa intuizione nei due concetti gemelli:
- **Panic buy**: il mercato sconta eccessivamente un evento temporaneo su un business con moat intatto.
- **Value trap**: il prezzo scende perche' il business sta effettivamente deteriorando.

Confondere i due e' uno degli errori piu' costosi nel value investing. [^src: raw/09_agent_py_method_analysis.md §2.4] [^src: raw/09_agent_py_method_analysis.md §5]

[^web: What is Value Investing? A Complete Guide for 2026 (Emeritus) — https://emeritus.org/blog/what-is-value-investing-a-complete-guide-for-2026/]

## Algoritmo in agent.py

### Step 1 — Drawdown 52 Settimane (`node_check_price_action`)

```python
# agent.py:1641-1767 (sintesi)
# Scarica storico OHLCV 12 mesi via /stable/historical-price-eod/full
# Calcola max_52w = max(close prices)
# Drawdown attuale = (max_52w - prezzo_corrente) / max_52w * 100

PANIC_DRAWDOWN_THRESHOLD  = 35.0  # % — attiva panic_discount flag
WARNING_DRAWDOWN_THRESHOLD = 25.0  # % — attiva deterioration_warning flag
```

- `drawdown >= 35%` → flag `panic_discount = True` (potenziale occasione Buffett)
- `drawdown >= 25%` → flag `deterioration_warning = True` (da esaminare con sospetto)

[^src: raw/agent.py:126-133] [^src: raw/09_agent_py_method_analysis.md §2.4]

### Step 2 — News Sentiment Classification (`node_news_sentiment`)

News degli ultimi 90 giorni via FMP `/stable/news/stock`. Classificazione a 3 vie via Claude Opus 4.7:

| Categoria | Significato | Esempi |
|---|---|---|
| `TEMPORARY_PANIC` | Evento negativo isolato, non strutturale | Scandalo contabile temporaneo, crisi macroeconomica settoriale, recall prodotto isolato |
| `STRUCTURAL_DAMAGE` | Cambiamento fondamentale del modello di business | Disruption tecnologica, perdita cliente >30% ricavi, regulatory ban core product |
| `NEUTRAL` | Nessun evento negativo significativo nelle news | Normale volatilita' di mercato |

[^src: raw/09_agent_py_method_analysis.md §2.4]

### Step 3 — Cascade Decisionale (`munger_decision`)

Il routing combina i tre segnali in una cascata con priorita' top-down:

```python
# agent.py:1901-1936
# 1. Rischio Estremo Munger (10-K)          → BOCCIATO_QUALITATIVO
# 2. deterioration_warning AND STRUCTURAL   → BOCCIATO_VALUE_TRAP
# 3. Test quant falliti (ROE<15%, D/E>0.5)  → BOCCIATO_NUMERICO
# 4. panic_discount AND (TEMPORARY|NEUTRAL) AND MoS>10%  → APPROVATO_PANIC_BUY
# 5. MoS > 30%                              → APPROVATO
# 6. default                                → WATCHLIST
```

**Priorita' critica**: la value-trap detection (2) veta il panic-buy (4). Un drawdown del 40% con news strutturalmente negative non diventa mai un'occasione di acquisto. [^src: raw/agent.py:1901-1936] [^src: raw/09_agent_py_method_analysis.md §5]

## Casi Storici di Riferimento

| Caso | Anno | Drawdown | Tipo | Verdetto atteso |
|---|---|---|---|---|
| American Express — scandalo olio di insalata | 1963 | ~-50% | TEMPORARY_PANIC | APPROVATO_PANIC_BUY |
| Coca-Cola — distribuzione nuovi prodotti falliti | 1988 | ~-25% | TEMPORARY_PANIC | APPROVATO_PANIC_BUY |
| Wells Fargo — svalutazioni immobiliari California | 1990 | ~-55% | TEMPORARY_PANIC | APPROVATO_PANIC_BUY |
| Kodak — disruption digitale | 2000-2012 | ~-90% | STRUCTURAL_DAMAGE | BOCCIATO_VALUE_TRAP |
| Blockbuster — Netflix streaming | 2005-2010 | ~-95% | STRUCTURAL_DAMAGE | BOCCIATO_VALUE_TRAP |

[^src: raw/09_agent_py_method_analysis.md §2.4] [^src: raw/agent.py:126]

## Position Sizing Panic Buy

Quando il verdetto e' `APPROVATO_PANIC_BUY`, il position sizing riceve un bonus del 20%:

```python
# agent.py:1843-1845
if panic:
    mult *= 1.2  # Buffett: "Quando piove oro, prendi un secchio"
```

La logica riflette la filosofia Buffett: le occasioni di panic buy sono rare e, quando si presentano su business fondamentalmente solidi, meritano un'allocazione maggiore. [^src: raw/agent.py:1829-1846]

## MoS Ridotto per Panic Buy

Il verdetto `APPROVATO_PANIC_BUY` richiede MoS > 10% (vs 30% per `APPROVATO` standard). Questo riconosce che in condizioni di panico il prezzo e' depresso proprio perche' il mercato sconta il business come se fosse una value trap — quindi il MoS calcolato al tasso 4.5% potrebbe essere sottostimato per effetto del panico stesso. [^src: raw/agent.py:1928] [^src: raw/09_agent_py_method_analysis.md §5]

## Decisioni Metodologiche Discutibili

1. **Soglia 35% hardcoded**: AmEx 1963 fu -50%, KO 1988 -25%, WFC 1990 -55%. Il range empirico e' 25-55%. La soglia potrebbe essere configurabile per settore (es. banche in crisi macro tollerano drawdown piu' elevati). [^src: raw/09_agent_py_method_analysis.md §6]

2. **News sentiment 3-way (coarse-grained)**: modelli moderni 2025 (FinBERT, multi-label) supportano classificazione a 7-10 dimensioni (litigation, regulatory, macro, competition, ...). Trade-off: piu' fine-grained = piu' LLM tokens = costo. [^src: raw/09_agent_py_method_analysis.md §6]

3. **Finestra news 90 giorni**: eventi strutturali potrebbero richiedere lookback piu' lungo (es. perdita cliente avvenuta 6 mesi fa ma effetti ancora in corso).

## Concetti correlati
[[munger-inversion-rag]]
[[value-investor-bot-architecture]]
[[dcf-discount-rate-policy]]
[[graham-modern-bot-methodologies]]
[[margin-of-safety]]

## Pagine collegate
[[value-investing-rule-engine]]
[[warren-buffett]]
[[benjamin-graham]]
[[intelligent-investor]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
