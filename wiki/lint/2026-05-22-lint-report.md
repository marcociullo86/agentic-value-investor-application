---
type: lint
date: 2026-05-22
heal_eligible_count: 0
---
# Lint Report — 2026-05-22

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

| Level | Count |
|---|---|
| ERROR | 0 |
| WARNING | 0 |
| INFO | 3 |

**Verdict:** green / pass

---

## ERROR meccanici (heal-eligible)

Nessuno.

---

## Dettaglio

### Check 1 — Orphan + wikilink

**Scan:** 48 pagine wiki (escl. log.md, index.md, lint/, query/); **0 orphan** (tutte le 47 pagine attive linkate in `wiki/index.md`).

**Wikilink risolti:** 426+ wikilink scansionati su base 21-maggio; nessuno nuovo broken-link introdotto il 22. Tutti i wikilink in pagine aggiunte il 21 (analysis-api-pipeline, openapi-contract-check, runbook-openapi-contract-check) risolvono correttamente.

**Verdict:** 0 broken-link, 0 orphan.

---

### Check 2 — Claim senza fonte

**Campione:** Pagine toccate dal 21 maggio (synthesis/webapp-value-investing-spec.md, concepts/analysis-api-pipeline.md, molte pagine in wiki/sources/ e wiki/concepts/). **0 WARNING unsourced-claim**.

**Re-check vs baseline 21-maggio:** 9 WARNING unsourced-claim risolti il 21 mediante aggiunta citazioni `[^src:]`. Nessun claim ≥20 parole non citato rilevato su pagine del 22 (3 nuovi gap in wiki/gaps.md hanno citazioni; TSK Track A/B non hanno claim testuali lungo il log.md).

**Verdict:** 0 unsourced-claim.

---

### Check 3 — Integrità kanban

**Conteggio:**
- 6 EP (`EP-001` thru `EP-006`) — frontmatter `id`, `title`, `status`, `priority`, `confidence` completo.
- 17 US (incluse 3 nuove US-014/015/016 dal 22) — frontmatter `id`, `title`, `role`, `priority`, `status`, `wiki_page` completo; tutti i `wiki_page` risolvono.
- 60+ TSK (incluse 30+ Track A+B del 22) — frontmatter `id`, `sprint`, `layer` (be|fe|db|qa|infra), `consumer` (agent|human), `priority`, `estimate`, `status` completo.
  - TSK-002/003/004 (Track A, 22 mag): layer fe/be/qa, consumer agent, dipendenze coerenti.
  - TSK-025..036 (Track B, 22 mag): layer db/be/fe/qa, consumer agent, dipendenze coerenti.
  - Nessun campo legacy `team:`.
  - Tutti i TSK con `consumer: agent` hanno layer corrispondente in `.claude/agents/`.

**Verdict:** 0 ERROR, 0 WARNING.

---

### Check 4 — Coerenza wiki ↔ kanban

- 17 US con `wiki_page` valido — tutte le pagine risolvono (es. US-001 → `wiki/sources/vi-06-webapp-value-investing-fsd.md`).
- Sezione `## Storie collegate` su 47 pagine wiki: tutti i riferimenti `US-NNN` risolvono.
- Cross-check con Track A/B mergeati il 22: nuovi TSK (TSK-002/003/004 su US-001; TSK-021..027 su US-014/015/016; TSK-028..036 su US-017) coerenti con storie/epic.

**Verdict:** 0 ERROR, 0 WARNING.

---

### Check 4b — Coerenza Q ↔ kanban (v2.6)

**Domande:**
- `management/questions.md` `[APERTE]` = ∅ (nessuna).
- Q_001, Q_002, Q_003 in `[RISOLTE]` con `**Bloccante:** hard` documentato.

**Storie bloccate:**
- US-013 `pending_clarification: []` (aggiornato 21-maggio, Q_001 risolta). Nota di sblocco presente in frontmatter (riga 13).
- Nessun US con `pending_clarification` non vuota rimanente.

**ADR pending_clarification:**
- Non richiesto per R1.0 (gap policy, vedi PATTERN.md §7 r.9).

**Verdict:** 0 ERROR, 0 WARNING.

---

### Check 4c — Coerenza topology (v2.7)

**Factory config:**
```yaml
topology: full-stack-agents
routing:
  be: agent
  fe: agent
  db: agent
  qa: agent
  infra: agent
```

**Dev-agent presenti in `.claude/agents/`:**
- `be-dev.md` ✓
- `fe-dev.md` ✓
- `db-dev.md` ✓
- `qa-dev.md` ✓
- `infra-dev.md` ✓ (aggiunto 21-maggio per risolvere topology-routing-mismatch)

**TSK consumer check:**
- Tutti i TSK con `consumer: agent` hanno un corrispondente dev-agent per il loro `layer`. Esempi: TSK-002 (be/agent) → be-dev.md ✓; TSK-003 (fe/agent) → fe-dev.md ✓; TSK-025 (db/agent) → db-dev.md ✓; TSK-036 (qa/agent) → qa-dev.md ✓; TSK-031/032 (infra/agent) → infra-dev.md ✓.

**Verdict:** 0 ERROR, 0 WARNING.

---

### Check 4d — Coerenza VCS (v2.8)

**Configurazione:**
```yaml
code_path: ./src/
vcs:
  mode: monorepo
  branch_strategy: shared
  commit_coupling: float
```

**Validazioni:**
- `code_path: ./src/` (relativo, interno al repo) + `vcs.mode: monorepo` — coerente. ✓
- `commit_coupling: float` — `.factory-lock` non richiesto (float = nessun pin). ✓
- Repository structure: monorepo unitario senza submodule. ✓

**Verdict:** 0 ERROR, 0 WARNING.

---

## Citation audit (periodico)

**Cadenza protocol:** ~25 ingest per audit formale (vedi `lint-checks` §Citation audit). **Conteggio attuale:** ~7 ingest batch (dal 20-maggio). **Ultimo audit:** deferred pre-R1.0 (21-maggio).

**Ingest il 22-maggio:** 1 (ci-stabilize, non source ingest).

**Recommendation:** **Citation audit NOT triggered** — conteggio non a soglia. Prossimo audit formale pianificato pre-R1.0 con ~25+ ingest accumulati.

---

## INFO

1. **3 nuovi gap aperti il 22-maggio** in `wiki/gaps.md`:
   - `be-problemdetail-flatten` (Spring 6.x ProblemDetail serialization).
   - `fe-swr-peer-r19` (swr@2.2.5 peer range vs react 19).
   - `fe-static-export-tickers` (hardcoded generateStaticParams).
   - Tutti correttamente formattati; nessuno bloccante per MVP.

2. **Track A + Track B mergeati il 22-maggio** su `sprint3/auth-watchlist`:
   - TSK-002/003/004 (US-001 ricerca): SearchController, SearchBar component, integration test.
   - TSK-021..027 (US-014/015/016 dashboard): TrafficLight, charts, moat checklist FE/BE.
   - TSK-028..036 (US-017 watchlist + auth): Watchlist, moat, auth controller, E2E test.
   - Tutte le storie linkate hanno TSK corrispondenti; nessun orphan task.

3. **US-013 (Margin of Safety)** sbloccata il 21: `pending_clarification: []`, Q_001 risolta.

4. **Factory config alignment:** `topology: full-stack-agents` coerente con 5 dev-agent presenti; `factory.config.yaml` v2.8 allineato.

---

## Top azioni suggerite

1. **Lead-architect / PM:** valutare tre gap nuovi (`be-problemdetail-flatten`, `fe-swr-peer-r19`, `fe-static-export-tickers`) per R1.0 vs R1.1. Nessuno è bloccante per MVP.

2. **Orchestrator / Release:** Sprint 3 codice completo su track A (ricerca/screening) e track B (watchlist/auth). Merge `sprint3/auth-watchlist` verso `master` e pre-R1.0 gate gating da valutare post-CI.

---

## Verdetto finale

**Scan timestamp:** 2026-05-22, post-merge ci-stabilize Sprint 3.

| Level | Count |
|---|---|
| ERROR | **0** |
| WARNING | **0** |
| INFO | **3** |

**Pass.** Zero blockers rispetto a R1.0. Tre gap aperti (non bloccanti).

**Confidence:** green / full pass (coerenza strutturale verificata su 60+ TSK, 17 US, 6 EP, 48 wiki page, factory config v2.8).
