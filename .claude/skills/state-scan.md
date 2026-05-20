---
name: state-scan
description: Scan dei 5 layer + memoria episodica per produrre la dashboard di stato. Riferimento dell'orchestrator.
---
# State scan (canonico)

Riferimenti: `PATTERN.md §8` (state derivation), `wiki-log-entry`.

## Output atteso

```
L | Status   | Ultimo update | Conteggio | Note
--|----------|---------------|-----------|----------------------------
L1| green    | YYYY-MM-DD    | N PDF     | manifest sincronizzato
L2| green    | YYYY-MM-DD    | N pagine  | (eventuale "📌 K gap aperti", "✨ M promotion candidates")
L3| amber    | YYYY-MM-DD    | N epiche  | gate: ⛔ hard / ⚠️ soft / ✅ clean (v2.6 graduato)
L4| green    | YYYY-MM-DD    | N task    | sprint corrente: SS (eventuale "🔁 R reconcile-needed")
L5| amber    | YYYY-MM-DD    | N file    | topology=full-stack-agents, code_path=./src/, VCS=monorepo
```

## Procedura (9 passi, v2.7+v2.8)

### 1. L1 status

```
Glob raw/*.pdf
Read raw/.extraction-manifest.json
```
- `green` se ogni PDF ha entry nel manifest.
- `amber` se PDF non estratti.
- `red` se il manifest manca.

### 2. L2 status

```
Read wiki/log.md  (ultime entry per tipo)
Glob wiki/**/*.md  (escludi log/gaps/query/lint)
```
Conteggio per sezione (concepts/entities/...).

### 3. Gap pendenti

```
Read wiki/gaps.md
```
Conta sezioni senza riga `**Risolto:**`. Se > 0 → "📌 N gap pendenti — suggerisci wiki-keeper".

### 4. L3 status + gate (graduato, v2.6)

```
Glob management/kanban/EP-*/EP-*.md
Read management/questions.md  (parse blocking_level di ogni Q in [APERTE])
```
**Gate graduato (PATTERN.md §7 r.9):**
- `hard_open > 0` → ⛔ "L4 hard-bloccato su US dipendenti (Q hard: <lista>)"
- `soft_open > 0 && hard_open == 0` → ⚠️ "L4 parziale — N Q soft, Arch+TPM possono procedere con `pending_clarification`"
- Tutte chiuse → ✅ "L4 gate clean"

### 5. L4 status

```
Read design_&_architecture/be_architecture.md
Glob management/kanban/**/TSK-*.md
Read management/kanban/sprint.md
```

### 6. L5 status (v2.7)

```
Read factory.config.yaml  (topology, code_path, routing, vcs.mode)
Glob <code_path>/**  (se interno al repo)
Grep "develop TSK-" wiki/log.md
```
- `green` se ci sono `develop` entry recenti e TSK done coerenti.
- `amber` se code_path valorizzato ma vuoto.
- `red` se code_path mancante con dev-agent presenti.

### 7. Reconcile-needed pendenti (v2.6, da operazione `Propagate`)

```
Grep "reconcile-needed" wiki/log.md
```
Conta marker ancora attivi (Read della US referenziata: `Q_NNN` ancora in
`blocked_by` o `pending_clarification`). Se > 0 → "🔁 N reconcile-needed
pendenti (US: <lista>)" come **prima** riga note.

### 8. Auto-promotion candidates (v2.6)

```
Glob wiki/{concepts,entities,syntheses}/*.md
Grep "wiki_page:" management/kanban/EP-*/US-*/US-*.md
```
Per ogni pagina wiki `status: draft`: conta US `committed|in-progress|done`
che la citano. Se ≥ 2 → promotion candidate. Max 5 in dashboard.

### 9. Continuità

```
Read memory/episodic/<ultimo>.md
```

## Suggerimento next-step

Heuristica (priorità decrescente, v2.7):

1. **🔁 Reconcile-needed pendenti > 0** → `product-manager` per riconciliare le US elencate.
2. Gate `hard` aperto → rispondi alle Q hard in `management/questions.md`.
3. Gap pendenti > 0 → `wiki-keeper`.
4. L1 ha PDF non estratti → `/sync-docs`.
5. L2 stale rispetto a L1 → `wiki-keeper`.
6. **Auto-promotion candidates > 0** → considera `/promote <path> review`.
7. L3 vuoto → `product-manager`.
8. L4 vuoto + nessuna Q hard sulle US target → `lead-architect`.
9. L4 architettura ma no task → `tpm` sulle US sbloccate.
10. **L4 con TSK `consumer: agent, status: todo` ready** → `/dev <TSK-id>` (v2.7).
11. **Stack `auto` ma `raw/tech_stack.md` non popolato** → invoca `tech-scout`.

**Mai delegare automaticamente.** Solo suggerire. Auto-promotion (regola 6) è **solo suggerimento** — l'orchestrator può modificare `status:` solo via `/promote` esplicito.

## Episodic memory (output collaterale)

Append a `memory/episodic/<YYYY-MM-DD-HH-MM>-run.md`:

```markdown
---
type: episodic
created: YYYY-MM-DD HH:MM
tags: [run, state-scan]
---
# Run del YYYY-MM-DD HH:MM

## Stato osservato
- L1: <status> (<conteggio>)
- L2: <status> (<conteggio>, gap=<N>, promotion-candidates=<M>)
- L3: <status> (<conteggio>, gate=<hard|soft|clean>, hard_open=<H>, soft_open=<S>)
- L4: <status> (<conteggio>, reconcile-needed=<R>)
- L5: <status> (<conteggio>, topology=<...>, code_path=<...>, vcs.mode=<...>)

## Decisione presa
Next-step suggerito: <agente> per <motivo>.

## Riferimenti
- Run precedente: memory/episodic/<file>.md
```
