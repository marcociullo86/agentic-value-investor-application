# CLAUDE.md — Agentic Value Investor Application

Questo repo segue il pattern definito in [`PATTERN.md`](PATTERN.md) (v2.17, agent-agnostic).

## Adapter Claude Code

L'adapter Claude Code vive in `.claude/`:

- **Agenti** (`.claude/agents/`): core + sync (`sync-docs`, `figma-sync`, `repo-sync`, `graphify-sync`) + publisher (`github-publisher`) + `code-reviewer` + dev-agent (`be`, `fe`, `db`, `qa`, `infra`)
- **Skill** (`.claude/skills/`): canoniche + procedurali incl. `code-review-protocol`, `parallel-scheduling`, `caveman-protocol`, `graphify-extraction-protocol`, `premortem-protocol` (v2.16), `visual-oracle-protocol` + `oracle-precheck` (v2.17), bootstrap skills
- **Commands** (`.claude/commands/`): `/run`, `/sync-docs`, `/figma-sync`, `/repo-sync`, `/graphify-sync`, `/compression`, `/query`, `/lint`, `/promote`, `/heal`, `/dev`, `/review`, `/premortem` (v2.16), `/visual-oracle` (v2.17), `/topology`, `/kanban-publish`

## Configurazione factory (v2.17)

[`factory.config.yaml`](factory.config.yaml): `pattern_version: "2.17"`, `code_paths[default]` → `./src/`, `adapters: [claude, cursor]`, `scheduler` (domini `premortem` + `visual-oracle` attivi), `code_quality` (enabled, 4° pass `premortem-on-merge` ON), `fe_correctness` (Visual Oracle **ON** — richiede Playwright nel code_path FE), `compression` (output + context, default OFF).

## Quick start

- Stato: `/run`
- PDF: `/sync-docs` → `wiki-keeper`
- Figma: `/figma-sync`
- Repo esistente: `/repo-sync <path>`
- Knowledge graph: `/graphify-sync default` (dopo `compression set context.enabled true`)
- Compression policy: `/compression show`
- Develop: `/dev <TSK-id>`
- Visual Oracle (TSK FE): `/visual-oracle <TSK-id>`
- Review CQRL: `/review <TSK-id>`
- Premortem (risk pre-mortem su piano/artefatto): `/premortem <target>`
- Kanban mirror: `/kanban-publish`

## Memoria

`memory/{episodic,semantic,procedural}/`

## Mapping ruoli → adapter

| Ruolo | File |
|---|---|
| Orchestrator | `.claude/agents/orchestrator.md` |
| Sync | `.claude/agents/sync-docs.md`, `figma-sync.md`, `repo-sync.md`, `graphify-sync.md` |
| Publisher | `.claude/agents/github-publisher.md` |
| Code Reviewer | `.claude/agents/code-reviewer.md` |
| Analyst | `.claude/agents/wiki-keeper.md` |
| PM / Arch / TPM | `product-manager`, `lead-architect`, `tpm` |
| Dev | `be-dev`, `fe-dev`, `db-dev`, `qa-dev`, `infra-dev` |

Migrazione v2.14: `wiki/runbooks/migration-v214.md`, `wiki/runbooks/migration-v214-fase2.md`.
Runbook v2.16/v2.17: `wiki/runbooks/premortem-runbook.md`, `wiki/runbooks/visual-oracle-installation.md`.
