# Cursor Adapter — `.cursor/`

Adapter Cursor (≥0.45) per il pattern `llm-wiki++` v2.13+. Maturity: **full**.

## Costrutti Cursor usati

| Costrutto pattern | Costrutto Cursor | File path |
|---|---|---|
| Agente specializzato (PATTERN §2) | `.cursor/rules/*.mdc` con frontmatter `description` + `globs` | `.cursor/rules/<name>.mdc` |
| Skill / procedura (PATTERN v2.3) | `.cursor/rules/skills/*.mdc` | `.cursor/rules/skills/<name>.mdc` |
| Slash command | `.cursor/commands/*.md` | `.cursor/commands/<name>.md` |
| File read | `@<file>` (mention) o built-in Read | inline |
| File write | Edit/Apply (Cursor agent mode) | inline |
| Shell | Terminal embedded (Cursor agent mode) | inline |
| Sub-agent fan-out | Compose agent (partial) o sequential rules | manual |

## Come scaffoldare

### Via meta-prompt seed v2.13 (automatico)

Al bootstrap, scegli `cursor` fra gli adapter da installare. Il `bootstrap-multiadapter-protocol`:

1. Legge questo `manifest.yaml`.
2. Per ciascun template in `templates/`, applica la condizione (es. `routing.be == agent`).
3. Genera il file `.mdc` traducendo il `.claude/<corrispondente>.md` con le mappature di
   `mappings:` (sostituisce tool name, path di riferimento, frontmatter format).
4. Scrive in `<factory>/.cursor/` la struttura risultante.

### Manuale

1. Copia ricorsivamente `adapters/cursor/templates/` → `<factory>/.cursor/`.
2. Per ciascun file `.mdc`, sostituisci i placeholder `{{...}}` con i valori della tua
   factory (es. `{{topology}}`, `{{code_paths}}`).
3. Rimuovi file non applicabili (es. `be-dev.mdc` se `routing.be == human`).

## Limitazioni

- **Sub-agent fan-out parallelo**: Cursor non supporta `Agent(subagent_type=…)` come
  Claude Code. Il `wiki-keeper-worker` per ingest paralleli (N ≥ 3 raw) è emulato come
  sequential invocation manuale dell'utente. Compose agent (parallel multi-action) può
  emulare parzialmente.
- **Scheduler wave plan (§18)**: presentato in chat per conferma utente; Cursor esegue
  sequenzialmente per default. Per parallelismo reale, l'utente apre N tab Cursor.
- **Multi-tool-call in singola response**: non supportato. Le operazioni atomiche
  funzionano; le batch operations sono sequenziali.

## Coesistenza con `.claude/`

Se la factory ha sia `.claude/` sia `.cursor/` (multi-adapter):
- Le rule Cursor leggono lo stesso `wiki/`, `management/`, `raw/`, `memory/`, ecc.
- Le regole §7 inviolabili sono enforced lo stesso (single-committer, append-only, ecc.).
- L'utente sceglie quale adapter usare per la sessione corrente.
- Se invoca contemporaneamente, è responsabilità sua serializzare (R.A3).

## Frontmatter Cursor convention

```mdc
---
description: <quando attivare la rule>
globs: <pattern file che triggera la rule, separati da virgola>
alwaysApply: false
---
```

Per ruoli ad alta attivazione (orchestrator, wiki-keeper), usa
`description: "Always relevant — orchestration / wiki maintenance"` + `globs: "**/*"`.

Per ruoli specializzati (be-dev, qa-dev), usa
`globs: "<code_path>/**/*"` + `description: "Triggers on code path changes"`.

## Tool conversion table (estesa)

Vedi [`adapters/README.md`](../README.md#tool-conversion-table) per la tabella completa.
