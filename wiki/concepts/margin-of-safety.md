---
type: concept
sources: ["raw/01_Principi_Fondamentali_Value_Investing.md", "raw/05_Analisi_10K_10Q_e_Regole_Buffett.md"]
status: draft
created: 2026-05-20
updated: 2026-05-21
tags: [value-investing, margin-of-safety, intrinsic-value, graham, buffett]
---
# Margine di Sicurezza (Margin of Safety)

> Il concetto cardinale del value investing: acquistare un'attivita' a un prezzo significativamente inferiore al suo valore intrinseco calcolato, per assorbire errori di stima e imprevisti.

## Contesto

Il Margine di Sicurezza e' la differenza quantitativa tra il valore intrinseco di un'azienda e il suo prezzo di mercato corrente. E' il pilastro difensivo che distingue l'investimento dalla speculazione nel framework di Benjamin Graham. [^src: raw/01_Principi_Fondamentali_Value_Investing.md §3. Il Margine di Sicurezza (Margin of Safety)]

## Dettaglio

### Definizione operativa

Il margine di sicurezza agisce come cuscinetto contro tre tipi di rischio: [^src: raw/05_Analisi_10K_10Q_e_Regole_Buffett.md §4. Il Margine di Sicurezza (Margin of Safety)]

1. **Errori di stima** nei modelli di valutazione (imprecisione del DCF).
2. **Imprevisti aziendali** non previsti nell'analisi fondamentale.
3. **Cigni neri macroeconomici** e fluttuazioni irrazionali di mercato.

### Implementazione Buffett

Warren Buffett stima gli Owner Earnings (forma modificata del Free Cash Flow), li attualizza con un modello DCF e richiede uno sconto finale minimo: [^src: raw/05_Analisi_10K_10Q_e_Regole_Buffett.md §4. Il Margine di Sicurezza (Margin of Safety)]

- **25-30%** per business robusti e prevedibili.
- **Superiore** per aziende cicliche o con minore visibilita' sui flussi.

L'esempio pratico: "comprare un dollaro pagandolo 60-70 centesimi".

### Relazione con Mr. Market

L'investitore intelligente sfrutta l'irrazionalita' di [[mr-market]] per costruire posizioni con ampio margine di sicurezza, comprando nei momenti di pessimismo eccessivo. [^src: raw/01_Principi_Fondamentali_Value_Investing.md §2. L'Allegoria di Mr. Market]

### Evoluzione moderna: il Fossato Economico

Il margine di sicurezza non e' solo un calcolo statico. Il value investing moderno richiede che la valutazione quantitativa sia supportata dalla presenza di un [[economic-moat]] durevole, che protegge i margini nel tempo e rende il valore intrinseco piu' stabile. [^src: raw/04_Gestione_Rischio_Psicologia_Integrazione.md §3. L'Evoluzione del Margine di Sicurezza: Il Fossato Economico]

### Rischio inflattivo

L'inflazione erode il potere d'acquisto dei rendimenti obbligazionari, rendendo preferibili azioni con forte pricing power come componente del portafoglio difensivo a lungo termine. [^src: raw/01_Principi_Fondamentali_Value_Investing.md §4. L'Impatto dell'Inflazione]

## Concetti correlati
[[intrinsic-value]]
[[mr-market]]
[[economic-moat]]
[[graham-number]]
[[fmp-key-metrics-ratios]]

## Pagine collegate
[[vi-01-principi-fondamentali]]
[[vi-05-analisi-10k-10q-buffett]]
[[warren-buffett]]
[[benjamin-graham]]

## Aggiornamenti (v2026-05-21)

**Implementazione WebApp:** `MarginOfSafetyEvaluator` confronta il prezzo da `fmp_profile_snapshot` con `dcfIntrinsicValue` prodotto da [[analysis-api-pipeline]].

| Condizione | `mosSignal` |
|------------|-------------|
| `prezzo < 0.70 × DCF` (DCF &gt; 0) | `GREEN` |
| `0.70 × DCF ≤ prezzo < DCF` | `YELLOW` |
| `prezzo ≥ DCF` | `RED` |
| DCF o prezzo assente / non positivi | `NOT_CALCULABLE` |

Costante: `MOS_DISCOUNT_FACTOR = 0.70` (sconto minimo 30% sul valore intrinseco DCF). [^src: design_&_architecture/api/openapi.yaml §RuleEngineResult]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
