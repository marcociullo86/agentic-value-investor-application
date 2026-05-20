---
name: lint-checks
description: Procedure dei check eseguiti dal wiki-lint (1 orphan+wikilink, 2 claim, 3 kanban, 4 wiki↔kanban, 4b Q↔kanban, 4c topology, 4d VCS, citation audit).
---
# Check del wiki-lint

Riferimenti: `citation-rules`, `wiki-log-entry`.

## Check 1 — Orphan + wikilink (scan unico)

1. `Glob wiki/**/*.md` (escludi `log.md`, `index.md`, `query/`, `lint/`).
2. Read `wiki/index.md`, estrai tutti i `[[…]]`.
3. Per ogni file: se non è linkato → **WARNING orphan**.
4. Per ogni `\[\[([^\]]+)\]\]` in ogni pagina: verifica esista un file con slug corrispondente. Wikilink non risolto → **ERROR broken-link** (heal-eligible se fuzzy match ≥ 0.90).

## Check 2 — Claim senza fonte

Vedi `citation-rules` per la definizione canonica di "claim che richiede citazione".

Procedura:

- Per ogni `wiki/**/*.md`, identifica claim secondo `citation-rules`.
- Verifica che entro 3 righe successive ci sia un `[^src: …]` o un `[[…]]`.
- Assenza → **WARNING unsourced-claim**.

## Check 3 — Integrità kanban

Per ogni `management/kanban/EP-*/EP-*.md`:

- Frontmatter ha `id`, `title`, `status`, `priority`, `confidence`? Altrimenti **ERROR**.
- `id` matcha `EP-XXX` con XXX = nome cartella? Altrimenti **ERROR**.

Per ogni `US-*.md`:

- Frontmatter ha `id`, `title`, `role`, `priority`, `status`, `wiki_page`?
- `wiki_page` punta a file esistente?

Per ogni `TSK-*.md` (v2.7):

- Frontmatter ha `id`, `sprint`, `layer` (`be|fe|db|qa|infra`), `consumer` (`agent|human`), `priority`, `estimate`, `status`?
- Campo legacy `team:` → **WARNING deprecated-team-field** (v2.7).
- `id` univoco globalmente?

## Check 4 — Coerenza wiki ↔ kanban

- Ogni US referenzia una pagina wiki: la pagina esiste?
- Ogni `## Storie collegate` in wiki ha solo storie esistenti?

## Check 4b — Coerenza Q ↔ kanban (v2.6, gate L4 graduato)

- Per ogni `Q_NNN` in `[APERTE]`: presenza campo `**Bloccante:** hard | soft`. Assenza → **WARNING missing-blocking-level** (default `hard`, compat retroattiva).
- Per ogni `Q_NNN` in `[RISOLTE]`: cerca US con `blocked_by:.*Q_NNN` o `pending_clarification:.*Q_NNN`. Match → **WARNING stale-blocked-by**. Cross-check con marker `reconcile-needed` in `wiki/log.md`.
- Per ogni US con `pending_clarification:` non vuota: almeno un ADR la deve citare nel suo `pending_clarification:`. Mismatch → **WARNING orphan-pending-clarification**.

## Check 4c — Coerenza topology (v2.7)

- Read `factory.config.yaml`. Per ogni `routing.<layer>: agent`: verifica esistenza `<layer>-dev.md` in `.claude/agents/`. Mismatch → **ERROR topology-routing-mismatch**.
- Per ogni `<layer>-dev.md` presente: verifica `routing.<layer>: agent`. Mismatch → **ERROR topology-orphan-agent**.
- `topology:` dichiarata deve essere coerente con dev-agent presenti (es. `knowledge-only` → 0 dev-agent; `full-stack-agents` → 4 dev-agent). Mismatch → **ERROR topology-declaration-mismatch**.
- Per ogni TSK con `consumer: agent`: verifica `<layer>-dev.md` esista. Mismatch → **ERROR tsk-consumer-mismatch**.

## Check 4d — Coerenza VCS (v2.8)

- Read `factory.config.yaml.vcs`. Se `code_path` valorizzato → `vcs.mode` deve essere valorizzato. Assenza → **ERROR vcs-mode-missing**.
- `vcs.mode: submodule` → `.gitmodules` deve esistere al root. Assenza → **ERROR submodule-not-configured**.
- `vcs.mode: monorepo` con `code_path` assoluto fuori dal repo → **ERROR vcs-mode-inconsistent**.
- `vcs.commit_coupling: pin` → file `.factory-lock` deve esistere. Assenza → **WARNING factory-lock-missing**.

## Citation audit (periodico)

Per ogni `[^src: <path> §<sez>]` in `wiki/**`:

- Verifica che `<path>` esista.
- Verifica che `<sez>` sia presente (header markdown matching).

Output: `wiki/lint/YYYY-MM-DD-citation-audit.md`.

## Output report

Path: `wiki/lint/YYYY-MM-DD-lint-report.md`

```markdown
---
type: lint
date: YYYY-MM-DD
heal_eligible_count: N
---
# Lint Report — YYYY-MM-DD

## Riepilogo
| Check | Errors | Warnings |
|---|---|---|
| 1 — Orphan + wikilink | N | N |
| 2 — Claim senza fonte | N | N |
| 3 — Integrità kanban | N | N |
| 4 — Coerenza wiki↔kanban | N | N |
| 4b — Coerenza Q↔kanban (v2.6) | N | N |
| 4c — Coerenza topology (v2.7) | N | N |
| 4d — Coerenza VCS (v2.8) | N | N |

## ERROR meccanici (heal-eligible)
<lista filtrata: solo broken-wikilink fuzzy>=0.90, missing-frontmatter-field, citation-section-mismatch>

## Dettaglio
### Check 1
- [ERROR] wiki/concepts/foo.md: wikilink `[[bar-nonesiste]]` non risolve.
...
```

## Log entry

Append a `wiki/log.md` secondo `wiki-log-entry` (template `lint`).
