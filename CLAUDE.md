# CLAUDE.md — App Template Demo

Questo repo segue il pattern definito in [`PATTERN.md`](PATTERN.md) (v2.8, agent-agnostic).

## Adapter Claude Code

L'adapter Claude Code vive in `.claude/`:

- **Agenti** (`.claude/agents/`): core — `orchestrator`, `sync-docs`, `wiki-keeper`, `wiki-keeper-worker`, `product-manager`, `lead-architect`, `tpm`, `wiki-query`, `wiki-lint`; dev (v2.7) — `be-dev`, `fe-dev`, `db-dev`, `qa-dev`
- **Skill** (`.claude/skills/`): canoniche `citation-rules`, `wiki-log-entry`, `wiki-gap-protocol`; procedurali `ingest-protocol`, `query-protocol`, `lint-checks`, `promote-status`, `state-scan`, `heal-protocol`, `propagate-resolution`, `dev-protocol` (v2.7), `dev-handoff` (v2.7), `tech-scout` (v2.7), `vcs-handoff` (v2.8); template `scrivi-wiki-page`, `scrivi-epica`, `scrivi-user-story`, `scrivi-task`, `apri-question`
- **Commands** (`.claude/commands/`): `/run`, `/sync-docs`, `/query`, `/lint`, `/promote`, `/heal`, `/dev` (v2.7), `/topology` (v2.7)

## Configurazione factory (v2.7 + v2.8)

[`factory.config.yaml`](factory.config.yaml) al root configura:

- **Topologia** `full-stack-agents` (tutti i dev-agent attivi)
- **Code path** (L5) `./src/` interno al repo
- **Stack mode** `auto` (skill `tech-scout` propone, gate umano per applicare)
- **Routing** TSK → consumer (`agent` per be/fe/db/qa/infra)
- **VCS mode** `monorepo` (commit chain unico; ogni operazione VCS distruttiva è gated umano via `vcs-handoff`)

## Quick start

- Stato del progetto: `/run`
- Nuovo PDF in `raw/`: `/sync-docs` → poi invoca `wiki-keeper` per l'ingest
- Domanda al wiki: `/query <domanda>` (aggiungi `--ephemeral` per non salvare)
- Health check: `/lint`
- Heal ERROR meccanici da lint report: `/heal [<report-path>]`
- Promote pagina: `/promote <path> <new-status>`
- Topologia / routing: `/topology [show|set <topology>]`
- Consumare un TSK con dev-agent: `/dev <TSK-id>`

## Memoria cross-conversazione

Il tree `memory/{episodic,semantic,procedural}/` persiste tra conversazioni.

## Mapping ruoli PATTERN.md → file adapter

| Ruolo §2 | File |
|---|---|
| Orchestrator | `.claude/agents/orchestrator.md` |
| Sync | `.claude/agents/sync-docs.md` |
| Analyst | `.claude/agents/wiki-keeper.md` |
| PM | `.claude/agents/product-manager.md` |
| Arch | `.claude/agents/lead-architect.md` |
| TPM | `.claude/agents/tpm.md` |
| Query | `.claude/agents/wiki-query.md` |
| Lint | `.claude/agents/wiki-lint.md` |
| BE-Dev | `.claude/agents/be-dev.md` |
| FE-Dev | `.claude/agents/fe-dev.md` |
| DB-Dev | `.claude/agents/db-dev.md` |
| QA-Dev | `.claude/agents/qa-dev.md` |
