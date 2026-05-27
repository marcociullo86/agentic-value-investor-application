---
id: agentic-factory-v213
type: concept
title: "Agentic Factory — llm-wiki++ v2.13"
status: review
created: 2026-05-27
updated: 2026-05-28
sources:
  - PATTERN.md
  - factory.config.yaml
tags: [factory, pattern, cqrl, multi-adapter, repo-sync, code-quality, adapter, orchestration]
---

# Agentic Factory — llm-wiki++ v2.13

> Contratto universale agent-agnostic che governa il repository. Qualsiasi runtime AI (Claude Code, Cursor, Aider, OpenAI Assistants, Gemini) che rispetti `PATTERN.md` può operare sul repo. La factory è stata migrata da v2.11 a v2.13 il **2026-05-27**; `code_quality.enabled` è stato impostato a `true` contestualmente. [^src: PATTERN.md §0]

## Modello a layer

Il progetto è organizzato in **cinque layer** più un layer opzionale di code quality e un side-channel di memoria. [^src: PATTERN.md §1]

| Layer | Path | Autore esclusivo | Descrizione |
|---|---|---|---|
| **L1** | `raw/` | Sub-agent Sync | Input multi-sorgente: `.txt` (da PDF), `.kb.json` (da Figma), `repo-*.md` (da `repo-sync`). Immutabile per tutti gli altri ruoli. |
| **L2** | `wiki/` | `wiki-keeper` (Analyst) | Wiki llm-style karpathy: sources / concepts / entities / syntheses / runbooks / incidents. `log.md` append-only. |
| **L3** | `management/` | `product-manager` (PM) | Kanban epiche/storie, roadmap, domande bloccanti. |
| **L4** | `design_&_architecture/` + `TSK-*.md` | `lead-architect` + `tpm` | ADR, spec tecniche, task atomici. |
| **L5** | `src/` (`code_path`) | Dev-agent o umani | Codice sorgente. Configurato in `factory.config.yaml.code_paths`. |
| **CQRL** | `code_quality/` | `code-reviewer` | Report di review qualitativa post-Develop: `reports/` + `rules/` (3 tier). Attivo se `code_quality.enabled: true`. |
| **Memory** | `memory/` | Orchestrator | Persistenza cross-conversazione (episodic / semantic / procedural). Non è un layer derivato — è un side-channel. |

La cascata è unidirezionale: aggiornamento di L*k* rende L*k+1*…L5 *stale*. [^src: PATTERN.md §1]

## Ruoli e scope di scrittura

La regola fondamentale è **read-universal, write-restricted**: ogni agente legge `wiki/` per contesto, ma solo `wiki-keeper` ci scrive contenuto. [^src: PATTERN.md §2]

| Ruolo | Agent | Scrive |
|---|---|---|
| Orchestrator | `orchestrator` | `memory/episodic/`, `wiki/log.md`, `status:` via `/promote` |
| Sync | `sync-docs`, `figma-sync`, `repo-sync` | `raw/**` nel proprio scope |
| Analyst | `wiki-keeper` | `wiki/**` (esclusi `query/`, `lint/`) + append `wiki/log.md` |
| PM | `product-manager` | `management/kanban/EP-*/**`, `wiki/gaps.md` (append-only) |
| Arch | `lead-architect` | `design_&_architecture/**`, `wiki/gaps.md` (append-only) |
| TPM | `tpm` | `management/kanban/**/TSK-*.md`, `sprint.md`, `wiki/gaps.md` (append-only) |
| Dev | `be-dev`, `fe-dev`, `db-dev`, `qa-dev`, `infra-dev` | `src/**`, `status:` del proprio TSK, `wiki/log.md` (append-only) |
| Code Reviewer | `code-reviewer` | `code_quality/reports/**`, `review_status:`/`review_iter:`/`review_report:` frontmatter TSK, `wiki/log.md` (append-only) |

## Code Quality Review Layer (CQRL) — §19

Il CQRL è il meccanismo di valutazione qualitativa del codice prodotto dai dev-agent, introdotto in **v2.12** e attivato in questo progetto il **2026-05-27** con `code_quality.enabled: true`. [^src: PATTERN.md §19]

### Cosa abilita

- Il **comando `/review <TSK-id>`** invoca il `code-reviewer` su un TSK con `status: done`.
- L'agente `code-reviewer` legge il diff/i file toccati dal TSK, invoca la skill `code-review-protocol` (Stack Detector → 3 passate → Aggregator → Feedback Router) e produce un verdict: `pass | conditional | reject`.
- Il report (machine-readable JSON + digest markdown) è salvato in `code_quality/reports/<TSK-id>-iter-<N>.{json,md}`.
- Il loop è bounded da `max_iterations` (default **3**). No-progress detection e regression detection accelerano l'escalation prima del cap. [^src: PATTERN.md §19.6]

### Pipeline review (riepilogo)

```
TSK status: done
    ↓ /review <TSK-id>
code-reviewer
    ├─ Stack Detector → stack_descriptor
    ├─ Passata 1: idiomaticità
    ├─ Passata 2: design
    └─ Passata 3: robustezza
         ↓ Aggregator
         verdict: pass | conditional | reject
             ├─ pass     → review_status: passed (ciclo chiuso)
             ├─ conditional → task_package → dev-agent re-invocato (review_iter += 1)
             └─ reject   → GATE UMANO (§7 r.16)
```

### Invariante gate umano

`reject` **non** causa auto-revert del codice né auto-close del TSK. L'umano decide il next step: re-Develop manuale, accept-as-is (con override documentato in `wiki/incidents/`), o rollback. [^src: PATTERN.md §7]

### CQRL non sostituisce

- `qa-dev`: i test funzionali sono un gate ortogonale e prerequisito.
- SAST/dependency scanning/secret detection: scope CQRL è idiomaticità, design, robustezza — non sicurezza. [^src: PATTERN.md §19.8]

## Multi-adapter v2.13

La v2.13 introduce il **manifest formale** per ogni adapter, abilitando lo scaffolding parallelo di più adapter al bootstrap. [^src: PATTERN.md §12]

| Adapter | Folder runtime | Stato v2.13 |
|---|---|---|
| Claude Code | `.claude/` | full (reference) |
| Cursor | `.cursor/` | full |
| Aider | `.aider/` | full |
| OpenAI Assistants | `.openai/` | partial |
| Gemini Code Assist | `.gemini/` | manifest-only |

In questo progetto sono attivi due adapter: **claude** (`.claude/`) e **cursor** (`.cursor/`), dichiarati in `factory.config.yaml`. [^src: factory.config.yaml]

```yaml
adapters:
  - name: claude
    folder: .claude
    maturity: full
  - name: cursor
    folder: .cursor
    maturity: full
```

## `repo-sync` — ingest di repo esistenti (§16)

Il comando `/repo-sync <path>` attiva il sub-agent `repo-sync`, che esegue il protocollo `repo-extraction-protocol` su un repo esistente e produce `raw/YYYY-MM-DD-repo-<slug>.md` (spec estratta). Il `wiki-keeper` ingerisce poi questo raw come qualsiasi `.txt`. [^src: PATTERN.md §16]

**Regola inviolabile**: `repo-sync` è **read-only verso la sorgente** — non aggiunge né modifica file nel repo scansionato (§7 r.17). [^src: PATTERN.md §7]

## Configurazione `factory.config.yaml` rilevante

```yaml
pattern_version: "2.13"
topology: full-stack-agents

code_paths:
  - name: default
    path: ./src/
    layers: [be, fe, db, qa, infra]
    vcs:
      mode: monorepo
      branch_strategy: shared
      commit_coupling: float

adapters:
  - name: claude
    folder: .claude
    maturity: full
  - name: cursor
    folder: .cursor
    maturity: full

scheduler:
  enabled: true
  max_parallel: 4
  domains:
    review: true            # CQRL schedulabile in parallelo con develop

code_quality:
  enabled: true             # CQRL attivo dal 2026-05-27
  max_iterations: 3
  thresholds:
    confidence_min: 0.6
    batching_split: 7       # ≤7 finding → all-in-one; >7 → severity-tiered
    pass_rate_warn: 0.05
    false_positive_warn: 0.30
  passes:
    idiomaticity: true
    design: true
    robustness: true
  router:
    strategy: severity-tiered
    max_diff_lines: 80
    ordering: severity_then_complexity
  ruleset:
    path: ./code_quality/rules/
    tiers: [canonical, emergent, team-specific]
    evolve:
      enabled: false        # loop evolutivo KB — gate umano, non auto
  reports:
    path: ./code_quality/reports/
    retain_iterations: 5
    digest_cadence: weekly
```

[^src: factory.config.yaml]

## Operazioni canoniche rilevanti

| Comando | Operazione | Chi |
|---|---|---|
| `/dev <TSK-id>` | Develop: L4 → L5 | dev-agent layer corrispondente |
| `/review <TSK-id>` | Review: L5 → CQRL | `code-reviewer` |
| `/repo-sync <path>` | Sync repo esistente → `raw/` | `repo-sync` |
| `/run` | Dashboard di stato factory | Orchestrator |
| `/lint` | Health check L2+L3+L4 | `wiki-lint` |
| `/promote <page> <status>` | Promuovi status pagina wiki | Orchestrator |
| `/kanban-publish` | Mirror L3/L4 su provider esterno | Publisher (provider: none qui) |

[^src: PATTERN.md §3]

## Aggiornamenti (v2026-05-28) — EP-019 CQRL Bonifica Generale Outcome

**Sprint 16, 2026-05-27 — EP-019 completato (Fase A + Fase B).** [^src: code_quality/reports/wave-01-be-auth-security-digest.md] [^src: code_quality/reports/wave-12b-qa-platform-digest.md]

### Fase A — Retro-review (14 wave, iter-1)

| Metrica | Valore |
|---|---|
| Wave eseguite | 14 |
| TSK storici revisionati | 224 |
| pass (iter-1) | 210 |
| conditional (iter-1) | 14 |
| reject | 0 |

Le 14 wave coprono l'intero codebase `./src/` per layer:

| Wave | Scope | pass | conditional |
|---|---|---|---|
| A1 | BE Auth & Security | 5 | 3 |
| A2 | BE FMP Adapter | 9 | 0 |
| A3 | BE Rule Engine core | 15 | 1 |
| A3b | BE Rule Engine Graham | 16 | 0 |
| A4 | BE Deep Analysis / SEC | 17 | 1 |
| A4b | BE Deep LLM / Cost | 14 | 3 |
| A5 | BE Screener & Top-Picks | 20 | 1 |
| A6 | FE Auth & Session | 0 | 3 |
| A7 | FE Analysis & Core Pages | 11 | 2 |
| A8 | FE Shared UI | 24 | 0 |
| A9 | FE E2E Playwright | 9 | 0 |
| A10 | DB Flyway migrations | 24 | 0 |
| A12a | BE Platform Config | 23 | 0 |
| A12b | QA Contract & Integration | 23 | 0 |

**Top rule_id ricorrenti (dedup across waves):** `kotlin.spring.design.single_responsibility_service`, `kotlin.spring.resilience.external_api_guard`, `typescript.nextjs.errorhandling.user_safe_messages`.

### Fase B — Refactor finding (10 TSK, iter-2)

Tutti i 14 conditional iter-1 sono stati risolti a **pass** in iter-2 tramite i TSK di refactor TSK-252…261 (Fase B). [^src: code_quality/reports/TSK-033-iter-2.md] [^src: code_quality/reports/TSK-027-iter-2.md]

**Outcome finale EP-019:** 224/224 pass, 0 reject, 0 conditional aperti.

### Osservazioni strutturali (non-blocking)

- `TSK-221`/`TSK-222`: la CSP `unsafe-inline` rimane documentata come trade-off esplicito fino alla chiusura del gap `[[gaps#fe-middleware-static-export-conflict]]`.
- Nessun `heap_eligible` o gate umano attivato: il loop CQRL ha funzionato entro `max_iterations: 3` (iter effettive: 2).

---

## Concetti correlati

[[code-quality-review-runbook]] — Runbook operativo CQRL (9 fasi, loop control, batching)
[[webapp-architecture-vi]] — Architettura applicativa L5 (Next.js + Spring Boot + PostgreSQL)
[[analysis-api-pipeline]] — Pipeline API principale dell'app

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
