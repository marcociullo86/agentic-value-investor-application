# /promote — Promuovi pagina wiki

Promuove lo `status:` di una pagina wiki (`draft` → `review` → `approved`).

## Argomenti

`<path-pagina> [<new-status>]`

Esempi:

- `/promote wiki/concepts/event-sourcing.md` → stato successivo canonico
- `/promote wiki/concepts/event-sourcing.md approved` → target esplicito

## Esecuzione

Delega al subagent **`orchestrator`**.

- Procedura: skill **`promote-status`**
- Modifica **solo** frontmatter `status:` e `updated:` — mai il corpo
- Transizione illegale → rifiuta senza auto-fix
