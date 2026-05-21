---
name: ingest-protocol
description: Protocollo ReAct di Ingest per il wiki-keeper (bootstrap → analisi → proposta → scrittura → log). Supporta ingest parallelo (batch >= 3) con wiki-keeper-worker.
---
# Protocollo di Ingest

Riferimenti: `citation-rules`, `wiki-log-entry`, `wiki-gap-protocol`, `scrivi-wiki-page`.

## Fase 0 — Bootstrap

- `Glob raw/**/*.{txt,md}` + Read `raw/.extraction-manifest.json`
- `Glob wiki/**/*.md` per sapere cosa c'è già
- Read ultimo `memory/episodic/*.md` per continuità con run precedente
- **Read `wiki/gaps.md`** (vedi `wiki-gap-protocol`): se ci sono gap aperti, mostra in chat la lista e proponi di colmarli prima o insieme al nuovo ingest. Attendi conferma esplicita.
- Decidi: ingest nuovo, update, gap-pickup, o no-op?

## Fase 1 — Analisi per documento (loop su manifest)

Per ogni `<data>-<nome>` nel manifest (non già coperto in `wiki/sources/`):

- Read `raw/<data>-<nome>.txt`
- `Glob raw/images/<data>-<nome>-fig-*.md`
- Mappa sezioni → pagine candidate karpathy-style (source / concept / entity / synthesis / runbook / incident)

**Branch parallelo (v2.4)**: se N ≥ 3 documenti nuovi, delega l'analisi (Read-only) a
sub-agent `wiki-keeper-worker` (uno per documento). Il worker ritorna candidate-pages
JSON. La scrittura resta esclusiva del keeper (single-committer §7 r.12).

Schema JSON di output worker (vedi `wiki-keeper-worker.md`):
```json
{
  "source_txt": "...",
  "candidate_pages": [{"path": "...", "type": "...", "thesis": "...", ...}],
  "gaps": [...],
  "contradictions": []
}
```

## Fase 1.bis — Merge (solo se branch parallelo)

Il keeper raccoglie i JSON dei worker, deduplica candidate pages, risolve conflitti
di slug, costruisce la mappa wikilink globale.

## Fase 2 — Proposta (STOP)

```
INGEST PROPOSTO
================
Documenti: <lista>
Pagine da creare: N (lista path)
Pagine da aggiornare: N
Figure referenziate: N
Gap identificati (prima passata): N
Procedo?
```

**Attendi conferma esplicita.**

## Fase 3 — Scrittura

- Per ogni pagina: usa `scrivi-wiki-page`. Una alla volta.
- Per ogni claim senza fonte robusta: apri un gap secondo `wiki-gap-protocol`.
- Citazioni e wikilink: secondo `citation-rules`.
- **Touch many small files**: 5–15 piccole pagine, non una mega-pagina.

## Fase 4 — Indice

Regenera `wiki/index.md` da `Glob wiki/**/*.md` (escludi `log.md`, `query/`, `lint/`).

## Fase 5 — Propagate (v2.6, se applicabile)

Se uno o più gap chiusi citano una `Q_NNN` risolta contestualmente, esegui `propagate-resolution` PRIMA del log entry di ingest.

## Fase 6 — Log entry (OBBLIGATORIA)

Append a `wiki/log.md` secondo `wiki-log-entry` (template `ingest` + `gap-closed`).

## Regola di concorrenza

Se durante l'ingest trovi una pagina con `## Storie collegate` non vuota → non toccare quella sezione, è del PM.

## Contraddizioni

Se un raw contraddice una wiki page esistente → **non risolvere silenziosamente**. Aggiungi `## Contradictions` alla pagina impattata; surface al chiamante.
