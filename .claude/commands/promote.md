---
description: Promuove una pagina wiki (draft → review → approved). Invoca orchestrator.
---

Argomenti: `<path-pagina> [<new-status>]`.

Esempi:

- `/promote wiki/concepts/event-sourcing.md` → next state dal corrente
- `/promote wiki/concepts/event-sourcing.md approved` → target esplicito

Invoca l'agente `orchestrator` via `Agent` (è l'unico autorizzato a editare `status:`
frontmatter di pagine wiki — vedi PATTERN.md §10 + skill `promote-status`).

Procedura: vedi skill `promote-status`.

Se la transizione è illegale → orchestrator rifiuta. Niente auto-fix.
