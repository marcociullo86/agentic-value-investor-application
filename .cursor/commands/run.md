# /run — Dashboard di stato

Mostra dashboard di stato e suggerisce il prossimo subagent. Argomento opzionale: focus layer (es. `l3`, `l5`).

## Esecuzione

1. Delega al subagent **`orchestrator`** (Task tool `subagent_type: orchestrator`, oppure `/orchestrator` in chat).
2. L'orchestrator applica la skill **`state-scan`**, legge l'ultima entry in `memory/episodic/` se presente.
3. Output: tabella layer L1–L5, TSK aperti, gap/question, **un solo** suggerimento next-step (nessuna delega automatica).
4. Append `memory/episodic/YYYY-MM-DD-HH-MM-run.md`.

Non modificare artefatti oltre a memoria episodica e (se `/promote` separato) frontmatter wiki.
