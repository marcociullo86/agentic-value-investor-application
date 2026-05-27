---
name: feedback-router
description: Skill di handoff Reviewer → dev-agent (PATTERN §19.4, v2.12). Produce task_package machine-readable, applica strategia di batching, gestisce loop control (max_iterations + no-progress + regression). Mai modifica codice direttamente.
---
# Skill — Feedback Router

Riferimenti: PATTERN §19.4 (Feedback Router), §19.6 R.Q2/R.Q3/R.Q4 (invarianti),
§7 r.16 (gate umano `reject`). Invocata dall'ultima fase di
[`code-review-protocol`](./code-review-protocol.md) con il report appena prodotto.

## Input

```python
{
  "report": <report json prodotto da code-review-protocol Fase 4>,
  "tsk":    <frontmatter del TSK in review>,
  "config": <factory.config.yaml.code_quality.router>
}
```

## Output

Side effects + un return value strutturato:

- **Side effect 1** — aggiorna frontmatter del TSK target (`review_status:`,
  `review_iter:`, `review_report:`, `updated:`). Mai del corpo (R.Q2).
- **Side effect 2** — append entry a `wiki/log.md` (template `review` di
  `wiki-log-entry`).
- **Return value**:
  ```json
  {
    "action": "close | dispatch_dev | escalate_human",
    "task_package": {...} | null,           // se action == dispatch_dev
    "target_agent": "be-dev | fe-dev | db-dev | qa-dev | null",
    "next_review_iter": <N+1 | null>,
    "escalation_message": "..." | null
  }
  ```

## Decisione per verdict

### `verdict: pass`

- `action: close`
- Aggiorna frontmatter TSK: `review_status: passed`, `review_iter: <N>`,
  `review_report: code_quality/reports/<TSK-id>-iter-<N>.md`.
- Append `wiki/log.md` con verdict `pass`.
- Suggerisci esplicitamente in chat:
  - Se ci sono pagine wiki in `status: review` citate dal TSK → suggerisci
    `/promote <path> approved` (l'orchestrator decide).
  - Se il TSK era l'ultimo dello sprint → suggerisci di chiudere lo sprint.
- Return: `{action: "close", task_package: null, target_agent: null, ...}`

### `verdict: conditional`

- `action: dispatch_dev`
- Determina `target_agent` da `tsk.layer`:
  - `be` → `be-dev` (se `routing.be: agent` + agent file presente)
  - `fe` → `fe-dev`
  - `db` → `db-dev`
  - `qa` → `qa-dev`
  - Fallback: `code-reviewer` segnala «no target agent disponibile per layer X»,
    forza `escalate_human` con messaggio chiaro.
- **Costruisci `task_package`** (vedi §19.4 schema):

  ```json
  {
    "tsk_id": "<tsk.id>",
    "iter": <N>,
    "constraint": {
      "scope": "fix only the findings below; no opportunistic refactor",
      "max_diff_lines": <config.max_diff_lines>
    },
    "actions": [
      {
        "rule_id": "...",
        "rule_ref": "code_quality/rules/<tier>/<rule_id>.md",
        "file": "...",
        "lines": [start, end],
        "current_snippet": "<3-5 righe contestuali>",
        "expected_fix": "...",        // dalla regola.detection_hints o sintetizzato
        "acceptance_criteria": "..."  // verificabile alla prossima passata
      }
    ],
    "report_ref": "code_quality/reports/<tsk_id>-iter-<N>.md",
    "previous_files_modified": [...]   // input per regression detection del prossimo round
  }
  ```

  Le `actions` sono **ordinate** secondo `config.router.ordering`:
  - `severity_then_complexity` (default): `severity DESC` + `fix_complexity ASC` (prima
    quelle gravi e facili — massimizza impatto del primo round, §19.4).
  - `complexity_then_severity`: `fix_complexity ASC` + `severity DESC` (prima i quick win,
    poi i big-ticket).

- **Applica strategia di batching** (`config.router.strategy`):

  | Strategia | Trigger | Effetto sul `task_package` |
  |---|---|---|
  | `all-in-one` | `len(findings) ≤ thresholds.batching_split` (default 7) | Un solo `task_package` con tutte le actions |
  | `severity-tiered` | `len(findings) > batching_split` | Splitta in 3 sub-package per tier (`high+critical` → `medium` → `low`). Il router consegna SOLO il primo tier nell'iterazione corrente; gli altri tier vivono come "follow-up" attesi nei round successivi (non scritti come TSK separati — same TSK, more iters). |
  | `split-by-area` | Finding distribuiti su moduli con `code_path` disgiunto | Splitta in N `task_package` paralleli, uno per modulo, con `actions` filtrate per `code_path` overlap. Lo scheduler §18 dispatcherà N invocazioni del dev-agent in parallelo (antichain conflict-free). |

  L'aggregator di Fase 3 può imporre `strategy` override se rileva pattern (es. force
  `severity-tiered` se markers `regression: true`).

- Aggiorna frontmatter TSK: `review_status: conditional`, `review_iter: <N>`,
  `review_report: ...`. **Non incrementare ancora** `review_iter` per la prossima:
  il dev-agent lavora, poi il code-reviewer al prossimo run incrementerà.

  Wait — semantica corretta: dopo questa iterazione, il TSK è "in re-Develop". Il
  prossimo trigger del code-reviewer farà `review_iter = N+1`. Quindi qui salviamo
  l'iter appena conclusa: `review_iter: N`.

- Append `wiki/log.md` con verdict `conditional`.

- **Gate umano per il dispatch** (analogo §7 r.15 publish): mostra in chat:

  ```
  REVIEW CONDITIONAL — TSK-XXX iter N
  ===================================
  Verdict: conditional ({M} finding, {H} high / {Md} medium / {L} low)
  Strategy: {strategy}
  Target: {target_agent} per re-Develop
  task_package: {N} actions, max_diff_lines: {X}
  Prossima review sarà iter {N+1} / {max_iterations}
  Procedo con dispatch del dev-agent? [y/N]
  ```

  Se `y` → return `action: dispatch_dev` (caller orchestra l'invocazione).
  Se `N` → forza `escalate_human` con messaggio «User declined dev-agent dispatch».

- Return: `{action: "dispatch_dev", task_package: {...}, target_agent: "<X>-dev",
  next_review_iter: <N+1>, escalation_message: null}`

### `verdict: reject`

- `action: escalate_human`
- Aggiorna frontmatter TSK: `review_status: rejected`, `review_iter: <N>`,
  `review_report: ...`.
- Append `wiki/log.md` con verdict `reject` E marker speciale (
  `loop_exhausted | no_progress | regression`).
- **Mai** invocare dev-agent. **Mai** auto-revert. **Mai** auto-close del TSK
  (resta `status: done` — la chiusura del Develop è separata dal verdict di review).
- Mostra in chat un blocco di escalation:

  ```
  REVIEW REJECT — TSK-XXX iter N → ESCALATION UMANA (PATTERN §7 r.16)
  ================================================================
  Verdict: reject
  Markers: {loop_exhausted | no_progress | regression | budget_exceeded}
  Stack: {language}/{framework} (conf {c})
  Finding totali (iter N): {H} high / {Md} medium / {L} low
  Report: code_quality/reports/<TSK-id>-iter-<N>.md

  Possibili next step:
  1. Re-Develop manuale (umano) con istruzioni dal report → quando pronto,
     /review <TSK-id> per re-validare.
  2. Accept-as-is con override → apri wiki/incidents/YYYY-MM-DD-tsk-<id>-accepted.md
     spiegando perché il finding resta non risolto.
  3. Rollback del codice → git revert del commit del Develop; il TSK resta done
     ma il codice torna allo stato pre-Develop.

  Decidi e segnala. CQRL non auto-procede.
  ```

- Return: `{action: "escalate_human", task_package: null, target_agent: null,
  next_review_iter: null, escalation_message: "<as above>"}`

## Digest aggregato per autore (settimanale)

Indipendentemente dal verdict, **al primo run di code-review-protocol del lunedì**
(`date +%u` == 1) il router aggiorna i digest aggregati per dev-agent:

- Path: `code_quality/reports/_digests/<dev-agent>-<YYYY-WW>.md`
  (es. `_digests/be-dev-2026-W22.md`)
- Contenuto: top 5 `rule_id` violate negli ultimi 7 giorni per quel dev-agent +
  trend (counts settimana corrente vs precedente).
- Append (mai overwrite) la nuova settimana; le vecchie restano per analytics.
- Memoria semantica: aggiorna `memory/semantic/dev-<layer>-recurring-issues.md`
  con i top 3 pattern ricorrenti per il dev-agent (questo abilita
  "miglioramento a monte" — il dev-agent al prossimo Develop legge la memoria
  semantica e tende a evitare quei pattern).

## Regole anti-corner-case

- **task_package > MAX_FINDINGS_PER_PACKAGE (default 20)**: forza strategia
  `severity-tiered` anche se config dice `all-in-one`. Mai consegnare un fix-list
  troppo lungo al dev-agent (overhead cognitivo + maggior rischio di refactor
  opportunistico, §19.8).
- **target_agent non disponibile** (es. `tsk.layer: fe` ma `fe-dev.md` non esiste):
  forza `escalate_human` con messaggio «Layer X richiede consumer human secondo
  topology corrente; review report disponibile, fix manuale».
- **Verdict ambiguo (impossibile)**: l'aggregator (`code-review-protocol` Fase 3)
  deve sempre produrre un verdict in `{pass, conditional, reject}`. Se manca,
  forza `reject` con marker `verdict_missing: true` e log incident.

## Non in scope per questa skill

- **Eseguire il dev-agent**: il router prepara il `task_package` e lo restituisce.
  È l'orchestrator (o il caller di `/review`) che invoca il dev-agent target.
- **Modificare codice** (mai, R.Q2).
- **Decidere il verdict**: quello è scope dell'aggregator (Fase 3 di code-review-protocol).
- **Scrivere il report**: scope di Fase 4 di code-review-protocol. Il router lo legge.
