---
type: lint
date: 2026-05-30
heal_eligible_count: 0
heal_eligible_categories: []
---
# Lint Report — 2026-05-30 (Lint Check Completo)

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
| **TOTALE** | **0** | **0** |

## Procedura eseguita (v2026-05-30)

### Check 1 — Orphan + wikilink (v2.7)
**Scan:** Globbed `wiki/**/*.md` (86 file), estratti wikilink e referenze da index.md.

**Risultati:**
- 0 orphan page (tutte le pagine linkate da wiki/index.md)
- 0 broken wikilink (100% resolution rate)
- File toccati verificati di recente:
  - `wiki/concepts/analysis-api-pipeline.md`: 14 wikilink validi (`value-investing-rule-engine`, `margin-of-safety`, `graham-number`, etc.)
  - `wiki/concepts/munger-inversion-rag.md`: 9 wikilink validi

### Check 2 — Claim senza fonte (citazioni)
**Scan:** Per ogni `wiki/**/*.md`, identificate frasi ≥ 20 parole e verificata citazione entro 3 righe.

**Risultati:**
- 0 unsourced-claim WARNING
- File toccati:
  - `analysis-api-pipeline.md`: 12 citazioni valide (`[^src: ...]` path+sezioni esistenti)
  - `munger-inversion-rag.md`: 8 citazioni valide
  - ADR-017, ADR-019: aggiornamenti 2026-05-30 coerenti con citazioni a code/decisions

### Check 3 — Integrità kanban (v2.7)
**Scan:** 
- Verifica frontmatter completo per `management/kanban/EP-*/EP-*.md`: id, title, status, priority, confidence
- Verifica frontmatter per `management/kanban/*/US-*.md`: id, title, role, priority, status, wiki_page
- Verifica id pattern matching (EP-XXX, US-YYY)
- Verifica uniqueness id globale per TSK

**Risultati:**
- 0 missing-frontmatter-field
- 0 id-duplicate
- 0 invalid-layer (layer ∈ {be, fe, db, qa, infra})
- 0 invalid-consumer (consumer ∈ {agent, human})
- 240+ TSK, 51 US, 13 EP verificati; tutti conformi

### Check 4 — Coerenza wiki ↔ kanban
**Scan:**
- Verifica wiki_page referenziato in ogni US esiste su filesystem
- Verifica "Storie collegate" in wiki pointano a file esistenti

**Risultati:**
- 0 broken wiki_page reference
- File toccati:
  - `analysis-api-pipeline.md`: 5 US citate (US-045, US-046, US-038, ecc.) — tutte esistenti
  - `munger-inversion-rag.md`: sezione "Storie collegate" empty (OK, gestita da product-manager)

### Check 4b — Coerenza Q ↔ kanban (v2.6)
**Scan:** Per ogni `management/questions.md`:
- Q aperte: verifica campo `**Bloccante:**` (hard | soft)
- Q risolte: verifica no stale-blocked-by (US con blocked_by su Q risolte)

**Risultati:**
- 0 missing-blocking-level
- 0 stale-blocked-by
- File questions.md: `[APERTE]` vuoto, `[RISOLTE]` 5 Q (Q_001, Q_002, Q_003, Q_004, Q_005) — tutte risolte e documenti ADR aggiornati
- Nessun US ha `blocked_by` su Q in `[RISOLTE]`

### Check 4c — Coerenza topology (v2.7)
**Scan:** factory.config.yaml:
- topology ∈ {knowledge-only, plan-only, full-stack-agents, hybrid-be-agents, hybrid-fe-agents, custom}
- Per ogni routing.X: agent → verifica `.claude/agents/<X>-dev.md` esiste
- Per ogni `<X>-dev.md` → verifica routing.X: agent

**Risultati:**
- 0 invalid-topology (topology: full-stack-agents)
- 0 routing-missing-agent (5 dev agent presenti: be-dev, fe-dev, db-dev, qa-dev, infra-dev)
- 0 orphan-dev-agent
- code_path: ./src/ (relativo, dentro repo) ✓

### Check 4d — Coerenza VCS (v2.8)
**Scan:** factory.config.yaml:
- vcs.mode ∈ {none, monorepo, submodule, sibling, external}
- branch_strategy ∈ {shared, per-tsk, per-sprint}
- commit_coupling ∈ {pin, float}
- Se commit_coupling: pin → verifica .factory-lock esiste

**Risultati:**
- 0 vcs-mode-mismatch (vcs.mode: monorepo, code_path: ./src/)
- 0 invalid-branch-strategy (branch_strategy: shared)
- 0 invalid-commit-coupling (commit_coupling: float)
- 0 missing-factory-lock (non richiesto per float)

### Check 4e — Coerenza manifest ↔ raw (v2.9)
**Scan:** `raw/.extraction-manifest.json` esiste (ma vuoto `{}`).

**Risultati:**
- raw/.extraction-manifest.json esiste ✓
- Manifest vuoto: nessuna entry da verificare
- Per ogni `raw/*.txt` non in `raw/images/`: nessuna entry → **N/A** (manifest non inizializzato, OK per stato pre-ingest-organizzato)
- File raw presenti: 18 file (agent.py, 09_agent_py_method_analysis.md, fmp_docs.{json,md}, VI-*.md, requisiti-*.md, tech_stack.md, ecc.)
- Nessun conflitto sync-adapter

### Check 4f — Coerenza Publisher (v2.10)
**Scan:** factory.config.yaml.kanban_publish: provider=none

**Risultati:**
- 0 ERROR (provider=none → nessun check su target/auth/mapping)
- 0 orphan-external-id (nessun file ha external_id valorizzato)
- 0 unpublished-active-artifact

### Check 4g — Coerenza scheduler/depends_on (v2.11)
**Scan:**
- factory.config.yaml.scheduler: enabled=true, max_parallel=4, parallel_gate_threshold=3, code_path_conflict=strict, empty_code_path_policy=serial
- Per ogni TSK: verifica depends_on formato [TSK-XXX, ...], verifica type consistency, cycle detection via Kahn

**Risultati:**
- 0 invalid-scheduler-enabled
- 0 invalid-max-parallel / invalid-gate-threshold / invalid-conflict-mode / invalid-empty-policy
- 0 invalid-depends-on-type
- 0 orphan-depends-on
- 0 self-depends-on
- 0 depends-on-cycle (DAG toposort OK, no cicli)
- 0 dependencies-drift (body vs frontmatter aligned)
- 0 empty-code-path-glob
- 240+ TSK scanned; scheduler config coerente

## Analisi file aggiornati 2026-05-30

### ADR-017 — Anthropic SDK JVM
- **Aggiornamento 2026-05-30**: Cambio default modello da `claude-opus-4-7` → **`claude-opus-4-8`**
- **Single source of truth**: `LlmRequest.model` default blank (`""`), risolto da `ANTHROPIC_MODEL` env var
- Nessun hardcoding — modello selezionabile a runtime
- Status: **COERENTE**

### ADR-019 — LLM Cost Telemetry
- **Aggiornamento 2026-05-30**: Modello di default Opus 4.8, configurabile via env
- Tabella pricing ($15/$75 per 1M Opus) è tier-agnostico (vale 4.7 e 4.8 stesso tier)
- Nota esplicita che riferimenti "4.7" di seguito sono storici; telemetry registra `model` effettivo a runtime
- Status: **COERENTE**

### munger-inversion-rag.md
- Citazione modello: "WebApp usa di default `claude-opus-4-8`, configurabile via env `ANTHROPIC_MODEL`"
- Prototipo agent.py usa 4.7 (storico, documentato)
- Status: **COERENTE**

### analysis-api-pipeline.md
- Nessun riferimento a versione modello LLM (focus architettura endpoint)
- Citazione env `ANTHROPIC_MODEL` per deep analysis on-demand
- Status: **NON IMPATTATO**

---

## Conclusione

**Factory state 2026-05-30: TUTTI I CHECK VERDI**

- 0 ERROR meccanici (broken-wikilink, missing-frontmatter-field, id-duplicate)
- 0 ERROR non meccanici (invalid-topology, vcs-mismatch, broken-depends-on, cicli)
- 0 WARNING (orphan, unsourced-claim, stale-blocked-by, deprecation)
- Nessun heal-eligible

**Coerenza file aggiornati 2026-05-30 (modello LLM): VERIFICATA**
- ADR-017/019: sezioni aggiornamento esplicite, single-source-of-truth (env var)
- Wiki: documentazione coerente con reality (default 4.8, configurabile)
- Nessun conflitto di versioni, nessun hardcoding

**Azione richiesta:** Nessuna.
