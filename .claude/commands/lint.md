---
description: Health check di wiki/, management/kanban/, design_&_architecture/, factory.config.yaml. Solo report, mai auto-fix.
---

Invoca l'agente `wiki-lint` via `Agent`. Procedura: vedi skill `lint-checks`.

Argomenti opzionali:

- nessun argomento → lint completo (Check 1-4d + citation audit periodico se opportuno)
- nome namespace (es. `concepts`, `kanban`, `topology`, `vcs`) → lint scoped
- `citation-audit` → audit completo delle citazioni

Output: `wiki/lint/YYYY-MM-DD-lint-report.md` (o `-citation-audit.md`).
L'agente NON modifica mai gli artefatti — solo report con severità ERROR/WARNING e
flag `heal-eligible` per ERROR meccanici (v2.5). Append a `wiki/log.md`.
