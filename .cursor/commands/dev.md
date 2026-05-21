# /dev — Consuma un TSK (Develop L4 → L5)

Invoca il dev-agent su un singolo task atomico.

## Argomenti

`<TSK-id>` obbligatorio (es. `019` o `TSK-019`). `<layer>` opzionale (override one-shot).

## Esecuzione

1. Glob `management/kanban/**/TSK-<id>.md` → leggi frontmatter (`layer`, `consumer`, `status`).
2. Se `consumer: human` ma invocazione esplicita `/dev` → consuma con agent **solo questo run** (non modificare il file).
3. Delega al subagent: `be-dev` | `fe-dev` | `db-dev` | `qa-dev` secondo `layer:`.
4. Subagent applica skill **`dev-protocol`**, **`dev-handoff`**, **`vcs-handoff`** (v2.8).

## STOP se

- TSK senza `layer:` o `consumer:`
- Dipendenze non `done`
- `code_path` vuoto in `factory.config.yaml`
- `raw/tech_stack.md` non copre il layer
- Routing `factory.config.yaml` ≠ agent disponibile
