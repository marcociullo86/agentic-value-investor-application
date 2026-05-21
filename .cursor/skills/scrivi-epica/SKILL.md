---
name: scrivi-epica
description: Template e regole per scrivere una EP-XXX.md.
---
# Procedura per scrivere un'epica

Riferimenti: `citation-rules` (cascade L3 → wiki).

## Path

`management/kanban/EP-XXX-<slug>/EP-XXX.md`

Slug: lowercase, spazi→`-`, max 40 char.

## Frontmatter

```yaml
---
id: EP-XXX
title: <titolo>
status: defined | in-progress | done
priority: high | medium | low
confidence: XX%
confidence_rationale: <1-2 frasi>
wiki_pages: [wiki/<file>.md]
created: YYYY-MM-DD
---
```

Note: `stories` non va — deducibile dalle sotto-cartelle.

## Corpo

```markdown
# EP-XXX — <Titolo>
> <Obiettivo in una riga>

## Obiettivo
<Cosa risolve, per chi, perché ora>
[^src: wiki/<file>.md §<sez>]

## Valore di business
<Outcome misurabile>

## Storie incluse
- [US-YYY](US-YYY-<slug>/US-YYY.md) — <titolo>

## Confidence: XX%
<Razionale>

## Dipendenze
<EP-/US- bloccanti, gap aperti>
```

## Regole

- Confidence obbligatorio. Score < 50% → epica in roadmap come Release 1.1+.
- Nessun tech detail.
- Citazioni: vedi `citation-rules`.
