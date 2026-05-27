---
name: lint-checks
description: Procedure dei 4 check eseguiti dal wiki-lint.
---
# Check del wiki-lint

Riferimenti: `citation-rules` (per la definizione di "claim non citato"),
`wiki-log-entry` (per il template del log report).

## Check 1 — Orphan + wikilink (scan unico)

1. `Glob wiki/**/*.md` (escludi `log.md`, `index.md`, `query/`, `lint/`).
2. Read `wiki/index.md`, estrai tutti i `[[…]]` e i path linkati.
3. Per ogni file: se non è linkato dall'index → **WARNING orphan**.
4. Read ogni pagina wiki: estrai `\[\[([^\]]+)\]\]`. Per ogni wikilink: verifica
   esista un file con slug corrispondente.
   - Wikilink che non risolve → **ERROR broken-link**.

## Check 2 — Claim senza fonte

Vedi `citation-rules` per la definizione canonica di "claim che richiede
citazione" (≥ 20 parole, esenzioni, ecc).

Procedura:
- Per ogni `wiki/**/*.md`, identifica frasi affermative che secondo
  `citation-rules` devono essere citate.
- Per ognuna: verifica che entro 3 righe successive (o nella stessa riga) ci sia
  un `[^src: …]` o un `[[…]]`.
- Assenza → **WARNING unsourced-claim**.

## Check 3 — Integrità kanban

Per ogni `management/kanban/EP-*/EP-*.md`:
- Frontmatter ha `id`, `title`, `status`, `priority`, `confidence`? Altrimenti **ERROR**.
- `id` matcha il pattern `EP-XXX` con XXX = nome cartella? Altrimenti **ERROR**.

Per ogni `US-*.md`:
- Frontmatter ha `id`, `title`, `role`, `priority`, `status`, `wiki_page`?
- `wiki_page` punta a file esistente? Altrimenti **ERROR**.

Per ogni `TSK-*.md` (v2.7):
- Frontmatter ha `id`, `sprint`, `layer`, `consumer`, `priority`, `estimate`, `status`?
- `id` univoco globalmente (cross-cartelle)?
- `layer` ∈ `{be, fe, db, qa, infra}` → altrimenti **ERROR invalid-layer**.
- `consumer` ∈ `{agent, human}` → altrimenti **ERROR invalid-consumer**.
- Campo legacy `team:` ancora presente → **WARNING deprecated-field** (v2.7,
  migrazione manuale a `layer:`).

## Check 4 — Coerenza wiki ↔ kanban

- Ogni US referenzia una pagina wiki: la pagina esiste?
- Ogni `## Storie collegate` in wiki ha solo storie esistenti?

### 4g — Coerenza scheduler/depends_on (v2.11, PATTERN §18)

Solo se almeno un EP/US/TSK in `management/kanban/**` ha frontmatter `depends_on:` valorizzato:

- Per ogni artefatto con `depends_on: [...]`:
  - Ogni `<id>` nella lista deve avere lo stesso prefisso dell'artefatto host (EP→EP, US→US, TSK→TSK). Cross-tipo (es. TSK in `depends_on` di US) → **ERROR `invalid-depends-on-type`**.
  - Ogni `<id>` deve essere file esistente in `management/kanban/**/<id>.md`. Assente → **WARNING `orphan-depends-on`** (referenza a artefatto eliminato o rinominato).
  - Auto-riferimento (`depends_on` contiene il proprio `id`) → **ERROR `self-depends-on`**.
- **Cycle detection**: costruisci DAG `E_dep` sull'insieme {EP, US, TSK} e applica toposort (algoritmo di Kahn). Se rimangono nodi con `in_degree > 0` a fine algoritmo → ciclo presente → **ERROR `depends-on-cycle`** con lista dei nodi nel ciclo. Non `heal-eligible` (richiede giudizio semantico).
- **Drift body ↔ frontmatter** (solo TSK): se il body contiene `## Dependencies\n- TSK-XXX` ma `TSK-XXX` non è in `depends_on:` frontmatter (o viceversa) → **WARNING `dependencies-drift`** (frontmatter prevale per lo scheduler; rinconciliare a mano).
- **`code_path` validation** (solo TSK con `code_path:` valorizzato):
  - Ogni glob deve essere stringa non vuota. Glob vuoto → **WARNING `empty-code-path-glob`**.
  - Se `factory.config.yaml.scheduler.code_path_conflict: strict` e ≥ 2 TSK al "level 0" (depends_on vuoto o tutti soddisfatti) condividono lo stesso glob esatto → **INFO `code-path-overlap`** (non error; informativo per chi pianifica lo sprint, segnala che i due TSK saranno serializzati dal partition step).
- **`blocked_by` su TSK** (v2.11, esteso da US):
  - Ogni `Q_NNN` referenziato deve esistere in `management/questions.md`. Assente → **WARNING `orphan-blocked-by-q`**.
  - Q in `[RISOLTE]` ancora in `blocked_by` di un TSK → **WARNING `stale-blocked-by-tsk`** (simmetrico al check 4b su US; genera `reconcile-needed`).
- **`scheduler:` block coerenza** (solo se `factory.config.yaml.scheduler` esiste):
  - `enabled` ∈ `{true, false}`. Altrimenti → **ERROR `invalid-scheduler-enabled`**.
  - `max_parallel` intero ≥ 1. Altrimenti → **WARNING `invalid-max-parallel`** (applica default 4).
  - `parallel_gate_threshold` intero ≥ 1 e ≤ `max_parallel`. Altrimenti → **WARNING `invalid-gate-threshold`** (applica default 3).
  - `code_path_conflict` ∈ `{strict, warn, off}`. Altrimenti → **ERROR `invalid-conflict-mode`**.
  - `empty_code_path_policy` ∈ `{serial, parallel}`. Altrimenti → **ERROR `invalid-empty-policy`**.

### 4f — Coerenza Publisher (v2.10, PATTERN §17)

Solo se `factory.config.yaml.kanban_publish` esiste:

- Read `factory.config.yaml.kanban_publish`. Estrai `provider`, `target`, `auth_env`, `mode`, `batch_limit`, `mapping`.
- `provider` ∈ `{none, github, gitlab, jira, linear, custom}`. Altrimenti **ERROR `invalid-publish-provider`**.
- `mode` ∈ `{push-only}` per v2.10 (`bidirectional` riservato a v2.11). Altrimenti **ERROR `invalid-publish-mode`**.
- Se `provider ≠ none`:
  - `target` non vuoto. Assenza → **ERROR `missing-publish-target`**.
  - `auth_env` non vuoto. Assenza → **ERROR `missing-publish-auth-env`**.
  - `batch_limit` intero ≥ 1. Altrimenti **WARNING `invalid-batch-limit`** (applica default 10).
  - Mapping coerente: `mapping.epic_to ∈ {milestone, issue-label, project-column}`, `mapping.story_to ∈ {issue-label, issue-type-story}`, `mapping.task_to ∈ {issue-label}`, `mapping.sprint_to ∈ {milestone, project-iteration, cycle}`. Altrimenti **ERROR `invalid-publish-mapping`**.
  - Esistenza sub-agent corrispondente in `.cursor/agents/<provider>-publisher.md`. Assenza → **ERROR `publisher-agent-missing`**.
  - Esistenza skill `.cursor/skills/<provider>-mapping.md`. Assenza → **ERROR `publisher-mapping-missing`**.
- Per ogni `management/kanban/EP-*/EP-*.md`, `US-*/US-*.md`, `**/TSK-*.md`:
  - Frontmatter `external_id:` valorizzato:
    - Forma `<prefisso>:<id>` con `<prefisso>` ∈ `{github, gitlab, jira, linear}`. Altrimenti **ERROR `invalid-external-id-format`**.
    - Se `kanban_publish.provider: none` → **WARNING `orphan-external-id`** (il file ha un `external_id:` ma il publish è disabilitato; eredità di config precedente).
    - Se `kanban_publish.provider ≠ none` e prefisso ≠ provider → **WARNING `external-id-cross-provider`** (il file è pubblicato su un provider diverso da quello attualmente configurato; il publisher attuale lo skipperà).
  - Frontmatter `external_id:` assente:
    - Se `kanban_publish.provider ≠ none` e `status: in-progress|done` → **WARNING `unpublished-active-artifact`** (l'artefatto è attivo ma mai pubblicato; suggerisci `/kanban-publish run`).
- `wiki/log.md` ultime 10 entry `publish`: presenza di `provider:` + `created=N`, `updated=M`. Assenza → **WARNING `publish-without-summary`**.

### 4e — Coerenza manifest ↔ raw filesystem (v2.9, PATTERN §16)

Solo se `raw/.extraction-manifest.json` esiste:

- Per ogni entry `<key>` nel manifest:
  - Campo `source` ∈ `{pdf, figma, notion, ...}`. Assente → assume `pdf` (retrocompat) ma emit **WARNING `manifest-source-implicit`** (suggerisce di esplicitare).
  - Campo `primary_artifact` (v2.9): file esistente in `raw/`. Mancante o broken path → **ERROR `manifest-primary-missing`**.
  - Per `source: pdf`: `primary_artifact` deve essere `raw/<key>.txt`. Mismatch → **ERROR `manifest-shape-mismatch`**.
  - Per `source: figma`: `primary_artifact` deve essere `raw/<key>.kb.json` ed essere JSON parsabile. Mismatch o JSON malformato → **ERROR `manifest-shape-mismatch`** (sub-categoria `kb-json-invalid` se malformed).
  - Per `source: figma`: il KB JSON deve avere top-level `project`, `screens`, `components`, `flows`, `features`, `tokens` (anche se vuoti). Top-level mancante → **WARNING `kb-schema-incomplete`** (l'estrazione potrebbe essere stata parziale; vedi `extraction_metadata.status`).
  - `secondary_artifacts[]` (v2.9): ogni path elencato deve esistere. File mancante → **WARNING `manifest-secondary-missing`**.
  - `extracted_at`: ISO-8601 parsabile. Mismatch → **WARNING `manifest-bad-timestamp`**.
  - `extractor_version` (v2.9): presente per entries scritte da v2.9+. Assenza in entries antecedenti accettata silenziosamente.

- **Inverso (filesystem → manifest)**:
  - Per ogni `raw/*.txt` non in `raw/images/`: deve avere entry corrispondente nel manifest. Assenza → **WARNING `orphan-raw-artifact`** (probabilmente sync-docs non è ancora stato eseguito; suggerisce `/sync-docs`).
  - Per ogni `raw/*.kb.json`: deve avere entry con `source: figma`. Assenza → **WARNING `orphan-raw-artifact`** (suggerisce di rieseguire `/figma-sync` o di aggiungere manualmente l'entry).

- **Isolamento (PATTERN §16 invariante)**:
  - `raw/<key>.txt` con manifest `source: figma` → **ERROR `sync-adapter-collision`**.
  - `raw/<key>.kb.json` con manifest `source: pdf` → **ERROR `sync-adapter-collision`**.

### 4d — Coerenza VCS (v2.8, PATTERN §7 r.14, §15)

Solo se `factory.config.yaml` esiste con `vcs.mode` valorizzato:

- `vcs.mode: none` → `code_path` DEVE essere `""`. Altrimenti **ERROR `vcs-mode-mismatch`**.
- `vcs.mode: monorepo` → `code_path` deve essere relativo e dentro al repo
  (non assoluto, non `../`). Altrimenti **ERROR `vcs-mode-mismatch`**.
- `vcs.mode: submodule`:
  - `vcs.submodule_path` valorizzato e non vuoto. Altrimenti **ERROR `missing-submodule-path`**.
  - File `.gitmodules` esistente al root del repo. Altrimenti **ERROR `missing-gitmodules`**.
  - Entry per `<submodule_path>` presente in `.gitmodules`. Altrimenti **ERROR `submodule-not-declared`**.
  - Submodule inizializzato (`<submodule_path>/.git` esiste come file o directory). Altrimenti **WARNING `submodule-not-initialized`** (suggerisce `git submodule update --init --recursive`).
- `vcs.mode: sibling` → `code_path` deve esistere sul filesystem (se valorizzato).
  Se assente → **WARNING `sibling-code-path-not-found`** (può essere intenzionale
  pre-clone). Se presente ma non git repo → **WARNING `sibling-not-git-repo`**.
- `vcs.mode: external` → nessun check (path opaco).
- `branch_strategy` ∈ `{shared, per-tsk, per-sprint}` → altrimenti **ERROR `invalid-branch-strategy`**.
- `commit_coupling` ∈ `{pin, float}` → altrimenti **ERROR `invalid-commit-coupling`**.
- Se `commit_coupling: pin` → file `.factory-lock` esiste al root (anche vuoto,
  almeno header). Assenza → **WARNING `missing-factory-lock`** (suggerisce di
  crearlo o cambiare a `float`).
- `wiki/log.md` ultime 10 entry `develop`: il campo `**VCS mode:**` è presente.
  Assenza in ≥ 1 entry → **WARNING `develop-without-vcs-info`** (entry pre-v2.8,
  retrocompat OK).

### 4c — Coerenza topology ↔ filesystem ↔ routing (v2.7, PATTERN §7 r.13)

Solo se `factory.config.yaml` esiste:

- Leggi `factory.config.yaml`: estrai `topology`, `routing`, `code_path`.
- Per ogni `routing.X: agent` in `{be, fe, db, qa}`: verifica esistenza
  `.cursor/agents/<X>-dev.md`. Assenza → **ERROR routing-missing-agent**.
- Per ogni `<X>-dev.md` presente: verifica `routing.X: agent`. Mismatch →
  **ERROR orphan-dev-agent**.
- `topology:` ∈ `{knowledge-only, plan-only, full-stack-agents, hybrid-be-agents, hybrid-fe-agents, custom}`.
  Altrimenti → **ERROR invalid-topology**.
- Se topologia ∈ {`full-stack-agents`, `hybrid-*`, `custom` con almeno un dev}
  ma `code_path:` è stringa vuota → **WARNING dev-agents-without-code-path**.
- Per ogni TSK con `consumer: agent`: verifica esista l'agente `<layer>-dev.md`
  corrispondente. Assenza → **WARNING tsk-consumer-no-agent** (è valido, ma
  l'utente dovrà esplicitamente forzare via `/dev`).

### 4b — Coerenza Q ↔ kanban (v2.6, gate L4 graduato)

- Per ogni `Q_NNN` in `management/questions.md` `[APERTE]`: verifica presenza
  campo `**Bloccante:** hard | soft`. Assenza → **WARNING missing-blocking-level**
  (non ERROR, per compatibilità retroattiva pre-v2.6; default = `hard`).
- Per ogni `Q_NNN` in `[RISOLTE]`: cerca US con `blocked_by:.*Q_NNN` o
  `pending_clarification:.*Q_NNN`. Match → **WARNING stale-blocked-by**:
  la US referenzia una Q già chiusa. Suggerisce: invocare `product-manager`
  o riconciliare manualmente. (Vedi marker `reconcile-needed` in `wiki/log.md`
  generati da `propagate-resolution`.)
- Per ogni US con `pending_clarification:` non vuota: verifica che almeno
  un ADR la citi nel proprio `pending_clarification:` frontmatter. Mismatch →
  **WARNING orphan-pending-clarification**.

## Citation audit (periodico)

Per ogni `[^src: <path> §<sez>]` in `wiki/**`:
- Verifica che `<path>` esista.
- Verifica che `<sez>` sia presente (header markdown matching) nel file citato.
- Vedi `citation-rules` per la grammatica completa.

Output separato: `wiki/lint/YYYY-MM-DD-citation-audit.md`.

## Classificazione `heal-eligible` (deterministica)

Per ogni ERROR, marca `heal-eligible: true` SOLO se rientra nella whitelist
`heal-protocol`:

- `broken-wikilink` → eligible iff esiste slug `Y` con `fuzzy(X, Y) ≥ 0.90`.
- `missing-frontmatter-field` → eligible iff il campo è deducibile dal path
  (`type` da `wiki/<kind>/`, `id` da `EP-XXX|US-YYY|TSK-ZZZ`).
- `citation-section-mismatch` → eligible iff esiste header `H` nel file citato
  con `edit_distance(<sez>, H) ≤ 3`.
- `id-duplicate` → **mai** eligible (rischio di rompere riferimenti esterni).
- WARNING / orphan / claim non citato / contradiction / gap / `missing-blocking-level` / `stale-blocked-by` / `orphan-pending-clarification` → mai eligible (richiedono giudizio semantico).

## Output report

Path: `wiki/lint/YYYY-MM-DD-lint-report.md`

```markdown
---
type: lint
date: YYYY-MM-DD
heal_eligible_count: N
heal_eligible_categories: [broken-wikilink, missing-frontmatter-field, citation-section-mismatch]
---
# Lint Report — YYYY-MM-DD

## Riepilogo
| Check | Errors | Warnings |
|---|---|---|
| 1 — Orphan + wikilink | N | N |
| 2 — Claim senza fonte | N | N |
| 3 — Integrità kanban | N | N |
| 4 — Coerenza wiki↔kanban | N | N |
| 4b — Coerenza Q↔kanban (v2.6) | N | N |
| 4c — Coerenza topology (v2.7) | N | N |
| 4d — Coerenza VCS (v2.8) | N | N |
| 4e — Coerenza manifest↔raw (v2.9) | N | N |
| 4f — Coerenza Publisher (v2.10) | N | N |
| 4g — Coerenza scheduler/depends_on (v2.11) | N | N |

## ERROR meccanici (heal-eligible)
- [ERROR][broken-wikilink][heal-eligible] wiki/concepts/foo.md: `[[oidc-flow]]` → suggerito `[[oidc-flows]]` (fuzzy 0.95)
- [ERROR][missing-frontmatter-field][heal-eligible] wiki/sources/bar.md: manca `type`, deducibile da path → `source`

## ERROR non meccanici (manuali)
- [ERROR][id-duplicate] management/kanban/EP-002/US-013/US-013.md: id duplicato di US-007 (NON heal-eligible)

## WARNING (igiene, mai heal-eligible)
- [WARNING] wiki/concepts/orphan.md: pagina non linkata dall'index.
- [WARNING][missing-blocking-level] management/questions.md Q_003: campo `**Bloccante:**` assente, applico default `hard`.
- [WARNING][stale-blocked-by] management/kanban/EP-001/US-017/US-017.md: `blocked_by: [Q_001]` ma Q_001 è in `[RISOLTE]` dal 2026-05-19. Vedi `reconcile-needed` in `wiki/log.md`.
- [WARNING][orphan-pending-clarification] management/kanban/.../US-024.md: `pending_clarification: [Q_005]` ma nessun ADR cita Q_005.
```

## Log entry

Append a `wiki/log.md` secondo `wiki-log-entry` (template `lint`).
