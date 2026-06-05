---
type: concept
sources: ["raw/agent.py", "raw/09_agent_py_method_analysis.md", "raw/08_Risoluzione_Q001_Owner_Earnings.md"]
status: draft
created: 2026-05-23
updated: 2026-05-23
tags: [value-investing, owner-earnings, dcf, buffett, greenwald, capex, working-capital, formula, vi-domain]
domain: value-investing
---
# Owner Earnings — Varianti della Formula

> Gli Owner Earnings misurано il "vero" potere generativo di cassa di un business. Esistono tre varianti principali: la formula originale Buffett 1986 (completa), il metodo Greenwald per la stima del maintenance CapEx (regola engine Kotlin), e la formula semplificata di agent.py. Le tre versioni producono risultati divergenti per business con capitale circolante volatile.

## Contesto

Warren Buffett definisce gli Owner Earnings nel rapporto annuale Berkshire Hathaway 1986 come la metrica piu' accurata per misurare il potere economico di un business. La formula e' progettata per superare i limiti dell'EPS contabile e del Free Cash Flow convenzionale. [^src: raw/09_agent_py_method_analysis.md §2.2]

[^web: What is Owner Earnings? (The Warren Buffett Guide) — Old School Value — https://www.oldschoolvalue.com/what-is-owner-earnings/]
[^web: Owner earnings — Wikipedia — https://en.wikipedia.org/wiki/Owner_earnings]
[^web: Mind the Gap: An Intangible Twist on Warren Buffett's Owner Earnings (substack 2025) — https://compcap.substack.com/p/mind-the-gap-an-intangible-twist-8ea]

## Variante 1 — Buffett 1986 Originale (Completa)

```
Owner Earnings = Net Income
               + Depreciation & Amortization
               +/- Other Non-Cash Charges
               − Maintenance CapEx
               +/- Delta Working Capital (ΔWC)
```

La formula completa include:
- **D&A**: addback delle spese non-cash (non richiedono esborso reale).
- **Non-Cash Charges**: stock-based compensation, svalutazioni, ecc.
- **Maintenance CapEx**: solo il CapEx necessario a mantenere la capacita' produttiva attuale — non il CapEx di crescita.
- **ΔWC**: variazione del capitale circolante operativo. Critica per business stagionali: un retailer accumula inventario in Q3 (ΔWC negativo = assorbimento di cassa reale).

**Pro**: misura esatta del potere economico del business.
**Contro**: richiede stima soggettiva del maintenance CapEx (non riportato separatamente nei bilanci); ΔWC introduce volatilita' trimestrale.

[^src: raw/09_agent_py_method_analysis.md §2.2]

### Aggiornamento 2025 — Economia Intangibile

Per business ad alta intensita' di intangibili (software, biotech, brand), alcuni analisti suggeriscono di capitalizzare una quota di R&D e SG&A come "asset di sviluppo", riducendo l'impatto sul Maintenance CapEx. Questo riflette l'economia post-2010 dove il CapEx fisico e' basso ma l'investimento in intangibili e' alto. [^src: raw/09_agent_py_method_analysis.md §2.2]

[^web: Mind the Gap: An Intangible Twist on Warren Buffett's Owner Earnings (substack 2025) — https://compcap.substack.com/p/mind-the-gap-an-intangible-twist-8ea]

## Variante 2 — Metodo Greenwald (Rule Engine Kotlin)

Il Rule Engine Kotlin usa il metodo Greenwald per stimare il Maintenance CapEx dal Total CapEx:

```
Maintenance CapEx = Total CapEx − (PPE/Sales × ΔSales)
Owner Earnings = OCF − Maintenance CapEx
```

Dove:
- `PPE/Sales` = rapporto storico tra Property Plant & Equipment e fatturato (stima dell'intensita' di capitale).
- `ΔSales` = crescita delle vendite nell'anno: identifica la quota di CapEx di crescita.
- `OCF` = Operating Cash Flow (gia' include D&A e ΔWC).

Implementato in `GreenwaldMaintenanceCapexEstimator.kt`. Il fallback per settori capital-intensive (Utilities, Telecomunicazioni) e' il Free Cash Flow tradizionale (`FcfFallbackEstimator.kt`). [^src: raw/08_Risoluzione_Q001_Owner_Earnings.md §3]

**Pro**: distingue rigorosamente maintenance vs growth CapEx; piu' preciso del totale CapEx.
**Contro**: dipende dalla stabilita' del rapporto PPE/Sales; in anni di forte espansione o contrazione il metodo puo' distorcere.

## Variante 3 — Formula Semplificata di agent.py

```python
# agent.py:1061-1067
# Owner Earnings = Net Income + D&A - CapEx (Berkshire 1986)
ni  = li.get("netIncome", 0)
da  = lc.get("depreciationAndAmortization", 0)
cap = abs(lc.get("capitalExpenditure", 0))
oe  = ni + da - cap
```

Formula: `OE = NI + D&A - |Total CapEx|`

Differenze dalla formula Buffett 1986 completa:
- **Usa Total CapEx** (non solo Maintenance CapEx): sovrastima l'investimento necessario per i business in crescita.
- **Esclude ΔWC**: errore per business stagionali; trascurabile per business stabili con WC costante.
- **Esclude Non-Cash Charges** (stock-based compensation, svalutazioni): impact variabile per settore.

[^src: raw/agent.py:1061-1067] [^src: raw/09_agent_py_method_analysis.md §2.2]

**Pro**: semplicissima da calcolare con i campi FMP disponibili; robusta a errori di classificazione CapEx.
**Contro**: per business con alto growth CapEx (Amazon, Alphabet) restituisce Owner Earnings artificialmente bassi; per business stagionali ignora il ciclo WC.

## Confronto Sintetico

| Variante | Formula | ΔWC | Maint.CapEx | Complessita' | Precisione |
|---|---|---|---|---|---|
| Buffett 1986 originale | NI + D&A +/- NonCash - MaintCapEx +/- ΔWC | Si | Solo maintenance | Alta | Alta |
| Greenwald (rule engine Kotlin) | OCF - MaintCapEx (stima PPE/Sales) | Implicito in OCF | Stima Greenwald | Media | Alta |
| agent.py semplificata | NI + D&A - |TotalCapEx| | No | Bassa | Media |

[^src: raw/09_agent_py_method_analysis.md §2.2]

## Implicazione per il Porting Kotlin (EP-011)

Il Rule Engine Kotlin gia' implementa Greenwald (Variante 2), che e' metodologicamente superiore alla formula agent.py. Il porting della Deep Analysis non deve degradare questa scelta.

Per la Deep Analysis (EP-011), l'Owner Earnings calcolato dal nodo `node_estrai_dati` puo' essere confrontato con quello del Rule Engine come sanity check: divergenze significative indicano alto growth CapEx o WC volatile.

## Concetti correlati
[[dcf-discount-rate-policy]]
[[intrinsic-value]]
[[margin-of-safety]]
[[value-investor-bot-architecture]]
[[graham-modern-bot-methodologies]]

## Pagine collegate
[[value-investing-rule-engine]]
[[warren-buffett]]
[[vi-08-risoluzione-q001-owner-earnings]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
