# Aider Adapter — `.aider/`

Adapter Aider (≥0.50) per il pattern `llm-wiki++` v2.13+. Maturity: **full**.

## Filosofia operativa

Aider è un AI pair-programmer **single-agent, single-turn**. Non ha sub-agent nativi né
multi-tool-call. Il pattern `llm-wiki++` (multi-role: orchestrator, wiki-keeper, PM,
arch, TPM, dev, …) è tradotto come **prompt templates** che l'utente carica con
`--read` / `/read` quando vuole "diventare" quel ruolo.

I "comandi" del pattern (`/run`, `/lint`, `/dev`, …) sono **shell wrapper scripts** che
invocano `aider` con i prompt giusti pre-caricati.

## Costrutti Aider usati

| Costrutto pattern | Costrutto Aider | File path |
|---|---|---|
| Convenzioni globali | `CONVENTIONS.md` (auto-letto da Aider) | `CONVENTIONS.md` (root della factory) |
| Agente specializzato | Prompt template | `.aider/prompts/<name>.md` |
| Skill / procedura | Prompt incluso via `/read` | `.aider/skills/<name>.md` |
| Slash command | Shell wrapper script | `.aider/commands/<name>.sh` |
| File read | Built-in `/read` o `--read <file>` | inline |
| File write | Built-in (con conferma utente) | inline |
| Shell | Built-in `/run <cmd>` | inline |
| Sub-agent fan-out | Sequential sessions | manual / via `.aider/commands/run.sh` |

## Come si usa la factory con Aider

### Per "diventare" un agente specializzato

```bash
# Diventa orchestrator (dashboard + suggerisce next-step)
aider --read .aider/prompts/orchestrator.md

# Diventa wiki-keeper per fare ingest
aider --read .aider/prompts/wiki-keeper.md raw/2026-05-27-foo.txt

# Diventa code-reviewer per un TSK
aider --read .aider/prompts/code-reviewer.md \
      --read .aider/skills/code-review-protocol.md \
      management/kanban/EP-001/US-005/TSK-042.md
```

### Tramite shell wrapper

```bash
bash .aider/commands/run.sh          # equivalente di /run di Claude Code
bash .aider/commands/lint.sh         # equivalente di /lint
bash .aider/commands/dev.sh TSK-042  # equivalente di /dev <TSK-id>
```

I wrapper sono auto-generati dal `bootstrap-multiadapter-protocol` in base a topology
+ opt-in features.

## Come scaffoldare

### Via meta-prompt seed v2.13 (automatico)

Al bootstrap, scegli `aider` fra gli adapter da installare. Il bootstrap-multiadapter-protocol:

1. Legge `manifest.yaml`.
2. Genera `CONVENTIONS.md` (vedi `templates/CONVENTIONS.md`).
3. Genera `.aider/prompts/<name>.md` per ciascun ruolo applicabile.
4. Genera `.aider/skills/<name>.md` per ciascuna skill applicabile.
5. Genera `.aider/commands/<name>.sh` shell wrapper.

### Manuale

1. Crea `CONVENTIONS.md` al root della factory (vedi template).
2. Crea `.aider/{prompts,skills,commands}/`.
3. Per ciascun ruolo/skill/comando in `manifest.yaml.templates`, copia il template
   corrispondente da `.claude/` del meta-framework e traducilo seguendo
   `manifest.yaml.template_translation`.

## Limitazioni

- **Sub-agent fan-out parallelo (R.S2 §18)**: NON SUPPORTATO nativamente. Sequential
  invocations o l'utente apre N terminal con Aider.
- **Wave plan dispatch (§18.6)**: il `.aider/commands/run.sh` stampa il wave plan ma
  esegue sequenziale. Per parallelismo reale, scripta in bash con `&` o `parallel`.
- **Multi-tool-call in singola response**: non applicabile (Aider è single-turn).

## Coesistenza con `.claude/` / `.cursor/`

Multi-adapter (PATTERN §12.2 R.A1-R.A6):
- `.aider/` scrive solo nel proprio scope (`CONVENTIONS.md` + `.aider/`).
- Stesso `wiki/`, `management/`, `raw/`, `memory/`, `code_quality/`.
- Single-committer wiki/ enforced globalmente (l'utente serializza fra adapter).
- Lo stesso TSK può essere consumato da `be-dev` in Aider OR `be-dev` in Cursor OR
  `be-dev` in Claude Code — quale runtime usare è una scelta dell'utente per sessione.

## CONVENTIONS.md

Aider legge automaticamente `CONVENTIONS.md` al root del repo in ogni sessione.
Il template scaffoldato include un riassunto del pattern + riferimento a PATTERN.md
+ regole inviolabili §7 fondamentali. Vedi `templates/CONVENTIONS.md`.

## Tool conversion table (estesa)

Vedi [`adapters/README.md`](../README.md#tool-conversion-table).
