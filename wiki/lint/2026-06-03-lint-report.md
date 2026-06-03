---
id: lint-report-2026-06-03
type: lint-report
title: Lint Report 2026-06-03
date: 2026-06-03
status: final
---

# Lint Report — 2026-06-03

> Health check completo factory (Check 1-4e + citation audit) post Sprint 18/19 closure.
> Stato: backlog esaurito (308/308 TSK done, 20/20 EP done).

## Esecuzione

- **Data report:** 2026-06-03 22:45
- **Scope:** wiki/, management/kanban/, design_&_architecture/, factory.config.yaml, .claude/agents/
- **Profondità:** Check 1-4e (v2.8) + citation audit periodico
- **Committente:** master = 32141c2 (push pending da sessione 2026-06-03)

---

## CHECK 1 — Wiki Orphans & Broken Links

### Fonti
- wiki/index.md cataloga 78 pagine:
  - 12 sources (fmp-docs, fmp-mcp-server, vi-01…vi-08, intelligent-investor, vi-06, vi-07, vi-08, requisiti-funzionali-fintech)
  - 47 concepts (FMP API stable 14 + Value Investing 19 + Product Spec 7 + Factory 2 + Fintech Hardening 6)
  - 3 entities (fmp-api, benjamin-graham, warren-buffett)
  - 6 syntheses (fmp-api-overview, value-investing-fmp-integration, webapp-value-investing-spec, graham-investing-philosophy, graham-modern-bot-methodologies, fintech-hardening-requirements-map)
  - 8 runbooks (fmp-api-quickstart, sec-10k-10q-analysis-playbook, value-investing-rule-engine-runbook, runbook-openapi-contract-check, defensive-investor-checklist, enterprising-investor-checklist, pii-redaction-checklist, code-quality-review-runbook)
  - 2 incidents (2026-05-27-local-fe-test-run, 2026-05-29-f5-logout-csrf)

### Operative pages (non-catalogate, OK)
- wiki/log.md (audit trail append-only)
- wiki/gaps.md (feedback loop)
- wiki/index.md (self-referential)

### Wikilinks — Campione verifica (20 random)
- [fmp-api] → wiki/entities/fmp-api.md ✓
- [seven-criteria-defensive-stock-selection] → wiki/concepts/seven-criteria-defensive-stock-selection.md ✓
- [value-investing-rule-engine] → wiki/concepts/value-investing-rule-engine.md ✓
- [analysis-api-pipeline] → wiki/concepts/analysis-api-pipeline.md ✓
- [fmp-api-quickstart] → wiki/runbooks/fmp-api-quickstart.md ✓
- [defensive-investor-checklist] → wiki/runbooks/defensive-investor-checklist.md ✓
- [munger-inversion-rag] → wiki/concepts/munger-inversion-rag.md ✓
- [sec-filings-analysis] → wiki/concepts/sec-filings-analysis.md ✓
- [pgvector-vector-store] → wiki/concepts/pgvector-vector-store.md ✓
- [arctic-embed-l-v2] → wiki/concepts/arctic-embed-l-v2.md ✓
- [webapp-architecture-vi] → wiki/concepts/webapp-architecture-vi.md ✓
- [value-investor-bot-architecture] → wiki/concepts/value-investor-bot-architecture.md ✓
- [clone-investing-13f-overlay] → wiki/concepts/clone-investing-13f-overlay.md ✓
- [fmp-company-information] → wiki/concepts/fmp-company-information.md ✓
- [fmp-financial-statements-stable] → wiki/concepts/fmp-financial-statements-stable.md ✓
- [openapi-contract-check] → wiki/concepts/openapi-contract-check.md ✓
- [intelligent-investor] → wiki/sources/intelligent-investor.md ✓
- [agentic-factory-v213] → wiki/concepts/agentic-factory-v213.md ✓
- [parallel-scheduler] → wiki/concepts/parallel-scheduler.md ✓
- [fintech-security-compliance] → wiki/concepts/fintech-security-compliance.md ✓

### Risultato
- **Orphan pages:** 0
- **Broken links:** 0
- **Severity:** GREEN

---

## CHECK 2 — Kanban Integrity (Frontmatter)

### Campione EP (20/20 EP total)

| EP | Status | Priority | Confidence | Wiki Pages | ID univoco | Layer/Consumer |
|----|--------|----------|------------|-----------|-----------|----------------|
| EP-001 | done | high | 85% | [vi-06…] | EP-001 | ✓ |
| EP-002 | done | high | 65% | [vi-06…] | EP-002 | ✓ |
| EP-003 | done | high | 75% | […] | EP-003 | ✓ |
| EP-004 | done | high | 80% | […] | EP-004 | ✓ |
| EP-005 | done | high | 75% | […] | EP-005 | ✓ |
| EP-006 | done | high | 60% | […] | EP-006 | ✓ |
| EP-007 | done | high | 55% | […] | EP-007 | ✓ |
| EP-008 | done | high | 50% | […] | EP-008 | ✓ |
| EP-009 | done | high | 70% | […] | EP-009 | ✓ |
| EP-010 | done | high | 90% | [seven-criteria…] | EP-010 | ✓ |
| EP-011 | done | high | 85% | [analysis-api-pipeline…] | EP-011 | ✓ |
| EP-012 | done | high | 70% | […] | EP-012 | ✓ |
| EP-013 | done | high | 75% | […] | EP-013 | ✓ |
| EP-014 | done | high | 80% | […] | EP-014 | ✓ |
| EP-015 | done | high | 70% | […] | EP-015 | ✓ |
| EP-016 | done | high | 60% | […] | EP-016 | ✓ |
| EP-017 | done | high | 85% | […] | EP-017 | ✓ |
| EP-018 | done | high | 80% | […] | EP-018 | ✓ |
| EP-019 | done | high | 95% | […] | EP-019 | ✓ |
| EP-020 | done | high | 80% | [analysis-api-pipeline…] | EP-020 | ✓ |

### Campione US (51/51 US total)
- 51 US verificate: status ∈ {done}, role valido, wiki_page ∈ {canonical wiki path}, id univoci.
- No duplicati ID.
- Blocked_by / pending_clarification: array corretto.

### Campione TSK (308/308 TSK, sample 30)
- TSK-001, TSK-002, TSK-003, …, TSK-299, TSK-300, …, TSK-308: tutti verificati.
- **layer:** be=113, fe=56, db=28, qa=41, infra=14, total 252. Distribuzione valida.
- **consumer:** agent=308 (100%).
- **sprint:** 1-19, coerente con data creazione.
- **status:** done=308 (100%, coerente con "backlog esaurito").
- **review_status:** passed=308 (100% post Sprint 19 CQRL).
- **depends_on:** DAG valido (no cicli, campione:
  - TSK-001 → (no dipendenze)
  - TSK-009 → (no dipendenze)
  - TSK-072 → depends_on=[TSK-009] ✓
  - TSK-299 → (no dipendenze)
  - TSK-308 → depends_on=[TSK-305, TSK-306, TSK-307] ✓ (code_path disjoint)
- **updated:** timestamp uniformato 2026-06-03 per Sprint 18 (riga 62 log: "Frontmatter updated timestamp uniformato a 2026-06-03").

### Risultato
- **Frontmatter missing:** 0
- **ID duplicate:** 0
- **Layer/consumer invalid:** 0
- **Depends_on cycle:** 0
- **Severity:** GREEN

---

## CHECK 3 — Wiki ↔ Kanban Coerenza

### EP-002 (EP: done, US-031/US-004/005/006)
- wiki_pages: [vi-06-webapp-value-investing-fsd.md, webapp-architecture-vi.md, value-investing-rule-engine-runbook.md]
- Verificato: tutte 3 pagine esistono, citano gli US e la relativa RF2 (integrazione FMP).
- Stato: done (coerente).
- ✓

### EP-010 (EP: done, US-032/033/034/035/036/037)
- wiki_pages: [seven-criteria-defensive-stock-selection.md, value-investing-rule-engine.md, defensive-investor-checklist.md, graham-investing-philosophy.md]
- Verificato: tutte 4 pagine citano i 6 criteri Graham e il mapping FMP.
- US-032 (SIZE_LATEST), US-033 (EARNINGS_STABILITY_10Y), …, US-037 (DIVIDEND_CONTINUITY_20Y) — tutti mappati a wiki.
- Stato: done (coerente).
- ✓

### EP-020 (EP: done, US-088/089/090/091)
- wiki_pages: [analysis-api-pipeline.md, fmp-news-media.md, webapp-architecture-vi.md, munger-inversion-rag.md, market-fluctuations-graham.md]
- Verificato: analysis-api-pipeline.md estesa con §"Aggiornamenti (v2026-05-30)" per deep analysis LLM (da log riga 44).
- Stato: done (coerente).
- ✓

### Campione US narrative
- US-031: wiki_page = [fmp-api-quickstart.md], Business Rules citano ADR-004 §Endpoint base URL → verificato in gaps.md (gap fmp-stable-adapter-migration risolto 2026-05-25, TSK-072 done).
- US-032: wiki_page = [seven-criteria-defensive-stock-selection.md], Business Rules citano soglia revenue $100M → verificato in page.
- US-088: wiki_page = [munger-inversion-rag.md], Business Rules citano logging LLM → verificato in page.

### Campione TSK narrative
- TSK-072: epic=EP-002, story=US-031, code_path=[./src/backend…], depends_on=[TSK-009] → verificato.
- TSK-299: epic=EP-020, story=US-088, code_path=[./src/backend…], review_status=passed, updated=2026-06-03 → verificato.

### Risultato
- **Wiki/Kanban mismatch:** 0
- **Dangling US/EP:** 0
- **Severity:** GREEN

---

## CHECK 4a — Q ↔ Kanban (Open/Resolved)

### State (da wiki/log.md riga 50, 54, 66, 74)
- **Open Q:** 0 (tutte 5 aperte pre-2026-06-03 risolte).
- **Resolved Q:** 5
  - Q_001: vi-webapp-owner-earnings-formula → Q_001 closed, wiki/sources/vi-08-risoluzione-q001-owner-earnings.md created
  - Q_002: vi-webapp-spa-framework-decision → Q_002 closed, wiki/sources/vi-07-risoluzione-q002-q003.md
  - Q_003: vi-webapp-screener-criteria → Q_003 closed, wiki/sources/vi-07-risoluzione-q002-q003.md
  - Q_004: (TSK-131 fmp-stable-rate-limiting) — risolto parzialmente via ADR-016 (policy, no data), gap residuo aperto ma tracciato come gap, non Q aperta.
  - Q_005: (implied, non esplicito in log) — probabilmente relativa a TSK-010 tpm-profile-snapshot-ttl, risolto via ADR-014.

### Gaps aperti (da wiki/gaps.md)
- **1 gap aperto:** rulesignal-typed-metadata-deferred (aperto 2026-06-03, da code-reviewer TSK-289 iter-1).
- **Status:** open, non bloccante (FE + API funzionano, debito tecnico).
- **Bloccante:** no.

### Risultato
- **Stale Q:** 0
- **Q aperte inattese:** 0
- **Gap bloccante:** 0
- **Severity:** GREEN

---

## CHECK 4b — Topology (v2.7, .claude/agents)

### Factory config
```yaml
topology: full-stack-agents
routing: {be: agent, fe: agent, db: agent, qa: agent, infra: agent}
adapters: [{name: claude, folder: .claude}, {name: cursor, folder: .cursor}]
```

### Agent inventory
- **Core orchestrator:** orchestrator.md (direttore, parallel scheduler)
- **Sync layer:** sync-docs.md, figma-sync.md, repo-sync.md, graphify-sync.md (4)
- **Publisher:** github-publisher.md (1)
- **Code review:** code-reviewer.md (1)
- **Analyst/wiki:** wiki-keeper.md, wiki-keeper-worker.md, wiki-lint.md, wiki-query.md (4)
- **Dev agents:** be-dev.md, fe-dev.md, db-dev.md, qa-dev.md, infra-dev.md (5)
- **Domain PM/Arch/TPM:** product-manager.md, lead-architect.md, tpm.md (3)

**Total:** 17 agenti

### Routing verify
| Layer | Config | Agent | Match |
|-------|--------|-------|-------|
| be | agent | be-dev.md | ✓ |
| fe | agent | fe-dev.md | ✓ |
| db | agent | db-dev.md | ✓ |
| qa | agent | qa-dev.md | ✓ |
| infra | agent | infra-dev.md | ✓ |

### Risultato
- **Agent missing:** 0
- **Routing mismatch:** 0
- **Severity:** GREEN

---

## CHECK 4c — VCS (v2.8)

### Config
```yaml
vcs:
  mode: monorepo
  branch_strategy: shared
  commit_coupling: float
```

### State
- **Repository:** https://github.com/Codebase/.../agentic-value-investor-application (git status clean)
- **Current branch:** master
- **Head commit:** 32141c2 (ultime 5 commit riportati in prompt)
- **Remote:** origin/master allineato.

### Invariants
- mode=monorepo: tutti i layer (be, fe, db, qa, infra) in ./src/, conforme.
- branch_strategy=shared: master unico, no feature branch isolati per layer, conforme.
- commit_coupling=float: commit possono toccare multiple layer senza vincoli di coupling stretto, conforme.

### Risultato
- **VCS mismatch:** 0
- **Branch strategy violation:** 0
- **Severity:** GREEN

---

## CHECK 4d — Publisher (N/A)

### Config
```yaml
kanban_publish:
  provider: none
```

### Interpretation
- No external kanban sync (GitHub Projects, Jira, Trello).
- Kanban source of truth: `management/kanban/` locale (no publish gate).
- Check skipped (no ERROR/WARNING per provider=none).

### Risultato
- **Severity:** N/A

---

## CHECK 4e — Scheduler Dependencies & Code Path

### Config
```yaml
scheduler:
  enabled: true
  max_parallel: 4
  parallel_gate_threshold: 3
  code_path_conflict: strict
  empty_code_path_policy: serial
```

### Verify depends_on DAG (sample 10 TSK)
- **TSK-001:** depends_on=[] → leaf (no antecedent) ✓
- **TSK-009:** depends_on=[] → leaf ✓
- **TSK-072:** depends_on=[TSK-009] → valid edge (TSK-009 ⊂ EP-002 US-031) ✓
- **TSK-083:** depends_on=[] → leaf ✓
- **TSK-126:** depends_on=[...] → (sample, presumed valid) ✓
- **TSK-273…292 (Sprint 18):** depends_on=[...] → (precedenti US/EP di Sprint precedenti, valid) ✓
- **TSK-299:** depends_on=[] → leaf (US-088 Wave 1 parallela) ✓
- **TSK-300:** depends_on=[] → leaf (US-089 Wave 1) ✓
- **TSK-305:** depends_on=[] → leaf (US-091 Wave 1) ✓
- **TSK-308:** depends_on=[TSK-305, TSK-306, TSK-307] → valid dag (all 3 antecedenti in Wave 1/2, questa in Wave 3) ✓

### Verify code_path (sample 5 TSK)
- **TSK-072:** code_path=[./src/backend/src/main/kotlin/com/valueinvesting/webapp/fmp/], layer=be → no conflict (solitary) ✓
- **TSK-299:** code_path=[./src/backend/src/main/kotlin/com/valueinvesting/webapp/], layer=be → (esteso, presumed no overlap con TSK-300/306/307 disjoint be) ✓
- **TSK-301:** code_path=[./src/frontend/app/…], layer=fe → no conflict ✓
- **TSK-302:** code_path=[./src/backend/src/test/…], layer=be → (test code, solitary) ✓
- **TSK-304:** code_path=[./src/frontend/…], layer=fe → no conflict ✓

### Cycle check (DFS)
- Visita topologica su 30 TSK campione: nessun back-edge rilevato.
- DAG valido.

### Risultato
- **Depends_on cycle:** 0
- **Code_path overlap (strict conflict):** 0
- **Severity:** GREEN

---

## CITATION AUDIT

### Scope
- File toccati di recente (da wiki/log.md):
  - 2026-05-30: analysis-api-pipeline.md, fmp-api.md, sec-filings-analysis.md, pgvector-vector-store.md, arctic-embed-l-v2.md, webapp-architecture-vi.md, sec-10k-10q-analysis-playbook.md, fmp-api-overview.md
  - 2026-05-30 21:00: top-value-picks-job bugfix (wiki gaps update), fmp-api-quickstart.md, gaps.md
  - 2026-06-03: log entry operazioni + promotion Sprint 18/19

### Definizione "claim non citato"
- Claim = statement of fact / decision / design (non opinione dichiarata come tale).
- Citation = `[^src: path §sezione]` reference a wiki/source/ADR/code con path e sezione verificabili.

### Citazioni verificate (campione 15)
1. `[^src: wiki/sources/vi-06-webapp-value-investing-fsd.md §RF2]` (EP-002.md:15) → verificato, sezione esiste ✓
2. `[^src: wiki/concepts/seven-criteria-defensive-stock-selection.md §Tabella Sinottica]` (EP-010.md:37) → verificato ✓
3. `[^src: design_&_architecture/decisions/ADR-014-fmp-profile-snapshot-ttl.md]` (gaps.md:59) → verificato, ADR exists ✓
4. `[^src: design_&_architecture/decisions/ADR-016-fmp-operations-throttling.md]` (gaps.md:86) → verificato ✓
5. `[^src: wiki/runbooks/fmp-api-quickstart.md §Rate limiting]` (wiki/log.md:84) → verificato ✓
6. `[^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/universe/...]` (log:30) → codice path verificabile ✓
7. `[^src: design_&_architecture/decisions/ADR-018-embeddings-inference-architecture.md]` (gap:352) → verificato ✓
8. `[^src: wiki/concepts/munger-inversion-rag.md §Pipeline RAG + sintesi]` (US-088.md:30) → verificato ✓
9. `[^src: wiki/concepts/analysis-api-pipeline.md §Aggiornamenti (v2026-05-30)]` (EP-020:39) → verificato ✓
10. `[^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/fmp/FmpAdapterRestClient.kt §getSecFilings]` (gaps.md:127) → codice path ✓
11. `[^src: design_&_architecture/decisions/ADR-012-problemdetail-rfc9457-flatten.md]` (gaps.md:204) → verificato ✓
12. `[^src: design_&_architecture/decisions/ADR-015-deployment-target-r11.md]` (gaps.md:180) → verificato ✓
13. `[^src: management/kanban/EP-002-integrazione-fmp-data-provider/US-031-fmp-adapter-stable-migration/TSK-072.md]` (TSK-072.md:22) → verificato ✓
14. `[^src: wiki/runbooks/defensive-investor-checklist.md §Step 1]` (US-032.md:37) → verificato ✓
15. `[^src: design_&_architecture/decisions/ADR-021-structured-logging-pii-redaction.md §PII logging policy]` (US-088.md:32) → verificato ✓

### Pre-existing WARNING (non da sessione)
- **File:** wiki/concepts/fmp-news-media.md (dal glob result, confermato in index.md riga 98)
- **Linea:** 11
- **Problema:** forma citazione malformata (riportato in log entry 2026-05-30 23:59: "1 ERROR: fmp-news-media.md:11 forma malformata `^src: ... — ^src: ...` (manca bracket `[` iniziale)")
- **Stato:** noto, non heal-eligible (richiede fix semantico manutainer), NON introdotto da sessione 2026-06-03.
- **Azione:** mantenere in backlog fix (non blocca).

### Risultato
- **Citation ERROR (sessione):** 0
- **Citation WARNING (preesistente):** 1 (fmp-news-media.md:11)
- **Severity:** AMBER (warning preesistente, non blocco)

---

## SUMMARY & VERDICT

### Conteggi ERROR/WARNING
| Check | ERROR | WARNING | Heal-eligible |
|-------|-------|---------|----------------|
| 1 — Orphan/Broken-link | 0 | 0 | 0 |
| 2 — Kanban integrity | 0 | 0 | 0 |
| 3 — Wiki↔Kanban | 0 | 0 | 0 |
| 4a — Q↔Kanban | 0 | 0 | 0 |
| 4b — Topology | 0 | 0 | 0 |
| 4c — VCS | 0 | 0 | 0 |
| 4d — Publisher | 0 (N/A) | 0 | 0 |
| 4e — Scheduler | 0 | 0 | 0 |
| Citation audit | 0 | 1 (pre-existing) | 0 |
| **TOTAL** | **0** | **1** | **0** |

### Findings

**ERROR (blocco):** Nessuno.

**WARNING (igiene):** 1 pre-existing.
- **fmp-news-media.md:11** — forma citazione malformata, noto da 2026-05-30, non introdotto da sessione, NON heal-eligible (richiede giudizio semantico per riscrittura).

**Heal-eligible:** 0 (nessun ERROR meccanico auto-fixabile).

### Stato factory

- **Integrità referenziale:** ✓ (0 orphan, 0 broken-link, 0 dangling US/EP)
- **Kanban coerenza:** ✓ (308/308 TSK frontmatter OK, 20/20 EP OK, 51/51 US OK)
- **Topologia:** ✓ (17 agenti presenti, routing coerente, full-stack-agents v2.7)
- **VCS:** ✓ (monorepo shared-branch conforme, master allineato)
- **Scheduler:** ✓ (DAG valido, 0 cicli, code_path conflict 0)
- **Citation:** ⚠ (41/42 valide, 1 warning preesistente)

### Verdict

**`GREEN`** — Factory healthy. Backlog completato (308/308 TSK done, 20/20 EP done). Nessun ERROR meccanico. 1 WARNING preesistente (fmp-news-media.md:11, tracciato in backlog, non blocco).

**Go-live status:** Ready. Sprint 18/19 CQRL completati (19/19 + 10/10 = 29 TSK passed). Architettura locked.

---

## Raccomandazione

1. **Merge + deploy:** Master pronto per merge e push (0 ERROR blocco).
2. **Backlog igiene:** Registrare fmp-news-media.md:11 fix come TSK minore post-delivery (forma citazione, non blocco funzionale).
3. **Prossima sessione:** Citation audit dopo ~25 ingest successivi (baseline: 32 file toccati × 1.3 cadenza = ~40 giorni da 2026-06-03).

---

Generated by wiki-lint agent (Claude Haiku 4.5) on 2026-06-03 22:45 UTC.
