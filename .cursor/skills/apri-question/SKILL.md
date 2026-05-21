---
name: apri-question
description: Template per aggiungere una domanda bloccante a management/questions.md (con blocking_level hard|soft, v2.6).
---
# Procedura per aprire una question

## Path

`management/questions.md` (file unico, append in `[APERTE]`).

Se il file non esiste, crealo con header:

```markdown
---
created: YYYY-MM-DD
updated: YYYY-MM-DD
status: open
---
# Questions — App Template Demo

## [APERTE]

## [RISOLTE]
```

## Entry

```markdown
### Q_NNN — <titolo conciso>
**Origine:** [[<pagina-wiki>]]
**Tipo:** Requisito incompleto | Logica ambigua | Conflitto business
**Impatto:** ALTO | MEDIO | BASSO
**Bloccante:** hard | soft   <!-- default hard se omesso (v2.6) -->
**Domanda:** <testo>
**Epiche bloccate:** EP-XXX
**Storie bloccate:** US-YYY
[^src: wiki/<file>.md §<sez>]

---
```

### `Bloccante:` — granularità del gate L4 (v2.6, PATTERN.md §7 r.9)

- **`hard`** (default): blocca Arch+TPM sulle US dipendenti. Usalo quando la
  risposta cambia in modo non-additivo architettura, contratti, standard
  normativi (§11), o schema dati.
- **`soft`**: Arch procede annotando `pending_clarification: [Q_NNN]` su ADR/US;
  TPM taskizza le US non dipendenti da hard aperte.

Regola pratica: invalida un ADR già accettato o cambia uno standard → `hard`.
Altrimenti → `soft`.

## Aggiornamento

- ID Q_NNN: sequenziale globale (Q_001, Q_002…).
- Se aggiungi → set `status: open` + `updated`.
- Frontmatter `status: open` resta finché esiste almeno una Q in `[APERTE]`,
  indipendentemente dal `blocking_level`.
- Quando risolta → sposta in `[RISOLTE]` con `**Data risoluzione:**`, `**Decisione:**`, `**Epiche/Storie sbloccate:**`. Se `[APERTE]` vuota → `status: resolved`.

## Effetti collaterali

- Per ogni storia in `Storie bloccate`: aggiorna `US-YYY.md` con:
  - Q `hard` → `status: blocked` + `blocked_by: [Q_NNN]`
  - Q `soft` → `status: ready` (invariato) + `pending_clarification: [Q_NNN]`
- Quando Q passa a `[RISOLTE]`: rimuovi `Q_NNN` da `blocked_by`/`pending_clarification`; se entrambi vuoti → `status: ready`.
- Se la riconciliazione downstream non avviene contestualmente (es. Q risolta via chiusura gap dal `wiki-keeper`), la skill `propagate-resolution` appende `reconcile-needed` a `wiki/log.md`; l'orchestrator lo surfaceizza in dashboard.
