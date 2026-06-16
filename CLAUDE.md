# CLAUDE.md — Agentic Value Investor Application

Questo repo segue il pattern definito in [`PATTERN.md`](PATTERN.md) (v2.21, agent-agnostic, multi-adapter).

## Adapter Claude Code

L'adapter Claude Code vive in `.claude/`:

- **Agenti** (`.claude/agents/`): core + sync (`sync-docs`, `figma-sync`, `repo-sync`, `graphify-sync`) + publisher (`github-publisher`) + `code-reviewer` + dev-agent (`be`, `fe`, `db`, `qa`, `infra`) + review (v2.18): `a11y-specialist`, `ux-ui-reviewer`, `ui-designer` + analytics (v2.18): `analytics-reporter`, `estimation-analyst` + `consistency-checker` (v2.19) + `docs-dev` (v2.19)
- **Skill** (`.claude/skills/`): canoniche + procedurali incl. `code-review-protocol`, `parallel-scheduling`, `caveman-protocol`, `graphify-extraction-protocol`, `premortem-protocol` (v2.16), `visual-oracle-protocol` + `oracle-precheck` (v2.17), `accessibility-testing-protocol` (v2.18), `ux-ui-review-protocol` + `ux-ui-design-protocol` (v2.18), `functional-oracle-protocol` + `interaction-drive-protocol` (v2.20), `token-ledger` + `llm-generator-separation-protocol` + `art-director-coordination-protocol` (v2.21), bootstrap skills
- **Commands** (`.claude/commands/`): `/run`, `/sync-docs`, `/figma-sync`, `/repo-sync`, `/graphify-sync`, `/compression`, `/query`, `/lint`, `/promote`, `/heal`, `/dev`, `/review`, `/premortem` (v2.16), `/visual-oracle` (v2.17), `/topology`, `/kanban-publish`, `/a11y` (v2.18), `/ux-ui-review`, `/ux-ui-design` (v2.18), `/analytics`, `/estimate` (v2.18), `/functional-oracle` (v2.20), `/token-ledger`, `/release`, `/complexity-budget`, `/pattern-view` (v2.21)

## Configurazione factory (v2.21)

[`factory.config.yaml`](factory.config.yaml): `pattern_version: "2.21"`, `code_paths[default]` → `./src/`, `adapters: [claude, cursor]`, `scheduler` (domini `premortem` + `visual-oracle` attivi), `code_quality` (enabled, 4° pass `premortem-on-merge` ON), `fe_correctness` (Visual Oracle **ON** — richiede Playwright nel code_path FE), `compression` (output + context, default OFF). Capability v2.18–v2.21 aggiunte con `enabled: false` (opt-in R.P3): `a11y`, `ux_ui`, `analytics` (measurement+estimation+dogfooding+token_ledger), `temporal`, `release_governance`, `complexity_budget`, `design_intelligence`.

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
- Accessibility scan (v2.18, opt-in): `/a11y <TSK-id>`
- UX/UI review (v2.18, opt-in): `/ux-ui-review <TSK-id>`
- UX/UI design (v2.18, opt-in): `/ux-ui-design <TSK-id>`
- Analytics cost/time (v2.18, opt-in): `/analytics`
- Stima costi/tempi (v2.18, opt-in): `/estimate <scope>`
- Functional Oracle (v2.20, opt-in): `/functional-oracle <TSK-id|app>`
- Token Ledger (v2.21, opt-in): `/token-ledger [--full]`
- Release gate (v2.21, opt-in): `/release [--dry-run]`

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
Runbook v2.21: `wiki/runbooks/decision-anchor-runbook.md`, `wiki/runbooks/consistency-checker-runbook.md`, `wiki/runbooks/complexity-budget-runbook.md`, `wiki/runbooks/analytics-pricing-runbook.md`.
