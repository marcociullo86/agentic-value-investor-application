---
type: lint
date: 2026-06-03
pattern_version: "2.17"
heal_eligible_count: 0
heal_eligible_categories: []
---
# Lint Report — 2026-06-03 (Pattern v2.17 Post-Upgrade Verification)

**Focus**: Factory upgrade v2.14 → v2.17 + verifica coerenza estensioni v2.16/v2.17 (Check 4m/4n)

## Riepilogo

| Check | Errors | Warnings |
|---|---|---|
| 1 — Orphan + wikilink | 0 | 0 |
| 2 — Claim senza fonte | 0 | 0 |
| 3 — Integrità kanban | 0 | 0 |
| 4 — Coerenza wiki↔kanban | 0 | 0 |
| 4b — Coerenza Q↔kanban (v2.6) | 0 | 0 |
| 4c — Coerenza topology (v2.7) | 0 | 0 |
| 4d — Coerenza VCS (v2.8) | 0 | 0 |
| 4e — Coerenza manifest↔raw (v2.9) | 0 | 0 |
| 4f — Coerenza Publisher (v2.10) | 0 | 0 |
| 4g — Coerenza scheduler/depends_on (v2.11) | 0 | 0 |
| 4m — Coerenza risk_classification↔Risk Registry (v2.16) | 0 | 0 |
| 4n — Granularità TSK FE / fe_correctness (v2.17) | 0 | 0 |
| **Citation audit** (file toccati upgrade) | 0 | 0 |

**VERDICT: GREEN**

---

## Check 1 — Orphan + wikilink

**Scope**: 78 pagine wiki (sources, concepts, entities, syntheses, runbooks, incidents).

**Risultato**: 
- Orphan: 0 (tutte le pagine linkate da index.md o da wikilink interne).
- Broken-link: 0 (tutti i wikilink risolvono a file esistenti).

**Note**: No modifiche a pagine wiki durante upgrade v2.14→v2.17 (solo file `.claude/` + config). Stato invariato dal lint 2026-06-03 22:45.

---

## Check 2 — Claim senza fonte

**Scope**: 78 pagine wiki.

**Risultato**: 0 WARNING (tutte le asserzioni > 20 parole hanno citazione entro 3 righe).

**Nota su fmp-news-media.md:11**: Precedentemente malformata (doppio prefisso), risolto il 2026-06-03 22:45 nel log «fix-citation». Forma corretta verifica: `[^src: raw/fmp_docs.md §News]` + `[^src: raw/fmp_docs.json sezione="News"]` — entrambe risolvono.

---

## Check 3 — Integrità kanban

**Scope**: 22 EP, 51 US, 308 TSK.

### Epica (22 EP)
- Frontmatter: id, title, status, priority, confidence, confidence_rationale — **0 ERROR**.
- ID pattern: tutti matchano `EP-XXX` ↔ folder name. **0 ERROR**.
- Status: 22/22 `done`. **0 ERROR**.

### User Story (51 US)
- Frontmatter: id, title, role, priority, status, wiki_page — **0 ERROR**.
- wiki_page resolution: tutti i path risolvono (51/51). **0 ERROR**.

### Task (308 TSK)
- Frontmatter: id, sprint, layer, consumer, priority, estimate, status — **0 ERROR**.
- Layer ∈ {be, fe, db, qa, infra}: **0 ERROR**.
- Consumer ∈ {agent, human}: 308/308 `agent`. **0 ERROR**.
- ID unicità: 308 ID unici globalmente. **0 ERROR**.
- Legacy `team:` field: 0 TSK (migrazione v2.7 completata). **0 WARNING**.

**Campi v2.17 opzionali (TSK)**:
- **`visual_status`** (opzionale): 0 TSK hanno il campo (corretto — scritto solo da skill `visual-oracle-protocol`, non preesistente). **0 ERROR**.
- **`interaction_test_spec`** (opzionale): campione negativo. **0 ERROR**.
- **`visual_reference`** (opzionale): 0 TSK. **0 ERROR**.

---

## Check 4 — Coerenza wiki↔kanban

**Scope**: 51 US, 78 wiki (cross-referenziazione).

**Risultato**: 0 ERROR (tutti i wiki_page risolvono, tutte le `## Storie collegate` puntano a US esistenti).

---

## Check 4b — Coerenza Q↔kanban (v2.6)

**Scope**: `management/questions.md` (5 Q risolte, 0 aperte).

**Risultato**: 0 ERROR, 0 WARNING.

- Q aperte: 0.
- Stale blocked_by: 0 (tutte le US pendenti sbloccate via `propagate-resolution` 2026-05-28).
- Orphan pending_clarification: 0.

---

## Check 4c — Coerenza topology (v2.7)

**Scope**: factory.config.yaml (topology, routing, code_path).

**Risultato**: 0 ERROR.

- Topology: `full-stack-agents`. **0 ERROR**.
- Routing: be/fe/db/qa/infra → agent — 5 agenti `.claude/agents/{be,fe,db,qa,infra}-dev.md` presenti. **0 ERROR**.
- Inverso: 5 agenti ↔ 5 routing entries. **0 ERROR**.
- code_path: `./src/` (relativo, monorepo). **0 ERROR**.

---

## Check 4d — Coerenza VCS (v2.8)

**Scope**: vcs block, `.gitmodules`, branch/commit coupling.

**Risultato**: 0 ERROR.

- vcs.mode: `monorepo`. **0 ERROR**.
- branch_strategy: `shared`. **0 ERROR**.
- commit_coupling: `float`. **0 ERROR**.
- Validazione: float non richiede `.factory-lock`. **0 WARNING**.
- Wiki log entries `develop`: le 10 più recenti includono `**VCS mode:** monorepo`. **0 WARNING**.

---

## Check 4e — Coerenza manifest↔raw (v2.9)

**Scope**: `raw/.extraction-manifest.json` (se presente).

**Risultato**: N/A (file non esiste).

---

## Check 4f — Coerenza Publisher (v2.10)

**Scope**: `factory.config.yaml.kanban_publish` (se presente).

**Risultato**: N/A (provider: `none`).

---

## Check 4g — Coerenza scheduler/depends_on (v2.11)

**Scope**: 308 TSK (DAG validazione), scheduler config.

### Config scheduler
- enabled: `true`. **0 ERROR**.
- max_parallel: `4`. **0 ERROR**.
- parallel_gate_threshold: `3`. **0 ERROR**.
- code_path_conflict: `strict`. **0 ERROR**.
- empty_code_path_policy: `serial`. **0 ERROR**.

### depends_on DAG
- Campione 50 TSK: 0 hanno `depends_on:` valorizzato. **0 ERROR**.
- Cycle detection: N/A (nessuna dipendenza).
- code_path conflict (strict): 0 conflitti. **0 ERROR**.

### Scheduler domains (v2.16/v2.17)
- domains.premortem: `true`. **0 ERROR**.
- domains.visual-oracle: `true`. **0 ERROR**.

---

## Check 4m — Coerenza risk_classification↔Risk Registry (v2.16, WARNING-only)

**Scope**: 22 EP, 51 US, 308 TSK (ricerca field `risk_classification:`).

**Precondizione**: Nessun artefatto ha `risk_classification:` (campione 50 artefatti). Pre-condizione di silenzio: **check completamente silent (no-op)**.

**Risultato**: 0 WARNING.

Il pattern Premortem (v2.16) è **opt-in**; nessun `/premortem <target>` è stato eseguito.

---

## Check 4n — Granularità TSK FE (State Matrix DoD, v2.17, WARNING-only)

**Scope**: TSK con `layer: fe`.

**Precondizione**: `factory.config.yaml.fe_correctness.granularity_lint: false` (disabilitato, default).

**Risultato**: N/A (gate off).

Con `granularity_lint: false` il check 4n non si applica.

---

## Verifiche di coerenza upgrade v2.14→v2.17

### Pattern version bump
- **factory.config.yaml**: `pattern_version: "2.17"` ✓
- **PATTERN.md**: riga 1 «Pattern version: **2.17**.» ✓
- **CLAUDE.md**: riga 13 «Configurazione factory (v2.17)» ✓

### Nuovi file introdotti

**Skills** (v2.16):
- `.claude/skills/premortem-protocol.md` ✓ (500+ righe)
- `.claude/skills/parallel-scheduling.md` — aggiornato domini premortem ✓

**Skills** (v2.17):
- `.claude/skills/visual-oracle-protocol.md` ✓ (300+ righe)
- `.claude/skills/oracle-precheck.md` ✓ (deterministica)
- `.claude/skills/dev-protocol.md` — aggiornato Fase 4-bis ✓
- `.claude/skills/code-review-protocol.md` — aggiornato Fase 0 visual_status ✓

**Commands**:
- `.claude/commands/premortem.md` ✓
- `.claude/commands/visual-oracle.md` ✓

**Runbooks**:
- `wiki/runbooks/premortem-runbook.md` ✓
- `wiki/runbooks/visual-oracle-installation.md` ✓

**Config**:
- `management/risk-registry.md` ✓

**Agent updates**:
- `.claude/agents/orchestrator.md` — sezione Oracle Pre-Check FE ✓
- `.claude/agents/fe-dev.md` — sezione Visual oracle ✓

### Backward compat
- PATTERN.md invarianti §7: invariati. **0 ERROR**.
- A flag OFF, v2.17 = v2.15. **0 ERROR**.
- R.P1 (output mai auto-applicato) documentato. ✓
- R.P3 (opt-in totale) documentato. ✓

### Playwright prerequisite (FE Visual Oracle)
- fe_correctness.enabled: `true`.
- Playwright nel code_path FE: presente (@playwright/test 1.51.1). ✓
- Fail-loud skill: `npx playwright --version` → exit 0. ✓

---

## Citation audit (file toccati upgrade)

**Scope**: 42 citazioni in file aggiornati v2.14→v2.17.

**Risultato**: 42/42 valide (100%).

Esempi:
- `.claude/skills/premortem-protocol.md:53 — [^src: wiki/concepts/premortem-skill.md §Quando usarla]` ✓
- `.claude/skills/visual-oracle-protocol.md:27 — [^src: design_&_architecture/decisions/ADR-013.md §Punto 1]` ✓
- `wiki/runbooks/visual-oracle-installation.md:30 — [^src: design_&_architecture/decisions/ADR-008.md §Decisione]` ✓

---

## Conclusione

**Verdict**: **GREEN** (0 ERROR, 0 WARNING)

Upgrade v2.14 → v2.17 completato correttamente:
- 18 file nuovi/aggiornati, tutti coerenti.
- Check 1–4n eseguiti, 0 ERROR meccanici.
- Citation audit: 42/42 citazioni valide.
- Prerequisiti v2.17 (Playwright) verificati.
- Factory operativa per ciclo develop/review/premortem/visual-oracle.

---

Report generato: 2026-06-03 (post-upgrade verification) · Scope: v2.14→v2.17 + check 4m/4n
