# /query — Domanda al wiki

Risponde a una domanda in linguaggio naturale leggendo **solo** `wiki/`.

Argomenti: `<domanda>` obbligatoria. Flag opzionale `--ephemeral` (nessuna scrittura su disco).

## Esecuzione

Delega al subagent **`wiki-query`** con la domanda nel prompt.

- Procedura: skill **`query-protocol`**
- Default: salva in `wiki/query/YYYY-MM-DD-<slug>.md` + append `wiki/log.md`
- `--ephemeral`: solo risposta in chat
- Mai inventare; mai leggere `raw/`, `management/`, `design_&_architecture/`, `src/`

Se la risposta è candidata a synthesis → proponi promozione; la scrittura in `wiki/syntheses/` è di **`wiki-keeper`**.
