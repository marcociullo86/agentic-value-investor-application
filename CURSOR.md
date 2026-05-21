# CURSOR.md — App Template Demo

Questo repo segue il pattern definito in [`PATTERN.md`](PATTERN.md) (v2.8, agent-agnostic).

## Adapter Cursor

L'adapter Cursor vive in [`.cursor/`](.cursor/):

- **Subagent** ([`.cursor/agents/`](.cursor/agents/)): core — `orchestrator`, `sync-docs`, `wiki-keeper`, `wiki-keeper-worker`, `product-manager`, `lead-architect`, `tpm`, `wiki-query`, `wiki-lint`; dev (v2.7) — `be-dev`, `fe-dev`, `db-dev`, `qa-dev`
- **Skill** ([`.cursor/skills/`](.cursor/skills/)): mirror delle skill canoniche (stesso contenuto di [`.claude/skills/`](.claude/skills/))
- **Comandi** ([`.cursor/commands/`](.cursor/commands/)): `/run`, `/sync-docs`, `/query`, `/lint`, `/promote`, `/heal`, `/dev`, `/topology`
- **Regole** ([`.cursor/rules/`](.cursor/rules/)): vincoli factory always-on

Adapter parallelo Claude Code: [`.claude/`](.claude/) + [`CLAUDE.md`](CLAUDE.md). I due adapter condividono `raw/`, `wiki/`, `management/`, `design_&_architecture/`, `memory/`, `src/`. In caso di conflitto nomi subagent, **`.cursor/` ha precedenza** (docs Cursor).

## Configurazione factory (v2.7 + v2.8)

[`factory.config.yaml`](factory.config.yaml) al root:

- **Topologia** `full-stack-agents` (tutti i dev-agent attivi)
- **Code path** (L5) `./src/`
- **Stack mode** `auto` (skill `tech-scout` propone, gate umano per applicare)
- **Routing** TSK → consumer (`agent` per be/fe/db/qa/infra)
- **VCS mode** `monorepo` (commit chain unico; operazioni VCS distruttive gated umano via `vcs-handoff`)

## Quick start (Cursor)

| Comando | Azione |
|---------|--------|
| `/run` | Dashboard stato + next-step (`orchestrator`) |
| `/sync-docs` | Estrazione PDF → `raw/*.txt` (`sync-docs`) |
| `/query <domanda>` | Risposta da solo `wiki/` (`wiki-query`; `--ephemeral` opzionale) |
| `/lint` | Health check strutturale (`wiki-lint`) |
| `/heal [<report>]` | Ripara ERROR heal-eligible (`wiki-keeper`) |
| `/promote <path> [status]` | Promuove pagina wiki (`orchestrator`) |
| `/topology [show\|set]` | Topologia e routing (`orchestrator` o agent principale) |
| `/dev <TSK-id>` | Consuma TSK con dev-agent per `layer` |

Invocazione esplicita subagent: `/orchestrator`, `/be-dev`, … oppure *"usa il subagent be-dev per TSK-019"*.

Dopo `/sync-docs`: invocare `wiki-keeper` per ingest L1 → L2.

## Memoria cross-conversazione

`memory/{episodic,semantic,procedural}/` — side-channel tra sessioni Cursor.

## Mapping ruoli PATTERN.md → adapter Cursor

| Ruolo §2 | Subagent |
|---|---|
| Orchestrator | `.cursor/agents/orchestrator.md` |
| Sync | `.cursor/agents/sync-docs.md` |
| Analyst | `.cursor/agents/wiki-keeper.md` |
| PM | `.cursor/agents/product-manager.md` |
| Arch | `.cursor/agents/lead-architect.md` |
| TPM | `.cursor/agents/tpm.md` |
| Query | `.cursor/agents/wiki-query.md` |
| Lint | `.cursor/agents/wiki-lint.md` |
| BE-Dev | `.cursor/agents/be-dev.md` |
| FE-Dev | `.cursor/agents/fe-dev.md` |
| DB-Dev | `.cursor/agents/db-dev.md` |
| QA-Dev | `.cursor/agents/qa-dev.md` |
