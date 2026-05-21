---
name: scrivi-wiki-page
description: Template e regole per scrivere una pagina della wiki llm-style (karpathy).
---
# Procedura per scrivere una pagina `wiki/`

Riferimenti: `citation-rules`, `wiki-gap-protocol`.

## Path (karpathy-style)

- Source: `wiki/sources/<kebab-slug>.md`
- Concept: `wiki/concepts/<kebab-slug>.md`
- Entity: `wiki/entities/<kebab-slug>.md`
- Synthesis: `wiki/syntheses/<kebab-question>.md`
- Runbook: `wiki/runbooks/<kebab-slug>.md`
- Incident: `wiki/incidents/YYYY-MM-DD-<kebab-slug>.md`

## Frontmatter minimo

```yaml
---
type: source | concept | entity | synthesis | runbook | incident | gap
sources: ["raw/YYYY-MM-DD-<slug>.pdf", ...]
status: draft | review | approved
created: YYYY-MM-DD
updated: YYYY-MM-DD
tags: [...]
---
```

## Struttura corpo

```markdown
# <Titolo>
> <Tesi centrale in una riga>

## Contesto
<Perché esiste questa pagina> [^src: raw/<data>-<nome>.txt §<sez>]

## Dettaglio
<Contenuto principale con citazioni>

## Figure e Diagrammi
[FIG-NN](../../raw/images/<data>-<nome>-fig-NN.md) — <didascalia>

## Concetti correlati
[[<concetto-correlato>]]

## Pagine collegate
[[<altra-pagina>]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
```

## Regole stilistiche

- Citazioni e wikilink: vedi `citation-rules`.
- Informazione assente → `wiki-gap-protocol`, non inventare.
- Update di pagina `review`: aggiungi `## Aggiornamenti (vYYYY-MM-DD)`.
- No emoji nel contenuto wiki.
- No timestamp in prosa.
