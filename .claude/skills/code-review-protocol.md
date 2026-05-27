---
name: code-review-protocol
description: Protocollo di review qualitativa post-Develop (PATTERN §19, v2.12). 5 fasi (Bootstrap → Stack detection → 3 Passate parallele → Aggregator → Router). Invocata da code-reviewer; sub-skill stack-detector + feedback-router.
---
# Protocollo di Code Quality Review

Riferimenti: PATTERN §19 (CQRL completo), §19.2 (Stack Detector), §19.3 (3 Passate +
Aggregator), §19.4 (Feedback Router), §19.6 (invarianti R.Q1-R.Q7), §7 r.16 (gate
umano `reject`). Sub-skill: [`stack-detector`](./stack-detector.md),
[`feedback-router`](./feedback-router.md).

## Prerequisiti

- `factory.config.yaml.code_quality.enabled: true` (R.Q5 — STOP altrimenti).
- `<code_path>` accessibile in lettura; per topologia VCS:
  - `monorepo`: `Read`/`Glob` su `<code_path>/**` direttamente.
  - `submodule`: `cd <submodule_path>` (mai `git submodule update --remote` automatico).
  - `sibling`/`external`: path assoluto, sola lettura.
- TSK target valido: `consumer: agent` + `status: done` + `review_status: pending`.
- `code_quality/rules/` esiste (o crealo vuoto al primo run; lint segnalerà se mancano
  regole per il `ruleset_id` rilevato).

## Costanti

```
MODEL                    = "claude-sonnet-4-6"  # passate; parametrizzabile
MAX_PARALLEL_PASSES      = 3                    # le 3 passate girano in parallelo (sub-skill interne)
MAX_FINDINGS_PER_PASS    = 30                   # cap difensivo, oltre → review theater suspect
PROMPT_TEMPERATURE       = 0.2                  # bassa per coerenza output JSON
```

## Fase 0 — Bootstrap

1. Read `factory.config.yaml.code_quality`. Se `enabled: false` → ABORT pulito,
   log a chat «CQRL disabilitato; abilitare con `code_quality.enabled: true`».
2. Read TSK target: frontmatter (id, layer, code_path, review_status, review_iter,
   review_report) + body markdown.
3. Verifica preconditions:
   - `review_status ∈ {pending, conditional}` → procedi; altrimenti STOP no-op.
   - `review_iter < max_iterations` → procedi; altrimenti forza verdict `reject` (sezione
     "Loop exhausted" sotto).
4. Calcola `current_iter = review_iter + 1` (incrementa per la nuova passata).
5. Determina `files_in_scope`:
   - Se `tsk.code_path` valorizzato → usa quei glob.
   - Altrimenti, dal commit del Develop (se `wiki/log.md` ha entry `develop TSK-ZZZ → <hash>`),
     calcola diff `git diff <hash>^ <hash> --name-only` (per `monorepo|sibling`; per
     `submodule` cd nel submodule).
   - Fallback (nessuna delle due): scansiona `<code_path>/**` recente (modificato dopo
     `tsk.updated`). Segnala in chat che lo scope è "best-effort".

## Fase 1 — Stack Detection

Invoca skill [`stack-detector`](./stack-detector.md) con `files_in_scope` + path radice
del progetto. Output: `stack_descriptor` (schema §19.2).

Se `stack_descriptor.confidence < code_quality.thresholds.confidence_min` (default 0.6):
- **Modalità degradata**: attiva flag `degraded: true` nel report.
- Le passate 1 e 3 useranno solo regole `{language}.*` (no framework-specific).
- La passata 2 (design) resta language-agnostic e quindi non degrada.
- Segnala in chat: «Stack confidence <X> < soglia; modalità degradata».

Se `confidence == 0` o `language: "other"` → ABORT, log + suggerisci `tech-scout` per
chiarire lo stack.

## Fase 2 — Tre passate (parallele, R.Q1)

Le 3 passate sono sub-skill **interne** (non sub-agent §19.9). Lancia in parallelo
(es. 3 tool-call asincrone se il runtime lo permette; sequenziali altrimenti).

### Input comune a tutte e 3

- `stack_descriptor` (Fase 1)
- `files_in_scope` con contenuto (max 10 file per passata; se >10, sampling
  prioritario su file con maggior LOC modificate)
- Regole rilevanti per stack: glob `code_quality/rules/{team-specific,emergent,canonical}/**`
  filtrato per `applies_to.language` + `applies_to.framework` + `applies_to.context`
  intersecato con la passata (vedi `passes.<name>` sotto). Priorità tier: team-specific
  > emergent > canonical (regola con stesso `rule_id` su tier più alto prevale).
- Output linter deterministici (Fase 2.bis): se `code_quality.passes.idiomaticity: true`
  E un linter standard è disponibile (`ruff` per python, `eslint` per JS/TS,
  `golangci-lint` per go, `clippy` per rust, …) esegui `Bash` per produrre output JSON
  iniettato come `linter_output` nel context della passata 1.

### Schema del prompt (5 sezioni)

```
[ROLE]
{role_persona_specifico_della_passata}

[CONTEXT]
- Stack: {stack_descriptor}
- Regole applicabili: {N regole filtrate per la passata}
- Linter output (se passata 1): {linter_output}
- File: {files_in_scope con contenuto}
- Iterazione: {current_iter} / {max_iterations}
- (Se current_iter > 1) Finding precedenti: {findings_iter_N-1}

[INPUT]
{Codice da revieware, con line numbers}

[TASK]
Identifica finding contro le regole {applicable_rules}. Per ogni finding produci:
- rule_id (deve esistere in regole applicabili — mai inventare)
- file, lines (range esatto)
- severity (high|medium|low — basato su severity_default della regola)
- rationale (≤ 50 parole, perché è un problema)
- fix_complexity (low|medium|high)
- auto_fixable (true se la regola è auto_fixable e il fix è meccanico)

Vincoli:
- Mai inventare rule_id che non esistono nelle regole applicabili.
- Mai duplicare finding (stesso rule_id su stesso file:lines).
- Max {MAX_FINDINGS_PER_PASS} finding per passata.
- Se nessun problema reale: ritorna findings: [].

[OUTPUT_CONTRACT]
JSON valido con schema:
{
  "pass": "idiomaticity | design | robustness",
  "findings": [...]
}
```

### Passata 1 — Idiomaticità (`role_persona: "Core contributor di {framework}"`)

Focus: astrazioni native, naming convention, style guide della community, no pattern
deprecati per la versione. Filtro regole: `applies_to.context` include "idiomaticity"
o "style".

### Passata 2 — Design (`role_persona: "Tech lead che dovrà mantenere il codice per 2 anni"`)

Focus: responsabilità, coesione, accoppiamento, naming, abstraction leak, complessità.
Filtro regole: `applies_to.context` include "design" o "architecture".
Pre-calcola metriche (`Bash` con tool deterministici se disponibili — `radon` per python,
`madge` per JS/TS, `gocyclo` per go, …) e inietta come `metrics_input` nel context.

### Passata 3 — Robustezza (`role_persona: "SRE che ha visto questo codice fallire in prod"`)

Focus: error handling, edge case, resource leak, concorrenza, validazione input,
timeout/retry. Filtro regole: `applies_to.context` include "robustness" o "reliability".
**Scope explicit** nel prompt: «Non valutare security (SAST, secret, dependency
scanning). Quella è scope di un layer dedicato — vedi PATTERN §19.6 R.Q7».

## Fase 3 — Aggregator (deterministico + mini-prompt)

### Step deterministico (no LLM call)

1. **Dedup**: raggruppa finding per `(file, lines, rule_id)`. Se ≥ 2 passate riportano
   lo stesso → mantieni una sola istanza con `detected_by: [pass1, pass2, …]` e severity
   = max(severity_per_pass).
2. **Soglie per stack**: applica `code_quality.thresholds` per filtri specifici (es.
   se la regola ha `applies_to.framework_version_min: "0.95"` e `stack.framework_version: "0.110"` ok;
   se è "0.110" ma min è "1.0" → skip).
3. **Cap finale**: se totale > 100, taglia per `severity DESC` + `fix_complexity ASC` e
   marca `truncated: true` nel report.
4. **No-progress detection** (se `current_iter > 1`): se `set(rule_id_iter_N) == set(rule_id_iter_N-1)`
   con tolerance esatta → marker speciale `no_progress: true` → forza verdict `reject`.
5. **Regression detection** (se `current_iter > 1`): se ci sono finding in file **NON**
   toccati dalla fix dell'iter precedente (lista `files_modified_by_fix` salvata in report
   precedente) → marker `regression: true` → flag rosso nel digest, raccomanda rollback.

### Step mini-prompt (1 LLM call, costo basso)

Prompt:
```
[ROLE] Sintetizzatore di review di codice.
[CONTEXT] {finding[]} aggregati e dedup. Stack: {stack}. Iter: {N} / {max}. Markers:
no_progress={true|false}, regression={true|false}.
[TASK] Produci:
1. summary: executive summary ≤ 200 parole.
2. verdict: pass | conditional | reject.
   Regole di verdict:
   - pass: 0 finding high/medium ESCLUSI low.
   - conditional: ≥ 1 finding high o ≥ 3 medium, NESSUN marker rosso.
   - reject: marker no_progress OR regression OR current_iter == max_iterations
     OR ≥ 5 finding high.
[OUTPUT_CONTRACT] JSON: {"summary": "...", "verdict": "...", "verdict_rationale": "..."}
```

## Fase 4 — Scrittura report

1. **Write** `code_quality/reports/<TSK-id>-iter-<N>.json` con schema §19.3:
   ```json
   {
     "tsk_id": "...",
     "stack_descriptor": {...},
     "iter": N,
     "passes_run": ["idiomaticity", "design", "robustness"],
     "findings": [...],
     "verdict": "pass | conditional | reject",
     "verdict_rationale": "...",
     "summary": "...",
     "markers": { "no_progress": false, "regression": false, "loop_exhausted": false, "degraded": false },
     "files_modified_by_fix": [...],   // input per regression detection nella prossima iter
     "generated_at": "<ISO-8601>",
     "reviewer_version": "code-reviewer@2.12.0"
   }
   ```

2. **Write** `code_quality/reports/<TSK-id>-iter-<N>.md` (digest umano-leggibile):
   ```markdown
   # Code Review — TSK-XXX — iter N

   ## Stack rilevato
   {language} / {framework} {version} (confidence {x})

   ## Verdict
   **{VERDICT}**. {summary}

   ## Finding ordinati
   | # | Severity | File:Lines | Rule | Rationale |
   |---|---|---|---|---|
   ...

   ## Loop status
   Iter {N}/{max}. {markers se presenti}

   ## Prossimo step
   {pass → chiusura | conditional → task_package consegnato al dev-agent | reject → escalation}
   ```

3. **Rotation**: se `code_quality.reports.retain_iterations: 5` e ci sono > 5 file per
   `<TSK-id>-iter-*`, elimina i più vecchi (mantieni gli ultimi 5).

## Fase 5 — Router

Invoca skill [`feedback-router`](./feedback-router.md) con:
- `report` (output Fase 4)
- `tsk` (frontmatter)
- `factory.config.yaml.code_quality.router`

Output del router:
- Per `verdict: pass` → aggiorna frontmatter TSK (`review_status: passed`,
  `review_iter: <N>`, `review_report: code_quality/reports/<TSK-id>-iter-<N>.md`,
  `updated: <ISO>`). Append `wiki/log.md` template (sotto). Suggerisci `/promote` se
  ci sono pagine wiki citate dal TSK e in `status: review`.
- Per `verdict: conditional` → produci `task_package` JSON in memoria e **mostra in
  chat**: «Consegno questo task_package al dev-agent <layer>-dev per re-Develop. Conferma?»
  Attendi `y/N`. Se `y`: invoca il dev-agent corrispondente (è scope dell'orchestrator
  fare il dispatch). Aggiorna frontmatter (`review_status: conditional`, `review_iter: <N>`,
  `review_report: ...`).
- Per `verdict: reject` → aggiorna frontmatter (`review_status: rejected`, `review_iter: <N>`,
  `review_report: ...`). **STOP** + escalation umana: mostra in chat il digest +
  marker (loop_exhausted / no_progress / regression) e attendi input.

## Append a `wiki/log.md`

Template (vedi `wiki-log-entry`):

```markdown
- YYYY-MM-DD HH:MM — `review TSK-XXX iter-N → <verdict>`
  - Reviewer: code-reviewer@2.12.0
  - Stack: {language}/{framework} {version} (conf {x})
  - Finding: {high: H, medium: M, low: L}, dedup: {D}
  - Markers: {no_progress|regression|loop_exhausted|degraded se attivi}
  - Report: [code_quality/reports/<TSK-id>-iter-<N>.md](../code_quality/reports/<TSK-id>-iter-<N>.md)
```

## Loop exhausted (gate §7 r.16)

Se `current_iter == max_iterations` E `verdict: conditional` calcolato:
- Forza verdict `reject` con `markers.loop_exhausted: true`.
- Scrivi il report normalmente (Fase 4).
- Router (Fase 5) gestisce come `reject` standard → escalation umana.

## Regole anti-corner-case

- **Repo vuoto o files_in_scope vuoto**: ABORT pulito, verdict implicito `pass` (no
  codice da revieware è caso degenere; segnala in chat ma non scrivere report).
- **Regole vuote per stack**: se `Glob code_quality/rules/**` non ritorna nulla per il
  `ruleset_id` rilevato, segnala in chat e ABORT con suggerimento «Popola
  `code_quality/rules/canonical/{language}.{framework}.*` o disabilita CQRL per questo
  stack». Mai inventare regole on-the-fly.
- **Conflitto cross-tier**: stesso `rule_id` in `canonical/` e `emergent/` con
  versioni diverse → vince il tier a priorità più alta E versione più recente come
  tiebreaker. Annota nel report `conflict_resolution: [{rule_id, chosen_tier, chosen_version}]`.
- **TSK senza commit-hash trackable**: se `wiki/log.md` non ha entry `develop` per il
  TSK e `tsk.code_path` è vuoto, lo scope è ambiguo. Procedi best-effort sull'intero
  `<code_path>` con flag `scope_inferred: true` nel report. Mai bloccare.

## Non in scope per questa skill

- **Eseguire codice** del repo per validare runtime behavior — quello è scope di
  `qa-dev` (test funzionali). CQRL legge codice statico + linter output.
- **Modificare codice** — mai (R.Q2).
- **Promuovere regole** — `emergent` → `canonical` è gate umano §19.5.
- **Pubblicare findings su tool esterni** — il report resta locale; pubblicazione
  opzionale è scope di un publisher futuro fuori da v2.12.
