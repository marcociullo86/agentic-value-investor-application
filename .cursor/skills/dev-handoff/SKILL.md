---
name: dev-handoff
description: Entry append-only su wiki/log.md a chiusura di un TSK consumato da dev-agent. Formato canonico develop (v2.7).
---
# Dev Handoff (v2.7)

Riferimenti: `wiki-log-entry` (template `develop`), `dev-protocol` (Fase 5), `vcs-handoff` (v2.8 per commit).

## Chi può eseguirla

Tutti i 4 dev-agent (`be-dev`, `fe-dev`, `db-dev`, `qa-dev`) a chiusura del proprio TSK.

## Trigger

Fase 5 di `dev-protocol`: DoD verificata + TSK passato a `done` (o partial).

## Formato entry

Append a `wiki/log.md`:

```markdown
## YYYY-MM-DD HH:MM — develop TSK-ZZZ
**Agente:** <be-dev|fe-dev|db-dev|qa-dev>
**TSK:** [[../management/kanban/EP-XXX-<slug>/US-YYY-<slug>/TSK-ZZZ]]
**Layer:** <be|fe|db|qa|infra>
**Code path:** <code_path>
**Files touched:** <count> (lista se ≤ 5, altrimenti "vedi commit")
**Commit:** <hash short se code_path git tracciato; oppure "n/a">
**DoD:** <pass | partial — descrivi>
**Note:** <free-form max 2-3 righe>
```

## Regole

- **Append-only**: mai overwrite, mai editare entry passate.
- **Timestamp obbligatorio**: `YYYY-MM-DD HH:MM` Europe/Rome.
- **Commit hash**: se `vcs.mode` permette tracking git, includi short hash (7 char). Se `vcs.mode: none` o `external` → `n/a`.
- **DoD partial**: se non tutti i DoD passano, dichiarare quali mancano. Il TSK resta `in-progress` (non `done`).
- **Note**: solo informazioni non deducibili dal commit/codice (es. "ho usato bcrypt come da OWASP citato in raw/2026-...", "scelta libreria X confermata via gap chiuso da wiki-keeper"). No what (visibile nel diff).

## Anti-pattern

| Anti-pattern | Correzione |
|---|---|
| Entry senza `Layer:` o `Code path:` | Campi obbligatori |
| Chiudere TSK come `done` con DoD partial | Vietato: lascia `in-progress` |
| Commit hash inventato | Usa `n/a` se non disponibile |
| Note che ripete il diff | Solo why non-ovvio |
| Editare entry di un altro dev-agent | Append-only, mai modificare |
