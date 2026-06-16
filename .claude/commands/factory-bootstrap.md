---
description: Scaffolda una nuova Agentic Factory llm-wiki++. Dispatcher thin → v2.21 (corrente, Design Intelligence Layer EP-019 + Token Ledger EP-022 opt-in) | v2.20 (FE Functional Oracle EP-018 opt-in) | v2.19 (Hardening & Sustainability EP-012..017) | v2.18 (A11y + UX/UI Integration opt-in) | v2-18-full (variante consolidata self-contained: tutta la catena extends in un unico file) | v2.17 (FE Visual Oracle Integration opt-in) | v2.16 (Premortem Integration opt-in) | v2.15 (consolidation release del Compression Layer) | v2.14 (compression layer first introduction) | v2.13 (multi-adapter) | v2.12 (legacy, single-adapter) | v2.11 (snapshot storico).
argument-hint: [nome-progetto] [path-destinazione] [--version=v2-21|v2-20|v2-19|v2-18|v2-18-full|v2-17|v2-16|v2-15|v2-14|v2-13|v2-12|v2-11]
allowed-tools: Read, Write, Edit, Bash, Glob, TodoWrite, WebSearch, WebFetch
---

# Factory Bootstrap — dispatcher

> **Sede e installazione.** Questo file è la **source-of-truth versionata** del dispatcher,
> co-locato con tutti gli altri comandi dell'adapter Claude Code in `.claude/commands/`.
> Per usarlo come slash command Claude Code va **installato user-level** copiandolo in
> `~/.claude/commands/factory-bootstrap.md`:
> ```bash
> cp <your-clone>/.claude/commands/factory-bootstrap.md ~/.claude/commands/factory-bootstrap.md
> ```
> A differenza degli altri comandi dell'adapter, il dispatcher **non** viene scaffoldato
> nelle factory derivate (non è nella lista curata di Fase 4.c del seed): è un meta-comando
> che *crea* factory, non uno che vive *dentro* una factory.

Argomenti utente: `$ARGUMENTS`

## Risoluzione versione

Parse `$ARGUMENTS` cercando `--version=<X>`:

- **`--version=v2-21` (DEFAULT)** → carica il seed v2.21 (Design Intelligence Layer EP-019 + Token Ledger EP-022;
  **delta seed** che estende v2-20; DUE capability opt-in derivabili — EP-019 Design Intelligence Layer
  (art-director DSL + LLM-Generator Separation + Critic/Judge + Intention Economy; skills
  `art-director-protocol`, `design-spec-dsl`, `critic-judge-protocol`, `design-intelligence-protocol`,
  `llm-generator-separation-protocol`; PATTERN §24; ADR-068..071) e EP-022 Token Ledger (visibilità
  token reali inline, script `show-session-tokens.py` + hook Stop); default
  `design_intelligence.enabled: false` / `analytics.token_ledger.enabled: false` → factory identica
  a v2.20; nessuna nuova invariante §7 (restano 18)).
  ⚠️ Essendo un **delta seed** con `extends: v2-20`, l'agente DEVE risolvere la catena
  `v2-21 → v2-20 → v2-19 → v2-18 → v2-17 → v2-16 → v2-15 → (Fase 2/5 da v2-12)` fetchando ogni seed padre.
- **`--version=v2-20`** → carica il seed v2.20 (FE Functional Oracle EP-018;
  **delta seed** che estende v2-19; unica capability di prodotto derivabile = EP-018 FE
  Functional Oracle opt-in — skill `functional-oracle-protocol` + `interaction-drive-protocol`
  + comando `/functional-oracle` + schema `acceptance-spec` + dominio scheduler
  `functional-oracle`; default `fe_correctness.functional_oracle.enabled: false` → factory
  identica a v2.19; nessuna nuova invariante §7).
  ⚠️ Essendo un **delta seed** con `extends: v2-19`, l'agente DEVE risolvere la catena
  `v2-20 → v2-19 → v2-18 → v2-17 → v2-16 → v2-15 → (Fase 2/5 da v2-12)` fetchando ogni seed padre.
- **`--version=v2-19`** → carica il seed v2.19 (Hardening & Sustainability;
  **delta seed** che estende v2-18; unico delta derivabile = EP-013 Analytics Dogfooding
  opt-in + fix ux_ui anti-fabbricazione ADR-063; §22/§23 governance META non scaffoldata in
  factory derivate; nessuna nuova invariante §7).
  ⚠️ Essendo un **delta seed** con `extends: v2-18`, l'agente DEVE risolvere la catena
  `v2-19 → v2-18 → v2-17 → v2-16 → v2-15 → (Fase 2/5 da v2-12)` fetchando ogni seed padre.
- **`--version=v2-18`** → carica il seed v2.18 (A11y + UX/UI Integration
  opt-in; **delta seed** che estende v2-17 con la Fase 1.sexies opt-in — capability `a11y`
  (accessibility testing WCAG 2.2 AA via tool `run_a11y_scan` + skill
  `accessibility-testing-protocol` + agente `a11y-specialist`) e `ux_ui` (UX/UI Review &
  Design via skill `ux-ui-review-protocol` + `ux-ui-design-protocol` + agenti
  `ux-ui-reviewer` + `ui-designer`); tutte le integrazioni no-op a flag spento).
  ⚠️ Essendo un **delta seed** con `extends: v2-17`, l'agente DEVE risolvere la catena
  `v2-18 → v2-17 → v2-16 → v2-15 → (Fase 2/5 da v2-12)` fetchando ogni seed padre, oppure
  usare la variante consolidata `--version=v2-18-full`.
- **`--version=v2-18-full`** → carica `meta-prompts/v2-18/factory-bootstrap-full.md`, la
  **variante consolidata self-contained**: identica funzionalmente a v2.18 ma con l'intera
  catena `extends` (v2-18 + v2-17 + v2-16 + v2-15 + Fase 2/5 di v2-12) **inlinata in un
  unico file** (nessun `extends:` da risolvere, nessun seed padre da fetchare). Consigliata
  quando l'agente non risolve in automatico la catena `extends:` (es. fetch del solo file
  v2-18 → procedura incompleta). Resta necessario il fetch dei template di contenuto
  (PATTERN.md, file `.claude/*`, manifest adapter) in Fase 3.
- `--version=v2-17` → v2.17 (FE Visual Oracle Integration opt-in; estende v2-16 con la
  Fase 1.quinquies opt-in per attivare il FE Visual Oracle — skill `visual-oracle-protocol`
  + `oracle-precheck` + comando `/visual-oracle` + blocco config `fe_correctness`).
- `--version=v2-16` → v2.16 (Premortem Integration opt-in; estende v2-15 con la Fase
  1.quater opt-in per scaffoldare la skill `premortem-protocol`).
- `--version=v2-15` → v2.15 (consolidation release del Compression Layer; gate Fase 1.5
  + 3a riformulati come opt-in deferred).
- `--version=v2-14` → v2.14 (introduzione Compression Layer a due assi opt-in, gate
  empirici come «pending run»).
- `--version=v2-13` → v2.13 (multi-adapter scaffolding, meta-prompt versionato nel repo).
- `--version=v2-12` → v2.12 (self-contained portable, single-adapter, CQRL + multi-repo).
- `--version=v2-11` → v2.11 (snapshot legacy, monolitico, parallel scheduler).

**Versione inesistente** (`--version=<X>` con `<X>` non in `{v2-21, v2-20, v2-19, v2-18, v2-18-full, v2-17, v2-16, v2-15, v2-14, v2-13, v2-12, v2-11}`):
STOP con errore esplicito — **niente silent fallback**:

```
ERROR: versione '<X>' non supportata. Versioni disponibili: v2-21 (default), v2-20, v2-19, v2-18, v2-18-full (consolidata self-contained), v2-17, v2-16, v2-15, v2-14, v2-13, v2-12, v2-11.
```

Il resto degli argomenti (`[nome-progetto] [path-destinazione]`) viene passato verbatim alla versione scelta.

## Risoluzione source del seed

Il seed v2.13+ vive **nel repo meta-framework** (`<repo>/meta-prompts/v2-XX/`).

**Method A — Local clone (preferito)**: se hai clonato il meta-framework localmente,
il seed è in:
```
<your-clone>/meta-prompts/v2-21/factory-bootstrap.md        # default corrente (delta seed)
<your-clone>/meta-prompts/v2-20/factory-bootstrap.md        # previous
<your-clone>/meta-prompts/v2-19/factory-bootstrap.md        # legacy
<your-clone>/meta-prompts/v2-18/factory-bootstrap.md        # legacy
<your-clone>/meta-prompts/v2-18/factory-bootstrap-full.md   # variante consolidata self-contained
```

**Method B — GitHub raw URL** (sempre fresco):
```
https://raw.githubusercontent.com/soli92/soli-multi-agents-factory/main/meta-prompts/v2-21/factory-bootstrap.md
https://raw.githubusercontent.com/soli92/soli-multi-agents-factory/main/meta-prompts/v2-20/factory-bootstrap.md
https://raw.githubusercontent.com/soli92/soli-multi-agents-factory/main/meta-prompts/v2-19/factory-bootstrap.md
https://raw.githubusercontent.com/soli92/soli-multi-agents-factory/main/meta-prompts/v2-18/factory-bootstrap.md
https://raw.githubusercontent.com/soli92/soli-multi-agents-factory/main/meta-prompts/v2-18/factory-bootstrap-full.md
https://raw.githubusercontent.com/soli92/soli-multi-agents-factory/main/meta-prompts/v2-17/factory-bootstrap.md
https://raw.githubusercontent.com/soli92/soli-multi-agents-factory/main/meta-prompts/v2-16/factory-bootstrap.md
https://raw.githubusercontent.com/soli92/soli-multi-agents-factory/main/meta-prompts/v2-15/factory-bootstrap.md
https://raw.githubusercontent.com/soli92/soli-multi-agents-factory/main/meta-prompts/v2-14/factory-bootstrap.md
https://raw.githubusercontent.com/soli92/soli-multi-agents-factory/main/meta-prompts/v2-13/factory-bootstrap.md
https://raw.githubusercontent.com/soli92/soli-multi-agents-factory/main/meta-prompts/v2-12/factory-bootstrap.md
https://raw.githubusercontent.com/soli92/soli-multi-agents-factory/main/meta-prompts/v2-11/factory-bootstrap.md
```

**Method C — Local cache legacy** (solo pre-v2.13, deprecato): `~/.claude/factory-bootstrap/v2-XX/`
conteneva i seed user-level **fino a v2-12**. Da v2.13 in poi i seed vivono **solo nel repo**
(`meta-prompts/`, Method A/B): questa cache NON contiene v2-13+ e va usata esclusivamente come
fallback offline per le versioni storiche v2-11/v2-12. Per le versioni correnti usa Method A o B.

## Versione corrente: v2.21 (Design Intelligence Layer + Token Ledger)

**Cambiamenti chiave vs v2.20** (gate v2.21.0 PASS — 2026-06-15):
- **Design Intelligence Layer opt-in** (PATTERN §24 nuovo, EP-019). DUE capability opt-in — art-director DSL
  (statement obbligatorio INTENT/PROBLEM/RATIONALE/CONSTRAINTS pre-ogni task design) + LLM-Generator
  Separation (separazione prompt-intenzione/generazione) + Critic/Judge multi-round (verdict
  pass/conditional/reject) + Intention Economy (contesto minimo → output massimo). Skills:
  `art-director-protocol`, `design-spec-dsl`, `critic-judge-protocol`, `design-intelligence-protocol`
  (meta-skill orchestratrice), `llm-generator-separation-protocol`. Config block
  `design_intelligence:` (default `enabled: false`, `art_director: false`, `critic_judge: false`,
  `intention_economy: false`). ADR-068..071. Nessuna nuova invariante §7 (restano 18).
- **Token Ledger opt-in** (EP-022). Visibilità token reali inline dopo ogni risposta con tool use:
  script `show-session-tokens.py` + hook Stop + invariante CLAUDE.md. Config block
  `analytics.token_ledger:` (default `enabled: false`). Complementare a EP-009/EP-013 (non
  sostituisce): EP-009 harvesta batch, EP-022 mostra inline per awareness real-time.
- Migration v2.20 → v2.21 = **no-op di codice** senza attivazione. Factory senza flag = identica a v2.20.

**Cambiamenti chiave vs v2.19 (eredità v2.20 preservata)**:
- **FE Functional Oracle opt-in** (PATTERN §3 operazione opzionale «Functional Oracle», EP-018). A
  differenza di v2.19 (hardening + governance META, delta derivabile sottile), v2.20 aggiunge **una
  capability di prodotto derivabile**: la review che *esercita* il flusso reale dell'app (serve →
  carica fixture → guida interazione Playwright → asserzioni **domain-agnostic** → verdict
  **deterministico** fail-closed; critic LLM **solo advisory** sul trace). Complementare a Visual
  Oracle (EP-005, osserva il render) + UX/UI Review (EP-008, giudica l'aspetto): chiude il failure
  mode «renderizza ma non funziona».
- Scaffolda opt-in: skill `functional-oracle-protocol` + `interaction-drive-protocol` + comando
  `/functional-oracle` + schema `acceptance-spec` + dominio scheduler `functional-oracle`.
- Migration v2.19 → v2.20 = **no-op di codice** senza attivazione.

**Cambiamenti chiave vs v2.18 (eredità v2.19 preservata)**:
- **Hardening & Sustainability** (EP-012..017). Delta derivabile minimo: EP-013 Analytics
  Dogfooding opt-in (hook `SessionEnd` + blocco `analytics.dogfooding:`, default `enabled:
  false`) + fix ux_ui anti-fabbricazione ADR-063 (`evidence-provenance`, fail-loud, 3 tool
  `.sh`). §22 Release Governance + §23 Complexity Budget = governance META, non scaffoldata
  in factory derivate. Nessuna nuova invariante §7 (restano 18). Migration v2.18 → v2.19 =
  **no-op di codice** senza attivazione.

**Cambiamenti chiave vs v2.17 (eredità v2.18 preservata)**:
- **A11y + UX/UI Integration opt-in** (PATTERN §3, 3 operazioni canoniche opzionali). Il
  seed v2-18 **estende v2-17** aggiungendo una sola sezione, la **Fase 1.sexies**, che
  attiva opt-in due capability standalone: `a11y` (Accessibility Testing WCAG 2.2 AA via
  tool `run_a11y_scan` + skill `accessibility-testing-protocol` + agente `a11y-specialist`
  + `/a11y`) e `ux_ui` (UX/UI Review & Design via skill `ux-ui-review-protocol` +
  `ux-ui-design-protocol` + agenti `ux-ui-reviewer` + `ui-designer` + `/ux-ui-review` +
  `/ux-ui-design`).
- Tocca skill/agent esistenti (`dev-protocol` Fase 4-ter, `code-review-protocol`
  precondition + 4° pass opzionale `accessibility`, `parallel-scheduling` domini `a11y` +
  `ux-ui-review`, `lint-checks` Check 4o + 4p, `scrivi-task` sezioni FE), ma **tutto no-op
  a flag spento**: l'opt-in reale è l'attivazione (`a11y.*` / `ux_ui.*` + Playwright).
- Ordering pipeline FE (tutti gli opt-in attivi): `develop → visual-oracle → ux-ui-review
  → code-review` (ADR-019). 7 ADR risolti (ADR-014..020). Nessuna nuova invariante §7.
- Default scelta utente **N** per entrambe (zero friction). Migration v2.17 → v2.18 =
  **no-op di codice** senza attivazione.
- ⚠️ **Nota delta seed**: v2-18 dichiara `extends: v2-17` (catena fino a v2-15 + Fase 2/5
  di v2-12). `extends:` è una convenzione, non auto-risolvente: un agente che fetcha solo
  `meta-prompts/v2-18/factory-bootstrap.md` ottiene la sola Fase 1.sexies, **non** la
  procedura completa. Per evitarlo, risolvi tutta la catena fetchando ogni seed padre,
  **oppure** usa `--version=v2-18-full` (catena inlinata in un unico file).

**Cambiamenti chiave vs v2.16 (eredità v2.17 preservata)**:
- **FE Visual Oracle opt-in** (PATTERN §3 variante di Develop FE). Il seed v2-17
  **estende v2-16** aggiungendo una sola sezione, la **Fase 1.quinquies**, che attiva
  opt-in il FE Visual Oracle: skill `visual-oracle-protocol` (render headless Playwright
  + critica visiva LLM multi-viewport/tema, pattern evaluator-optimizer) + `oracle-precheck`
  + comando `/visual-oracle` + blocco config `fe_correctness`.
- A differenza di v2.16 (file puramente additivi), tocca anche skill esistenti
  (`dev-protocol` Fase 4-bis, `code-review-protocol` Fase 0 precondition, `fe-dev`,
  `scrivi-task` State Matrix/Granularity, `lint-checks` Check 4n, `orchestrator` Oracle
  Gate, `parallel-scheduling` dominio `visual-oracle`), ma **tutte le integrazioni sono
  no-op a flag spento**: l'opt-in reale è l'attivazione (`fe_correctness.*` + Playwright).
- Default scelta utente **N** (zero friction — factory senza attivazione = identica a v2.16).
- Nessuna nuova invariante §7 (restano 18). Single-writer `visual_status` (solo la skill).
- Migration v2.16 → v2.17 = **no-op di codice** senza attivazione.

**Cambiamenti chiave vs v2.15 (eredità v2.16 preservata)**:
- **Pattern Premortem opt-in** (PATTERN §3 operazione opzionale). Il seed v2-16
  **estende v2-15** (non v2-13: estendere v2-13 perderebbe il Compression Layer)
  aggiungendo una sola sezione, la **Fase 1.quater**, che scaffolda opt-in la skill
  `premortem-protocol` + comando `/premortem` + template `management/risk-registry.md`.
- Default scelta utente **N** (zero friction, R.P3 — factory senza skill = identica a v2.15).
- Nessuna nuova invariante §7 (R.P1-R.P3 vivono nella skill). Nessun gate auto-enforcing.
- Migration v2.15 → v2.16 = **no-op di codice** senza opt-in.

**Cambiamenti chiave vs v2.14 (eredità v2.15 preservata)**:
- **Consolidation release**: nessuna nuova feature di framework. Bump versione del
  PATTERN 2.14 → 2.15 per chiudere il ciclo del Compression Layer a due assi come
  baseline stabile.
- **Gate empirici Fase 1.5 + 3a riformulati come opt-in deferred** (non bloccanti
  per il consolidamento del PATTERN). Restano setup-ready ma eseguibili a
  discrezione del derivatore della factory quando dispone di parametri di baseline
  adeguati.
- Tutte le invarianti R.C1-R.C6 (OCL), R.G1-R.G6 (CCL), R.K1 (karpathy non
  comprimibile) **preservate identiche**.
- Default `compression.output.enabled: false` + `compression.context.enabled: false`
  invariati.
- Migration v2.14 → v2.15 = **no-op di codice**. Le factory v2.14 si comportano
  identiche su v2.15.

**Cambiamenti chiave di v2.14 (eredità preservata)**:
- **Compression Layer a due assi opt-in** (PATTERN §20 nuovo):
  - Asse OUTPUT (Fase 1 OCL via Caveman) — comprime canali messaging agent-to-agent.
  - Asse CONTEXT (Fase 2 CCL via Graphify) — knowledge graph del code_path come
    context replacement, confidence-gated dispatch (executor/explorer/reviewer).
- 6 invarianti R.C1-R.C6 (output, R.C1 non overridabili neppure in `custom`).
- 6 invarianti R.G1-R.G6 (context, filesystem single source of truth + side-channel
  write-restricted).
- Nuova §7 r.18 PATTERN: compression mai sugli artefatti persistenti.
- 4° sync adapter `graphify-sync` (PDF / Figma / Repo / Graph).
- Tooling: Graphify v0.8.22+ (pip install graphifyy, binario `graphify`).

**Architettura skill-driven** (invariata da v2.13):
```
factory-bootstrap (thin orchestrator)
    │
    ├── bootstrap-input-protocol         (input + archetipi)
    ├── bootstrap-multirepo-protocol     (coupling se existing-repo)
    ├── bootstrap-multiadapter-protocol  (adapter selection + scaffold)
    ├── bootstrap-scaffolding-protocol   (file + dir L1-L5 + compression artefacts v2.14+)
    ├── bootstrap-vcs-protocol           (submodule stamps + .factory-lock)
    └── bootstrap-validation-protocol    (35+ check + wiki feeding + report)
```

## Esecuzione

**Read** il file della versione risolta (via Method A/B/C) e seguilo letteralmente.
Il seed è auto-contenuto: include riferimenti a PATTERN.md (fetched), agli adapter
manifests, e ai template di reference.

## Cronologia versioni

| Versione | Data | Cambiamenti principali |
|---|---|---|
| **v2.21 (corrente)** | 2026-06-15 | **Design Intelligence Layer + Token Ledger** (EP-019 + EP-022, opt-in). Estende v2-20. DUE capability opt-in: EP-019 (art-director DSL + LLM-Generator Separation + Critic/Judge + Intention Economy; PATTERN §24; ADR-068..071; skills `art-director-protocol`/`design-spec-dsl`/`critic-judge-protocol`/`design-intelligence-protocol`/`llm-generator-separation-protocol`) + EP-022 (Token Ledger inline — `show-session-tokens.py` + hook Stop). Default entrambi `false`. Nessuna nuova invariante §7 (restano 18). Gate v2.21.0 PASS (3/3 RUN-REPORT, analytics_events_count > 0). Migration v2.20 → v2.21 = no-op senza attivazione. |
| v2.20 | 2026-06-10 | **FE Functional Oracle** (EP-018, opt-in). Estende v2-19. Prima capability di prodotto derivabile dopo l'hardening v2.19: la review che *esercita* il flusso reale dell'app (serve → fixture → interazione Playwright → asserzioni domain-agnostic → verdict deterministico, critic LLM advisory). Complementare a Visual Oracle + UX/UI Review; chiude «renderizza ma non funziona». Skill `functional-oracle-protocol` + `interaction-drive-protocol` + `/functional-oracle` + schema `acceptance-spec` + dominio scheduler `functional-oracle`. Default `fe_correctness.functional_oracle.enabled: false` → factory identica a v2.19. ADR-065/066/067. Nessuna nuova invariante §7. Migration v2.19 → v2.20 = no-op senza attivazione. |
| v2.19 | 2026-06-09 | **Hardening & Sustainability** (EP-012..017). Estende v2-18. Delta derivabile: EP-013 Analytics Dogfooding opt-in + fix ux_ui anti-fabbricazione ADR-063. §22/§23 governance META, non scaffoldata in factory derivate. Nessuna nuova invariante §7 (restano 18). Migration v2.18 → v2.19 = no-op senza attivazione. |
| v2.18 | 2026-06-04 | **A11y + UX/UI Integration** opt-in (PATTERN §3, 3 operazioni canoniche opzionali). Estende v2-17 con la Fase 1.sexies opt-in (capability `a11y` via tool `run_a11y_scan` + skill `accessibility-testing-protocol` + agente `a11y-specialist`; capability `ux_ui` via skill `ux-ui-review-protocol` + `ux-ui-design-protocol` + agenti `ux-ui-reviewer` + `ui-designer`). Tocca skill/agent esistenti ma no-op a flag spento. 7 ADR risolti (ADR-014..020). Nessuna nuova invariante §7. Default N. Migration v2.17 → v2.18 = no-op senza attivazione. **Variante `v2-18-full`**: catena `extends` consolidata in un unico file self-contained. |
| v2.17 | 2026-06-03 | **FE Visual Oracle Integration** opt-in (PATTERN §3 variante Develop FE). Estende v2-16 con la Fase 1.quinquies opt-in (attiva skill `visual-oracle-protocol` + `oracle-precheck` + `/visual-oracle` + blocco `fe_correctness`). Tocca skill esistenti ma no-op a flag spento. Nessuna nuova invariante §7 (restano 18). Default N. Migration v2.16 → v2.17 = no-op senza attivazione. |
| v2.16 | 2026-06-01 | **Premortem Integration** opt-in (PATTERN §3). Estende v2-15 con la Fase 1.quater opt-in (scaffolda skill `premortem-protocol` + `/premortem` + template risk-registry). Nessuna nuova invariante §7. Default N. Migration v2.15 → v2.16 = no-op senza opt-in. |
| v2.15 | 2026-05-29 | **Consolidation release** del Compression Layer v2.14. Gate Fase 1.5 + 3a riformulati come opt-in deferred. 35 check. Migration v2.14 → v2.15 = no-op di codice. |
| v2.14 | 2026-05-28 | **Compression Layer a due assi opt-in** (§20 nuovo): Output via Caveman (R.C1-R.C6) + Context via Graphify (R.G1-R.G6). Nuova §7 r.18. 4° sync adapter `graphify-sync`. 34 check. |
| v2.13 | 2026-05-27 | Multi-adapter scaffolding parallelo (§12 esteso) — registry + 5 adapter + R.A1-R.A6. Meta-prompt versionati nel repo. 28 check. |
| v2.12 | 2026-05-27 | CQRL (§19) + multi-repo `code_paths` (§13) + coupling modes R.B1-R.B6 (§16) + existing-repo wiki feeding. Thin orchestrator + 5 bootstrap-* skill. |
| v2.11 | 2026-05-26 | Parallel scheduler DAG-driven (§18). |
| v2.10 | 2026-05-25 | Publisher adapters (§17). |
| v2.9 | 2026-05-21 | Sync adapters multi-sorgente — figma-sync (§16). |
| v2.8 | precedente | VCS integration (§15). |
| v2.7 | precedente | execution layer L5, dev-agent opzionali, topology esplicite. |

Per il diff completo + statistiche evolutive vedi
[`meta-prompts/README.md`](../../meta-prompts/README.md).
