---
name: parallel-scheduling
description: Algoritmo DAG-based per riconoscere e dispatchare operazioni parallele in modo sicuro (PATTERN §18, v2.11). Invocato dall'orchestrator.
---
# Parallel scheduling protocol (v2.11)

Riferimenti: PATTERN §18 (modello DAG, regole R.S1–R.S8), `state-scan` (input
candidates), `wiki-log-entry` (log dei wave), §5 (campi frontmatter
`depends_on` / `blocked_by` / `code_path`).

Eseguito dall'**Orchestrator** in 5 fasi: Discovery → Build DAG → Toposort &
Partition → Gate → Dispatch.

## Fase 0 — Discovery dei candidati

Input: stato corrente del repo (`/run` → `state-scan`).

- `Glob management/kanban/**/TSK-*.md` filtrato per:
  - `status: todo`
  - `consumer: agent`
  - `consumer: <layer>` agent presente in `.claude/agents/` (es. `be-dev.md` per `layer: be`)
  - `factory.config.yaml.scheduler.domains.develop: true`
- `Glob management/kanban/**/US-*.md` per il **resolve** di `depends_on TSK` cross-storia (vedi §1).
- Read `factory.config.yaml.scheduler` (default valori in PATTERN §18.5 se assente).
- Read `wiki/gaps.md` per `blocked_by Q_NNN` ancora aperte.

Output: lista candidati `V` con per ciascuno: `id`, `layer`, `priority`, `estimate`, `depends_on`, `blocked_by`, `code_path`.

## Fase 1 — Build DAG

Costruisci `G = (V, E_dep ∪ E_conf)`:

### `E_dep` (causal, oriented)

Per ogni `u ∈ V`:
- Per ogni `v_id ∈ u.depends_on`:
  - Read `management/kanban/**/<v_id>.md`
  - Se `v.status != done` → aggiungi arco `v → u`
  - Se `v.status == done` → la dipendenza è soddisfatta, **non** aggiungere arco
- Per ogni `q_id ∈ u.blocked_by`:
  - Cerca `q_id` in `management/questions.md` o `wiki/gaps.md`
  - Se aperta → aggiungi un **virtual root** `Q_NNN` con arco `Q_NNN → u` (mai resolvibile da uno scheduler → il TSK resta in coda, non eseguito)

### `E_conf` (file conflict, unoriented)

Solo per i TSK con `factory.config.yaml.scheduler.code_path_conflict ≠ off`:

```
for u, v in pairs(V):
  if u.code_path == [] or v.code_path == []:
    if empty_code_path_policy == 'serial':
      E_conf.add({u, v})    # entrambi serializzanti
    # else 'parallel': no arco
  elif glob_intersect(u.code_path, v.code_path):
    E_conf.add({u, v})
```

`glob_intersect(A, B)` = `True` se esiste un path che matcha sia un glob di `A`
sia un glob di `B`. Implementazione minimale: expand glob a regex, check
overlap su prefisso comune (es. `src/auth/**` vs `src/auth/handlers/**` →
overlap; `src/auth/**` vs `src/users/**` → no overlap).

### Validazione

- **Cycle detection** su `E_dep`: DFS con stack di visita. Se ciclo → `ABORT` con messaggio:
  ```
  ERRORE: ciclo in depends_on:
    TSK-A → TSK-B → TSK-C → TSK-A
  Risolvere a mano (rimuovere una dipendenza). /run non procede.
  ```
- **Orphan `depends_on`**: `v_id` referenziato ma file non trovato → warning, l'arco è ignorato (no blocco, ma loggato come anomalia per `wiki-lint`).

## Fase 2 — Toposort + level grouping

Algoritmo di Kahn modificato per assegnare i **level** (antichain):

```
in_degree := {v: |{e ∈ E_dep | e = (_, v)}| for v in V}
level := {}
ready := {v in V | in_degree[v] == 0}
current_level := 0

while ready not empty:
  level[v] := current_level for v in ready
  next_ready := {}
  for v in ready:
    for (v, u) in E_dep:
      in_degree[u] -= 1
      if in_degree[u] == 0:
        next_ready.add(u)
  ready := next_ready
  current_level += 1

if any v in V not in level:    # nodi orfani → c'era un ciclo
  ABORT "ciclo rilevato (post-validate)"
```

Output: `levels[i] = [v_1, v_2, ...]` (antichain al level `i`).

## Fase 3 — Partition per conflict detection

Per ogni level `L_i`, applica **graph coloring greedy** su `E_conf` ristretto a `L_i`:

```
def partition(level_nodes, E_conf):
  nodes_sorted := sort(level_nodes, key=lambda v: (-v.priority_score, v.estimate_score))
  # priority_score: P0=3, P1=2, P2=1
  # estimate_score: XS=1, S=2, M=3, L=4
  groups := []
  for v in nodes_sorted:
    placed := False
    for g in groups:
      if not any({v, u} in E_conf for u in g):
        if len(g) < max_parallel:    # R.S3
          g.append(v)
          placed = True
          break
    if not placed:
      groups.append([v])
  return groups
```

Output per level: lista di `groups`, dove ogni `group` è parallelizzabile.

## Fase 4 — Gate

Per ogni `group`:

- Se `len(group) >= scheduler.parallel_gate_threshold` (default 3):
  - Stampa il **wave plan** in chat (formato §18.6).
  - Attendi conferma esplicita `y/N`. Su `N` → ABORT (no parziali).
- Se `len(group) < threshold`:
  - Stampa il wave plan come info (no gate).

**Wave plan template**:

```
WAVE PLAN (sprint NN, sched v2.11)
====================================
Level 0 — parallel (3 of max 4):
  ▸ Group A:
    • TSK-007 [be, S, P0] code_path=src/auth/**
    • TSK-012 [db, M, P1] code_path=db/migrations/0042_*.sql
    • TSK-019 [fe, S, P0] code_path=web/src/login/**
Level 1 — serial (2 nodes, depends_on Level 0):
  ▸ TSK-008 [be, S, P0] depends_on=[TSK-007]
  ▸ TSK-013 [qa, M, P1] depends_on=[TSK-007,TSK-012,TSK-019]

VCS hand-off accodato seriale dopo ogni wave.
Procedo? [y/N]
```

## Fase 5 — Dispatch

Per ogni `level i`:
  Per ogni `group g in level[i]`:
    1. **Multi-tool-call** nello stesso turno: N invocazioni `Agent` parallele,
       una per ogni TSK in `g` (adapter Claude Code: subagent_type = `<layer>-dev`).
    2. Attendi che TUTTI i sub-agent del group terminino (foreground).
    3. Per ognuno:
       - Output OK → `dev-handoff` ha già aggiornato `status: done` + appendato `wiki/log.md`.
       - Output FAIL → append `wiki/log.md` entry `develop-failed TSK-ZZZ rationale=...`. Il TSK resta `status: todo`. **Non rollba** gli altri (R.S7).
    4. **VCS hand-off serializzato** (R.S8): per ogni TSK terminato con successo, invoca `vcs-handoff` **uno alla volta** in coda al group.

Quando `level i` è completo (tutti i group dispatched + VCS chiusi), passa a `level i+1`.

## Fase 6 — Log

Append a `wiki/log.md` (template `wave`):

```
## YYYY-MM-DD HH:MM — wave sprint-NN
**Levels:** 2 (0=3 parallel, 1=2 serial)
**Dispatched:** 5 TSK (4 ok, 1 failed)
**Failed:** TSK-013 (reason: vcs-handoff abortito da utente)
**Wall-clock saved:** ~estimated 60% vs serial baseline
```

E un record episodico in `memory/episodic/YYYY-MM-DD-HH-MM-wave-NN.md` con la
struttura completa del DAG (per audit + retroactive analysis).

## Regole inviolabili (R.S1–R.S8, PATTERN §18.4)

- **R.S1**: Single-committer su `wiki/log.md` e `wiki/gaps.md` — l'orchestrator
  serializza le append, mai due agent scrivono nello stesso turno.
- **R.S2**: Conflict-free su `code_path` — `partition()` lo garantisce.
- **R.S3**: Cap `max_parallel` (default 4).
- **R.S4**: Gate umano sopra `parallel_gate_threshold` (default 3).
- **R.S5**: Ciclo in `depends_on` → ABORT, no auto-fix.
- **R.S6**: Re-scheduling idempotente — DAG ricostruito da zero ogni run.
- **R.S7**: Fallimento di un sub-agent non rollba gli altri.
- **R.S8**: VCS sempre serializzato — coda di `vcs-handoff` a fine wave.

## Quando NON eseguire (short-circuit)

- `factory.config.yaml.scheduler.enabled: false` → l'orchestrator esegue il
  comportamento pre-v2.11 (suggerisce **un solo** next-step, niente DAG).
- `|V| == 1` → un solo candidato, nessun DAG da costruire, dispatch diretto.
- `topology: knowledge-only` o `plan-only` → no L5 → niente da parallelizzare
  a livello develop (ma ingest e lint paralleli restano possibili).

## Esempio di esecuzione (dry-run su sprint con 5 TSK)

Input candidates:
```
TSK-001 [be, S, P0] depends_on=[]            code_path=[src/db/**]
TSK-002 [fe, S, P0] depends_on=[]            code_path=[web/src/login/**]
TSK-003 [be, M, P0] depends_on=[TSK-001]     code_path=[src/auth/**]
TSK-004 [be, S, P1] depends_on=[TSK-001]     code_path=[src/auth/handlers/**]
TSK-005 [qa, M, P0] depends_on=[TSK-003,TSK-004,TSK-002] code_path=[tests/e2e/**]
```

E_dep:
```
TSK-001 → TSK-003
TSK-001 → TSK-004
TSK-003 → TSK-005
TSK-004 → TSK-005
TSK-002 → TSK-005
```

E_conf:
```
{TSK-003, TSK-004} (overlap src/auth/** ∩ src/auth/handlers/**)
```

Levels:
- Level 0: TSK-001, TSK-002 → no conflict → 1 group di 2 → parallel
- Level 1: TSK-003, TSK-004 → conflict → 2 group di 1 ciascuno → serial fra loro
- Level 2: TSK-005 → 1 group di 1 → solo

Plan: 4 wave (Level 0 parallel; Level 1.a; Level 1.b; Level 2).
Wall-clock saved: 1 wave eliminato dal parallelismo del Level 0.
