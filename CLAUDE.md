# CLAUDE.md — App Template Demo

Questo repo segue il pattern definito in [`PATTERN.md`](PATTERN.md) (v2.13, agent-agnostic).

## Adapter Claude Code

L'adapter Claude Code vive in `.claude/`:

- **Agenti** (`.claude/agents/`): core + sync (`sync-docs`, `figma-sync`, `repo-sync`) + publisher (`github-publisher`) + `code-reviewer` + dev-agent
- **Skill** (`.claude/skills/`): canoniche + procedurali incl. `code-review-protocol`, `parallel-scheduling`, `repo-extraction-protocol`, bootstrap skills
- **Commands** (`.claude/commands/`): `/run`, `/sync-docs`, `/figma-sync`, `/repo-sync`, `/query`, `/lint`, `/promote`, `/heal`, `/dev`, `/review`, `/topology`, `/kanban-publish`

## Configurazione factory (v2.13)

[`factory.config.yaml`](factory.config.yaml): `pattern_version: "2.13"`, `code_paths`, `adapters: [claude, cursor]`, `scheduler`, `code_quality` (opt-in), `kanban_publish`.

## Quick start

- Stato: `/run`
- PDF: `/sync-docs` → `wiki-keeper`
- Figma: `/figma-sync`
- Repo esistente: `/repo-sync <path>`
- Develop: `/dev <TSK-id>`
- Review CQRL: `/review <TSK-id>` (con `code_quality.enabled: true`)
- Kanban mirror: `/kanban-publish`

## Memoria

`memory/{episodic,semantic,procedural}/`

## Mapping ruoli → adapter

| Ruolo | File |
|---|---|
| Orchestrator | `.claude/agents/orchestrator.md` |
| Sync | `.claude/agents/sync-docs.md`, `figma-sync.md`, `repo-sync.md` |
| Publisher | `.claude/agents/github-publisher.md` |
| Code Reviewer | `.claude/agents/code-reviewer.md` |
| Analyst | `.claude/agents/wiki-keeper.md` |
| PM / Arch / TPM | `product-manager`, `lead-architect`, `tpm` |
| Dev | `be-dev`, `fe-dev`, `db-dev`, `qa-dev`, `infra-dev` |
