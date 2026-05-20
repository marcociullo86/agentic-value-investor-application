---
name: scrivi-user-story
description: Template per una User Story atomica e contractuale.
---
# Procedura per scrivere una User Story

Riferimenti: `citation-rules`, `wiki-gap-protocol`.

## Path

`management/kanban/EP-XXX-<slug>/US-YYY-<slug>/US-YYY.md`

## Frontmatter

```yaml
---
id: US-YYY
title: <titolo>
role: <ruolo utente>
priority: high | medium | low
status: ready | blocked
wiki_page: wiki/<file>.md
blocked_by: []            # solo Q_NNN con Bloccante: hard
pending_clarification: [] # opzionale, v2.6 — Q_NNN soft aperte
---
```

### `blocked_by` vs `pending_clarification` (v2.6)

- `blocked_by: [Q_NNN]` → Q hard aperta. `status: blocked`, Arch/TPM non possono partire.
- `pending_clarification: [Q_NNN]` → solo Q soft aperte. `status: ready`, Arch progetta annotando l'ADR con la stessa lista, TPM taskizza.
- US con entrambe le liste valorizzate resta `blocked` finché tutte le hard non sono chiuse.

## Corpo

```markdown
# US-YYY — <Azione + Oggetto>

## Descrizione
Come <ruolo>, voglio <azione>, affinché <valore di business>.

## Business Rules
- Regola 1
- Regola 2

## UI Reference
[^src: wiki/concepts/<concetto>.md §<sez>]

## Acceptance Criteria
- [ ] Criterio oggettivo 1
- [ ] Criterio oggettivo 2

## Fonti
[^src: wiki/<file>.md §<sez>]
```

## Regole

- Tecnologia-agnostico.
- AC verificabili oggettivamente (no "veloce" → "< 200ms").
- Dettaglio mancante → NON inventare:
  - Non-bloccante → `wiki-gap-protocol`
  - Bloccante hard → `apri-question` con `**Bloccante:** hard`; storia in `status: blocked` con `blocked_by: [Q_NNN]`
  - Bloccante soft → `apri-question` con `**Bloccante:** soft`; storia resta `ready` con `pending_clarification: [Q_NNN]` (v2.6)
- Citazioni: vedi `citation-rules`.
