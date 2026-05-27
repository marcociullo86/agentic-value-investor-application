# CURSOR.md — App Template Demo

Questo repo segue il pattern definito in [`PATTERN.md`](PATTERN.md) (v2.13, agent-agnostic).

## Adapter Cursor

L'adapter Cursor vive in [`.cursor/`](.cursor/):

- **Subagent** ([`.cursor/agents/`](.cursor/agents/)): core — `orchestrator`, `sync-docs`, `figma-sync`, `repo-sync`, `github-publisher`, `wiki-keeper`, `wiki-keeper-worker`, `product-manager`, `lead-architect`, `tpm`, `wiki-query`, `wiki-lint`, `code-reviewer`; dev — `be-dev`, `fe-dev`, `db-dev`, `qa-dev`, `infra-dev`
- **Skill** ([`.cursor/skills/`](.cursor/skills/)): mirror delle skill canoniche (stesso contenuto di [`.claude/skills/`](.claude/skills/))
- **Comandi** ([`.cursor/commands/`](.cursor/commands/)): `/run`, `/sync-docs`, `/figma-sync`, `/repo-sync`, `/query`, `/lint`, `/promote`, `/heal`, `/dev`, `/review`, `/topology`, `/kanban-publish`
- **Regole** ([`.cursor/rules/`](.cursor/rules/)): vincoli factory always-on

Adapter parallelo Claude Code: [`.claude/`](.claude/) + [`CLAUDE.md`](CLAUDE.md). Registry multi-adapter: [`adapters/`](adapters/) + `factory.config.yaml.adapters`.

## Configurazione factory (v2.7–v2.13)

[`factory.config.yaml`](factory.config.yaml) al root:

- **Topologia** `full-stack-agents`
- **Code paths** `code_paths[default]` → `./src/` (monorepo)
- **Stack mode** `auto`
- **Adapters** `claude` + `cursor` installati
- **Scheduler** parallelo DAG (develop, ingest, lint, review, sync)
- **Code quality** `enabled: false` (opt-in — attiva per `/review` post-develop)
- **Kanban publish** `none` (opt-in GitHub)

## Quick start (Cursor)

| Comando | Azione |
|---------|--------|
| `/run` | Dashboard + wave plan parallelo (`orchestrator`) |
| `/sync-docs` | PDF → `raw/*.txt` |
| `/figma-sync <url>` | Figma → `raw/*.kb.json` |
| `/repo-sync <path>` | Repo esistente → `raw/*-repo-*.md` (read-only sorgente) |
| `/dev <TSK-id>` | Develop L5 |
| `/review <TSK-id>` | Code quality review (CQRL, opt-in) |
| `/kanban-publish` | Mirror kanban → GitHub Issues |
| `/lint` | Health check (`wiki-lint`) |

Invocazione esplicita subagent: `/orchestrator`, `/be-dev`, `/code-reviewer`, …

## Memoria cross-conversazione

`memory/{episodic,semantic,procedural}/` persiste tra conversazioni.

## Migrazioni

- v2.9–v2.11: `wiki/runbooks/migration-v29.md`, `migration-v210.md`, `migration-v211.md`
- v2.12: `wiki/runbooks/code-quality-review-runbook.md`
- Contratto completo: `PATTERN.md` §16–§20
