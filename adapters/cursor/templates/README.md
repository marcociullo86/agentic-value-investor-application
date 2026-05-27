# Cursor adapter templates

Questa cartella contiene **template di riferimento** scaffoldabili automaticamente dal
meta-prompt v2.13 (`bootstrap-multiadapter-protocol`). Sono **starter coerenti**: il
contenuto operativo (responsabilità, scope, gate, regole) è IDENTICO al
`.claude/<corrispondente>.md` del meta-framework; cambia solo l'invocazione tool e il
formato Cursor `.mdc`.

## File presenti (esempi di traduzione completa)

| Tipo | Path | Riferimento Claude Code |
|---|---|---|
| Agent | `rules/orchestrator.mdc` | `.claude/agents/orchestrator.md` |
| Agent | `rules/wiki-keeper.mdc` | `.claude/agents/wiki-keeper.md` |
| Skill | `rules/skills/ingest-protocol.mdc` | `.claude/skills/ingest-protocol.md` |
| Command | `commands/run.md` | `.claude/commands/run.md` |

## File mancanti (scaffoldati al bootstrap dal manifest)

Tutti gli altri file dichiarati in `../manifest.yaml`. Il `bootstrap-multiadapter-protocol`:

1. Legge `manifest.yaml`.
2. Per ciascun template non già presente in questa cartella, genera il file `.mdc`
   traducendo dal `.claude/<corrispondente>.md` del meta-framework via le mappature
   di `manifest.yaml.mappings`.
3. Scrive in `<factory>/.cursor/`.

## Convenzione di traduzione

### Frontmatter

| Claude Code | Cursor `.mdc` |
|---|---|
| `name: <slug>` | (assente — usa il filename) |
| `description: ...` | `description: ...` |
| `model: claude-sonnet-4-6` | (assente — Cursor usa il modello globale) |
| `tools: [Read, Write, Edit, Glob, Bash]` | `globs: <pattern>` (Cursor attiva tramite glob match) |

### Body

| Claude Code | Cursor `.mdc` |
|---|---|
| `Vedi \`<skill>\`.` | `Vedi [<skill>](mdc:.cursor/rules/skills/<skill>.mdc).` |
| `Invoca \`Agent(subagent_type=<X>)\`` | "Invoca manualmente Compose agent con la rule `<X>.mdc`" |
| `Read <file>` | `@<file>` (mention) o "Apri `<file>` in agent mode" |
| `Write <file>` | "Crea `<file>` via Edit/Apply" |
| `Bash <cmd>` | "Esegui `<cmd>` nel terminale embedded" |

### Sezioni del body

Le sezioni canoniche del template `.claude/agents/<X>.md`:
- `## ROLE: <name>` — invariata
- `## Scope` — invariata (Legge / Scrive / Mai scrive in)
- `## Trigger` — invariata
- `## Procedura` — riferimenti skill aggiornati con `mdc:` URI
- `## Regole` — invariata (cita PATTERN §7)

Vedi `rules/orchestrator.mdc` come esempio di traduzione completa.

## Validazione

Dopo lo scaffolding, verifica:
- Ogni `.mdc` ha frontmatter valido (`description` obbligatorio; `globs` o `alwaysApply: true`).
- I `mdc:` link puntano a file effettivamente scaffoldati (no broken links).
- Il body cita correttamente PATTERN.md §X (non `<claude>/path/...`).

`wiki-lint` (anche in Cursor) controllerà la coerenza routing↔layers come per `.claude/`.
