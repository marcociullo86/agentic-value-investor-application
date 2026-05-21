# /lint — Health check factory

Health check di `wiki/`, `management/kanban/`, `design_&_architecture/`, `factory.config.yaml`. Solo report, mai auto-fix.

## Argomenti opzionali

- (nessuno) → lint completo (Check 1–4d + topology + VCS)
- namespace: `concepts`, `kanban`, `topology`, `vcs`, …
- `citation-audit` → audit citazioni dedicato

## Esecuzione

Delega al subagent **`wiki-lint`** (`readonly: true`).

- Procedura: skill **`lint-checks`**
- Output: `wiki/lint/YYYY-MM-DD-lint-report.md` (o `-citation-audit.md`)
- Flagga ERROR `heal-eligible` per `/heal`
- Append `wiki/log.md`
