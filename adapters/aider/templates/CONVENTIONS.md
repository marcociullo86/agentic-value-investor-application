# CONVENTIONS.md — Agentic Factory `llm-wiki++` v2.13

> Letto automaticamente da Aider in ogni sessione. Definisce le convenzioni globali
> della factory e i vincoli inviolabili. Per il contratto completo, vedi `PATTERN.md`.

## Identità

Questa factory segue il pattern `llm-wiki++` v2.13 (vedi `PATTERN.md`). È
**agent-agnostic**: lo stesso filesystem `wiki/` / `management/` / `raw/` /
`design_&_architecture/` / `memory/` / `code_quality/` è gestito da uno o più adapter
runtime simultaneamente. Tu (Aider) sei uno di questi adapter.

## Layer e scope (PATTERN §1)

- **L1 `raw/`** — input multi-sorgente. **Read-only** per te (eccetto se sei in modalità Sync agent).
- **L2 `wiki/`** — wiki llm-style. **Read-only** per te, eccetto in modalità wiki-keeper.
- **L3 `management/`** — kanban EP/US, roadmap, questions. **Write** in modalità PM/TPM.
- **L4 `design_&_architecture/`** — architettura. **Write** in modalità lead-architect.
- **L4 `management/kanban/**/TSK-*.md`** — TSK atomici. **Write** in modalità TPM. Dev-agent **edita solo** `status:` / `review_status:` / `updated:` del frontmatter (mai corpo).
- **L5 `<code_path(s)>/`** — codice. **Write** in modalità dev-agent (be-dev / fe-dev / db-dev / qa-dev), solo nel target risolto.
- **`memory/`** — side-channel cross-conversation. Append-only in modalità orchestrator.
- **`code_quality/`** — side-channel CQRL (v2.12). Solo code-reviewer scrive (R.Q2).

## Multi-repo (v2.12)

Se `factory.config.yaml` ha `code_paths:` (lista), ogni TSK ha un `target:` che
identifica quale repo scrivere. Risolvi via `dev-protocol` Fase 0-bis:
- TSK ha `target: X` → usa `code_paths[name==X].path`
- TSK senza `target:` ma 1 entry copre il layer → auto-derive
- 1+ entry coprono il layer e `target:` assente → ERROR (TPM doveva valorizzare)

## Regole inviolabili (PATTERN §7, le 17)

Le seguenti regole sono **non bypassabili** in ogni adapter:

1. **L1 read-only** (eccetto Sync agents).
2. **Zero invenzione** — info assente → `wiki/gaps.md`, mai sintesi.
3. **Citazione obbligatoria** su ogni claim ≥ 20 parole: `[^src: <path> §<sezione>]`.
4. **Wikilink** `[[nome-pagina]]` per link interni, mai path relativi `../`.
5. **`wiki/log.md`, `wiki/gaps.md`, `wiki/incidents/`** append-only.
6. **Report preliminare + STOP** prima di scritture batch.
7. **Update non distruttivo** su pagine `review|approved` — `## Aggiornamenti (vYYYY-MM-DD)`.
8. **Scope di scrittura chiuso** per ruolo (vedi §2 PATTERN).
9. **Gate L4 graduato** (`Q_NNN` con `blocking_level: hard` blocca; `soft` annota).
10. **`raw/tech_stack.md` priorità assoluta** — no auto-replace di SAML/OIDC/SOAP.
11. **`memory/` NON è `wiki/log.md`**.
12. **`wiki/` single-committer** — solo wiki-keeper scrive contenuto (eccezioni puntuali §2).
13. **Topology + routing dichiarati** in `factory.config.yaml`.
14. **VCS dichiarato** + gate umano per `git submodule add/update`, `git clone`, `git push`, `--amend`, `--force`, `--no-verify`. **Mai automatici**.
15. **Cross-tool publish gate umano** (kanban_publish § r.15) — conferma esplicita.
16. **(v2.12)** Code review verdict `reject` = gate umano. Mai auto-revert/merge.
17. **(v2.12)** Sync read-only verso la sorgente. `repo-sync` mai modifica repo scansionato.

## Come "diventarsi" un ruolo

Carica il prompt template corrispondente con `/read` o `--read`:

```
/read .aider/prompts/wiki-keeper.md   # diventi wiki-keeper
/read .aider/prompts/be-dev.md        # diventi be-dev
```

Ogni prompt template è auto-contenuto: descrive scope, trigger, procedura, regole.

## Skill condivise

Le skill sono procedure ricorrenti riusabili da più ruoli. Caricale con `/read`:

```
/read .aider/skills/ingest-protocol.md   # protocollo ingest L1→L2
/read .aider/skills/dev-protocol.md      # protocollo Develop L4→L5
/read .aider/skills/code-review-protocol.md  # CQRL v2.12
```

## Comandi rapidi (shell wrappers)

```
bash .aider/commands/run.sh        # dashboard + suggerisci next-step
bash .aider/commands/lint.sh       # health check wiki/
bash .aider/commands/dev.sh TSK-X  # lancia be/fe/db/qa-dev su TSK
bash .aider/commands/review.sh TSK-X  # code-reviewer (CQRL v2.12)
```

## Confronto con altri adapter

| Concetto | Aider | Claude Code | Cursor |
|---|---|---|---|
| Agente | `/read prompts/<name>.md` | `Agent(subagent_type=name)` | rule `.cursor/rules/<name>.mdc` |
| Skill | `/read skills/<name>.md` | (auto-load) | rule `.cursor/rules/skills/<name>.mdc` |
| Comando | `bash commands/<name>.sh` | `/<name>` | `/<name>` |
| Sub-agent parallelo | sequential | parallel multi-tool-call | partial (Compose) |

Vedi `PATTERN.md §12` per il contratto multi-adapter completo.
