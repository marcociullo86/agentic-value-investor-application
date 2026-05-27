# Aider adapter templates

Template di riferimento per lo scaffolding automatico (via meta-prompt v2.13
`bootstrap-multiadapter-protocol`).

## File presenti (esempi di traduzione completa)

| Tipo | Path | Riferimento Claude Code |
|---|---|---|
| Conventions | `CONVENTIONS.md` | (none — file specifico Aider) |
| Prompt agent | `prompts/orchestrator.md` | `.claude/agents/orchestrator.md` |
| Shell wrapper | `commands/run.sh` | `.claude/commands/run.md` |

## File mancanti (scaffoldati al bootstrap dal manifest)

Vedi `../manifest.yaml.templates` per la lista completa. Tutti gli altri agenti
(wiki-keeper, PM, arch, TPM, dev-X, code-reviewer, ecc.), skill, e command wrapper
seguono lo stesso pattern dei file di esempio.

## Convenzione di traduzione

Da un file `.claude/agents/<X>.md` (sorgente):

```markdown
---
name: wiki-keeper
description: ...
model: claude-sonnet-4-6
tools: [Read, Write, Edit, Glob, Bash]
---
# ROLE: ...

Read `raw/.extraction-manifest.json`...
Invoca `Agent(subagent_type=wiki-keeper-worker)` per N ≥ 3 raw.
```

Al template Aider `.aider/prompts/<X>.md`:

```markdown
# Prompt: wiki-keeper (Aider adapter)

> Carica con `/read .aider/prompts/wiki-keeper.md`.

## Identità
Sei il **Wiki-Keeper**...

## Scope di lettura/scrittura
- Legge: `raw/.extraction-manifest.json` (usa `/add` per portarlo in chat)
- Scrive: `wiki/<sezione>/<slug>.md` (l'utente conferma la modifica)
- ...

## Procedura
...
(In Claude Code, sub-agent fan-out per N ≥ 3 raw. In Aider, **sequential** —
l'utente lancia N sessioni separate.)

## Regole
Vedi CONVENTIONS.md §Regole inviolabili.
```

### Mapping campi

| Claude Code | Aider prompt |
|---|---|
| `name:` | (assente, nome dal filename) |
| `description:` | Sezione `## Identità` |
| `tools: [...]` | (assente — Aider ha tool fissi: Read/Write/Run/Glob) |
| `Read <file>` | "Apri `<file>` con `/add` o `--read`" |
| `Write <file>` | "Modifica `<file>` (Aider conferma)" |
| `Edit <file>` | come sopra |
| `Bash <cmd>` | "Esegui `/run <cmd>`" |
| `Agent(subagent_type=X)` | "Esci da questa sessione e lancia `aider --read .aider/prompts/X.md`" |
| `Glob <pattern>` | "Usa `/ls <pattern>` o `find . -path <pattern>`" |

### Frontmatter assente

Aider non usa frontmatter nei prompt template. La descrizione è inline come prima
sezione (`## Identità`).

## Shell wrapper convention

I `.sh` wrappers in `commands/` invocano `aider` con i prompt template giusti
pre-caricati. Pattern base (vedi `run.sh` come esempio):

```bash
#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
FACTORY_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$FACTORY_ROOT"

ARGS=(--read .aider/prompts/<role>.md)
# eventuali skill da pre-caricare
if [[ -f .aider/skills/<skill>.md ]]; then
  ARGS+=(--read .aider/skills/<skill>.md)
fi

MESSAGE="${1:-default message}"
ARGS+=(--message "$MESSAGE")

exec aider "${ARGS[@]}"
```

Set `chmod +x` per renderli eseguibili.

## Limitazioni note

- **No parallel sub-agent**: ogni operazione è sequenziale. Lo scheduler §18 stampa
  il wave plan ma esegue serialmente.
- **No auto-attivazione**: l'utente sceglie esplicitamente quale ruolo "diventare"
  con `/read`. Non c'è auto-activation come Cursor rules `globs`.
- **Frontmatter limitato**: Aider non ha frontmatter convention; descrizione inline.
- **Multi-tool-call**: non applicabile (Aider è single-turn).
