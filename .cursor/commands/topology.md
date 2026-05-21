# /topology — Topologia factory

Mostra o modifica topologia e routing (PATTERN §13).

## Argomenti

- `show` o nessun argomento → tabella read-only
- `set <topology>` → proposta diff + **STOP per conferma umana**

## Output (show)

- `topology`, dev-agent presenti in `.cursor/agents/` (e `.claude/agents/`), `routing`, `code_path`, `stack_mode`, `vcs.mode`
- Check R1–R3 coerenza agent file ↔ `factory.config.yaml`

## set

- Archivio file agent rimossi → `.cursor/agents/.archive/` (mai delete)
- Append `wiki/log.md`
- **Non** ri-route TSK esistenti; solo TSK nuovi dal TPM

Mai modificare `factory.config.yaml` senza conferma esplicita dell'utente.
