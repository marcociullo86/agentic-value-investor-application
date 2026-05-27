---
description: Seed self-contained per scaffoldare una Agentic Factory llm-wiki++ v2.12. Replicabile da qualunque AI agent con file I/O su qualunque macchina/cartella. Hybrid: procedura + PATTERN essentials inline + adapter templates fetched da GitHub.
argument-hint: [nome-progetto] [path-destinazione]
allowed-tools: Read, Write, Edit, Bash, Glob, TodoWrite, WebSearch, WebFetch
---

# Factory Bootstrap v2.12 — Self-Contained Portable Seed

> **Replicabilità**: questo singolo file Markdown è il **seed completo** per scaffoldare
> una Agentic Factory llm-wiki++ v2.12 su **qualunque macchina/cartella** con
> **qualunque AI agent** (Claude Code, Cursor, OpenAI Assistants, Aider, Gemini Code,
> ChatGPT con file tools, etc.).
>
> Non serve clonare il repo meta-framework: il seed istruisce l'agente a fetchare le
> componenti necessarie da GitHub (con fallback offline documentato).

## §0 — How to use this seed (qualunque agent)

1. **Apri il seed** con il tuo AI agent (es. lo passi come system prompt o lo fai
   leggere come file context).
2. **Dichiara l'intento**: «Esegui factory-bootstrap v2.12: scaffolda una nuova
   factory in `<path-destinazione>` per il progetto `<nome>`».
3. **Rispondi alle domande** che l'agente porrà (vedi §2 Fase 1).
4. L'agente scaffolda i file e produce un report finale (§2 Fase 6).

**Requirements**:
- Capacità di **lettura/scrittura file** (qualsiasi runtime LLM con file I/O).
- **Network access** per fetchare i template adapter (vedi §3.3). Fallback offline:
  vedi §3.4.
- **Optional**: shell access per `git clone` / `curl` (semplifica il fetch).

## §1 — Runtime conversion table (agent-agnostic)

Il seed è scritto in Markdown standard. Per ogni costrutto runtime-specifico, ecco
le equivalenze fra i principali agent runtime. **Le procedure di §2 funzionano in
ognuno** purché tu usi il costrutto corrispondente del tuo runtime.

| Concetto | Claude Code | Cursor | OpenAI Assistants | Aider | Gemini Code | ChatGPT (file tool) |
|---|---|---|---|---|---|---|
| Agente specializzato | sub-agent (`.claude/agents/<name>.md` + Agent tool) | Custom agent (`.cursor/agents/...`) | Assistant via API (con instructions) | Conventions in `CONVENTIONS.md` | Gemini Custom Gem | Custom GPT instructions |
| Skill / procedura riutilizzabile | `.claude/skills/<name>.md` (markdown) | `.cursor/rules/<name>.md` | Tool / function | `.aider-docs/<name>.md` | Gem instructions | GPT instructions section |
| Slash command | `.claude/commands/<name>.md` | `.cursor/commands/...` | Custom action / function call | `/cmd <name>` (built-in) | Custom Gem function | Custom action |
| File read/write tool | `Read`/`Write`/`Edit`/`Glob` | `Read file`/`Write file` | `code_interpreter` / `file_search` + tools | Built-in file ops | `read_file`/`write_file` | Code Interpreter / file API |
| Shell access | `Bash` tool | Terminal | `code_interpreter` exec | Built-in subprocess | Code Execution tool | Code Interpreter |
| Multi-tool-call parallelo | "Multiple tool uses in one message" | Multi-action | Parallel function calls | Sequential | Parallel tool calls | Sequential typically |
| Sub-agent (fan-out) | `Agent` tool with `subagent_type` | "Compose agent" | "Run sub-assistant" | Manual | "Spawn sub-Gem" | Manual |

**Adapter scaffoldato dal seed**: `.claude/` (Claude Code, di riferimento). Per altri
runtime: leggi PATTERN.md §12 (Adapter contract) e adatta i file scaffoldati al tuo
runtime usando questa tabella di conversione. La sostanza (ruoli, procedure, formati
di citazione, regole inviolabili) è invariata; cambia solo il *costrutto* in cui vive.

## §2 — Bootstrap procedure (~6 fasi)

### Fase 0 — Setup

Parsing argomenti `$ARGUMENTS`:
- Primo argomento → **Nome progetto** (se assente, chiedi).
- Secondo argomento → **Path destinazione assoluto** (default: cwd).

Verifica preliminari:
- Path destinazione esiste o è creabile.
- Hai accesso a network (per fetch §3.3) oppure hai pre-clonato il repo meta-framework
  (vedi §3.4 fallback offline).

### Fase 1 — Input collection (Quick path o Linear path)

**Quick path** — proponi 5 archetipi pre-impostati (default):

```
SCEGLI ARCHETIPO O 'custom':
1. knowledge-only      — solo wiki/ingest, no codice
2. greenfield-full     — nuovo progetto, full-stack agentico in monorepo
3. existing-monolith   — repo monolite esistente, retrofit con factory
4. microservices       — N microservizi BE + (opzionale) 1 FE
5. micro-frontend      — N FE indipendenti + 1+ BE shared
6. custom              — flusso completo A→G
```

Preset per archetipo applicano `topology`, `wiki_feed_source`, `code_quality`,
`kanban_publish`, `scheduler` defaults; l'utente conferma o override.

**Linear path A→G** (se `custom` o override esplicito):

A. **Lingua** del contenuto (`it` default | `en` | altro)
B. **Owner** (string libera, default `soli92`)
C. **Topologia** (PATTERN §13): `knowledge-only | plan-only | full-stack-agents | hybrid-be-agents | hybrid-fe-agents | custom`
D. **Code path (L5)** — SKIP se G=`existing-repo` (derivato dal coupling)
D-bis. **VCS mode** (`monorepo | submodule | sibling | external | none`) — SKIP se G=`existing-repo`
D-ter. **External task tracker** (kanban_publish, **opt-in**): `none` default | `github | gitlab | jira | linear | custom` + `target` se ≠none + `auth_env` (default `GH_TOKEN`)
D-quater. **Parallel scheduler** (v2.11): `enabled: true` default + cap (`max_parallel: 4`, `parallel_gate_threshold: 3`)
D-quinquies. **Code Quality Review Layer** (v2.12, §19): `enabled: false` default (opt-in). Se on: max_iterations=3, 3 passate (idiom/design/robustness), reject=gate umano §7 r.16
E. **Stack mode** (PATTERN §14): `manual | guided | auto`
F. **Standards verbatim** (§11): lista libera (SPID, OIDC, FHIR, GDPR, …)
G. **Wiki feeding source** (v2.12): `empty | pdf | figma | existing-repo`. Se `existing-repo`, vai a Fase 2 (multi-repo). Altrimenti vai a Fase 3.

### Fase 2 — Multi-repo + coupling (solo se G=`existing-repo`)

**Loop su N repo** (1, 2-3, 4+). Per ciascuno chiedi:

- **Path locale** (assoluto). Verifica esistenza + presenza `.git/` o manifest.
- **Name logico** univoco (es. `auth-service`, `web-app`).
- **Layers**: subset di `[be, fe, db, qa, infra]`. Almeno uno.
- **Tags**: descrittivi (`monolith | microservice | mfe | shared-lib | …`).
- **Coupling mode** (PATTERN §16):
  | Coupling | factory_dest | `path` derivato | `vcs.mode` derivato | Modifica al repo sorgente? |
  |---|---|---|---|---|
  | `monorepo` | = path repo | `./` (radice) o sub-path | `monorepo` | **Sì** (gate R.B2) |
  | `sibling-new-repo` (default) | nuovo path separato | assoluto al repo sorgente | `sibling` | **No** (R.B1) |
  | `submodule-new-repo` | nuovo path separato | `./code/<name>/` | `submodule` + `submodule_path` | **No** al bootstrap; submodule add manuale (§7 r.14) |

**Vincoli inviolabili** (R.B1–R.B6, PATTERN §16):
- R.B1: mai modificare repo sorgente in sibling/submodule
- R.B2: gate umano esplicito per monorepo (verifica assenza file factory nel repo)
- R.B3: `repo-sync` read-only sempre
- R.B4: coupling immutabile a runtime
- R.B5: agent-agnostic preservato
- R.B6: **massimo UNA entry può essere `monorepo`**

Auto-deriva il blocco `code_paths` di `factory.config.yaml` da queste scelte. Verifica
univocità `name`, coerenza routing↔layers, no path overlapping.

### Fase 3 — Read templates (fetch da GitHub o fallback)

Fetch via uno dei seguenti metodi (l'agente sceglie quello disponibile):

**Method A — Git clone (preferito se hai shell access)**:

```bash
TMPDIR=$(mktemp -d)
git clone --depth=1 --branch=main https://github.com/soli92/soli-multi-agents-factory.git "$TMPDIR/meta-framework"
META="$TMPDIR/meta-framework"
```

Poi leggi:
- `$META/PATTERN.md` (~1452 righe — contratto universale v2.12)
- `$META/factory.config.yaml` (template config con `code_paths` v2.12)
- `$META/.claude/agents/*.md` (14 agent files; 10 core + 4 dev opzionali + publisher opzionale + code-reviewer opzionale)
- `$META/.claude/skills/*.md` (~25 skill files inclusi i 5 `bootstrap-*`)
- `$META/.claude/commands/*.md` (~13 command files)

**Method B — Curl da raw GitHub (no shell, solo HTTP)**:

Per ogni file individuale:
```
https://raw.githubusercontent.com/soli92/soli-multi-agents-factory/main/PATTERN.md
https://raw.githubusercontent.com/soli92/soli-multi-agents-factory/main/factory.config.yaml
https://raw.githubusercontent.com/soli92/soli-multi-agents-factory/main/.claude/agents/<name>.md
https://raw.githubusercontent.com/soli92/soli-multi-agents-factory/main/.claude/skills/<name>.md
https://raw.githubusercontent.com/soli92/soli-multi-agents-factory/main/.claude/commands/<name>.md
```

Lista file esatta (manifest §3.5).

**Method C — WebFetch tool (agenti che ce l'hanno)**:

Stesso URL del Method B, ma via `WebFetch` invece di `curl`.

**Method D — Fallback offline (no network)**:

L'utente deve avere il repo meta-framework già clonato localmente. Chiedi il path,
poi opera come Method A ma con `META=<user-provided-path>`.

### Fase 4 — Scaffolding (template → destinazione)

In ordine:

**4.a — Root files** (cwd = `factory_dest_path`):

| File | Source |
|---|---|
| `PATTERN.md` | `$META/PATTERN.md` (copia integrale, mai modificare il contenuto — è il contratto universale) |
| `CLAUDE.md` | template breve (vedi §3.2 essentials sotto) che punta a `.claude/` adapter |
| `README.md` | template progetto (lingua scelta) |
| `factory.config.yaml` | da template `$META/factory.config.yaml` con sostituzione dei valori raccolti in Fase 1+2 (`topology`, `code_paths` o `code_path`, `vcs`, `routing`, `stack`, `stack_mode`, `kanban_publish`, `scheduler`, `code_quality`) |

**4.b — Directory L1-L5 + side-channel**:

Sempre:
- `raw/` (+ `raw/tech_stack.md` se `standards` non vuoto)
- `wiki/{sources,concepts,entities,syntheses,runbooks,incidents,query,lint}/`
- `wiki/{index.md,log.md,gaps.md}` (vuoti, head only)
- `management/kanban/`, `management/{roadmap.md,questions.md}`
- `design_&_architecture/{decisions,api_specs,db_schemas}/`
- `memory/{episodic,semantic,procedural}/`

L5 (`code_path`): crea cartella se relativo+monorepo o submodule. Mai per sibling/external.

CQRL side-channel (solo se `code_quality.enabled: true`):
- `code_quality/rules/{canonical,emergent,team-specific}/`
- `code_quality/reports/{,_digests}/`
- `code_quality/rules/README.md` con istruzioni base

**4.c — Adapter `.claude/`** (o adapter del tuo runtime — vedi §1):

Copia condizionale dai template fetched in Fase 3:

| Categoria | Sempre | Condizionale |
|---|---|---|
| Agents | `orchestrator`, `sync-docs`, `wiki-keeper`, `wiki-keeper-worker`, `product-manager`, `lead-architect`, `tpm`, `wiki-query`, `wiki-lint` | `figma-sync` (se G=figma), `repo-sync` (se G=existing-repo), `be-dev`/`fe-dev`/`db-dev`/`qa-dev` (per topology), `github-publisher`/etc (per kanban_publish.provider), `code-reviewer` (se code_quality.enabled) |
| Skills | core: `ingest-protocol`, `lint-checks`, `heal-protocol`, `propagate-resolution`, `parallel-scheduling`, template `scrivi-*`, `apri-question`, `citation-rules`, `wiki-log-entry`, `wiki-gap-protocol`, `query-protocol`, `state-scan`, `promote-status` | `dev-protocol`+`dev-handoff`+`tech-scout`+`vcs-handoff` (se dev-agent), `figma-extraction-protocol` (se figma-sync), `repo-extraction-protocol`+`stack-detector` (se repo-sync), `code-review-protocol`+`feedback-router`+`stack-detector` (se code-reviewer), `publisher-protocol`+`<provider>-mapping` (se publisher) |
| Commands | `/run`, `/sync-docs`, `/query`, `/lint`, `/promote`, `/heal` | `/dev`+`/topology` (se dev-agent), `/figma-sync` (se figma-sync), `/repo-sync` (se repo-sync), `/kanban-publish` (se publisher), `/review` (se code-reviewer) |

**Sanity check finale**: vedi §2 Fase 6.

### Fase 5 — VCS bootstrap

Per ciascuna entry di `code_paths` (multi-repo) o per il `vcs:` top-level (single-repo):

- `monorepo` / `none` → niente operazioni VCS speciali. Annota.
- `submodule` → **stampa** il comando da eseguire (§7 r.14, mai automatico):
  ```bash
  cd <factory_dest_path>
  git submodule add <remote_url> <submodule_path>
  git commit -m "chore: add <name> as submodule"
  ```
- `sibling` → se path vuoto **stampa** `git clone <remote_url> <path>`; altrimenti annota
  «già presente».
- `external` → nessuna istruzione VCS.

Se almeno una entry ha `commit_coupling: pin`, crea `.factory-lock` al root con header
(vedi PATTERN §15).

### Fase 6 — Validation + wiki feeding + report

**Wiki feeding source post-scaffolding** in base a `wiki_feed_source`:
- `empty` → niente. Suggerisci `/sync-docs`/`/figma-sync`/`/repo-sync` quando pronto.
- `pdf` → copia PDF dalla `pdf_folder` in `<dest>/raw/`. Suggerisci `/sync-docs`.
- `figma` → stampa `/figma-sync <url>` come prossimo step.
- `existing-repo` (v2.12) → **loop**: per ogni entry in `code_paths`, invoca `/repo-sync <entry.path>`. Produce N file in `raw/`. Suggerisci `wiki-keeper` batch (fan-out parallelo se N ≥ 3 via `wiki-keeper-worker`).

**24 check di accettazione** (la lista completa, da verificare e segnalare PASS/FAIL):

1. `PATTERN.md` esiste e dichiara `v2.12` in §0.
2. `factory.config.yaml` esiste con `pattern_version: "2.12"`.
3. `topology:` ↔ presenza dev-agent: `routing.X: agent` ⇔ `<X>-dev.md` presente.
4. `.claude/agents/` contiene esattamente gli agent file attesi.
5. `.claude/commands/` contiene `/dev` e `/topology` sse topology include dev-agent.
6. Single-repo legacy: `code_path:` valorizzato sse topology include dev-agent; `vcs.mode` coerente.
7. **Multi-repo v2.12**: blocco `code_paths:` valorizzato; `name` univoco per ogni entry; `path`, `layers`, `vcs.mode` presenti.
8. **R.B6 multi-repo**: max 1 entry ha `vcs.mode: monorepo`.
9. **Routing↔layers**: per ogni `routing.<X>: agent`, almeno una entry `code_paths` ha `<X>` in `layers`.
10. Directory L1-L4 esistono. L5 esiste solo se monorepo/submodule.
11. `memory/{episodic,semantic,procedural}/` esistono.
12. Skill `vcs-handoff.md` presente sse almeno una entry ha `vcs.mode != none`.
13. `.factory-lock` presente sse almeno una entry ha `commit_coupling: pin`.
14. Blocco `kanban_publish:` valorizzato (anche se `provider: none`).
15. Se `kanban_publish.provider != none`: agent `<provider>-publisher.md`, skill `<provider>-mapping.md` + `publisher-protocol.md`, comando `/kanban-publish` presenti.
16. Blocco `scheduler:` valorizzato; skill `parallel-scheduling.md` presente sse `scheduler.enabled: true`.
17. Orchestrator cita `parallel-scheduling` se `scheduler.enabled: true`.
18. **CQRL v2.12**: blocco `code_quality:` valorizzato. Se `enabled: true`: agent `code-reviewer.md`, skill `code-review-protocol`+`stack-detector`+`feedback-router`, comando `/review` presenti.
19. Se `code_quality.enabled: true`: directory `code_quality/rules/{canonical,emergent,team-specific}/` + `code_quality/reports/{,_digests}/` esistono.
20. `scheduler.domains.review` valorizzato (default `true` se CQRL on).
21. **Repo-sync v2.12**: se `wiki_feed_source == existing-repo`: agent `repo-sync.md`, skill `repo-extraction-protocol`+`stack-detector`, comando `/repo-sync` presenti.
22. Skill `stack-detector.md` presente sse almeno uno fra `code-reviewer` e `repo-sync` agent presenti.
23. **R.B1**: per entry con coupling sibling/submodule, `git -C <source-path> status` → unchanged.
24. **R.B2**: per entry con coupling monorepo, gate umano registrato (conferma esplicita data prima dello scaffolding).

**Fix-up automatico** per check falliti meccanicamente (file mancante per typo, ecc.).
Mai dichiarare completato con check falliti.

**Report finale**:

```
========================================
BOOTSTRAP COMPLETATO — Agentic Factory llm-wiki++ v2.12
========================================
Progetto: <project_name>
Destinazione: <factory_dest_path>
Runtime adapter scaffoldato: .claude/ (Claude Code)
                              ↳ Per altri runtime, vedi §1 conversion table

[ALBERO]
<find <dest> -maxdepth 3 -type d>

[CONFIGURAZIONE]
Topology: <topology>
Routing: <be/fe/db/qa: agent|human>
Stack mode: <stack_mode>
Multi-repo entries: <N> (se existing-repo, tabella)

[FEATURE OPT-IN]
Wiki feeding source: <empty|pdf|figma|existing-repo>
External task tracker: <provider> + target se attivo
Parallel scheduler (v2.11): <enabled> + cap
Code Quality Review (v2.12): <enabled> + max_iterations + passate

[CHECK ACCETTAZIONE]
24/24 PASS

[PROSSIMI STEP]
<condizionale per wiki_feed_source × topology — vedi PATTERN §10 trigger>

[REMINDER]
- Repo agent-agnostic. PATTERN.md è il contratto.
- Adapter .claude/ scaffoldato. Per Cursor/OpenAI/Aider/Gemini/altri,
  vedi §1 conversion table del seed.
- {se monorepo + existing-repo}: commit dedicato per isolare aggiunta factory.
- {se CQRL on}: popolare code_quality/rules/canonical/ per lo stack prima del primo /review.
- {se submodule}: esegui git submodule add stampato prima di consumare TSK.
```

## §3 — Inline templates

### §3.1 — PATTERN essentials (concetti chiave per orientamento agent)

Quello che segue è un riassunto **operativo** dei concetti fondamentali. Il
**PATTERN.md completo** (1452 righe) va fetched in Fase 3 e scritto verbatim nella
destinazione: è il contratto universale.

**Modello a layer** (PATTERN §1):
- L1 `raw/` — input multi-sorgente (PDF / Figma / repo). Immutabile (solo Sync agents scrivono).
- L2 `wiki/` — wiki llm-style append-only `log.md`. Single-committer: solo wiki-keeper.
- L3 `management/` — kanban EP/US, roadmap, questions. Autore: PM.
- L4 `design_&_architecture/` + `kanban/**/TSK-*.md` — autore: Arch + TPM.
- L5 `<code_path(s)>/` — codice. In v2.12 multi-repo è una LISTA di code_paths.
- `memory/` — persistenza cross-conversation (side-channel, non layer).
- `code_quality/` (v2.12) — KB regole + report (side-channel CQRL).

**17 regole inviolabili** (PATTERN §7): non bypassabili:
1. L1 read-only (eccetto Sync agents)
2. Zero invenzione — info assente → gaps.md o questions.md
3. Citazione obbligatoria su ogni claim non triviale
4. Wikilink per link interni, mai path relativi `../`
5. `wiki/log.md` + `wiki/gaps.md` + `wiki/incidents/` append-only
6. Report preliminare + STOP prima di scritture batch
7. Update non distruttivo su pagine review|approved
8. Scope di scrittura chiuso per ruolo
9. Gate L4 graduato (Q_NNN blocking_level hard|soft)
10. raw/tech_stack.md priorità assoluta (no auto-replace di SAML/OIDC/SOAP)
11. memory/ NON è wiki/
12. wiki/ single-committer (solo wiki-keeper scrive contenuto)
13. Topology & consumer routing dichiarati
14. VCS dichiarato (§15)
15. Cross-tool publish gate umano (§17)
16. **(v2.12)** Code review verdict `reject` = gate umano (§19)
17. **(v2.12)** Sync read-only verso la sorgente (anche `repo-sync` mai modifica repo scansionato)

**Ruoli principali** (PATTERN §2):
- **Orchestrator** — dispatcher operazioni; episodic memory; parallel scheduler v2.11
- **Sync** — sub-agent per sorgente (PDF=`sync-docs`, Figma=`figma-sync`, repo=`repo-sync` v2.12)
- **Analyst** (`wiki-keeper`) — unico autore di wiki/ contenuto
- **PM** (`product-manager`) — wiki → kanban EP/US
- **Arch** (`lead-architect`) — kanban → design_&_architecture/
- **TPM** (`tpm`) — design → TSK atomici
- **Dev** (opzionali) — `be-dev`/`fe-dev`/`db-dev`/`qa-dev` — TSK → codice
- **Code Reviewer** (`code-reviewer`, v2.12 opt-in) — review post-Develop su 3 passate
- **Publisher** (opzionale) — kanban → tool esterno (GitHub Issues, GitLab, Jira, Linear)

**Operazioni canoniche** (PATTERN §3):
`Ingest` (L1→L2) | `Plan` (L2→L3) | `Design` (L3→L4) | `Execute` (L4 TSK) | `Develop` (L4→L5) | `Review` (L5 post-Develop, v2.12) | `Query` (NL → wiki) | `Lint` (health check) | `Promote` (status draft→review→approved) | `Heal` (evaluator-optimizer bounded) | `Publish` (L3/L4 → tool esterno).

**Coupling modes** (PATTERN §16, v2.12) per existing-repo bootstrap:
- `monorepo` — factory installata dentro al repo esistente (R.B2 gate)
- `sibling-new-repo` — factory in nuovo repo, code_path assoluto al vecchio (R.B1)
- `submodule-new-repo` — factory in nuovo repo, vecchio aggiunto come submodule

Vincolo R.B6: max 1 entry monorepo in multi-repo.

### §3.2 — CLAUDE.md template (~30 righe)

```markdown
# CLAUDE.md — <Project Name>

Questo repo segue il pattern definito in [`PATTERN.md`](PATTERN.md) (v2.12, agent-agnostic).

## Adapter Claude Code

L'adapter Claude Code vive in `.claude/`:
- **Agenti** (`.claude/agents/`): core + opzionali (vedi `factory.config.yaml.topology`)
- **Skill** (`.claude/skills/`): template e procedure ri-utilizzabili
- **Commands** (`.claude/commands/`): `/run`, `/sync-docs`, `/lint`, `/promote`, `/heal`, `/query`, e opzionali in base alla config

## Configurazione factory

[`factory.config.yaml`](factory.config.yaml) configura:
- Topology (PATTERN §13)
- Code paths (multi-repo v2.12) o code_path (legacy single)
- VCS mode per ciascuna entry (PATTERN §15)
- Stack mode (PATTERN §14)
- Routing TSK → consumer
- Kanban publish opt-in (PATTERN §17)
- Parallel scheduler (PATTERN §18)
- Code Quality Review Layer opt-in (PATTERN §19)

## Quick start

- Stato + suggerimento next-step: `/run`
- Health check: `/lint`
- Domande NL al wiki: `/query <domanda>`

Vedi `PATTERN.md` per dettagli completi.
```

### §3.3 — README.md template (5 righe)

```markdown
# <Project Name>

<descrizione breve, lingua scelta>

Factory: Agentic Factory llm-wiki++ v2.12. Vedi `PATTERN.md` per il contratto.
```

### §3.4 — Fallback offline manifest

Se l'agente non ha network, l'utente deve pre-clonare il repo meta-framework
localmente e dichiarare il path:

```bash
git clone --depth=1 https://github.com/soli92/soli-multi-agents-factory.git /tmp/meta-framework
```

Poi il bootstrap usa `/tmp/meta-framework` come source invece dei raw URL.

### §3.5 — Manifest file list (per fetch puntuale)

Lista completa dei file template necessari (incluso il manifest):

**Root**:
- `PATTERN.md`
- `factory.config.yaml`

**`.claude/agents/`** (14 file, condizionale):
- Core sempre: `orchestrator.md`, `sync-docs.md`, `wiki-keeper.md`, `wiki-keeper-worker.md`, `product-manager.md`, `lead-architect.md`, `tpm.md`, `wiki-query.md`, `wiki-lint.md`
- Condizionali: `figma-sync.md`, `repo-sync.md`, `be-dev.md`, `fe-dev.md`, `db-dev.md`, `qa-dev.md`, `github-publisher.md`, `code-reviewer.md`

**`.claude/skills/`** (~25 file, condizionale):
- Core sempre: `ingest-protocol.md`, `lint-checks.md`, `heal-protocol.md`, `propagate-resolution.md`, `parallel-scheduling.md`, `scrivi-wiki-page.md`, `scrivi-epica.md`, `scrivi-user-story.md`, `scrivi-task.md`, `apri-question.md`, `citation-rules.md`, `wiki-log-entry.md`, `wiki-gap-protocol.md`, `query-protocol.md`, `state-scan.md`, `promote-status.md`
- Condizionali: `dev-protocol.md`, `dev-handoff.md`, `tech-scout.md`, `vcs-handoff.md`, `figma-extraction-protocol.md`, `repo-extraction-protocol.md`, `stack-detector.md`, `code-review-protocol.md`, `feedback-router.md`, `publisher-protocol.md`, `github-mapping.md`
- Bootstrap skills (usate da questo seed): `bootstrap-input-protocol.md`, `bootstrap-multirepo-protocol.md`, `bootstrap-scaffolding-protocol.md`, `bootstrap-vcs-protocol.md`, `bootstrap-validation-protocol.md` (queste 5 sono inline opzionalmente — il seed corrente le esegue inline, ma la presenza nel repo è per re-uso futuro tipo `/retrofit-factory`)

**`.claude/commands/`** (~13 file, condizionale):
- Core sempre: `run.md`, `sync-docs.md`, `query.md`, `lint.md`, `promote.md`, `heal.md`
- Condizionali: `dev.md`, `topology.md`, `figma-sync.md`, `repo-sync.md`, `kanban-publish.md`, `review.md`

URL pattern per ciascuno (Method B): `https://raw.githubusercontent.com/soli92/soli-multi-agents-factory/main/<path>`.

## §4 — Self-test esteso

Oltre ai 24 check di Fase 6, esegui questi smoke test:

1. **PATTERN.md hash**: il file scritto in destinazione deve avere il SHA-1 atteso del PATTERN.md v2.12 (per garanzia di non corruption durante fetch). Hash atteso: TBD al primo commit v2.12 pushato. Verifica via `sha1sum <dest>/PATTERN.md`.
2. **factory.config.yaml validità**: parse YAML, verifica chiavi obbligatorie presenti.
3. **Agent count match**: numero di file in `.claude/agents/` matcha il numero atteso dato topology + opt-in.
4. **Wiki feeding source artefact**: se `pdf` → PDF in `raw/`; se `existing-repo` → N file `raw/*-repo-*.md`.

## §5 — Note di portabilità

- **Replicabile**: questo seed funziona da qualsiasi cwd, qualsiasi macchina con
  network access (o repo pre-clonato per fallback offline).
- **Agent-agnostic**: la procedura §2 funziona con qualsiasi agent runtime; usa la
  tabella §1 per adattare i costrutti specifici.
- **Versioned**: questo è v2.12. Per versioni successive vedi
  `~/.claude/factory-bootstrap/v2-XX/` (se in Claude Code) o il repo GitHub
  `https://github.com/soli92/soli-multi-agents-factory/releases`.
- **Source of truth**: il PATTERN.md fetched in Fase 3 è la fonte canonica. Questo
  seed è un'**istruzione di bootstrap**, non duplica il contratto.
- **Diff con v2.11**: vedi `~/.claude/factory-bootstrap/README.md` (se in Claude
  Code) o il file `factory-bootstrap-v2.11-snapshot.md` sul repo GitHub.

## Vincoli inviolabili (top-level, riassunto per agent compliance)

Estratti dai 17 §7 (vedi §3.1):
- **R.1 — L1 read-only** (eccetto Sync agents).
- **R.2 — Zero invenzione**: info assente → gaps.md, mai inventare.
- **R.3 — Citazione obbligatoria** su ogni claim ≥ 20 parole.
- **R.7 — Update non distruttivo** su pagine review|approved.
- **R.8 — Scope di scrittura chiuso** per ruolo.
- **R.12 — wiki/ single-committer** (eccezioni §2 puntuali).
- **R.14 — VCS gate umano**: mai `git submodule add/update`, `git clone`, `git push`,
  `--amend`, `--force`, `--no-verify` automatici.
- **R.15 — Cross-tool publish gate umano**: mai batch sopra `batch_limit` senza secondo gate.
- **R.16 (v2.12) — CQRL `reject` = gate umano**: mai auto-revert/auto-merge.
- **R.17 (v2.12) — Sync read-only verso la sorgente**: `repo-sync` mai modifica repo scansionato.

In multi-repo (v2.12):
- **R.B1 — sibling/submodule**: mai modificare repo sorgente.
- **R.B2 — monorepo**: gate umano esplicito + verifica assenza file factory.
- **R.B6 — max 1 entry monorepo**.

Vedi PATTERN.md fetched in destinazione per la lista completa e dettagliata.
