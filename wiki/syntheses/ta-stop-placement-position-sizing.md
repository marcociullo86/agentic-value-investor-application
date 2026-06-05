---
id: ta-stop-placement-position-sizing
type: synthesis
title: "Stop Placement e Position Sizing — Come Proteggere il Capitale dopo il Verdetto VI"
status: draft
created: 2026-06-05
updated: 2026-06-05
sources:
  - raw/2026-06-05-alexander-elder-new-trading-living-2014.txt
  - raw/2026-06-05-technical-analysis-financial-markets-1999.txt
tags: [technical-analysis, trading, risk-management, stop-loss, position-sizing, ta-domain]
domain: technical-analysis-trading
---

# Stop Placement e Position Sizing — Come Proteggere il Capitale dopo il Verdetto VI

**Dominio:** Analisi tecnica / trading — layer advisory sul dettaglio titolo. Vedi [[technical-analysis-trading-domain]] per il confine con il dominio value investing dell'applicazione.

Il verdetto value investing dice che un titolo vale più di quello che costa — ma non dice per quanto tempo il mercato impiegherà a riconoscerlo. Questa sintesi documenta come usare i principi TA di Murphy (stop ancorati alla struttura di prezzo) e di Elder (position sizing basato sul rischio) per limitare il danno se la tesi fondamentale si rivela corretta ma il timing è sbagliato — il caso COPART.

---

## Il caso motivante: COPART

L'applicazione ha selezionato COPART (CPRT) come buona scelta value investing: verdetto fondamentale positivo (regole Buffett/Graham superate), margine di sicurezza presente, moat riconosciuto (leader mondiale nel remarketing veicoli usati). L'investimento è andato in perdita ed è stato chiuso da uno stop loss.

Diagnosi dell'utente: **il problema era nel timing di ingresso, non nella selezione del titolo**. Il verdetto fondamentale era corretto; l'entry era avvenuta in un momento di trend sfavorevole (o ipercomprato a breve termine), con uno stop posizionato in modo non strutturale — probabilmente a percentuale arbitraria invece che ancorato alla struttura tecnica.

Questa diagnosi definisce il problema che questa sintesi vuole formalizzare: **dove mettere lo stop e come dimensionare la posizione** in modo che uno stop prematuramente colpito non distrugga la tesi fondamentale.

---

## Principio 1: lo stop risponde alla struttura, non alla percentuale

Elder lo enuncia con chiarezza: [^src: raw/2026-06-05-alexander-elder-new-trading-living-2014.txt §54. How to Set Stops]

> "Before you enter a trade, write down three numbers: the entry, the target, and the stop. Placing a trade without defining these three numbers is gambling."

La logica fondamentale è che lo stop deve essere posizionato **dove il trade è "sbagliato" per ragioni tecniche** — non dove la matematica del 2% Rule lo richiederebbe. La 2% Rule determina la **size** del trade data la distanza dallo stop; non determina la posizione dello stop. [^src: raw/2026-06-05-alexander-elder-new-trading-living-2014.txt §54. How to Set Stops]

Murphy formalizza i livelli strutturali a cui ancorare lo stop:

### Stop sotto il support (per i long)

In un uptrend, il support identifica i livelli dove il mercato ha dimostrato buying interest sufficiente a far rimbalzare i prezzi. Se il prezzo scende **decisivamente sotto un livello di support**, il mercato sta dicendo qualcosa che l'analisi fondamentale non ha ancora catturato. [^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 82]

**Regola:** Stop sotto il support identificato più vicino al livello di entry. Se il support è a $45 e si entra a $48, lo stop va a $44.50 (leggermente sotto il livello di support per evitare il noise). Non a $45.60 (arbitrario) né a "entry meno 5%" (percentuale senza radice strutturale).

### Livelli di support utilizzabili per lo stop

| Tipo di livello | Fonte | Uso come stop |
|---|---|---|
| Precedente swing low | Grafico giornaliero/settimanale | Stop classico: sotto il minimo più recente rilevante |
| SMA200 (LongTermTrendBadge) | Pagina dettaglio app | Stop medio-lungo: se il prezzo chiude stabilmente sotto SMA200, la tesi di trend cambia |
| Retracement 66% del move precedente | Murphy §Page 85 | Stop al massimo retracement tollerabile in uptrend |
| Precedente area di support/resistance | Role reversal Murphy §Page 82 | La vecchia resistance diventata support: stop sotto di essa |
| Trendline uptrend | Murphy §Page 65 | Violazione della trendline come segnale di stop o riduzione |

[^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 82]
[^src: raw/2026-06-05-technical-analysis-financial-markets-1999.txt §Page 85]

---

## Principio 2: la distanza dello stop determina la size della posizione (2% Rule)

Una volta che lo stop è ancorato alla struttura, Elder introduce la relazione inversa fondamentale: **più lo stop è lontano, più piccola deve essere la posizione**. [^src: raw/2026-06-05-alexander-elder-new-trading-living-2014.txt §50. The Two Percent Rule]

### La formula

```
Rischio massimo accettabile = Equity dell'account × 2%
Stop distance = Entry price − Stop price
Numero massimo di azioni = Rischio massimo / Stop distance
```

**Esempio con COPART (ipotetico):**

| Parametro | Valore |
|---|---|
| Equity account | €50.000 |
| Max rischio (2%) | €1.000 |
| Entry price CPRT | €80,00 |
| Support identificato | €74,00 |
| Stop price (sotto support) | €73,50 |
| Stop distance | €80,00 − €73,50 = €6,50 per azione |
| Azioni max | €1.000 / €6,50 = **153 azioni** (€12.240 investiti) |

Se invece si fosse usato uno stop arbitrario a "entry meno 3%" (€77,60), la stop distance sarebbe €2,40 e si sarebbero comprate 416 azioni — quasi il triplo dell'esposizione. Una perdita dello stesso 8% del titolo con uno stop non strutturale è matematicamente più distruttiva rispetto a una perdita dello stesso importo in euro gestita con stop strutturale e size ridotta.

**La legge di Elder:** quando lo stop strutturalmente corretto è molto lontano dall'entry (titolo volatile, range ampio), il rispetto del 2% Rule impone una posizione molto piccola. Questo non è un fallimento del sistema — è il sistema che funziona. Se la size risultante è troppo piccola per avere senso, il trade non va fatto finché non si trova un entry migliore (pullback più vicino al support).

[^src: raw/2026-06-05-alexander-elder-new-trading-living-2014.txt §50. The Two Percent Rule]

---

## Principio 3: la 6% Rule come limite mensile di drawdown

La 2% Rule protegge dal singolo trade catastrofico. La 6% Rule protegge da una serie di trade perdenti nello stesso mese: [^src: raw/2026-06-05-alexander-elder-new-trading-living-2014.txt §51. The Six Percent Rule]

> "If the equity in your trading account falls more than 6% below its level at the beginning of the month, stop trading for the rest of the month."

Se si perdono tre trade consecutivi (ciascuno al 2%), qualcosa non funziona:
- Il timing è sistematicamente sbagliato (condizioni di mercato avverse)
- O lo stato emotivo è disfunzionale (revenge trading)

La pausa è obbligatoria per analisi, non punizione. Per l'investitore value, questa regola si traduce in: se il mercato sta respingendo tutte le entry nonostante verdetti fondamentali positivi, è possibile che si stia operando contro un trend primario forte (macroeconomico, settoriale) che il verdetto fondamentale non cattura a breve termine.

---

## Il caso COPART: ricostruzione con principi TA

Applicando retroattivamente i principi documentati:

### Dove lo stop avrebbe dovuto essere

Uno stop ancorato a struttura avrebbe usato uno dei livelli seguenti (da verificare su grafico storico CPRT):

1. **Sotto il precedente swing low** sul timeframe settimanale: il livello più recente che il mercato aveva già testato come support. Questo level riflette l'equilibrio di mercato — scendere sotto di esso segnala che l'equilibrio è cambiato.

2. **Sotto la SMA200** (ora visibile come `LongTermTrendBadge`): se CPRT era entrato in `Below trend` prima che lo stop fosse colpito, era un segnale tecnico di deterioramento del trend primario.

3. **Al retracement del 66% del rally precedente**: violazione del 66% significa che il mercato ha cancellato più di due terzi del move — una reversal tecnica riconosciuta, non rumore.

### Come uno stop strutturale avrebbe cambiato l'esito

Due scenari opposti:

**Scenario A — Stop strutturale più lontano, size più piccola:**
Se il support era a -10% dal livello di entry, la 2% Rule avrebbe imposto una posizione al 20% del normale. L'impatto economico dello stop colpito sarebbe stato il 2% dell'account — non la perdita full-position che si è verificata. La tesi fondamentale su COPART (corretta nel lungo periodo) avrebbe potuto essere ri-espressa con un'entry successiva migliore.

**Scenario B — Stop strutturale più stretto, entry migliorata:**
Se lo stop fosse stato ancorato a un support vicino (swing low recente a -5%), la posizione standard avrebbe avuto un rapporto rischio/reward più favorevole. L'Impulse System di Elder avrebbe potuto segnalare "barra rossa" (downtrend accelerante) prima dello stop strutturale, suggerendo di ridurre la posizione preventivamente.

**La lezione:** il problema non era solo dove stava lo stop, ma che lo stop non aveva una radice strutturale — il mercato non aveva "ragione tecnica" di rimbalzare da quel livello perché il livello stesso era arbitrario.

---

## La relazione tra ampiezza dello stop e size della posizione: tabella

| Stop distance (% dal entry) | Shares max con 2% Rule su €50k | Nota |
|---|---|---|
| 2% | 500 | Stop strettissimo — solo in trend molto ordinati |
| 5% | 200 | Stop medio — swing low recente |
| 10% | 100 | Stop largo — support significativo |
| 15% | 67 | Stop molto largo — valore vicino a SMA200 in forte downtrend |
| 20% | 50 | Stop estremo — considerare di non aprire la posizione |

La tabella mostra che uno stop "too tight" (arbitrario al 2-3%) con una size piena è più pericoloso di uno stop strutturale al 10% con una size ridotta. Il primo viene colpito da rumore di mercato; il secondo richiede una vera rottura strutturale.

---

## La volatilita come componente implicita

Murphy e Elder convergono su un punto: la volatilità del titolo deve entrare nel calcolo dello stop. Un titolo con range giornaliero dell'1% richiede uno stop diverso da un titolo con range del 3%, anche se entrambi hanno lo stesso livello di support. [^src: raw/2026-06-05-alexander-elder-new-trading-living-2014.txt §54. How to Set Stops]

Regola pratica derivata: lo stop non deve essere più stretto di 1-2 Average True Range (ATR) sotto il support. Se il support è a $74 e l'ATR giornaliero è $2, lo stop va a $72 (non a $73.50) — altrimenti il normale rumore giornaliero lo farà scattare prima che la struttura sia effettivamente violata.

L'ATR non è calcolato dall'app (sarebbe un'estensione futura del layer advisory), ma è disponibile su qualunque piattaforma TA.

---

## Il Businessman's Risk: la distinzione concettuale di Elder

Elder separa due categorie: [^src: raw/2026-06-05-alexander-elder-new-trading-living-2014.txt §48. Emotions and Probabilities]

- **Businessman's risk**: perdita predefinita, entro la 2% Rule, accettata senza stress come costo del fare business.
- **Loss**: perdita che supera i limiti predefiniti, che minaccia la sopravvivenza dell'account.

Per l'investitore value, questa distinzione si traduce in: uno stop colpito su COPART non è una "perdita" nel senso devastante — è un businessman's risk se era stato dimensionato correttamente (max 2% dell'account). La "perdita" vera sarebbe stata una posizione full-size senza stop strutturale, dove la tesi fondamentale corretta porta comunque a un danno economico significativo nel percorso verso la convergenza al valore intrinseco.

---

## Sintesi: checklist stop + sizing prima dell'acquisto

Dopo aver verificato i due badge advisory (timing entry, vedi [[ta-entry-timing-stock-detail]]):

1. **Identificare il livello di support più vicino e significativo:** swing low recente, SMA200, retracement 66%, role reversal.
2. **Calcolare la stop distance:** entry price − stop price (non una percentuale arbitraria).
3. **Calcolare la size massima:** (equity × 2%) / stop distance.
4. **Verificare il reward/risk ratio:** target (livello di resistance/valore intrinseco) / rischio (stop distance). Minimo 2:1 per procedere.
5. **Documentare:** entry, stop, target, ragione prima di eseguire l'ordine.
6. **Monitorare la 6% Rule mensile:** se si sono persi tre trade consecutivi, fare pausa e analisi.

---

## Relazione con altri concetti wiki

- [[technical-analysis-trading-domain]] — confine di dominio TA/VI
- [[ta-entry-timing-stock-detail]] — la pagina complementare: quando entrare (prerequisito per questa)
- [[ta-vs-vi-decision-layer]] — il layer TA come complemento advisory al verdetto fondamentale
- [[elder-risk-management-2pct-6pct]] — source primario: 2% Rule, 6% Rule, Iron Triangle, stop placement
- [[elder-triple-screen-impulse-system]] — il sistema di segnali che precede il sizing
- [[elder-trading-psychology]] — psicologia: perché non si rispettano le regole di stop
- [[trend-trendlines-support-resistance]] — support/resistance come struttura per lo stop placement (Murphy)
- [[oscillators-momentum-rsi]] — RSI come conferma del momentum prima di stringere o allargare lo stop
- [[volume-open-interest]] — volume come conferma della validita di una violazione di support
- [[chart-patterns-reversal-continuation]] — i pattern Murphy come segnali di allerta per spostare lo stop a breakeven
- [[margin-of-safety]] — il concetto VI analogo: buffer qualitativo vs stop meccanico quantitativo

---

## Storie collegate

<!-- Sezione proprieta PM — non modificare -->

- [EP-024](../../management/kanban/EP-024-riepilogo-e-technical-analysis-tab/EP-024.md) — Tab Technical Analysis + Tab Riepilogo (motivata dal caso COPART qui documentato)
- [US-100](../../management/kanban/EP-024-riepilogo-e-technical-analysis-tab/US-100-stop-placement-position-sizing-be/US-100.md) — BE Stop-placement (SUPPORT_BASED/SMA200_BASED/ATR_BASED) + position-sizing 2%/6% Rule
- [US-101](../../management/kanban/EP-024-riepilogo-e-technical-analysis-tab/US-101-tab-technical-analysis-fe/US-101.md) — FE Pannello stop + position-sizing (consumo visuale)
- [US-102](../../management/kanban/EP-024-riepilogo-e-technical-analysis-tab/US-102-qa-technical-analysis-e2e/US-102.md) — QA Fixture CPRT come test-anchor del caso COPART
