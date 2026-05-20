---
description: Mostra (o modifica) topologia + routing della factory (PATTERN §13).
argument-hint: [show | set <topology>]
---

`/topology` o `/topology show`: tabella read-only — topologia dichiarata,
dev-agent presenti, routing attivo, code_path, stack_mode, summary stack,
vcs.mode, 3 check di coerenza (R1: agent file presenti = topology declared;
R2: routing.X=agent ⇔ <X>-dev.md; R3: vcs.mode coerente con code_path).

`/topology set <topology>`: mostra il diff (agent file da creare/archiviare,
routing risultante), STOP per conferma, poi applica + append a `wiki/log.md`.
Archivio (mai delete) dei file rimossi → `.claude/agents/.archive/`.

Mai ri-route automatico di TSK esistenti: il TPM applica il nuovo routing
solo ai TSK nuovi; quelli esistenti restano con il loro `consumer:`.
