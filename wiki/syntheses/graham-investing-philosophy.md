---
type: synthesis
sources: ["raw/investitore intelligente.txt", "raw/01_Principi_Fondamentali_Value_Investing.md", "raw/02_L_Investitore_Difensivo_vs_Intraprendente.md", "raw/03_Analisi_Fondamentale_e_Valutazione.md", "raw/05_Analisi_10K_10Q_e_Regole_Buffett.md"]
status: draft
created: 2026-05-22
updated: 2026-05-22
tags: [value-investing, graham, philosophy, synthesis, framework, buffett, margin-of-safety, mr-market, superinvestors, vi-domain]
domain: value-investing
---
# Filosofia di Investimento di Benjamin Graham — Sintesi Cross-Domain

> Sintesi integrata del framework Graham: dalla definizione canonica di investimento ai criteri operativi, passando per l'evidenza empirica dei Superinvestitori e la genealogia del value investing moderno (Buffett, Munger).

## Il Framework in Cinque Strati

### Strato 1 — La Definizione Fondante

Il sistema di Graham poggia su una definizione operativa di investimento che esclude programmaticamente la speculazione:

> "Un'operazione di investimento e' un'attivita' che, dopo un'analisi approfondita, promette la sicurezza del capitale e un rendimento adeguato." [^src: raw/investitore intelligente.txt §Cap.1]

Le tre condizioni (analisi, sicurezza, rendimento) non sono intercambiabili. Mancarne una transforma l'operazione in speculazione. Vedi [[investment-vs-speculation]].

### Strato 2 — La Psicologia dell'Investitore

La parabola di Mr. Market (Cap.8) stabilisce il rapporto corretto tra investitore e mercato: il mercato e' uno strumento di servizio, non un oracolo da seguire. Le fluttuazioni di prezzo non misurano il valore del business — lo quotano irrazionalmente.

Il corollario: il rischio reale non e' la volatilita' del prezzo ma la perdita permanente di capitale. Un'azienda eccellente che scende del 30% e' diventata piu' economica, non piu' rischiosa. Vedi [[mr-market]] e [[market-fluctuations-graham]].

### Strato 3 — I Due Profili Operativi

Graham divide il mondo in due profili con strategie distinte:

| Dimensione | Difensivo | Intraprendente |
|---|---|---|
| Tempo dedicato | Minimo (passivo) | Alto (attivo) |
| Strumento principale | ETF + 7 criteri meccanici | Analisi fondamentale + ricerca attiva |
| Obiettivo rendimento | Mercato - commissioni (ma con sicurezza) | Mercato + alpha (ma con disciplina) |
| Criteri selezione titoli | 7 filtri Cap.14 ([[seven-criteria-defensive-stock-selection]]) | Criteri Cap.15 + net-net + situazioni speciali |
| Rivalutazione | Annuale | Continua |

Vedi [[defensive-vs-enterprising-investor]].

### Strato 4 — I Criteri Quantitativi

Il cuore operativo del framework e' la griglia dei 7 criteri difensivi (Cap.14). Graham li chiama "standard minimi" — non suggerimenti ma filtri binari non negoziabili.

| Criterio | Soglia canonica | Proxy moderno |
|---|---|---|
| Dimensioni | Fatturato ≥ $100M | Esclude micro-cap |
| Current Ratio | ≥ 2:1 | `CURRENT_RATIO_LATEST` ≥ 2 |
| Stabilita' utili | Utili positivi 10 anni | `ROE_10Y_AVG` / `NET_MARGIN_10Y_AVG` costanti |
| Dividendi | 20 anni ininterrotti | Non implementato nel Rule Engine MVP |
| Crescita EPS | ≥ +33% in 10 anni | Implicito in `ROE_10Y_AVG` / `ROIC_10Y_AVG` trend |
| P/E | ≤ 15 (media 3 anni) | `grahamNumber` come soglia prezzo |
| P/B | ≤ 1.5; P/E × P/B ≤ 22.5 | `grahamNumber` incorpora BVPS |

Vedi [[seven-criteria-defensive-stock-selection]] e [[graham-number]].

### Strato 5 — Il Margine di Sicurezza

Il [[margin-of-safety]] e' il principio unificatore: acquistare sempre con uno sconto significativo sul valore intrinseco calcolato. Non e' un parametro da ottimizzare ma un requisito non negoziabile. Graham conclude il libro (Cap.20) con questa affermazione:

> "Il concetto di margine di sicurezza diventa, in fin dei conti, la pietra angolare della filosofia di investimento." [^src: raw/investitore intelligente.txt §Cap.20 — Il Margine di Sicurezza come Concetto Centrale]

## L'Evidenza Empirica: Doddsville

I nove fondi dell'Appendice 1 ([[superinvestors-graham-doddsville]]) dimostrano che il framework Graham, applicato con disciplina indipendente, ha prodotto rendimenti superiori sistematici su periodi da 13 a 28 anni. Dati chiave:

| Metrica | Valore |
|---|---|
| Graham-Newman Corp (1936-1956) | 14.7% annuo vs 12.2% mercato |
| Walter Schloss (28 anni) | 21.3% lordo vs 8.4% S&P |
| Buffett Partnership (13 anni) | 29.5% lordo vs 7.4% Dow |
| Sequoia Fund (14 anni) | 17.2% vs 10.0% S&P |

La prova e' anti-EMH (Efficient Market Hypothesis): se i mercati fossero efficienti, questi risultati non potrebbero essere sistematici su 9 fondi indipendenti con la stessa origine intellettuale.

## Genealogia: Da Graham a Buffett

```
Security Analysis (1934) — Graham & Dodd
        |
L'Investitore Intelligente (1949, 1973)
        |
Graham-Newman Corp (1936-1956) — 14.7%/yr
        |
        +----> Walter Schloss — puro Graham quantitativo
        +----> Bill Ruane — Sequoia Fund
        +----> Warren Buffett (1954-1956 apprendistato)
                    |
                    +----> (1956-1969) Buffett Partnership — Graham puro
                    +----> (1970+) Berkshire Hathaway — Graham + Munger
                                |
                                Charlie Munger — "Business eccellenti a prezzi equi"
                                        |
                                        Evoluzione: [[economic-moat]] + [[warren-buffett]] framework
```

## Il Passaggio Critico: Graham → Buffett

La differenza fondamentale tra il Graham operativo e il Buffett maturo:

| Dimensione | Graham (puro) | Buffett (post-1969) |
|---|---|---|
| Tipo di business ideale | Qualsiasi, se abbastanza economico | Eccellente, con moat durevole |
| Prezzo ideale | Molto sotto il valore (net-net) | Ragionevole sul valore (non necessariamente stracciato) |
| Orizzonte | Breve-medio | Idealmente "per sempre" |
| Concentrazione | Alta diversificazione meccanica | Concentrata sulle migliori idee |
| Valutazione management | Non determinante | Determinante |

Buffett stesso dichiarero': "E' meglio comprare un'azienda meravigliosa a un prezzo equo che un'azienda mediocre a un prezzo meraviglioso."

## Applicazione alla WebApp Value Investing

Il [[value-investing-rule-engine]] implementa una sintesi dei due framework:

- **Screening Graham**: Current Ratio, Debt/Income, stabilita' utili (ROE/ROIC/Margin costanti 10 anni).
- **Qualita' Buffett**: Gross Margin > 40% (pricing power), CapEx Intensity < 25% (basso fabbisogno di capitale).
- **Valutazione ibrida**: Graham Number (approccio Graham meccanico) + DCF Owner Earnings (approccio Buffett).
- **Soglia acquisto**: prezzo < 70% valore intrinseco DCF (MoS 30% minimo, allineato alla soglia Buffett 25-30%).

## Concetti correlati
[[investment-vs-speculation]]
[[mr-market]]
[[market-fluctuations-graham]]
[[margin-of-safety]]
[[seven-criteria-defensive-stock-selection]]
[[net-net-stocks]]
[[superinvestors-graham-doddsville]]
[[graham-number]]
[[intrinsic-value]]
[[economic-moat]]
[[value-investing-rule-engine]]

## Pagine collegate
[[intelligent-investor]]
[[benjamin-graham]]
[[warren-buffett]]
[[value-investing-fmp-integration]]
[[webapp-value-investing-spec]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
