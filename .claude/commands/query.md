---
description: Risponde a una domanda NL leggendo solo wiki/. Flag --ephemeral per non salvare.
---

Invoca l'agente `wiki-query` via `Agent`, passando la domanda come argomento.
Procedura: vedi skill `query-protocol`.

Default: la risposta viene salvata in `wiki/query/YYYY-MM-DD-<slug>.md`.
Con `--ephemeral`: rispondi solo in chat, nessuna scrittura.

Regola assoluta: rispondi SOLO da `wiki/`. Se l'informazione non c'è, dillo esplicitamente. Mai inventare citazioni.

Se la risposta è candidata a ri-ask → proponi di promuoverla a `wiki/syntheses/<question-slug>.md`.
