---
type: risk-registry
status: draft
created: 2026-06-01
updated: 2026-06-01
append_only: true
---

# Risk Registry — append-only

Output persistente delle invocazioni `/premortem` (pattern Premortem, operazione
opzionale PATTERN §3, v2.16). Ogni run di `/premortem` può — **opt-in, mai in
autonomia** (R.P1) — appendere qui una sezione pre-mortem con il proprio Risk
Registry classificato.

**Natura del file**: **append-only**. Le sezioni esistenti non vengono mai
riscritte né cancellate; si aggiungono solo nuove sezioni in coda. Le revisioni di
un rischio si fanno aggiornando la colonna `Decision` in un nuovo append datato,
non sovrascrivendo la riga storica.

**Write-restriction**: scrivono qui **solo** il PM o l'output di `/premortem`
(skill `premortem-protocol`). Mai un dev-agent o il code-reviewer direttamente.

**Decisione Q1 (ADR-002)**: in v2.16 il registry è un **single-file**. v2.17+
valuterà lo split per-EP se il file cresce oltre **50 sezioni pre-mortem** (soglia
di rivalutazione, non un hard cap).

## Schema canonico — tabella a 9 colonne

Ogni sezione pre-mortem contiene un header di conteggio + una tabella con queste 9
colonne, in quest'ordine:

`# | Risk | Category | Tier | Urgency | Evidence | Mitigation | Owner | Decision`

| Colonna | Significato |
|---|---|
| `#` | Indice progressivo del rischio nella sezione |
| `Risk` | Descrizione sintetica del rischio (1 frase) |
| `Category` | Una delle 5 categorie di failure: `Execution \| External \| People \| Technical \| Assumptions` |
| `Tier` | Classificazione: `Tiger \| Paper Tiger \| Elephant` (vedi sotto) |
| `Urgency` | Solo per i Tiger: `LB` (Launch-Blocking) \| `FF` (Fast-Follow) \| `Track`. Altrimenti `—` |
| `Evidence` | Su cosa si fonda il rischio (dato, precedente, assenza di dato) |
| `Mitigation` | Azione concreta di mitigazione (o `nessuna` se Paper Tiger dismissed) |
| `Owner` | Handle responsabile (`@user`, team) o `—` |
| `Decision` | Stato corrente: `open \| accepted \| mitigated \| dismissed` |

### Tier ammessi (tassonomia Tigers / Paper Tigers / Elephants)

- **Tiger** — rischio reale e affrontabile. Sotto-classificato per urgency:
  - `LB` (**Launch-Blocking**) — blocca il rilascio finché non mitigato.
  - `FF` (**Fast-Follow**) — non blocca il lancio ma va affrontato subito dopo.
  - `Track` — reale ma a bassa urgenza, da monitorare.
- **Paper Tiger** — sembra spaventoso ma all'analisi è gestito/improbabile. Spesso
  `Decision: dismissed` con evidenza del perché non preoccupa.
- **Elephant** — il rischio nella stanza che nessuno nomina: spesso `People` o
  `Assumptions`, alto impatto, scomodo da affrontare. Nominarlo è metà del lavoro.

Vedi `wiki/concepts/risk-classification-tigers-paper-tigers-elephants.md` per la
tassonomia completa.

### Stati di `Decision`

- `open` — rischio identificato, nessuna decisione presa.
- `accepted` — rischio accettato consapevolmente (si procede comunque).
- `mitigated` — mitigazione applicata, rischio ridotto.
- `dismissed` — rischio scartato dopo analisi (tipico dei Paper Tiger).

### Colonna opzionale futura — `Related incidents` (ADR-007)

Una sezione pre-mortem può, opzionalmente, linkare incident materializzati in
`wiki/incidents/` tramite wikilink (`[[incident-slug]]`). Il collegamento è
**bidirezionale suggerito ma mai auto-applicato** (ADR-007): la skill propone il
wikilink in chat, l'umano decide se inserirlo. Non è una colonna obbligatoria
dello schema a 9 colonne.

## Sezioni pre-mortem

<!-- Append output di /premortem qui -->

## Pre-Mortem: v2.16 release (self-premortem)

**Target**: `wiki/concepts/factory-premortem-integration.md` (design doc v2.16) ·
**Timeframe**: 12mo · **Data**: 2026-06-01 · **Invoker**: agent (TSK-018 release gate) ·
**Anchor**: `#pre-mortem-v216-release-self-premortem`

Total risks: 7
  - Tigers: 3 (Launch-Blocking: 1, Fast-Follow: 1, Track: 1)
  - Paper Tigers: 2
  - Elephants: 2

**Hidden Assumption**: che strutturare la premortem (5 fasi + tassonomia + registry
persistente) produca più valore della cerimonia che impone — e che l'opt-in totale
generi abbastanza evidenza per giustificare la propria evoluzione a v2.17.

**Most Likely Failure**: opt-in limbo perpetuo (pattern rilasciato, nessuna soglia di
rivalutazione → "opzionale per sempre"). **Most Dangerous**: telemetria solo-metadati
→ percorso di promozione strutturalmente impossibile.

| # | Risk | Category | Tier | Urgency | Evidence | Mitigation | Owner | Decision |
|---|------|----------|------|---------|----------|------------|-------|----------|
| 1 | Promozione v2.17 mai attivata: nessuna soglia telemetrica → opt-in limbo | Execution | Tiger | LB | ADR-006 non definisce soglia | definire soglia ora | @soli92 | open |
| 2 | Telemetria solo-metadati non può provare valore → no business case promozione | Assumptions | Tiger | FF | ADR-006 = metadata only | campo outcome opzionale | @soli92 | open |
| 3 | WARNING Check 4m accumulano come rumore → utenti disattivano lint | Technical | Tiger | Track | 4m.2 missing_registry_row | review WARNING dopo 1 mese | @soli92 | open |
| 4 | Fan-out N×M mai esercitato realmente → percorso parallelo non testato | Technical | Paper Tiger | — | gate test inline, non spawn reale | test N×M schedulato | @soli92 | open |
| 5 | Tassonomia T/PT/E richiede 3 pagine wiki → costo cognitivo → saltata | People | Paper Tiger | — | concept su 3 file | quickstart nel runbook | — | mitigated |
| 6 | Premortem strutturata = cerimonia senza valore vs «what could go wrong?» inline | Assumptions | Elephant | — | scommessa Opzione B non validata | regola dei 30s nel runbook | @soli92 | open |
| 7 | Self-premortem resta l'unica premortem mai eseguita → dogfooding cerimoniale | People | Elephant | — | named in questa premortem | telemetria + nudge opt-in | @soli92 | open |

**Calibration**: ✅ valida (≥1 Tiger LB + ≥1 Paper Tiger + ≥1 Elephant).

**Revised Plan** (suggerimenti, R.P1 — mai auto-applicati): (1) definire soglia
telemetrica v2.17; (2) campo `outcome` opzionale nella telemetria; (3) "regola dei
30 secondi" nel runbook; (4) quickstart tassonomia; (5) test reale fan-out N×M.

**Stress-test invarianti**: R.P1 counterexample tentato → output NON auto-applicato
(nessun edit al design doc/TSK, solo append + suggerimenti) ✅. R.P2: bar soddisfatto
per deduzione, fail-loud non scatta (contesto sufficiente) ✅. R.P3: `/lint` v2.15-only
= 0 nuove ERROR/WARNING (TSK-009) ✅.
