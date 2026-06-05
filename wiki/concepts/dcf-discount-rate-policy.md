---
type: concept
sources: ["raw/agent.py", "raw/09_agent_py_method_analysis.md"]
status: draft
created: 2026-05-23
updated: 2026-05-23
tags: [value-investing, dcf, discount-rate, wacc, risk-free, buffett, methodology, vi-domain]
domain: value-investing
---
# DCF Discount Rate Policy

> La scelta del discount rate e' la decisione metodologica piu' impattante di un DCF: agent.py usa r=4.5% (risk-free only, approccio Buffett puro), il rule engine Kotlin usa r=9.5% (WACC standard CFA). Le due scelte sono entrambe difendibili ma presuppongono universi di business molto diversi.

## Contesto

Nel DCF a due stadi il discount rate `r` e' il denominatore che attualizza i flussi di cassa futuri. Abbassarlo del 5% puo' aumentare il valore intrinseco stimato del 40-60% su un orizzonte 10 anni. Per questa ragione la scelta del tasso e' la piu' critica dell'intero modello DCF. [^src: raw/09_agent_py_method_analysis.md §2.1]

## Confronto dei Sistemi

| Sistema | Discount Rate | Razionale |
|---|---|---|
| Graham 1973 | Non specifica `r` esatto; Cap.10 cita yield AAA corporate ~7-8% | Conservativo, ancorato al costo opportunita' obbligazionario |
| Finanza moderna 2026 | WACC large-cap 8-10%; risk-free 10Y Treasury ~4.2-4.5%; MRP forward-looking 4-5% | Standard accademico; applicabile a universo ampio di business |
| agent.py v2.6.1 | `r = 0.045` (4.5%) hardcoded | Solo risk-free 10Y Treasury, nessun premio rischio |
| Rule engine Kotlin | `r = 0.095` (9.5%) | WACC standard large-cap, approccio CFA |

[^src: raw/09_agent_py_method_analysis.md §2.1] [^src: raw/agent.py:1792]

[^web: The Complete Guide to Calculating Discount Rates for DCF Valuation (2025) — https://financialmodeling.tech/learnings/discounted-cash-flow/discount-rate]
[^web: Value Investing — CFA Level III Study Notes (AnalystPrep) — https://analystprep.com/study-notes/cfa-level-iii/value-investing/]

## Dettaglio: r=4.5% in agent.py

```python
# agent.py:1792
g1, g2, r, anni = 0.05, 0.02, 0.045, 10
```

Il tasso 4.5% corrisponde approssimativamente al rendimento del Treasury 10Y USA in regime 2024-2026 (4.2-4.5%). Buffett ha storicamente usato il risk-free come tasso di sconto per i business che considera "certi come un bond" — business con moat forte, earnings prevedibili su 10 anni, bassa ciclicita'. [^src: raw/agent.py:1774-1792]

La giustificazione metodologica e' che lo screener di agent.py fa gia' pre-filtering severo:
- ROE > 15% (costante 5 anni)
- D/E < 0.5
- Solo settori Buffett (esclude biotech, mining, airlines, gambling, SPAC)
- marketCap > $5B

Su questo sottoinsieme di business, Buffett argomenta che il premio rischio azionario tradizionale e' gia' catturato dallo screening qualitativo, non dal tasso. [^src: raw/09_agent_py_method_analysis.md §2.1]

## Decisione Metodologica Discutibile

**Rischio**: r=4.5% **sovrastima sistematicamente** il valore intrinseco se applicato a business non perfettamente prevedibili. Se i criteri di pre-screening si allentano (es. universo screener NASDAQ+NYSE senza filtri settoriali severi), il DCF diventa troppo ottimistico. [^src: raw/09_agent_py_method_analysis.md §6]

Esempio numerico su Owner Earnings = $1B, crescita 5%/10 anni + terminal 2%:
- Con r=4.5%: valore totale ~$28.5B
- Con r=9.5%: valore totale ~$16.1B
- Delta: +77% con il tasso aggressivo

Questo delta e' la differenza tra trovare "occasione di acquisto" e "valutazione corretta".

## Dettaglio: r=9.5% nel Rule Engine Kotlin

Il DCF del rule engine usa r=9.5% come default, implementato in `DcfCalculator`: [^src: raw/09_agent_py_method_analysis.md §2.1]

```
r = 9.5%  (discount)
g1 = 5-7% (crescita fase 1, 10 anni)
g2 = 2.5% (terminal growth)
```

Questo e' il valore standard CFA per business large-cap USA: risk-free ~4.5% + equity risk premium ~5%. Applicabile a qualsiasi business senza assumere pre-screening qualitativo.

## Raccomandazione per la WebApp

- **Default conservativo**: mantenere `r=9.5%` nel rule engine Kotlin come default.
- **Deep Analysis (EP-011)**: esporre `r` come parametro configurabile dall'utente (range 4-12%) con default 9.5%. Documentare il razionale.
- **Preset "Buffett Pure"**: r=4.5%, disponibile solo se l'utente ha attivato tutti i 7 criteri Graham + filtri settoriali severi.

[^src: raw/09_agent_py_method_analysis.md §2.1 §6]

## Relazione con il DCF a Due Stadi

Il DCF a due stadi di agent.py:
1. **Fase 1** (10 anni): `PV1 = sum(OE * (1+g1)^n / (1+r)^n for n in 1..10)`
2. **Terminal value**: `TV = OE * (1+g1)^10 * (1+g2) / (r - g2)`
3. **PV terminal**: `PV2 = TV / (1+r)^10`

Con `g1=5%`, `g2=2%`, `r=4.5%`. [^src: raw/agent.py:1793-1795]

## Concetti correlati
[[owner-earnings-formula-variants]]
[[intrinsic-value]]
[[margin-of-safety]]
[[value-investor-bot-architecture]]
[[graham-modern-bot-methodologies]]

## Pagine collegate
[[value-investing-rule-engine]]
[[warren-buffett]]
[[seven-criteria-defensive-stock-selection]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
