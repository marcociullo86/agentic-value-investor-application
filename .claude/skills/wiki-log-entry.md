---
name: wiki-log-entry
description: Template canonici per gli append a wiki/log.md. Riferimento unico per ogni agente che logga operazioni.
---
# Template di log entry (canonici)

`wiki/log.md` è **append-only** (vedi `PATTERN.md §7 r.5`).

## Formato generale

```
[YYYY-MM-DD HH:MM] <operation> — <one-line summary> — files touched: <N>
```

## Template per operazione

### `ingest` (wiki-keeper)

```
## [YYYY-MM-DD] ingest | <nomi-pdf separati da +>
Pagine create: N | Figure: N | Aggiornamenti: N | Gap nuovi: N | Gap chiusi: N
```

Per ogni gap chiuso:
```
[YYYY-MM-DD HH:MM] gap-closed — <slug> via [[<pagina>]] — files touched: 1
```

### `query` (wiki-query)

```
## [YYYY-MM-DD] query | <prime parole della domanda>
```

### `lint` (wiki-lint)

```
## [YYYY-MM-DD] lint | check completo
Orphan: N | Broken: N | Unsourced: N | Kanban: N err | Coerenza: N err | Topology: N err | VCS: N err
```

### `promote` (orchestrator)

```
[YYYY-MM-DD HH:MM] promote — <path> <old-status> → <new-status> — files touched: 1
```

### `plan` (product-manager)

```
[YYYY-MM-DD HH:MM] plan — EP-XXX created (N stories) — files touched: <N>
```

### `design` (lead-architect)

```
[YYYY-MM-DD HH:MM] design — <componenti: BE/FE/DB/API> + ADR-NNN — files touched: <N>
```

### `execute` (tpm)

```
[YYYY-MM-DD HH:MM] execute — sprint NN with <N> tasks — files touched: <N>
```

### `bootstrap` (sync-docs)

```
[YYYY-MM-DD HH:MM] bootstrap — <N> PDF extracted to raw/ — files touched: <N>
```

### `reconcile-needed` (wiki-keeper via `propagate-resolution`, v2.6)

Marker emesso quando il keeper chiude un gap che cita una `Q_NNN` risolta
contestualmente, ma una o più US dipendenti hanno ancora `Q_NNN` in
`blocked_by` / `pending_clarification`. Una riga per US stale:

```
[YYYY-MM-DD HH:MM] reconcile-needed — US-YYY → Q_NNN closed (gap [[<slug>]]) — files touched: 0
```

`files touched: 0` perché il keeper non scrive sul kanban (proprietà PM, §2).
L'orchestrator lo surfaceizza in dashboard come "🔁 N reconcile-needed pendenti".
Chiusura del marker: implicita (`state-scan` ricalcola da filesystem).

### `develop` (dev-agent v2.7)

Vedi skill canonica `dev-handoff` per il formato esteso. Sintesi:
```
## [YYYY-MM-DD HH:MM] develop TSK-ZZZ
Agente: <be|fe|db|qa>-dev | Layer: <be|fe|db|qa|infra> | Files: N | Commit: <hash|n/a> | DoD: <pass|partial>
```

### `tech-scout` (skill omonima, v2.7)

```
[YYYY-MM-DD HH:MM] tech-scout — raw/tech_stack.md.proposal generated (N alternative) — files touched: 1
```

### `vcs-handoff` (skill omonima, v2.8)

```
[YYYY-MM-DD HH:MM] vcs-handoff — proposed <action> on <repo|submodule> — gate: <approved|pending|rejected>
```

### `policy` / `docs` / `migration` (meta-eventi)

```
[YYYY-MM-DD HH:MM] policy — <descrizione concisa> — files touched: <N>
```

## Regole

- **Mai overwrite**: append-only è inviolabile.
- **Sempre `files touched`**: numero intero, anche `0` se l'operazione è abortita.
- **Timestamp obbligatorio**: `YYYY-MM-DD HH:MM` in italiano (Europe/Rome).
- **One-line summary < 120 caratteri**: se serve dettaglio, va nella pagina dedicata (synthesis, runbook, incident).
