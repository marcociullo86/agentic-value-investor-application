---
type: concept
sources: ["raw/investitore intelligente.txt"]
status: draft
created: 2026-05-22
updated: 2026-05-22
tags: [value-investing, graham, inflation, bonds, equities, real-return, portfolio-protection]
---
# Inflazione e Investimento — Il Metodo Graham (Cap.2)

> Il Capitolo 2 de L'Investitore Intelligente analizza come l'inflazione erode i rendimenti reali delle obbligazioni e perche' le azioni con strong pricing power sono la protezione parzialmente superiore, pur non essendo un hedge perfetto.

## Contesto

Il Capitolo 2 e' la risposta di Graham al problema dell'erosione del potere d'acquisto nel lungo periodo. La tesi non e' "compra sempre azioni per battere l'inflazione" ma piuttosto "le obbligazioni a tasso fisso sono la peggior protezione contro l'inflazione; le azioni di aziende con pricing power sono meglio, ma non abbastanza da essere l'unica risposta". [^src: raw/investitore intelligente.txt §Cap.2 — L'Investitore e l'Inflazione]

## La Minaccia dell'Inflazione

### Erosione del Potere d'Acquisto

Un'obbligazione a cedola fissa del 4% paga il 4% nominale, ma se l'inflazione e' al 6%, il rendimento reale e' negativo (-2%). Il detentore di obbligazioni a lungo termine in un regime inflattivo perde potere d'acquisto in modo garantito. Graham scrive nell'edizione 1973 dopo un decennio di inflazione elevata negli USA — il problema era molto concreto. [^src: raw/investitore intelligente.txt §Cap.2 — L'Investitore e l'Inflazione]

### Azioni come Hedge Parziale

Le azioni rappresentano quote di business reali. Un'azienda con forte [[economic-moat]] e pricing power puo' trasferire l'aumento dei costi sui prezzi, proteggendo i margini in termini reali. Nel lungo periodo (decenni), le azioni hanno storicamente sovraperformato l'inflazione.

Tuttavia, Graham mette in guardia contro l'argomento semplicistico "le azioni proteggono sempre dall'inflazione":
- In periodi di alta inflazione, anche le azioni possono scendere in termini nominali (es. 1973-1974).
- Le aziende capital-intensive subiscono l'effetto della reinflazione degli asset fissi (CapEx di rimpiazzo piu' caro).
- Il multiplo P/E tende a comprimersi nei regimi di alta inflazione (il tasso privo di rischio sale, il discount rate sale, le valutazioni scendono).

[^src: raw/investitore intelligente.txt §Cap.2 — L'Investitore e l'Inflazione]

## Raccomandazioni Operative Graham

### Asset Allocation Anti-Inflazione

L'approccio Graham non cambia radicalmente con l'inflazione: la divisione azioni/obbligazioni 25-75% (regola [[defensive-vs-enterprising-investor]]) gia' incorpora protezione contro diversi scenari. In regime di alta inflazione:

- Ridurre obbligazioni a tasso fisso di lungo periodo (massima esposizione al rischio inflattivo).
- Preferire obbligazioni inflation-linked (TIPS negli USA) o a breve scadenza.
- Mantenere la quota azionaria su aziende con pricing power documentato (Gross Margin > 40% come proxy).

### Categorie di Aziende Resistenti all'Inflazione

Graham identifica alcune caratteristiche che rendono un'azienda relativamente resistente all'inflazione:

1. **Pricing power**: capacita' di aumentare i prezzi senza perdere quota di mercato (correlato a [[economic-moat]]).
2. **Bassi asset fissi**: business leggeri dal punto di vista patrimoniale subiscono meno la reinflazione del CapEx.
3. **Fatturato in crescita reale**: crescita che supera l'inflazione, non solo in termini nominali.

[^src: raw/investitore intelligente.txt §Cap.2 — L'Investitore e l'Inflazione]

## Relazione con il Rule Engine

La metrica `GROSS_MARGIN_10Y_AVG` del [[value-investing-rule-engine]] (soglia > 40%) e' il proxy piu' diretto del pricing power — e quindi della resilienza all'inflazione. Un Gross Margin stabile sopra il 40% per 10 anni indica che l'azienda ha mantenuto il potere di prezzo in condizioni di mercato variabili, inclusi periodi di pressione sui costi di input.

La metrica `CAPEX_INTENSITY_10Y_AVG` (CapEx/Utile < 25%) identifica i business a bassa intensita' di capitale — meno esposti alla reinflazione degli asset fissi.

## Contesto Attuale (2026)

Nel contesto del 2026, con inflazione stabilizzata dopo il ciclo 2021-2024, il tema e' ancora rilevante per:
- La gestione della quota obbligazionaria del portafoglio difensivo.
- La preferenza per aziende con pricing power documentato (es. technology platform, consumer staples) rispetto a quelle capital-intensive (es. utilities, airlines).

## Concetti correlati
[[margin-of-safety]]
[[economic-moat]]
[[defensive-vs-enterprising-investor]]
[[value-investing-rule-engine]]

## Pagine collegate
[[intelligent-investor]]
[[benjamin-graham]]
[[margin-of-safety]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
