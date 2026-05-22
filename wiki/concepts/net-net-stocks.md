---
type: concept
sources: ["raw/investitore intelligente.txt"]
status: draft
created: 2026-05-22
updated: 2026-05-22
tags: [value-investing, graham, net-net, working-capital, cigar-butt, liquidation-value, enterprising-investor]
---
# Net-Net Stocks (Azioni sotto il Net Current Asset Value)

> La strategia piu' conservativa e piu' meccanica di Graham: comprare azioni a un prezzo inferiore ai 2/3 del capitale circolante netto per azione, assumendo che le immobilizzazioni non valgano nulla. Graham la chiama la forma piu' semplice e provata di value investing.

## Contesto

Il criterio net-net e' sviluppato nei Capitoli 7 e 15 de L'Investitore Intelligente e in "Security Analysis" (1934). E' la tecnica con cui Graham-Newman Corp. ottenne i suoi rendimenti storici piu' elevati nel periodo 1936-1956. Buffett lo chiama l'"investimento cigar-butt" (mozzicone di sigaro): si raccoglie un mozzicone che sembra orribile ma ha ancora un'ultima tirata gratis. [^src: raw/investitore intelligente.txt §Cap.7 — Politica per l'Investitore Intraprendente: Aspetti Positivi]

## Definizione

### Net Current Asset Value (NCAV)

```
NCAV = Attivita' Correnti Totali - Passivita' Totali (correnti + non correnti)
NCAV per azione = NCAV / Numero di Azioni in Circolazione
```

**Criterio di acquisto**: prezzo di mercato inferiore a 2/3 del NCAV per azione.

```
Prezzo < (2/3) × NCAV per azione
```

Il ragionamento: se si potesse liquidare immediatamente l'azienda e realizzare il 100% delle attivita' correnti (cassa, crediti, magazzino) e pagare tutte le passivita', si recupererebbe il NCAV. Acquistare a 2/3 del NCAV significa ottenere le immobilizzazioni e l'avviamento gratis con un margine di sicurezza del 33%. [^src: raw/investitore intelligente.txt §Cap.15 — Selezione Titoli per l'Investitore Intraprendente]

### Citazione Canonica di Graham

> "E' sempre sembrato, e sembra ancora, assurdamente semplice dire che se si puo' comprare un gruppo diversificato di azioni ordinarie a un prezzo inferiore agli attivi correnti netti — dedotti tutti i diritti preesistenti e assumendo pari a zero le immobilizzazioni e gli altri attivi — i risultati dovrebbero essere piuttosto soddisfacenti." [^src: raw/investitore intelligente.txt §Cap.20 — Il Margine di Sicurezza come Concetto Centrale]

## Strategia Operativa

### Diversificazione Obbligatoria

Le net-net stocks sono spesso aziende in difficolta', settori in declino, o business dimenticati dal mercato. Singolarmente, alcune falliranno. Graham raccomanda esplicitamente una **lista di 30+ titoli** diversificati: come gruppo, il portafoglio net-net storicamente ha prodotto rendimenti superiori alla media anche con un tasso di fallimenti non trascurabile. [^src: raw/investitore intelligente.txt §Cap.15 — Selezione Titoli per l'Investitore Intraprendente]

### Criteri Aggiuntivi (Intraprendente)

Oltre al criterio net-net, Graham aggiunge per il portafoglio dell'intraprendente:

- **Current Ratio ≥ 1.5**: liquidita' minima accettabile.
- **Nessuna perdita negli ultimi cinque anni**: stabilita' minima degli utili.
- **Dividendo presente**: segnale di generazione di cassa reale.
- **P/E ≤ 9**: valutazione estremamente conservativa.
- **Prezzo ≤ 120% degli attivi netti tangibili**: ulteriore vincolo sul P/B.
- **Debito ≤ 110% del net current asset value**: verifica che il debito non superi il valore di liquidazione.

[^src: raw/investitore intelligente.txt §Cap.15 — Selezione Titoli per l'Investitore Intraprendente]

## Rarita' Attuale

Nelle fasi normali di mercato (indici elevati, 2010-2026), le azioni sotto il NCAV sono estremamente rare nei mercati sviluppati (US, Europa). Si trovano piu' facilmente:
- Nei mercati emergenti (Japan small caps negli anni '90; Corea, Vietnam oggi).
- In settori colpiti da shock settoriali improvvisi.
- Nelle fasi di crisi sistemica (2008-2009, COVID-2020).

Graham stesso riconosce che nelle fasi di mercato caro le net-net scompaiono quasi del tutto, rendendo questa strategia piu' teorica che pratica per l'intraprendente odierno.

## Differenza con il Metodo Buffett

| Dimensione | Net-Net Graham | Buffett (Moat + Qualita') |
|---|---|---|
| Focus | Valore di liquidazione | Valore del franchise/moat |
| Tipo di business | Mediocre ma economico | Eccellente a prezzo equo |
| Orizzonte | Breve-medio (catalizzatore atteso) | Lungo (hold forever) |
| Diversificazione | Alta (30+ posizioni) | Concentrata (migliori idee) |
| Disponibilita' oggi | Molto bassa nei mercati sviluppati | Alta in qualsiasi mercato |

Munger persuase Buffett ad abbandonare il puro net-net in favore delle aziende con [[economic-moat]] durevole a prezzi ragionevoli — il cosiddetto passaggio dal "buon affare" al "business eccellente". [^src: raw/investitore intelligente.txt §Appendice 1 — I Superinvestitori di Graham-and-Doddsville]

## Relazione con il Rule Engine

Il [[value-investing-rule-engine]] della WebApp non implementa il criterio net-net (troppo raro, difficile da automatizzare con i soli dati FMP). Il NCAV richiederebbe:
- `totalCurrentAssets` dal Balance Sheet.
- `totalLiabilities` (correnti + non correnti) dal Balance Sheet.
- `sharesOutstanding` dal Key Metrics o Profile.

Questi dati sono disponibili via [[fmp-financial-statements-stable]], ma non esiste una regola dedicata nel MVP. E' un candidato per un'estensione futura (gap aperto: vedere [[gaps]]).

## Concetti correlati
[[margin-of-safety]]
[[graham-number]]
[[seven-criteria-defensive-stock-selection]]
[[defensive-vs-enterprising-investor]]
[[economic-moat]]

## Pagine collegate
[[intelligent-investor]]
[[benjamin-graham]]
[[enterprising-investor-checklist]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
