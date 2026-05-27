---
description: Scaffolda una nuova Agentic Factory llm-wiki++ (v2.11) — topology + stack mode + VCS integration + sync adapters + publisher adapters + parallel scheduler + dev-agent opzionali.
argument-hint: [nome-progetto] [path-destinazione]
allowed-tools: Read, Write, Edit, Bash, Glob, TodoWrite, WebSearch, WebFetch
---

> **SNAPSHOT v2.11** — meta-prompt pre-CQRL e pre-multi-repo, snapshotato il 2026-05-27.
> Per la versione corrente, vedi [v2-12/factory-bootstrap.md](../v2-12/factory-bootstrap.md).
> Per il changelog, vedi [README.md](../README.md).

Sei l'agente di scaffolding di una **Agentic Factory `llm-wiki++` v2.11**, agent-agnostic.

Argomenti utente: `$ARGUMENTS`

## Procedura

### 1. Leggi la specifica completa

Fonte di verità (in ordine):

```
/Users/simone.olivieri/Documents/Personal/Repos/soli-multi-agents-factory/PATTERN.md        (v2.11 contratto universale)
/Users/simone.olivieri/Documents/Personal/Repos/soli-multi-agents-factory/factory.config.yaml (template config)
/Users/simone.olivieri/Documents/Personal/Repos/soli-multi-agents-factory/.claude/           (adapter Claude Code di riferimento — copia agenti/skill/commands)
```

### 2. Raccogli input

Se `$ARGUMENTS` contiene almeno un valore:
- Primo argomento → **Nome progetto**
- Secondo argomento (se presente) → **Path destinazione** (default: cwd)

Poi chiedi (in **una sola sequenza** di AskUserQuestion) i seguenti input mancanti:

#### A. Lingua del contenuto
- Italiano (default) / Inglese / Altra

#### B. Owner
- (libero, default: `soli92`)

#### C. Topologia (PATTERN §13)

| Topologia | Descrizione |
|---|---|
| `knowledge-only` | Solo ingest + wiki (Sync→Analyst). No planning, no execution. |
| `plan-only` | Fino a TSK; consumer umano. Default storico v2.6. |
| `full-stack-agents` | Tutti i dev-agent (be/fe/db/qa) attivi. Tutto agentico. |
| `hybrid-be-agents` | BE/DB agentici, FE/QA umani. |
| `hybrid-fe-agents` | FE agentico, BE/DB/QA umani. |
| `custom` | Scegli a la carte i dev-agent (chiedere quali). |

#### D. Code path (L5)

Se topologia ∈ {`full-stack-agents`, `hybrid-*`, `custom` con almeno un dev-agent},
chiedi **`code_path`**:
- Default: `./src/` (relativo al repo)
- Opzione: percorso assoluto fuori dal repo (es. `/Users/me/Repos/altro/`)
- Se topologia in {`knowledge-only`, `plan-only`}, salta questa domanda e lascia
  `code_path: ""`.

#### D-bis. VCS mode (v2.8, PATTERN §15)

Solo se `code_path` è valorizzato. Chiedi `vcs.mode`:

| Mode | Significato | Quando |
|---|---|---|
| `monorepo` | L5 dentro al factory repo, un solo commit chain | code_path relativo (es. `./src/`) |
| `submodule` | L5 come git submodule | code_path relativo, gestito come submodule |
| `sibling` | L5 in un altro clone, repo separato | code_path assoluto, altro working tree |
| `external` | path opaco, factory non coordina git | code_path assoluto, factory non si interessa di VCS |

Se `code_path: ""` → `vcs.mode: none` automatico.

**Domande follow-up condizionali**:
- Se `submodule`: chiedi `submodule_path` (default = `code_path` con `./` prefix) e `remote_url` (opzionale, URL del repo da submodulare).
- Se `sibling`: chiedi `remote_url` (opzionale, documentazione).
- Per `submodule` e `sibling`: chiedi `branch_strategy` (default `shared`; opzioni `per-tsk`, `per-sprint`) e `commit_coupling` (default `float`; opzione `pin` se vuoi `.factory-lock` per reproducibilità).

#### D-ter. Kanban publish (v2.10, PATTERN §17)

Chiedi se il kanban deve essere pubblicato come mirror push-only su un tool esterno:

| Provider | Sub-agent | Stato v2.11 |
|---|---|---|
| `none` | — | default; il kanban resta solo locale |
| `github` | `github-publisher` (gh CLI) | implementazione di riferimento |
| `gitlab` | `gitlab-publisher` | placeholder (contratto pronto, agent non scaffoldato) |
| `jira` | `jira-publisher` | placeholder |
| `linear` | `linear-publisher` | placeholder |

Se `none` → skip blocco `kanban_publish` (resta esempio commentato).
Se `github` → chiedi `target` (`<org>/<repo>`) e ricorda all'utente di settare `GH_TOKEN` e fare `gh auth login` prima del primo `/kanban-publish run`.

#### D-quater. Parallel scheduler (v2.11, PATTERN §18)

Chiedi se abilitare lo scheduler DAG-driven per il dispatch parallelo di operazioni e sub-agent (default raccomandato: **abilitato**):

| Opzione | Comportamento |
|---|---|
| `enabled: true` (default) | `/run` analizza `depends_on`/`code_path` dei TSK e dispatcha in parallelo TSK indipendenti su antichain conflict-free. Gate umano sopra `parallel_gate_threshold` (default 3). |
| `enabled: false` | Comportamento pre-v2.11: orchestrator suggerisce un solo next-step per turno. |

Se abilitato, ricorda all'utente i valori default sicuri: `max_parallel: 4`, `parallel_gate_threshold: 3`, `code_path_conflict: strict`, `empty_code_path_policy: serial`. Domini attivi default: `ingest`, `develop`, `lint`, `query`, `sync`; off: `plan`, `design`, `publish`.

In `knowledge-only` o `plan-only` il dominio `develop` non si attiva comunque (no `consumer: agent`); lo scheduler resta utile per `ingest`/`lint`/`query`.

#### E. Stack mode (PATTERN §14)

- `manual` — utente popola `raw/tech_stack.md` a mano (default)
- `guided` — bootstrap mostra opzioni curate per layer (vedi §3.b sotto)
- `auto` — skill `tech-scout` propone dopo wiki primo ingest (più tardi)

#### F. Standards / vincoli normativi noti

Lista libera (SPID, OIDC, FHIR, eIDAS, GDPR, …). Se valorizzata, viene messa
in `raw/tech_stack.md` (sezione "Standards verbatim") come vincolo iniziale.

#### G. PDF iniziali (opzionale)

Se l'utente ha già file in mano, gli chiediamo se vuole copiarli al termine.

### 3. Per `stack_mode: guided` — mini-questionario stack

Per ciascun layer attivo nella topologia scelta:

- **backend** (se topologia copre BE): proponi 3 opzioni curate 2026 con 1 riga pro/contro
  (es. FastAPI / Express / Spring Boot — citare brevemente Why).
- **frontend** (se topologia copre FE): React / Vue / SvelteKit / Solid.
- **database**: PostgreSQL / MongoDB / SQLite / DynamoDB.
- **qa**: Pytest+Playwright / Vitest+Cypress / JUnit+Selenium.

Per ciascuno l'utente seleziona o sceglie "Altro" e specifica. **Mai inventare:
se l'utente non sceglie, lascia `""` in `factory.config.yaml.stack`.

### 4. Mostra il riepilogo e attendi conferma

Prima di scrivere qualsiasi file, mostra:
- Input raccolti
- Path destinazione finale
- Topologia + lista dev-agent che verranno creati (e quali no)
- `code_path` con annotazione `(interno al repo)` o `(esterno: <abs>)`
- `vcs.mode` (v2.8) + opzionali (`submodule_path`, `branch_strategy`, `commit_coupling`)
- `kanban_publish.provider` (v2.10) e se `≠ none`, il `target`
- `scheduler.enabled` (v2.11) + cap (`max_parallel`, `parallel_gate_threshold`)
- `stack_mode` + stack scelto (se guided)
- Numero stimato di file da creare (~45-55 file in base a topologia + adapter abilitati)
- Versione pattern: **v2.11**

Attendi un OK esplicito.

### 5. Esegui scaffolding

In ordine:

1. **Root files**
   - `PATTERN.md` — copia integrale dalla fonte di verità (v2.11, niente runtime-specifici).
   - `CLAUDE.md` — pointer all'adapter `.claude/` (template §5b del meta-prompt).
   - `README.md` — descrizione progetto (template breve, in lingua scelta).
   - `factory.config.yaml` — generato dai valori raccolti (topology, code_path,
     vcs (v2.8), stack_mode, routing coerente, stack se guided, kanban_publish (v2.10), scheduler (v2.11)).

2. **Directory L1-L5**
   - `raw/` (con eventuale `raw/tech_stack.md` se l'utente ha citato standards)
   - `wiki/sources/`, `wiki/concepts/`, `wiki/entities/`, `wiki/syntheses/`,
     `wiki/runbooks/`, `wiki/incidents/`, `wiki/query/`, `wiki/lint/`
   - `wiki/index.md`, `wiki/log.md` (vuoto, head only), `wiki/gaps.md` (vuoto)
   - `management/kanban/`, `management/roadmap.md`, `management/questions.md`
   - `design_&_architecture/decisions/`, `design_&_architecture/api_specs/`,
     `design_&_architecture/db_schemas/`
   - `memory/episodic/`, `memory/semantic/`, `memory/procedural/`
   - L5 (`<code_path>`): crea la cartella SOLO se `code_path` non vuoto e
     punta dentro il repo. Se è path esterno, NON creare nulla (assume esista
     o sarà responsabilità dell'utente).

3. **`.claude/`**
   - `.claude/agents/` — copia gli agenti core sempre: `orchestrator`,
     `sync-docs`, `wiki-keeper`, `wiki-keeper-worker`, `product-manager`,
     `lead-architect`, `tpm`, `wiki-query`, `wiki-lint`. Aggiungi `figma-sync` (v2.9) opt-in se l'utente lo richiede esplicitamente per la fase 1 (Figma source).
   - Dev-agent — copia SOLO quelli richiesti dalla topologia:
     - `full-stack-agents` → tutti e 4 (`be-dev`, `fe-dev`, `db-dev`, `qa-dev`)
     - `hybrid-be-agents` → `be-dev`, `db-dev`
     - `hybrid-fe-agents` → `fe-dev`
     - `custom` → quelli scelti
     - `knowledge-only` / `plan-only` → nessuno
   - Publisher-agent (v2.10) — solo se `kanban_publish.provider != none`:
     - `github` → `.claude/agents/github-publisher.md`
     - `gitlab`/`jira`/`linear` → placeholder (l'agent non è ancora scaffoldato in repo; segnala all'utente che dovrà crearlo seguendo il contratto §17)
   - `.claude/skills/` — copia tutte le skill canoniche/procedurali/template
     dalla fonte di verità. Condizionali:
     - `dev-protocol`, `dev-handoff` — solo se topologia include dev-agent.
     - `tech-scout` — solo se topologia include dev-agent OR `stack_mode: auto`.
     - `vcs-handoff` (v2.8) — solo se topologia include dev-agent E `vcs.mode != none`.
     - `figma-extraction-protocol` (v2.9) — solo se `figma-sync` agent presente.
     - `publisher-protocol` + `<provider>-mapping` (v2.10) — solo se `kanban_publish.provider != none`.
     - `parallel-scheduling` (v2.11) — sempre presente se `scheduler.enabled: true` (default raccomandato). Mai rimuovere: l'orchestrator la invoca da `/run`.
   - `.claude/commands/` — copia: `run`, `sync-docs`, `query`, `lint`, `promote`,
     `heal`. Aggiungi:
     - `/dev` e `/topology` solo se topologia include almeno un dev-agent.
     - `/figma-sync` (v2.9) solo se `figma-sync` agent presente.
     - `/kanban-publish` (v2.10) solo se `kanban_publish.provider != none`.

4-bis. **VCS bootstrap (v2.8)** — operazioni VCS al bootstrap:
   - `vcs.mode: monorepo` o `none`: niente da fare, lo scaffolding già crea/non crea L5.
   - `vcs.mode: submodule`: **NON** lanciare `git submodule add` automaticamente.
     Stampa il comando da eseguire (es. `git submodule add <remote_url> <submodule_path>`)
     e ricorda all'utente di eseguirlo dopo il bootstrap. Crea un placeholder `.gitmodules`
     vuoto solo se l'utente NON ha intenzione di lanciare `submodule add` immediatamente.
   - `vcs.mode: sibling`: **NON** lanciare `git clone` automaticamente.
     Stampa il comando da eseguire (`git clone <remote_url> <code_path>`).
   - `vcs.mode: external`: nessuna istruzione VCS, è opaco per disegno.
   - Se `vcs.commit_coupling: pin`: crea un `.factory-lock` minimal al root del repo:
     ```yaml
     # .factory-lock — generato da vcs-handoff (PATTERN §15)
     # Append-only: ogni Develop chiuso aggiunge una entry.
     ```

4. **Niente file vietati**
   - Mai `project_manifest.json`.
   - Mai `wiki/confidences/`, `reviewer`, `sprint.md` pre-popolato.
   - `factory.config.yaml` SÌ — è config, non stato (PATTERN §8).

### 6. Test di accettazione

Verifica e riporta:

- [ ] `PATTERN.md` esiste e dichiara `v2.11` in §0.
- [ ] `factory.config.yaml` esiste con `pattern_version: "2.11"`.
- [ ] `topology:` in config coerente con i file dev-agent presenti
      (`<X>-dev.md` esiste ⇔ `routing.X: agent`).
- [ ] `code_path:` valorizzato sse topologia include dev-agent.
- [ ] Le directory L1-L4 esistono (vuote ok). L5 esiste solo se `code_path`
      interno al repo + `vcs.mode ∈ {monorepo, submodule}`.
- [ ] `.claude/agents/` contiene esattamente gli agent file attesi.
- [ ] `.claude/commands/` contiene `/dev` e `/topology` sse topologia
      include almeno un dev-agent.
- [ ] `vcs.mode` valorizzato e coerente con `code_path`:
      - `mode: none` ⇔ `code_path: ""`
      - `mode: monorepo` ⇔ `code_path` relativo dentro al repo
      - `mode: submodule` ⇔ `code_path` relativo + `submodule_path` valorizzato
      - `mode: sibling`/`external` ⇔ `code_path` assoluto (o relativo fuori repo)
- [ ] Skill `vcs-handoff.md` presente sse `vcs.mode != none`.
- [ ] `.factory-lock` presente al root sse `commit_coupling: pin`.
- [ ] Blocco `kanban_publish:` valorizzato in config (v2.10); se `provider != none`, agent `<provider>-publisher.md` e skill `<provider>-mapping.md` presenti.
- [ ] Comando `/kanban-publish` presente sse `kanban_publish.provider != none`.
- [ ] Blocco `scheduler:` valorizzato in config (v2.11); skill `parallel-scheduling.md` presente sse `scheduler.enabled: true`.
- [ ] Orchestrator (`.claude/agents/orchestrator.md`) cita esplicitamente la skill `parallel-scheduling` se `scheduler.enabled: true`.

Se qualcosa fallisce, fixalo prima di dichiarare completato.

### 7. Report finale

Output:
- Albero creato (`tree -L 3` o `find . -maxdepth 3 -type d`)
- Topologia + routing attivi
- Stack mode + stack scelto (riassunto)
- Prossimi step suggeriti, dipende dalla topologia:
  - `knowledge-only` → drop PDF in `raw/`, poi `/sync-docs`
  - `plan-only` → drop PDF + ingest + `product-manager` + `/run`
  - dev-attiva (`full-stack-*` / `hybrid-*`) → drop PDF + ingest + plan + design +
    TPM produce TSK con `layer:` e `consumer:` derivati da `routing:` →
    `/run` suggerirà `/dev <TSK-id>` per i TSK consumer=agent

- Reminder: il repo è agent-agnostic. `PATTERN.md` è il contratto, `.claude/`
  è l'adapter di default. Altri adapter (`.cursor/`, `.openai/`, …) possono
  coesistere.

## Vincoli inviolabili

- **Niente file fuori dal pattern.** Se in `$ARGUMENTS` o nei chiarimenti
  emerge una richiesta che violerebbe `PATTERN.md` (es. "aggiungi `reviewer`",
  "crea `project_manifest.json`"), segnalala e proponi l'alternativa allineata.
- **Output deterministico.** Stessi input → stessa struttura.
- **Agent-agnostic preservato.** `PATTERN.md` non deve contenere riferimenti
  a tool specifici (Read/Write/Glob), modelli, slash command.
- **Skill-driven.** I template (frontmatter, struttura corpo, formati output)
  vivono nelle skill, mai inlineati nei file agente.
- **Topology e routing coerenti.** Mai scrivere un `factory.config.yaml` con
  `routing.X: agent` se `<X>-dev.md` non è presente (e viceversa).
- **`code_path` esterno è opzione legittima.** Se l'utente lo configura,
  ricordagli nel report che i dev-agent scriveranno fuori dal repo.
