---
id: elder-risk-management-2pct-6pct
type: concept
title: "Risk Management del Trader (Elder) — 2% Rule, 6% Rule, Iron Triangle"
status: draft
created: 2026-06-05
updated: 2026-06-05
sources:
  - raw/2026-06-05-alexander-elder-new-trading-living-2014.txt
tags: [technical-analysis, trading, risk-management, position-sizing, ta-domain]
domain: technical-analysis-trading
---

# Risk Management del Trader — Alexander Elder

**Dominio:** Analisi tecnica / trading attivo. Vedi [[technical-analysis-trading-domain]] per il confine con il dominio value investing dell'applicazione.

**Avviso**: le regole di risk management descritte qui (2% rule con stop meccanici) sono specifiche del trading attivo. Il value investor usa il margine di sicurezza come buffer contro le perdite permanenti, NON stop-loss meccanici su variazioni di prezzo a breve termine.

---

## Premessa: perche il risk management e il fattore piu negletto

Revisione di qualsiasi account di un trader perdente: "a single terrible loss or a short string of bad losses did most of the damage." Se il trader avesse tagliato le perdite prima, il saldo sarebbe molto piu alto.

Due errori fatali:
1. **Trading senza stop**: espone a perdite illimitate
2. **Posizioni troppo grandi per l'account**: una corrente di mercato modesta e sufficiente a distruggere l'equity ("too large a sail on a small boat")

I mercati uccidono in due modi:
- **Shark bite**: singola perdita catastrofica
- **Piranhas**: serie di piccole perdite ravvicinate che erodono l'account

La 2% Rule difende dagli shark bite; la 6% Rule dalle piranhas.

[^src: raw/2026-06-05-alexander-elder-new-trading-living-2014.txt §49. The Two Main Rules of Risk Control]

---

## La 2% Rule

**Definizione**: non rischiare mai piu del 2% dell'equity totale dell'account su un singolo trade.

Il 2% NON e la dimensione del trade — e la **perdita massima accettabile** se lo stop viene colpito.

### Formula di position sizing

```
Max rischio in $ = Equity account × 0.02
Shares = Max rischio in $ / (Entry price - Stop price)
```

**Esempio:**
- Account: $50,000
- Max rischio: $50,000 × 0.02 = $1,000
- Stock: entry $40, stop $38 → rischio per share = $2
- Shares max: $1,000 / $2 = 500 shares

Il trader puo comprare meno di 500 shares; non puo comprare di piu.

### Perche il 2% e il numero giusto

Un trader che perde il 25% dell'account in un singolo trade ("shark bite") deve generare un +33% per tornare al break-even — obiettivo quasi impossibile emotivamente e matematicamente. Con la 2% Rule, anche 10 perdite consecutive consecutive riducono l'account solo del ~18% (compound), lasciando sufficiente capitale e morale per continuare.

> "A poor beginner who loses a quarter of his equity in a single trade is like a swimmer who just lost an arm or a leg to a shark and is bleeding into the water."

### Variazioni per trader esperti

- Trader con track record solido possono aumentare a 1.5% o al massimo 2% per trade ad alta conviction
- Trader nuovi o in drawdown: scendere a 1% o 0.5% per ridurre l'esposizione emotiva
- Non va mai aumentato oltre il 2%, mai

[^src: raw/2026-06-05-alexander-elder-new-trading-living-2014.txt §50. The Two Percent Rule]

---

## La 6% Rule

**Definizione**: se l'equity totale dell'account scende del 6% in un mese (calcolato dall'inizio del mese), fermare immediatamente tutta l'attivita di trading per il resto del mese.

### Come calcolare il drawdown mensile

- Registrare l'equity dell'account il primo del mese
- Se l'equity scende del 6% rispetto a quella data → stop immediato, nessun nuovo trade fino al primo del mese successivo
- Le posizioni gia aperte possono rimanere (gestirle), ma nessun nuovo ingresso

### Perche il 6% e il numero giusto

Tre trade da 2% ciascuno, tutti persi → drawdown del 6% (leggermente meno per l'effetto compound). Se si perdono tre trade consecutivi, qualcosa non funziona:
- Il sistema non e in sintonia con le condizioni di mercato correnti
- Il trader e in uno stato emotivo disfunzionale
- Entrambe le cose

La pausa obbligatoria serve per analisi, non per frustrazione.

> "Losing three in a row is a serious signal that you need a vacation. Stop trading, step back, and analyze."

### Interazione tra 2% Rule e 6% Rule

Se si e perso il 4% del mese su due trade:
- Il terzo trade non puo rischiare piu del 2% standard (regola sempre attiva)
- Ma se il terzo trade perde il 2% → si e al 6% → stop mensile

Il vantaggio: la 6% Rule con trade da 2% massimo garantisce almeno due trade positivi prima di arrivare al limite (se i trade perdenti sono 3 su 3, il sistema non funziona in queste condizioni).

[^src: raw/2026-06-05-alexander-elder-new-trading-living-2014.txt §51. The Six Percent Rule]

---

## Businessman's Risk vs Loss

Elder distingue:
- **Businessman's risk**: perdita predefinita, entro i limiti del 2% Rule; normale costo del fare business; accettata senza stress
- **Loss**: perdita che supera i limiti predefiniti; minaccia la salute o sopravvivenza dell'account

> "If you follow risk management rules, you'll accept only a normal businessman's risk. Violating a well-defined red line will expose you to dangerous losses."

Il mercato seduce continuamente i trader a "dare un po' di spazio extra" a un trade perdente. La disciplina e non farlo mai: le regole valgono sempre, non "questa volta e diverso."

[^src: raw/2026-06-05-alexander-elder-new-trading-living-2014.txt §48. Emotions and Probabilities]

---

## Iron Triangle of Risk Control

Le tre componenti del triangolo di ferro:
1. **2% Rule**: protezione dal singolo trade catastrofico
2. **6% Rule**: protezione dalla serie di perdite mensile
3. **Disciplina** nel seguire le regole: senza disciplina, le prime due sono inutili

Il triangolo e "di ferro" perche non si piega: nessuna eccezione, nessun "questa volta e speciale."

---

## Come tornare da un drawdown

**Regola generale**: se l'account scende del 6% nel mese, ridurre la size dei trade nel mese successivo.

**Protocollo di recovery:**
- Drawdown 6-10%: ridurre la size dei trade al 50% del normale
- Drawdown 10-15%: ridurre al 25% del normale; revisione approfondita del sistema
- Drawdown >15%: fermare il trading, analisi completa, considera di paper-trade per un periodo

La recovery richiede rendimenti progressivamente maggiori per tornare al break-even:
- Perdita 10% → serve +11% per pareggiare
- Perdita 20% → serve +25%
- Perdita 50% → serve +100%

> "Never put yourself in the position where you need to double your account to return to its previous level."

[^src: raw/2026-06-05-alexander-elder-new-trading-living-2014.txt §52. A Comeback from a Drawdown]

---

## Profit targets e stop placement

### Come impostare i profit targets

**Regola base**: il target deve essere impostato sul timeframe piu lungo (settimanale se si usa settimanale + giornaliero), sulla base di support/resistance e value zone.

**Reward/Risk ratio minimo**: 2:1 (guadagnare almeno il doppio di quanto si rischia). Solo in presenza di segnali eccezionalmente forti e ammissibile scendere sotto questo ratio.

> "Before you enter a trade, write down three numbers: the entry, the target, and the stop. Placing a trade without defining these three numbers is gambling."

### Come impostare gli stop

**Logica base**: lo stop deve essere posizionato dove il trade e "sbagliato" per ragioni tecniche, non dove il 2% Rule lo richiederebbe matematicamente. La 2% Rule determina la SIZE del trade data la distanza dallo stop, non la posizione dello stop.

**Regole pratiche:**
- Stop sotto il recente minor low (per i long) o sopra il recente minor high (per i short)
- Non usare numeri tondi (clustering di stop altrui — l'essere qualche centesimo oltre riduce la probabilita di essere triggerati da noise)
- Stop piu stretti nei trading range (tighter stop = uscita rapida se il range tiene)
- Stop piu ampi nei trend forti (lasciare spazio al trend di respirare)

[^src: raw/2026-06-05-alexander-elder-new-trading-living-2014.txt §53. How to Set Profit Targets]
[^src: raw/2026-06-05-alexander-elder-new-trading-living-2014.txt §54. How to Set Stops]

---

## Relazione con il record-keeping

Il risk management senza record-keeping e cieco. Il diario di trading deve includere per ogni trade:
- Entry/exit price e date
- Stop iniziale e tutti gli aggiustamenti
- Slippage effettivo (confronto prezzo ordine vs fill)
- Commissioni
- Max paper profit e max paper loss before exit
- Ragioni di entry e obiettivi di exit

Questi dati permettono di verificare:
- Se si stanno rispettando le regole 2%/6%
- Se il sistema ha mathematical expectation positiva nel tempo
- Pattern di errori ripetuti (psicologia)

[^src: raw/2026-06-05-alexander-elder-new-trading-living-2014.txt §57. Your Daily Homework]

---

## Collegamento con altri concetti wiki

- [[technical-analysis-trading-domain]] — confine di dominio e nota su MoS vs 2% Rule
- [[elder-trading-psychology]] — radici psicologiche del risk management
- [[elder-triple-screen-impulse-system]] — sistemi di trading che generano i segnali da gestire con queste regole
- [[margin-of-safety]] — concept esistente (dominio value investing): il buffer fondamentale analogo al 2% Rule
- [[elder-new-trading-living-2014]] — sorgente completa
- [[alexander-elder]] — entita autore

---

## Storie collegate

<!-- Sezione proprieta PM — non modificare -->

- [EP-024](../../management/kanban/EP-024-riepilogo-e-technical-analysis-tab/EP-024.md) — Tab Technical Analysis (porta 2%/6% Rule come advisor sul dettaglio ticker)
- [US-100](../../management/kanban/EP-024-riepilogo-e-technical-analysis-tab/US-100-stop-placement-position-sizing-be/US-100.md) — BE Position-sizing 2%-Rule + 6%-Rule + Reward/Risk vs DCF intrinsic value
