---
name: dev-protocol
description: Spina dorsale dell'operazione Develop (L4 → L5). 5 fasi gate → context → handoff iniziale → implementazione → DoD/handoff finale. Skill canonica di tutti i dev-agent (v2.7).
---
# Protocollo Develop (v2.7)

Riferimenti: `dev-handoff`, `vcs-handoff` (v2.8), `citation-rules`, `wiki-log-entry`, `wiki-gap-protocol`.
Operazione canonica `Develop` (PATTERN.md §3).

## Fase 1 — Gate

Verifica pre-condizioni; se una fallisce → **STOP** con messaggio specifico.

1. **Config**: `factory.config.yaml` presente con `code_path:` valorizzato, `routing.<layer>: agent`, `vcs.mode:` valorizzato (v2.8). Se `code_path` esterno al repo: documentato, accessibile.
2. **TSK**: `id` valido, frontmatter v2.7 valido (`layer:` + `consumer:` espliciti), `consumer: agent` (o override one-shot via comando), `status: todo`, dipendenze (`Dependencies` nel corpo) tutte `done`.
3. **Stack**: `raw/tech_stack.md` esiste e copre il layer corrente (es. `## Backend` per `be-dev`). Se mancante → apri gap, STOP.
4. **Architettura**: per layer non-QA, `design_&_architecture/<layer>_architecture.md` o `api_specs/openapi_schema.yaml` o `db_schemas/` coprono il TSK. Mancanza → apri gap o Q, STOP.
5. **Standards**: standards normativi citati nei raw sono presenti nell'architettura corrispondente (PATTERN §11).

## Fase 2 — Preparazione contesto

Read order:

1. `factory.config.yaml` (code_path, stack, vcs)
2. `raw/tech_stack.md` (vincoli)
3. TSK corrente (cosa fare)
4. US riferita (perché farlo, AC)
5. ADR rilevanti
6. Architettura layer
7. `wiki/**` solo per concept/synthesis citati (contesto, mai produrre design)
8. `<code_path>/**` per stato del codice esistente (esplorazione mirata)

## Fase 3 — Handoff iniziale

Edit `status: todo → in-progress` del TSK + `updated: YYYY-MM-DD`. **Solo questi 2 campi**; mai toccare il corpo.

## Fase 4 — Implementazione

- Scrivi codice solo in `<code_path>/` (sub-scope per layer: `frontend/`, `migrations/`, `tests/`, …).
- **Atomicità**: il TSK definisce lo scope; mai fix opportunistici fuori scope.
- **Standards verbatim**: PATTERN §11.
- **Gap detection**: se scopri un'ambiguità (es. quale validator usare, quale header HTTP, format JSON):
  - Risolvibile via lettura di raw/wiki esistenti → procedi citando.
  - Non risolvibile → apri gap (`wiki-gap-protocol`) o Q hard (`apri-question`). STOP se Q hard.
- **STOP su scelte architetturali**: se il TSK ti chiede implicitamente di disegnare (es. "scegli pattern", "definisci schema"), STOP: è del `lead-architect`. Apri gap.

## Fase 5 — DoD verification + handoff finale

1. Verifica i punti della sezione `## Definition of Done` del TSK uno per uno.
2. Se tutti pass → segna in `dev-handoff` `DoD: pass`.
3. Se alcuni fallisce → `DoD: partial — <descrivi cosa manca>`. **Non chiudere il TSK** se i DoD critici fallisco; lascia `in-progress` o riapri.
4. **VCS handoff (v2.8)**: invoca `vcs-handoff` per proporre il commit. Gate umano obbligatorio.
5. Edit `status: in-progress → done` (solo se DoD pass) + `updated:` del TSK.
6. Append a `wiki/log.md` secondo `dev-handoff`.

## Vincoli inviolabili

- **Mai editare il corpo del TSK** (solo `status:`/`updated:` frontmatter).
- **Mai scrivere su `wiki/**`** fuori `wiki/log.md` + `wiki/gaps.md` (append-only).
- **Mai scrivere su `design_&_architecture/`**.
- **Mai eseguire operazioni VCS distruttive automatiche** (PATTERN §7 r.14). Tutto via `vcs-handoff` con gate umano.
- **STOP** se `code_path` non valorizzato o se topology incoerente.
- **Standards verbatim** (PATTERN §11): mai sostituire con equivalenti.

## Anti-pattern

| Anti-pattern | Correzione |
|---|---|
| Disegnare architettura nel codice | STOP, apri gap per `lead-architect` |
| Aggiungere endpoint non in OpenAPI | Apri gap, mai inventare |
| Fix di bug fuori scope TSK | Apri TSK nuovo via PM/TPM |
| Commit automatico senza `vcs-handoff` | Vietato (PATTERN §7 r.14) |
| Editare il corpo del TSK | Vietato — solo `status:`/`updated:` |
