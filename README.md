# App Template Demo

Repo agentic factory **`llm-wiki++` v2.8** — knowledge base eseguibile stile Karpathy
estesa con planning (PM/Arch/TPM), execution layer L5 (4 dev-agent), memoria
cross-conversazione e VCS integration esplicita.

- **Contratto autoritativo (universale, agent-agnostic):** [`PATTERN.md`](PATTERN.md)
- **Adapter Claude Code:** [`CLAUDE.md`](CLAUDE.md) + [`.claude/`](.claude/)
- **Adapter Cursor:** [`CURSOR.md`](CURSOR.md) + [`.cursor/`](.cursor/)
- **Configurazione factory:** [`factory.config.yaml`](factory.config.yaml)

## Topologia attiva

- **`topology: full-stack-agents`** — tutti i dev-agent (be/fe/db/qa) attivi
- **`code_path: ./src/`** — codice nel monorepo
- **`stack_mode: auto`** — la skill `tech-scout` propone lo stack (gate umano per applicare)
- **`vcs.mode: monorepo`** — commit chain unico, ogni operazione VCS è gated umano

## Layer

| | |
|---|---|
| L1 `raw/` | PDF + estratti `.txt` (read-only eccetto `sync-docs`) |
| L2 `wiki/` | Wiki llm-style karpathy (single-committer: `wiki-keeper`) |
| L3 `management/` | Epiche, storie, roadmap, questions (PM) |
| L4 `design_&_architecture/` + `management/kanban/**/TSK-*.md` | Architettura + task atomici (Arch + TPM) |
| L5 `src/` | Codice sorgente (dev-agent o umani) |
| `memory/` | Persistenza cross-conversazione (side-channel) |

## Quick start

**Cursor:** comandi in [`.cursor/commands/`](.cursor/commands/) (`/run`, `/sync-docs`, …). Vedi [`CURSOR.md`](CURSOR.md).

**Claude Code:** comandi in [`.claude/commands/`](.claude/commands/). Vedi [`CLAUDE.md`](CLAUDE.md).

```text
1. Aggiungere PDF in raw/ con naming YYYY-MM-DD-<nome>.pdf
2. Lanciare /sync-docs → estrazione testo+figure
3. Invocare wiki-keeper → ingest L1 → L2 con citazioni
4. /run → dashboard di stato + next-step suggerito
5. Lanciare product-manager → epiche/storie
6. Lanciare lead-architect → ADR + architettura
7. Lanciare tpm → TSK atomici
8. /dev <TSK-id> → consumare un task con dev-agent (richiede stack definito)
```

## Note

- Il repo è **agent-agnostic**: il contratto vive in `PATTERN.md` (universale), gli adapter (`.claude/`, `.cursor/`, …) implementano i ruoli §2 con i costrutti del proprio runtime.
- Tutte le operazioni distruttive su VCS sono **gated umano** (PATTERN §7 r.14).
- Lo stato del progetto si **deduce dal filesystem** + `wiki/log.md` + `memory/episodic/` — mai file di stato scritti a mano.
