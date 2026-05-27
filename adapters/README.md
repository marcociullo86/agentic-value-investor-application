# Adapter Registry — `adapters/`

Registry degli adapter runtime supportati dal pattern `llm-wiki++` v2.13+
(PATTERN.md §12). Ogni sub-folder è un **adapter manifest + template**: descrive come
tradurre i ruoli/skill/comandi del pattern nei costrutti specifici di un runtime AI
agent.

## Adapter disponibili (v2.13)

| Adapter | Folder runtime | Maturity | Adapter folder qui | Note |
|---|---|---|---|---|
| **Claude Code** | `.claude/` | full reference | — (fonte: `.claude/` al root del meta-framework) | Implementazione di riferimento del pattern. Ogni nuovo adapter usa `.claude/` come template di traduzione. |
| **Cursor** | `.cursor/` | full v2.13 | [`cursor/`](cursor/) | `.cursor/rules/*.mdc` + `.cursor/commands/*.md`. Cursor ≥0.45. |
| **Aider** | `.aider/` | full v2.13 | [`aider/`](aider/) | `CONVENTIONS.md` + `.aider/prompts/*.md` (templates per `--read`). Aider ≥0.50. |
| **OpenAI Assistants** | `.openai/` | partial v2.13 | [`openai/`](openai/) | Manifest + `setup.py` stub per creare Assistants via API. |
| **Gemini Code Assist** | `.gemini/` | manifest-only v2.13 | [`gemini/`](gemini/) | Custom Gem instructions; scaffolding manuale. |
| **ChatGPT (Custom GPT)** | `.chatgpt/` | manifest-only v2.13 | [`chatgpt/`](chatgpt/) | Custom GPT instructions + file tools. |

## Maturity levels

- **`full`** — Tutti i 9 agent core + 4 dev opzionali + skill condizionali + comandi
  sono scaffoldabili automaticamente dal bootstrap. Il manifest dichiara mappature
  complete per ogni costrutto pattern.
- **`partial`** — Subset scaffoldabile automaticamente; resto richiede intervento
  utente. Il manifest dichiara le parti automatizzabili e i passi manuali.
- **`manifest-only`** — Solo manifest formale + README di traduzione. L'utente
  segue il README per scaffoldare a mano.

## Come usare il registry

### Al bootstrap (factory-bootstrap v2.13+)

Il meta-prompt seed v2.13 chiede all'utente quali adapter installare nella factory.
Per ogni adapter selezionato, invoca `bootstrap-multiadapter-protocol` che:

1. Legge `adapters/<name>/manifest.yaml`.
2. Risolve i template attivi (in base a `topology`, `code_paths`, `code_quality.enabled`, ecc.).
3. Scaffolda i file nel `<adapter_folder>` della factory generata (es. `.cursor/rules/orchestrator.mdc`).
4. Annota l'adapter in `factory.config.yaml.adapters[]`.

### Aggiungere un adapter a runtime (post-bootstrap)

```
/factory-add-adapter <name>   # comando candidato v2.14
```

O manualmente: invoca `bootstrap-multiadapter-protocol` con il manifest del nuovo adapter.

### Aggiungere un NUOVO adapter al registry

Per supportare un runtime non ancora listato (es. `windsurf`, `claude-desktop`,
`Gemini Code Companion`):

1. Crea `adapters/<name>/`.
2. Scrivi `manifest.yaml` seguendo lo schema §12.1 di PATTERN.md.
3. Aggiungi `README.md` con istruzioni operative + tool conversion table.
4. (Opzionale) Aggiungi template files in `adapters/<name>/templates/`.
5. Aggiorna questo README.
6. Invia PR al meta-framework.

## Invarianti adapter (PATTERN §12.2)

- **R.A1** — Isolamento di cartella: ogni adapter scrive solo nel proprio folder.
- **R.A2** — State filesystem condiviso (wiki/, management/, raw/, memory/, code_quality/).
- **R.A3** — Single-committer preservato globalmente (anche cross-adapter).
- **R.A4** — Manifest immutabile a runtime (cambia solo via release PATTERN).
- **R.A5** — Adapter aggiungibile a runtime.
- **R.A6** — Agent-agnostic preservato (PATTERN.md, config, layer L1-L5 mai runtime-specific).

## Tool conversion table (riferimento rapido)

| Pattern concept | Claude Code | Cursor | OpenAI Assistants | Aider | Gemini | ChatGPT |
|---|---|---|---|---|---|---|
| Agent specializzato | sub-agent (`.claude/agents/<name>.md`) | `.cursor/rules/<name>.mdc` | Assistant via API | role-prompt + `--read` | Custom Gem | Custom GPT instructions |
| Skill / procedura | `.claude/skills/<name>.md` | `.cursor/rules/skills/<name>.mdc` | Function tool | `.aider/prompts/<name>.md` | Gem instructions section | GPT instructions section |
| Slash command | `.claude/commands/<name>.md` | `.cursor/commands/<name>.md` | Custom action | `/cmd` built-in + alias | Custom Gem function | Custom action |
| File read | `Read` | `@<file>` | `code_interpreter` / `file_search` | built-in | `read_file` | Code Interpreter |
| File write | `Write` / `Edit` | Edit/Apply | `code_interpreter` exec | built-in subprocess | `write_file` | Code Interpreter |
| Shell | `Bash` | Terminal | `code_interpreter` exec | built-in subprocess | Code Execution | Code Interpreter |
| Multi-tool parallel | "Multiple tool uses in one message" | Multi-action | Parallel function calls | sequential | parallel tool calls | sequential typically |
| Sub-agent fan-out | `Agent(subagent_type=...)` | "Compose agent" | "Run sub-assistant" | manual | "Spawn sub-Gem" | manual |
