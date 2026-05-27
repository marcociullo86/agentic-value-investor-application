---
id: parallel-scheduler
type: concept
title: "Parallel scheduler (DAG-driven dispatch)"
status: review
created: 2026-05-28
updated: 2026-05-28
sources:
  - PATTERN.md §18
  - factory.config.yaml (blocco scheduler:)
tags: [factory, orchestration, dag, parallelism, depends_on, code_path]
---

# Parallel scheduler (DAG-driven dispatch)

> Meccanismo con cui l'orchestrator raggruppa TSK in **wave** eseguibili in parallelo senza conflitti su `code_path`, rispettando `depends_on` / `blocked_by` nei frontmatter kanban. [^src: PATTERN.md §18]

## Modello

1. **Grafo dipendenze** (`depends_on`): archi causali EP/US/TSK → solo TSK con prerequisiti `done` entrano in coda.
2. **Conflitti file** (`code_path`): due TSK con path sovrapposti non possono stare nella stessa wave (graph coloring / antichain).
3. **Cap fan-out**: `factory.config.yaml` → `scheduler.max_parallel` (default 4).

## Frontmatter rilevanti (TSK)

| Campo | Ruolo |
|---|---|
| `depends_on` | Lista `TSK-NNN` (o `US-NNN` / `EP-NNN`) prerequisiti |
| `code_path` | Glob di scrittura L5 — usato per conflict detection |
| `blocked_by` | Blocco esplicito (domande, gate umani) |

## Regole inviolabili (sintesi R.S1–R.S8)

- Single-committer per layer preservato; parallelismo solo tra consumer/agent distinti e scope disgiunti.
- VCS (commit/push) resta serializzato — gate umano `vcs-handoff`.
- Nessun rollback collaterale su TSK paralleli falliti.

## Uso con CQRL

Le wave Fase A EP-019 (TSK-240..264) sono state partizionate con `depends_on: []` a Level 0 e `code_path` disgiunti per permettere fino a 4 `/review` paralleli. [^src: wiki/runbooks/code-quality-review-runbook.md §Roadmap implementativa]

## Vedi anche

- [[agentic-factory-v213]] — factory v2.13 + CQRL
- [[migration-v211]] — introduzione scheduler v2.11
- [[code-quality-review-runbook]] — batching review post-Develop
