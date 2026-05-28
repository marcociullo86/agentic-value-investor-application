# CURSOR.md — Agentic Value Investor Application

Questo repo segue il pattern definito in [`PATTERN.md`](PATTERN.md) (v2.14, agent-agnostic).

## Adapter Cursor

L'adapter Cursor vive in [`.cursor/`](.cursor/):

- **Subagent** ([`.cursor/agents/`](.cursor/agents/)): core + sync (`sync-docs`, `figma-sync`, `repo-sync`, `graphify-sync`) + `github-publisher` + `code-reviewer` + dev (`be`, `fe`, `db`, `qa`, `infra`)
- **Skill** ([`.cursor/skills/`](.cursor/skills/)): mirror di [`.claude/skills/`](.claude/skills/) — rigenerare con `./scripts/sync-cursor-adapter.sh`
- **Comandi** ([`.cursor/commands/`](.cursor/commands/)): `/run`, `/sync-docs`, `/figma-sync`, `/repo-sync`, `/graphify-sync`, `/compression`, `/query`, `/lint`, `/promote`, `/heal`, `/dev`, `/review`, `/topology`, `/kanban-publish`
- **Regole** ([`.cursor/rules/`](.cursor/rules/)): vincoli factory always-on

Adapter parallelo Claude Code: [`.claude/`](.claude/) + [`CLAUDE.md`](CLAUDE.md).

## Configurazione factory (v2.14)

[`factory.config.yaml`](factory.config.yaml):

- **Topologia** `full-stack-agents`
- **Code paths** `code_paths[default]` → `./src/` (monorepo)
- **Stack mode** `auto`
- **Adapters** `claude` + `cursor`
- **Scheduler** parallelo DAG (develop, ingest, lint, review, sync)
- **Code quality** `enabled: true`
- **Compression** output + context (default OFF; target `default` preconfigurato per Graphify)
- **Kanban publish** `none`

## Quick start (Cursor)

| Comando | Azione |
|---------|--------|
| `/run` | Dashboard + wave plan (`orchestrator`) |
| `/graphify-sync default` | Knowledge graph da `./src/` → `.graphify-state/` |
| `/compression show` | Stato compression layer |
| `/dev <TSK-id>` | Develop L5 |
| `/review <TSK-id>` | CQRL post-develop |
| `/lint` | Health check |

Dopo modifiche a `.claude/`: `./scripts/sync-cursor-adapter.sh`

## Memoria

`memory/{episodic,semantic,procedural}/`

## Migrazioni

- v2.14: `wiki/runbooks/migration-v214.md`, `migration-v214-fase2.md`
- v2.12 CQRL: `wiki/runbooks/code-quality-review-runbook.md`
- Contratto: `PATTERN.md` §16–§20
