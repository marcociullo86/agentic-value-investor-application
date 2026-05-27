---
description: Seed self-contained per scaffoldare una Agentic Factory llm-wiki++ v2.13. Replicabile da qualunque AI agent con file I/O su qualunque macchina/cartella. Hybrid: procedura + PATTERN essentials inline + adapter templates fetched da GitHub. Multi-adapter scaffolding nativo (.claude/ + .cursor/ + .aider/ + .openai/ + .gemini/ + .chatgpt/).
argument-hint: [nome-progetto] [path-destinazione]
allowed-tools: Read, Write, Edit, Bash, Glob, TodoWrite, WebSearch, WebFetch
---

# Factory Bootstrap v2.13 — Self-Contained Portable Seed (Multi-Adapter)

> **Replicabilità**: questo singolo file Markdown è il **seed completo** per scaffoldare
> una Agentic Factory llm-wiki++ v2.13 su **qualunque macchina/cartella** con
> **qualunque AI agent** (Claude Code, Cursor, OpenAI Assistants, Aider, Gemini Code,
> ChatGPT con file tools, etc.).
>
> **NUOVO in v2.13**: scaffolding multi-adapter nativo. Una factory può ospitare
> simultaneamente `.claude/` + `.cursor/` + `.aider/` + `.openai/` + `.gemini/` + `.chatgpt/`.

## §0 — Cambiamenti v2.13 vs v2.12

- **Multi-adapter scaffolding** (PATTERN §12 esteso). Registry formale di adapter in
  `adapters/<name>/manifest.yaml`. Scaffolda 1+ adapter al bootstrap.
- **Adapter disponibili**: `.claude/` (full reference), `.cursor/` (full v2.13),
  `.aider/` (full v2.13), `.openai/` (partial — setup.py stub), `.gemini/` +
  `.chatgpt/` (manifest-only).
- **6 invarianti R.A1-R.A6** per multi-adapter coexistence (§12.2).
- **Nuovo blocco `factory.config.yaml.adapters[]`** (§12.3) per dichiarare adapter installati.
- **6° skill bootstrap**: `bootstrap-multiadapter-protocol` (§12).
- **Meta-prompt versionati nel repo**: `<meta-framework>/meta-prompts/{v2-11,v2-12,v2-13}/`
  (spostati da `~/.claude/factory-bootstrap/`).

Backward compat preservata da v2.12 (CQRL §19, multi-repo `code_paths` §13, coupling
modes R.B1-R.B6 §16). Vedi anche backward compat v2.11 (`code_path:` singolare, `vcs:`
top-level).

## §1 — How to use this seed (qualunque agent)

1. **Apri il seed** con il tuo AI agent (es. lo passi come system prompt o lo fai
   leggere come file context).
2. **Dichiara l'intento**: «Esegui factory-bootstrap v2.13: scaffolda una nuova
   factory in `<path-destinazione>` per il progetto `<nome>`».
3. **Rispondi alle domande** che l'agente porrà (vedi §3 Fase 1).
4. **Scegli gli adapter da scaffoldare** (§3 Fase 1.bis, NUOVO in v2.13).
5. L'agente scaffolda i file e produce un report finale (§3 Fase 7).

## §2 — Runtime conversion table (agent-agnostic)

Il seed è scritto in Markdown standard. Per ogni costrutto runtime-specifico, ecco
le equivalenze fra i principali agent runtime.

| Concetto | Claude Code | Cursor | OpenAI Assistants | Aider | Gemini Code | ChatGPT |
|---|---|---|---|---|---|---|
| Agente specializzato | sub-agent `.claude/agents/<name>.md` + Agent tool | rule `.cursor/rules/<name>.mdc` | Assistant via API | prompt `.aider/prompts/<name>.md` + `/read` | Custom Gem | Custom GPT |
| Skill / procedura | `.claude/skills/<name>.md` | rule `.cursor/rules/skills/<name>.mdc` | Function tool | `.aider/skills/<name>.md` + `/read` | Gem instructions | GPT instructions |
| Slash command | `.claude/commands/<name>.md` | `.cursor/commands/<name>.md` | Custom action | shell wrapper `.aider/commands/<name>.sh` | Custom Gem function | Custom action |
| File read | `Read` tool | `@<file>` mention | `code_interpreter` / `file_search` | `/add` o `--read` | `read_file` | Code Interpreter |
| File write | `Write`/`Edit` | Edit/Apply | `code_interpreter` exec | built-in | `write_file` | Code Interpreter |
| Shell | `Bash` tool | Terminal | `code_interpreter` exec | `/run` | Code Execution | Code Interpreter |
| Multi-tool parallel | "Multiple tool uses in one message" | Multi-action | Parallel function calls | sequential | parallel tool calls | sequential |
| Sub-agent fan-out | `Agent(subagent_type=...)` | "Compose agent" | "Run sub-assistant" | manual | "Spawn sub-Gem" | manual |

**Adapter di reference scaffoldato**: `.claude/`. Per altri adapter, il
`bootstrap-multiadapter-protocol` traduce i `.claude/` templates ai costrutti del
runtime target seguendo questa tabella + le mappature in `adapters/<name>/manifest.yaml`.

## §3 — Bootstrap procedure (~7 fasi)

### Fase 0 — Setup

Parsing argomenti `$ARGUMENTS`:
- Primo argomento → **Nome progetto** (se assente, chiedi).
- Secondo argomento → **Path destinazione assoluto** (default: cwd).

Verifica preliminari:
- Path destinazione esiste o è creabile.
- Hai accesso a network (per fetch §4) oppure hai pre-clonato il repo meta-framework.

### Fase 1 — Input collection (Quick path o Linear path)

**Quick path** — proponi 5 archetipi pre-impostati:

```
SCEGLI ARCHETIPO O 'custom':
1. knowledge-only      — solo wiki/ingest, no codice
2. greenfield-full     — nuovo progetto, full-stack agentico in monorepo
3. existing-monolith   — repo monolite esistente, retrofit con factory
4. microservices       — N microservizi BE + (opzionale) 1 FE
5. micro-frontend      — N FE indipendenti + 1+ BE shared
6. custom              — flusso completo A→G
```

**Linear path A→G** (se `custom` o override):

A. **Lingua** (`it`/`en`/altro)
B. **Owner**
C. **Topologia** (`knowledge-only | plan-only | full-stack-agents | hybrid-be-agents | hybrid-fe-agents | custom`)
D. **Code path (L5)** — SKIP se G=`existing-repo` (derivato da coupling)
D-bis. **VCS mode** — SKIP se G=`existing-repo`
D-ter. **External task tracker** (kanban_publish, opt-in)
D-quater. **Parallel scheduler** (v2.11)
D-quinquies. **Code Quality Review Layer** (v2.12, opt-in)
E. **Stack mode** (`manual | guided | auto`)
F. **Standards verbatim** (§11)
G. **Wiki feeding source** (v2.12): `empty | pdf | figma | existing-repo`. Se `existing-repo` → vai a Fase 2.

### Fase 1.bis — Adapter selection (NUOVO in v2.13)

Chiedi quali adapter installare:

```
SELEZIONA ADAPTER DA SCAFFOLDARE (multi-select):

  [x] claude       (full reference)             — .claude/
  [ ] cursor       (full v2.13)                 — .cursor/
  [ ] aider        (full v2.13)                 — .aider/
  [ ] openai       (partial — setup.py stub)    — .openai/
  [ ] gemini       (manifest-only)              — .gemini/
  [ ] chatgpt      (manifest-only)              — .chatgpt/

Default: [claude]. Multi-adapter use case (raccomandato per team):
  [claude, cursor] — Claude Code per agentic + Cursor per refactoring manuale
  [claude, aider]  — Claude Code per dev + Aider per quick edits
```

R.A6 — Agent-agnostic: la factory funziona con qualunque combinazione.
R.A1 — Isolamento: ogni adapter scrive solo nel proprio folder.

### Fase 2 — Multi-repo + coupling (solo se G=`existing-repo`)

Identico a v2.12 — vedi seed v2-12 §Fase 2 per i dettagli (loop N repo, coupling
modes `monorepo` / `sibling-new-repo` / `submodule-new-repo`, R.B1-R.B6).

### Fase 3 — Read templates (fetch da GitHub o fallback)

**Method A — Git clone (preferito)**:

```bash
TMPDIR=$(mktemp -d)
git clone --depth=1 --branch=main https://github.com/soli92/soli-multi-agents-factory.git "$TMPDIR/meta-framework"
META="$TMPDIR/meta-framework"
```

Poi leggi:
- `$META/PATTERN.md` (v2.13 contratto universale)
- `$META/factory.config.yaml` (template con code_paths + adapters block v2.13)
- `$META/adapters/README.md` (registry adapter)
- Per ciascun adapter selezionato:
  - `$META/adapters/<name>/manifest.yaml`
  - `$META/adapters/<name>/templates/**/*` (file template starter, dove presenti)
- `$META/.claude/agents/*.md` (template di reference per gli altri adapter)
- `$META/.claude/skills/*.md`
- `$META/.claude/commands/*.md`

**Method B — Curl da raw GitHub**: stesso pattern URL ma fetch HTTP individuale.

**Method C — WebFetch tool**: come Method B via WebFetch.

**Method D — Fallback offline**: utente fornisce path del repo meta-framework pre-clonato.

### Fase 4 — Scaffolding (template → destinazione)

In ordine:

**4.a — Root files**:

| File | Source |
|---|---|
| `PATTERN.md` | `$META/PATTERN.md` (copia integrale verbatim) |
| `CLAUDE.md` | template breve |
| `README.md` | template progetto |
| `factory.config.yaml` | da template + sostituzione valori raccolti (`topology`, `code_paths`, `adapters[]`, ecc.) |

**4.b — Directory L1-L5 + side-channel**: come v2.12.

**4.c — Multi-adapter scaffolding** (NUOVO in v2.13):

Per ciascun adapter in `adapters_selected`, invoca **`bootstrap-multiadapter-protocol`**
(skill in `$META/.claude/skills/bootstrap-multiadapter-protocol.md`).

Per ciascun adapter:
- Legge `adapters/<name>/manifest.yaml`.
- Risolve i template condizionali (in base a topology + opt-in features).
- Scaffolda nel `<factory_dest>/<adapter_folder>` (es. `.claude/`, `.cursor/`, `.aider/`).
- Aggiorna `factory.config.yaml.adapters[]` con la nuova entry.

**Caso speciale per `.claude/`**: copia direttamente dal meta-framework (file già pronti).

**Caso `.cursor/` / `.aider/` / `.openai/`**: il manifest ha alcuni template starter in
`adapters/<name>/templates/`; per i template mancanti, traduce automaticamente dal
`.claude/<corrispondente>.md` applicando `manifest.mappings`.

**Caso `.gemini/` / `.chatgpt/`** (manifest-only): crea solo `<folder>/README.md` con
le `scaffolding_instructions` del manifest. L'utente scaffolda manualmente.

### Fase 5 — VCS bootstrap

Identico a v2.12 — vedi seed v2-12 §Fase 5.

### Fase 6 — Validation + wiki feeding + report

Aggiunge ai 24 check di v2.12 i seguenti (v2.13):

25. `factory.config.yaml.adapters[]` valorizzato con almeno 1 entry.
26. Per ciascun adapter in `adapters[]`, la cartella `<adapter_folder>` esiste e ha
    contenuti coerenti col manifest (R.A1).
27. **R.A6 agent-agnostic**: `PATTERN.md` scaffoldato non contiene riferimenti a tool
    Claude-specifici (Read/Write/Glob/Bash). Verifica via grep: 0 match attesi (PATTERN
    cita tool generici come "agente Read/Write" ma non come API specifiche).
28. **R.A1 isolation**: nessun file scaffoldato è fuori dal `<adapter_folder>` del
    rispettivo adapter. Verifica via find.

Wiki feeding source bootstrap (post-scaffolding) come v2.12.

### Fase 7 — Report finale

Includi info multi-adapter (NUOVO):

```
========================================
BOOTSTRAP COMPLETATO — Agentic Factory llm-wiki++ v2.13
========================================
Progetto: <project_name>
Destinazione: <factory_dest_path>

[ADAPTER INSTALLATI]
| Adapter | Folder    | Maturity      | File creati |
|---------|-----------|---------------|-------------|
| claude  | .claude/  | full reference| 45          |
| cursor  | .cursor/  | full v2.13    | 38          |
| aider   | .aider/   | full v2.13    | 23          |

[ALBERO]
<find <dest> -maxdepth 2 -type d>

[CONFIGURAZIONE]
... (come v2.12)

[CHECK ACCETTAZIONE]
28/28 PASS

[PROSSIMI STEP]
- Wiki feeding: <empty|pdf|figma|existing-repo> → <suggerimento>
- Adapter primario per la sessione: scegli quale runtime usare (es. Claude Code,
  Cursor, Aider).
- {se .openai/ scaffoldato}: esegui `python .openai/setup.py` per creare gli
  Assistant via OpenAI API (richiede OPENAI_API_KEY).
- {se .gemini/ o .chatgpt/}: scaffolding manuale richiesto, vedi <folder>/README.md.

[REMINDER]
- Agent-agnostic preservato (R.A6): PATTERN.md è il contratto, runtime mappato via adapter.
- Multi-adapter coexistence (R.A1-R.A6): ogni adapter scrive solo nel proprio folder;
  filesystem state è condiviso (wiki/, management/, raw/, memory/, code_quality/).
- Single-committer wiki/ enforced globalmente (R.A3): mai invocare wiki-keeper da
  due adapter contemporaneamente.
- {se monorepo existing-repo}: commit dedicato per isolare aggiunta factory.
- {se CQRL on}: popolare code_quality/rules/canonical/ per lo stack prima del primo /review.
- {se submodule}: esegui git submodule add stampato prima di consumare TSK.
```

## §4 — Inline templates

### §4.1 — PATTERN essentials (concetti chiave per orientamento)

Per il PATTERN.md completo (1500+ righe), fetch via §3 Fase 3 e scrivi verbatim. Qui
solo i concetti essenziali per orientare l'agent durante il bootstrap.

**Modello a layer**:
- L1 `raw/` — input multi-sorgente (PDF / Figma / repo). Read-only (solo Sync agents).
- L2 `wiki/` — wiki llm-style append-only `log.md`. Single-committer (wiki-keeper).
- L3 `management/` — kanban EP/US, roadmap, questions (PM).
- L4 `design_&_architecture/` + `kanban/**/TSK-*.md` (Arch + TPM).
- L5 `<code_paths>/` — codice (v2.12 multi-repo).
- `memory/`, `code_quality/` — side-channel.

**17 regole inviolabili §7** (non bypassabili — vedi PATTERN.md fetched).

**Ruoli** (PATTERN §2):
- Orchestrator, Sync (sync-docs / figma-sync / repo-sync), Analyst (wiki-keeper),
  PM, Arch, TPM, Dev (be/fe/db/qa opzionali), Code Reviewer (CQRL opt-in v2.12),
  Publisher (opzionale).

**v2.13**: contratto multi-adapter §12.0-§12.4 con manifest formale + R.A1-R.A6 invarianti.

### §4.2 — CLAUDE.md template + README.md template

Come v2.12, con aggiunta riga:
- `adapters/` registry referenziato in CLAUDE.md.
- Lista adapter installati in `factory.config.yaml.adapters[]`.

### §4.3 — Fallback offline manifest

Se l'agente non ha network, l'utente pre-clona:

```bash
git clone --depth=1 https://github.com/soli92/soli-multi-agents-factory.git /tmp/meta-framework
```

Poi il bootstrap usa `/tmp/meta-framework` come source.

### §4.4 — Manifest file list

Lista template necessari (oltre a quelli di v2.12):

**`adapters/`**:
- `adapters/README.md`
- `adapters/cursor/{manifest.yaml,README.md,templates/}`
- `adapters/aider/{manifest.yaml,README.md,templates/}`
- `adapters/openai/{manifest.yaml,README.md,templates/}`
- `adapters/gemini/{manifest.yaml,README.md}`
- `adapters/chatgpt/{manifest.yaml,README.md}`

**`.claude/skills/`** (nuova in v2.13):
- `bootstrap-multiadapter-protocol.md`

## §5 — Self-test esteso (28 check)

Vedi §3 Fase 6 per la lista completa. Tutti devono essere PASS prima di dichiarare
bootstrap completato.

## §6 — Note di portabilità

- **Replicabile**: questo seed funziona da qualsiasi cwd, qualsiasi macchina con
  network (o repo pre-clonato).
- **Agent-agnostic**: la procedura §3 funziona con qualsiasi agent runtime.
- **Multi-adapter**: scegli uno o più adapter al bootstrap (R.A5: aggiungibili a
  runtime con `bootstrap-multiadapter-protocol` standalone).
- **Versioned**: meta-prompt seeds in `<meta-framework>/meta-prompts/{v2-11,v2-12,v2-13}/`.
- **Source of truth**: PATTERN.md fetched è la fonte canonica. Adapter manifests in
  `adapters/<name>/manifest.yaml`.

## Vincoli inviolabili (top-level, riassunto)

Da PATTERN §7 (17 regole inviolabili) + §12.2 (6 invarianti multi-adapter):

**Regole inviolabili §7**:
- R.1 — L1 read-only (eccetto Sync).
- R.2 — Zero invenzione.
- R.3 — Citazione obbligatoria.
- R.5 — Append-only su wiki/log.md / gaps.md / incidents.
- R.7 — Update non distruttivo su review|approved.
- R.8 — Scope di scrittura chiuso per ruolo.
- R.12 — wiki/ single-committer.
- R.14 — VCS gate umano (mai operazioni distruttive automatiche).
- R.15 — Cross-tool publish gate umano.
- R.16 (v2.12) — CQRL verdict `reject` = gate umano.
- R.17 (v2.12) — Sync read-only verso sorgente.

**Multi-repo R.B1-R.B6 (v2.12)** + **Multi-adapter R.A1-R.A6 (v2.13)**:
- R.A1 — Isolamento cartella per adapter.
- R.A2 — State filesystem condiviso.
- R.A3 — Single-committer preservato globalmente.
- R.A4 — Manifest immutabile a runtime.
- R.A5 — Adapter aggiungibili a runtime.
- R.A6 — Agent-agnostic preservato (PATTERN.md mai runtime-specific).

Vedi PATTERN.md fetched in destinazione per dettagli completi.
