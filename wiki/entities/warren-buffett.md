---
type: entity
sources: ["raw/05_Analisi_10K_10Q_e_Regole_Buffett.md", "raw/04_Gestione_Rischio_Psicologia_Integrazione.md"]
status: draft
created: 2026-05-20
updated: 2026-05-20
tags: [value-investing, buffett, entity, person, investor, berkshire]
---
# Warren Buffett

> Il piu' celebre discepolo di Benjamin Graham; ha evoluto il value investing aggiungendo la valutazione qualitativa del fossato economico, il cerchio di competenza e il metodo degli Owner Earnings per il calcolo del valore intrinseco.

## Contesto

Warren Buffett ha applicato e trasformato il framework Graham, spostando il focus dal puro arbitraggio statistico (azioni a sconto meccanico) alla ricerca di business eccellenti con moat durevoli a prezzi equi. [^src: raw/05_Analisi_10K_10Q_e_Regole_Buffett.md §3. Il Metodo Warren Buffett: "Replicare il Cervello dell'Oracolo"]

## Contributi principali

### Cerchio di Competenza

Buffett opera solo entro il perimetro in cui ha un vantaggio analitico reale. Se il modello di business non e' prevedibile su un orizzonte di 10 anni, il titolo viene scartato a priori, indipendentemente dal prezzo apparente. [^src: raw/05_Analisi_10K_10Q_e_Regole_Buffett.md §3A. Il Cerchio di Competenza (Circle of Competence)]

### Fossato Economico

Ha codificato le quattro forme di vantaggio competitivo durevole. Vedi [[economic-moat]] per il dettaglio.

### Regole Quantitative

| Metrica | Soglia Buffett |
|---|---|
| ROE | > 15%, costante, senza leva eccessiva |
| ROIC | > 12-15% |
| Gross Margin | > 40% |
| Net Margin | > 10% |
| CapEx / Utile Netto | < 25-30% |
| Debito LT / Utile Netto | Estinguibile in < 4 anni |

[^src: raw/05_Analisi_10K_10Q_e_Regole_Buffett.md §3C. Le Regole Finanziarie Quantitative]

### Valutazione del Management

Il management di qualita' si comporta da comproprietario: compra azioni proprie sotto il valore intrinseco (buyback disciplinato), distribuisce cassa in eccesso quando non ci sono reinvestimenti ad alto ROIC, evita acquisizioni distruttive di valore (diworsification). [^src: raw/05_Analisi_10K_10Q_e_Regole_Buffett.md §3D. Valutazione del Management]

### Owner Earnings e Margin of Safety

Buffett stima gli Owner Earnings (Free Cash Flow modificato), li attualizza con DCF e applica uno sconto del 25-30% come [[margin-of-safety]] finale. [^src: raw/05_Analisi_10K_10Q_e_Regole_Buffett.md §4. Il Margine di Sicurezza (Margin of Safety)]

## Concetti correlati
[[economic-moat]]
[[margin-of-safety]]
[[intrinsic-value]]
[[sec-filings-analysis]]
[[fmp-key-metrics-ratios]]

## Pagine collegate
[[benjamin-graham]]
[[vi-05-analisi-10k-10q-buffett]]
[[vi-04-gestione-rischio-psicologia]]

## Aggiornamenti (v2026-05-22)

**Fonte aggiunta:** `raw/investitore intelligente.txt` — edizione italiana 2020.

### Graham come Figura Fondativa

Buffett descrive il rapporto con Graham con una citazione che va oltre il rapporto professore-allievo:

> "Per me, Ben Graham e' stato molto piu' che un autore o un insegnante. Ha influenzato la mia vita piu' di ogni altro uomo a parte mio padre." [^src: raw/investitore intelligente.txt §Prefazione di Warren Buffett]

Buffett lesse la prima edizione de "L'Investitore Intelligente" all'eta' di 19 anni (inizio 1950) e la definisce "di gran lunga il testo migliore mai scritto sul tema degli investimenti". Lavorò presso Graham-Newman Corp. nel periodo 1954-1956, dove apprese i metodi operativi del framework. [^src: raw/investitore intelligente.txt §Prefazione di Warren Buffett]

### Buffett come Superinvestitore Graham-and-Doddsville

La Buffett Partnership (1957-1969) registra 29.5% lordo annuo vs 7.4% Dow Jones su 13 anni — il rendimento piu' elevato tra i nove "Superinvestitori di Graham-and-Doddsville" presentati da Buffett al convegno Columbia 1984. [^src: raw/investitore intelligente.txt §Appendice 1 — I Superinvestitori di Graham-and-Doddsville]

Vedi [[superinvestors-graham-doddsville]] per l'analisi completa dei nove fondi.

## Aggiornamenti (v2026-05-23)

**Fonte aggiunta:** `raw/agent.py` (Value Investor Bot v2.6.1, che simula il processo decisionale Buffett) + `raw/09_agent_py_method_analysis.md`.

### Owner Earnings — Formula Completa 1986

Nel rapporto annuale Berkshire Hathaway 1986, Buffett definisce gli Owner Earnings come:

```
Owner Earnings = Net Income
               + Depreciation & Amortization
               +/- Other Non-Cash Charges
               − Maintenance CapEx
               +/- Delta Working Capital (ΔWC)
```

La distinzione cruciale rispetto al Free Cash Flow e' la separazione tra **Maintenance CapEx** (necessario a mantenere la capacita' produttiva attuale) e **Growth CapEx** (investimento per espandere la capacita'). Solo il primo riduce gli Owner Earnings; il secondo e' un investimento futuro che produce rendimenti. Vedi [[owner-earnings-formula-variants]] per il confronto tra la formula completa, il metodo Greenwald (rule engine Kotlin) e la formula semplificata di agent.py. [^src: raw/09_agent_py_method_analysis.md §2.2]

[^web: What is Owner Earnings? (The Warren Buffett Guide) — Old School Value — https://www.oldschoolvalue.com/what-is-owner-earnings/]

### Discount Rate Buffett-Style (Risk-Free Only)

Buffett ha storicamente usato il rendimento del Treasury 10Y (risk-free rate) come tasso di sconto per i business che considera "certi come un bond" — business con moat forte, cash flow prevedibili su 10 anni, bassa ciclicita'. La logica: se un business e' abbastanza prevedibile, non merita un premio rischio azionario aggiuntivo rispetto al tasso risk-free.

In agent.py v2.6.1 questo approccio e' implementato come `r = 0.045` (4.5%, corrispondente al Treasury 10Y 2024-2026), con la giustificazione che lo screener pre-filtra severamente su ROE>15%, D/E<0.5, settori Buffett-approvati. [^src: raw/agent.py:1792] [^src: raw/09_agent_py_method_analysis.md §2.1]

**Implicazione per la WebApp**: questa scelta e' difendibile solo in combinazione con un pre-screening severo. Il rule engine Kotlin usa 9.5% (WACC standard) come default piu' conservativo e universalmente applicabile. Vedi [[dcf-discount-rate-policy]] per l'analisi completa e la raccomandazione per EP-011.

### Sector Blacklist — Cerchio di Competenza Operativizzato

Agent.py implementa il "cerchio di competenza" di Buffett come blacklist esplicita di settori da escludere (biotech, mining, airlines, tobacco, gambling, SPAC, crypto). Buffett stesso ha dichiarato di non investire in settori dove non puo' prevedere la traiettoria a 10 anni — e ha venduto tutte le posizioni aeree nel 2020 citando l'imprevedibilita' post-COVID. [^src: raw/09_agent_py_method_analysis.md §2.4]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
