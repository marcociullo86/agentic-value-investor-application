---
type: lint
date: 2026-05-30
time: 21:30
scope: Complete factory checks (1-4g) + citation audit (files touched in bugfix session)
heal_eligible_count: 0
heal_eligible_categories: []
---
# Lint Report — 2026-05-30 21:30

**Scope:** Lint completo della factory (Check 1–4g) con focus su integrità dei file toccati nella sessione bugfix (ADR-016 Appendice A, TSK-132 SUPERSEDED, TSK-127 FIX, wiki/gaps.md update, wiki/runbooks/fmp-api-quickstart.md update).

---

## Riepilogo

| Check | Errors | Warnings | Notes |
|---|---|---|---|
| 1 — Orphan + wikilink | 0 | 0 | 86 wiki/*.md scansionati (0 orphan, 0 broken-link) |
| 2 — Claim senza fonte | 0 | 0 | Citazioni verificate su files toccati |
| 3 — Integrità kanban | 0 | 0 | 91 US + 20 EP + 240+ TSK (frontmatter OK, 0 id-duplicate) |
| 4 — Coerenza wiki↔kanban | 0 | 0 | 0 wiki_page missing; 0 storie orfane |
| 4b — Coerenza Q↔kanban (v2.6) | 0 | 0 | 0 Q aperte; 5 Q risolte (Q_001–Q_005), 0 stale-blocked-by |
| 4c — Coerenza topology (v2.7) | 0 | 0 | Topology full-stack-agents OK; 5 dev-agent presenti (be, fe, db, qa, infra) |
| 4d — Coerenza VCS (v2.8) | 0 | 0 | VCS monorepo OK; branch_strategy=shared, commit_coupling=float |
| 4e — Coerenza manifest (v2.9) | 0 | 0 | raw/.extraction-manifest.json assente (N/A) |
| 4f — Coerenza Publisher (v2.10) | 0 | 0 | kanban_publish provider=none (no check) |
| 4g — Coerenza scheduler (v2.11) | 0 | 0 | scheduler enabled=true; depends_on DAG OK, 0 cicli |

---

## Citation audit — Files toccati sessione 2026-05-30 (bugfix FMP throttling consolidation)

**Scope:** Verifica citazioni in files modificati durante bugfix del rate limiter FMP (consolidamento `fmp-batch` → `fmp` 280/min, fix SEC 13F information table filename, fix CIK Oakmark).

**Files auditati:** ADR-016 (Appendice A), wiki/gaps.md (sezione fmp-stable-rate-limiting aggiornamento), wiki/runbooks/fmp-api-quickstart.md (§Rate limiting update), TSK-132.md (SUPERSEDED note), TSK-127.md (FIX note).

### Citazioni verificate

| File | Linea | Forma | Path | Sezione | Esito |
|------|-------|-------|------|---------|-------|
| ADR-016-fmp-operations-throttling.md | 82 | `[^src: management/kanban/...US-029.md §Business Rules]` | valid | §Business Rules | ✓ VALID |
| ADR-016-fmp-operations-throttling.md | 12 | `[^src: wiki/gaps.md §fmp-rate-limiting]` | valid | §fmp-rate-limiting | ✓ VALID |
| ADR-016 Appendice A | 103 | `[^src: design_&_architecture/decisions/ADR-016-fmp-operations-throttling.md §Appendice A]` | valid | §Appendice A | ✓ VALID |
| wiki/gaps.md | 212 | `[^src: design_&_architecture/decisions/ADR-016-fmp-operations-throttling.md §Appendice A]` | valid | §Appendice A | ✓ VALID |
| wiki/runbooks/fmp-api-quickstart.md | 32 | `[^src: design_&_architecture/decisions/ADR-016-fmp-operations-throttling.md §Appendice A]` | valid | §Appendice A | ✓ VALID |
| TSK-132.md | 24 | `[^src: design_&_architecture/decisions/ADR-016-fmp-operations-throttling.md#appendice-a-...]` | valid | §Appendice A (anchor HTML) | ✓ VALID |
| TSK-127.md | 26 | `[^src: wiki/log.md]` | valid | (append-only log, sezione 2026-05-30 bugfix) | ✓ VALID |

**Totale:** 7 citazioni testate → **7/7 VALIDE** (100%).

**Wikilink verificati (nei files toccati):**
- ADR-016 linea 124: `[[fmp-api-quickstart]]` → file esiste `wiki/runbooks/fmp-api-quickstart.md` → ✓ VALID
- wiki/runbooks/fmp-api-quickstart.md linea 144: `[[fmp-api]]` → file esiste `wiki/entities/fmp-api.md` → ✓ VALID
- wiki/runbooks/fmp-api-quickstart.md linea 149: `[[gaps]]` → file esiste `wiki/gaps.md` → ✓ VALID

---

## ERROR meccanici (heal-eligible)

**Nessuno rilevato.**

---

## ERROR non meccanici (manuali)

**Nessuno rilevato da questa sessione.**

**Nota:** Pre-existing ERROR noto nel log (2026-05-30 23:59): `fmp-news-media.md:11` citazione malformata (forma `^src: ... — ^src: ...` senza bracket `[` iniziale). Questo error **non è stato introdotto da questa sessione** (file non toccato nel bugfix). Rimane aperto per fix semantico del maintainer wiki.

---

## WARNING (igiene)

**Nessuno rilevato.**

---

## Dettagli verifiche strutturali

### Check 1 — Orphan + wikilink

- **Wiki files scansionati:** 86 (glob `wiki/**/*.md` escluso `log.md`, `index.md`, `query/`, `lint/`)
- **Index.md crosslink:** tutti i 86 file sono linkati da `wiki/index.md` via categoria tematica
- **Wikilink scansionati in files toccati:** 3 (fmp-api-quickstart, fmp-api, gaps) → 3/3 risolvono a file esistenti

**Esito:** 0 orphan, 0 broken-link.

### Check 2 — Claim senza fonte

**Scope:** File toccati contengono solo citazioni a raw, ADR, kanban o appendici — nessun nuovo claim affermativo ≥20 parole senza citazione.

- ADR-016 Appendice A: descrive decisione architetturale consolidamento rate limit — tutte le affermazioni numeriche (280/min, 300/min FMP Starter, margine ~7%) sono guidate dalla citazione della source (operatore account confirmation + policy L4) o ripetono valori precedentemente definiti nel §4.

**Esito:** 0 claim unsourced.

### Check 3 — Integrità kanban

- **EP.md files:** 20 verificati (EP-001 … EP-020). Tutti hanno `id`, `title`, `status`, `priority`, `confidence` in frontmatter. Pattern `id` matcha `EP-XXX` con nome cartella.
- **US.md files:** 91 verificati. Tutti hanno `id`, `title`, `role`, `priority`, `status`, `wiki_page`. `wiki_page` punta a file esistente (se valorizzato).
- **TSK.md files:** 240+ scansionati. Tutti hanno `id`, `sprint`, `layer`, `consumer`, `priority`, `estimate`, `status`. `layer` ∈ {be, fe, db, qa, infra}. `consumer` ∈ {agent, human}.

**Field legacy check:** Nessun TSK contiene campo `team:` (deprecato v2.7).

**Esito:** 0 ERROR frontmatter, 0 id-duplicate, 0 invalid-layer, 0 invalid-consumer.

### Check 4 — Coerenza wiki ↔ kanban

- **US.wiki_page:** tutti i 91 US hanno `wiki_page` valorizzato puntando a file wiki esistente.
- **Wiki "Storie collegate":** scansionati i files toccati (`fmp-api-quickstart.md`, `gaps.md`) — tutte le storie citate (EP-002, EP-009, EP-012) esistono in kanban.

**Esito:** 0 wiki_page missing, 0 storie orfane.

### Check 4b — Coerenza Q ↔ kanban (v2.6)

- **Q aperte:** 0 (lista `[APERTE]` vuota in `management/questions.md`)
- **Q risolte:** 5 (Q_001, Q_002, Q_003, Q_004, Q_005) — tutte hanno campo `**Bloccante:**` (hard | soft) e sono documentate.
- **Stale-blocked-by:** verifica cross-US per campo `blocked_by: [Q_NNN]` — nessun US referenzia Q risolte.
- **pending_clarification:** nessun US toccato in questa sessione ha `pending_clarification`.

**Esito:** 0 missing-blocking-level, 0 stale-blocked-by, 0 orphan-pending-clarification.

### Check 4c — Coerenza topology (v2.7)

- **factory.config.yaml:** `topology: full-stack-agents` (valido).
- **Routing verificato:** be, fe, db, qa, infra → agent (5 routing config, tutti `agent`).
- **Dev-agent presenti:**
  - `.claude/agents/be-dev.md` ✓
  - `.claude/agents/fe-dev.md` ✓
  - `.claude/agents/db-dev.md` ✓
  - `.claude/agents/qa-dev.md` ✓
  - `.claude/agents/infra-dev.md` ✓
- **code_path:** `./src/` (non vuota, coerente con topologia full-stack).

**Esito:** 0 routing-missing-agent, 0 orphan-dev-agent, 0 invalid-topology, 0 dev-agents-without-code-path.

### Check 4d — Coerenza VCS (v2.8)

- **vcs.mode:** `monorepo` (valido).
- **code_path:** `./src/` (relativo, dentro repo — coerente con monorepo).
- **branch_strategy:** `shared` (valido, ∈ {shared, per-tsk, per-sprint}).
- **commit_coupling:** `float` (valido, ∈ {pin, float}).
- **Ultimi develop log entries (wiki/log.md):** 10 entry analizzate — tutte contengono campo `**VCS mode:**` oppure sono pre-v2.8 (retrocompat OK).

**Esito:** 0 vcs-mode-mismatch, 0 invalid-branch-strategy, 0 invalid-commit-coupling, 0 develop-without-vcs-info.

### Check 4e — Coerenza manifest (v2.9)

- **raw/.extraction-manifest.json:** non presente (N/A per questo progetto — ingest via raw PDF e Figma non sfruttato).

**Esito:** N/A (skip, nessun manifest).

### Check 4f — Coerenza Publisher (v2.10)

- **kanban_publish:** `provider: none` → nessun check applicabile.

**Esito:** N/A (provider=none).

### Check 4g — Coerenza scheduler (v2.11)

- **scheduler.enabled:** `true` (valido).
- **scheduler.max_parallel:** 4 (intero ≥1, valido).
- **scheduler.parallel_gate_threshold:** 3 (intero ≥1 e ≤4, valido).
- **scheduler.code_path_conflict:** `strict` (valido, ∈ {strict, warn, off}).
- **scheduler.empty_code_path_policy:** `serial` (valido, ∈ {serial, parallel}).
- **depends_on DAG:** scansione TSK per campo `depends_on` — nessun TSK ha `depends_on` valorizzato (tutti indipendenti al "level 0"). DAG acyclic per default (no cicli, 0 nodi con in_degree > 0 a fine toposort).
- **code_path overlap:** 0 TSK al level 0 condividono lo stesso glob (N/A — no depends_on).

**Esito:** 0 invalid-scheduler-enabled, 0 invalid-max-parallel, 0 invalid-gate-threshold, 0 invalid-conflict-mode, 0 invalid-empty-policy, 0 depends-on-cycle.

---

## Conclusione

**Factory status:** 🟢 **GREEN**

- **Check 1:** 0 ERROR, 0 WARNING
- **Check 2:** 0 ERROR, 0 WARNING
- **Check 3:** 0 ERROR, 0 WARNING
- **Check 4 (wiki↔kanban):** 0 ERROR, 0 WARNING
- **Check 4b (Q↔kanban):** 0 ERROR, 0 WARNING
- **Check 4c (topology):** 0 ERROR, 0 WARNING
- **Check 4d (VCS):** 0 ERROR, 0 WARNING
- **Check 4e (manifest):** N/A
- **Check 4f (Publisher):** N/A
- **Check 4g (scheduler):** 0 ERROR, 0 WARNING

**Citation audit (files toccati):** 7/7 citazioni valide (100%).

**Heal-eligible count:** 0

**Files toccati da questa sessione (bugfix FMP):**
- `design_&_architecture/decisions/ADR-016-fmp-operations-throttling.md` (Appendice A added 2026-05-30)
- `wiki/gaps.md` (sezione fmp-stable-rate-limiting updated 2026-05-30)
- `wiki/runbooks/fmp-api-quickstart.md` (§Rate limiting updated 2026-05-30)
- `management/kanban/EP-012-batch-top-value-picks/US-048-job-notturno-top-picks/TSK-132.md` (SUPERSEDED note added 2026-05-30)
- `management/kanban/EP-012-batch-top-value-picks/US-047-universe-screener-service/TSK-127.md` (FIX note added 2026-05-30)
- `wiki/log.md` (entry bugfix 2026-05-30 21:00)

**Zero structural defects introduced by this bugfix session. All citations valid. Factory ready for next sprint.**
