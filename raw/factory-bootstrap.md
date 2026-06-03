---
description: Seed self-contained per scaffoldare una Agentic Factory llm-wiki++ v2.17. Replicabile da qualunque AI agent con file I/O su qualunque macchina/cartella. Estende v2-16 (Premortem Integration opt-in) aggiungendo la Fase 1.quinquies opt-in per l'attivazione del FE Visual Oracle (skill visual-oracle-protocol + oracle-precheck, comando /visual-oracle, blocco config fe_correctness). FE Visual Oracle = variante opzionale di Develop FE (PATTERN §3), opt-in totale, niente nuova invariante §7.
argument-hint: [nome-progetto] [path-destinazione]
allowed-tools: Read, Write, Edit, Bash, Glob, TodoWrite, WebSearch, WebFetch
pattern_version: "2.17"
extends: meta-prompts/v2-16/factory-bootstrap.md
---

# Factory Bootstrap v2.17 — Self-Contained Portable Seed (FE Visual Oracle Integration)

> **Replicabilità**: questo file è il **delta seed** v2.17. Per la procedura completa
> (8 fasi: Setup → Input → Adapter selection → Compression setup → Premortem → Multi-repo
> → Templates → Scaffolding → VCS → Validation → Report) **eredita verbatim
> `meta-prompts/v2-16/factory-bootstrap.md`** (vedi `extends:` nel frontmatter), che a
> sua volta eredita v2-15. v2.17 aggiunge **una sola sezione nuova**: la **Fase
> 1.quinquies** opt-in FE Visual Oracle.

## §0 — Cambiamenti v2.17 vs v2.16

v2.17 **estende v2-16** (catena: v2-16 → v2-15 → v2-13). Eredita l'intero seed v2-16:
- multi-adapter scaffolding (v2.13, R.A1-R.A6);
- Compression Layer a due assi opt-in — OCL/Caveman + CCL/Graphify (v2.14, R.C1-R.C6 + R.G1-R.G6);
- consolidation + gate empirici opt-in deferred (v2.15);
- Premortem Integration opt-in — skill `premortem-protocol` + `/premortem` (v2.16, R.P1-R.P3).

### Unica feature nuova: FE Visual Oracle Integration (opt-in totale)

- Nuova **Fase 1.quinquies** (sotto): attivazione opt-in del **FE Visual Oracle** —
  chiusura del loop aperto del frontend tramite render headless + critica visiva LLM
  multi-viewport/tema (pattern evaluator-optimizer, come CQRL).
- **Nessuna nuova invariante §7** (le 18 regole restano invariate; tutto vive in
  `factory.config.yaml.fe_correctness` opt-in + skill/command).
- Le integrazioni nei file scaffoldati esistenti (`dev-protocol` Fase 4-bis,
  `code-review-protocol` Fase 0 precondition, `fe-dev` sezione Visual oracle,
  `scrivi-task` State Matrix + Granularity, `lint-checks` Check 4n, `orchestrator`
  Oracle Pre-Check, `parallel-scheduling` dominio `visual-oracle`) sono **tutte no-op
  a flag spento** — ereditate dallo scaffold base senza rischio (ADR-009/ADR-013).
- Default scelta utente: **N** (zero friction — una factory che non opta-in si comporta
  identica a v2.16).

> **Nota sulla natura del delta**: a differenza di v2.16 (Premortem = 3 file puramente
> additivi), il FE Visual Oracle tocca anche skill esistenti. La sicurezza è garantita
> dal design opt-in: ogni integrazione è gated da `fe_correctness.*` (default `false`),
> quindi i file scaffoldati dallo scaffold base **contengono già** le sezioni v2.17 ma
> restano inerti. L'opt-in reale di v2.17 è l'**attivazione** (flag + prerequisito
> Playwright), non la copia dei file. Vedi ADR-012 §Backward compat.

## §1 — How to use this seed

Identico a v2-16 §1. In più, durante l'input (Fase 1) l'agente proporrà la
**Fase 1.quinquies** (attivazione FE Visual Oracle opt-in) subito dopo la
Fase 1.quater (Premortem).

## §3 — Bootstrap procedure (delta)

Tutte le fasi sono **identiche a v2-16 §3**, con l'inserimento della Fase 1.quinquies
tra la Fase 1.quater (Premortem) e la Fase 2 (Multi-repo + coupling).

### Fase 1.quinquies — Attivazione FE Visual Oracle (v2.17 opt-in)

Chiedi (default **N**):

```
FE VISUAL ORACLE (PATTERN §3 variante di Develop FE, v2.17 — opt-in totale)

Vuoi attivare il FE Visual Oracle nella tua factory?

Cos'è: un loop chiuso che a fine Develop FE renderizza il componente in browser
headless (Playwright), cattura screenshot multi-viewport/tema, e li critica contro
la specifica del TSK (critic = stesso fe-dev in passata multimodale, pattern
evaluator-optimizer come CQRL). Chiude il "loop aperto" del frontend, dove oggi
"build verde" viene scambiato per "fatto". Verdict pass/conditional/reject,
bounded da max_iterations. Ordering: develop → visual-oracle → review.

Prerequisito (se attivi): Playwright nel project host FE
  npm i -D @playwright/test && npx playwright install --with-deps chromium
  Vedi: wiki/runbooks/visual-oracle-installation.md

Scelta [y/N]: <default N>
```

**Se `N` (default)**:
- il bootstrap procede invariato. I file v2.17 (`visual-oracle-protocol.md`,
  `oracle-precheck.md`, `/visual-oracle`, runbook) e le sezioni nei file esistenti
  sono **comunque scaffoldati** dallo scaffold base, ma **inerti** (tutti i flag
  `fe_correctness.*` restano `false`);
- **nessun edit a `factory.config.yaml`**: il blocco `fe_correctness` è già presente
  come template con tutti i flag `false` (vedi §Templates ereditata);
- la factory si comporta identica a v2.16.

**Se `y`** — guida l'utente all'attivazione (non copiare file: già presenti):

1. **Verifica i file scaffoldati** (devono esistere dallo scaffold base):

   | File (adapter `.claude/`) | Ruolo |
   |---|---|
   | `.claude/skills/visual-oracle-protocol.md` | loop 5 fasi (+ Fase 3-bis structured checks) |
   | `.claude/skills/oracle-precheck.md` | gate euristico 4 condizioni (orchestrator) |
   | `.claude/commands/visual-oracle.md` | comando standalone `/visual-oracle <TSK-id>` |
   | `wiki/runbooks/visual-oracle-installation.md` | setup Playwright |

2. **Attiva in `factory.config.yaml.fe_correctness`** (default tutti `false` → scegli cosa accendere):

   ```yaml
   fe_correctness:
     enabled: true              # master switch: Fase 4-bis Visual Verification in dev-protocol
     max_iterations: 3          # bound loop conditional (analogo R.Q4 di CQRL)
     viewports: [{name: mobile, width: 375}, {name: desktop, width: 1280}]
     themes: [light, dark]
     checks: []                 # opt-in: [visual-regression, axe-a11y, interaction-test]
     state_matrix_inject: false # scrivi-task inietta State Matrix nei TSK FE
     granularity_lint: false    # Lint Check 4n WARNING-only
     granularity: {max_estimate_hours: 8, max_states: 3}
     dispatch_gate: false       # Orchestrator Oracle Pre-Check FE
   ```

   E nel blocco `scheduler.domains`: `visual-oracle: true` (default `false`; vale solo
   se `enabled: true`).

3. **Installa Playwright** nel/i `code_path` FE (vedi runbook). Fail-loud se assente:
   la skill stoppa con messaggio azionabile, **niente degrado silenzioso**.

> **Nota ordering**: con `enabled: true`, un TSK FE deve avere `visual_status: pass`
> prima di `/review` (precondition additiva in `code-review-protocol` Fase 0, no-op a
> flag spento — ADR-009/ADR-013). Il dominio scheduler `visual-oracle` è sub-step di
> L2 (develop), non un livello DAG separato (ADR-012 §F).

Cross-link multi-adapter: per Cursor/Aider/OpenAI/Gemini il porting di skill + comando
+ runner Playwright è un gap noto non-bloccante (vedi `wiki/gaps.md`); v2.17 scaffolda
i file nell'adapter `.claude/`.

## §5 — Self-test (delta)

Eredita i 35+3 check di v2-16 §5. Aggiungi 4 check (sempre, anche a flag spento — i
file sono scaffoldati comunque):

- [ ] `.claude/skills/visual-oracle-protocol.md` esiste nella factory derivata.
- [ ] `.claude/skills/oracle-precheck.md` esiste.
- [ ] `.claude/commands/visual-oracle.md` esiste.
- [ ] `factory.config.yaml` ha il blocco `fe_correctness` con tutti i flag `false` (default).

Se l'utente ha scelto `y` alla Fase 1.quinquies, aggiungi 2 check di attivazione:

- [ ] `factory.config.yaml.fe_correctness.enabled: true` e `scheduler.domains.visual-oracle: true`.
- [ ] Playwright disponibile nel project host FE (`npx playwright --version` → exit 0),
      oppure l'utente è stato avvisato del prerequisito (fail-loud documentato).

Backward compat (R: opt-in totale): a flag spento il self-test v2-16 deve restare
identico — le integrazioni v2.17 sono no-op.

## §6 — Note di portabilità

Identiche a v2-16 §6. Aggiunta: il FE Visual Oracle è provider-agnostic a livello di
orchestrazione (critic multimodale via capability nativa dell'LLM ospitante; i file
scaffoldati sono Markdown puri). L'unica dipendenza ambientale è **Playwright**
(richiesta solo ad attivazione, fail-loud se assente) — non un prerequisito del
bootstrap.

## Vincoli inviolabili (delta v2.17)

Oltre ai vincoli ereditati da v2-16 (incl. R.P1-R.P3, R.C*, R.G*, R.A*):
- **Nessuna nuova invariante §7** — il FE Visual Oracle non aggiunge regole inviolabili
  (le 18 restano invariate). Tutto vive in `factory.config.yaml.fe_correctness` + skill.
- **Single-writer `visual_status`** — solo la skill `visual-oracle-protocol` scrive il
  campo frontmatter `visual_status:` del TSK (analogo R.Q2 per `review_status`).
- **Opt-in totale** — factory con `fe_correctness.*` tutti `false` = identica a v2.16;
  nessun trigger automatico, fail-loud sul prerequisito Playwright (mai silent fallback).
