---
name: query-protocol
description: Protocollo del wiki-query (index → candidate pages → synthesize → persist | ephemeral). Simmetrico a ingest-protocol.
---
# Protocollo di Query

Riferimenti: `citation-rules`, `wiki-log-entry`.

## Fase 0 — Bootstrap

- Read `wiki/index.md` per la mappa delle sezioni.
- Identifica entità chiave + tipo risposta + 3-6 keyword.
- Read ultimo `wiki/log.md` (sezione `query`) per evitare duplicati.

## Fase 1 — Candidate pages

Ordine di priorità:

1. `wiki/syntheses/` — risposte già consolidate
2. `wiki/concepts/` — concetti di dominio
3. `wiki/entities/` — persone, organizzazioni, prodotti
4. `wiki/sources/` — documenti raw ingeriti
5. `wiki/runbooks/` — playbook operativi
6. `wiki/incidents/` — post-mortem

Per ogni keyword: `Glob wiki/**/*<keyword>*.md`. Read max 6-8 pagine plausibili.

## Fase 2 — Sintesi

```markdown
# Risposta: <domanda riformulata>

<Risposta in 1-3 paragrafi>

## Fonti
- [[<pagina-1>]] §<sez>
- [[<pagina-2>]] §<sez>
[^src: wiki/<file>.md §<sez>]
```

Citazioni secondo `citation-rules`. Se l'informazione non è in `wiki/`, dillo esplicitamente. Mai inventare.

## Fase 3 — Persistenza

Default: salva in `wiki/query/YYYY-MM-DD-<slug>.md`.
Con `--ephemeral`: solo chat, nessuna scrittura.

## Fase 4 — Log entry

Append a `wiki/log.md` secondo `wiki-log-entry` (template `query`). Skippa se `--ephemeral`.

## Fase 5 — Proposta synthesis (opzionale)

Se la risposta è candidata a ri-ask → proponi promozione:

```
Questa risposta sembra una synthesis candidata. Vuoi promuoverla a
wiki/syntheses/<question-slug>.md? (Richiede invocazione di wiki-keeper.)
```

**Mai promuovere autonomamente.**

## Scope di lettura (inviolabile)

`wiki-query` legge **solo** `wiki/**/*.md`. Mai `raw/`, `management/`, `design_&_architecture/`, `memory/`, `src/`.

Se la domanda richiede info fuori scope:

> "L'informazione richiesta vive fuori da `wiki/`. Posso dirti solo quello che la wiki documenta su <topic>: ..."

## Anti-pattern (vietati)

| Anti-pattern | Correzione |
|---|---|
| Leggere `raw/` per "verificare" la wiki | Se la wiki ha un gap, segnalalo via `wiki-gap-protocol`. |
| Rispondere senza citazione | Vedi `citation-rules`. |
| Promuovere query → synthesis autonomamente | Proponi all'umano, lascia agire `wiki-keeper`. |
| Inventare la risposta se la wiki tace | Vietato. Dillo esplicitamente. |
