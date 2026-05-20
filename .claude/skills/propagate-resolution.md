---
name: propagate-resolution
description: Riconcilia downstream quando il wiki-keeper chiude un gap che cita una Q_NNN. Appende marker reconcile-needed a wiki/log.md per ogni US dipendente. Nessuna scrittura su kanban.
---
# Propagate Resolution (operazione canonica `Propagate`, PATTERN.md §3)

Skill del `wiki-keeper`. Eseguita **solo** come effetto collaterale della
chiusura di un gap che cita esplicitamente una `Q_NNN` risolta contestualmente.

Riferimenti: `wiki-gap-protocol`, `wiki-log-entry` (template `reconcile-needed`),
`PATTERN.md §3` (operazione `Propagate`) + `§7 r.9` (gate L4 graduato).

## Chi può eseguirla

**Solo il `wiki-keeper`**. Scrive solo `wiki/log.md` append-only (già nello
scope). Single-committer §7 r.12 invariato.

## Trigger

Il keeper, mentre marca un gap come `**Risolto:** YYYY-MM-DD — [[<pagina>]]`,
rileva che la sezione del gap cita una o più `Q_NNN` passate contestualmente
in `[RISOLTE]`.

## Procedura

1. Read `management/questions.md`. Estrai `Q_NNN` in `[RISOLTE]` con
   `**Data risoluzione:**` uguale alla data chiusura gap.
2. Per ogni Q: raccogli `**Storie sbloccate:**` o, se assente, deriva da
   `grep "blocked_by:.*Q_NNN\|pending_clarification:.*Q_NNN"
   management/kanban/**/US-*.md`.
3. Per ogni US trovata: read `US-YYY.md`. Se `Q_NNN` non è più presente →
   riconciliazione già fatta a mano, SKIP. Altrimenti → marker.
4. Append a `wiki/log.md` una riga per US stale:

   ```
   [YYYY-MM-DD HH:MM] reconcile-needed — US-YYY → Q_NNN closed (gap [[<slug>]]) — files touched: 0
   ```

5. Surface in chat la lista riassuntiva (Q chiuse, US stale).

## Cosa NON fa

- Mai scrittura su `management/kanban/**` (proprietà PM, §2).
- Mai notifiche fuori `wiki/log.md` (l'orchestrator surfaceizza in dashboard).
- Mai chiusura silenziosa di Q (la skill reagisce *dopo*, non innesca).

## Idempotenza

Eseguire due volte sullo stesso gap chiuso produce marker duplicati. Accettabile
(log append-only, segnale ridondante non rumore). Il keeper la esegue **una sola
volta**, contestualmente alla chiusura del gap (Fase 5 di `ingest-protocol`,
prima del log-entry di ingest).

## Anti-pattern (vietati)

| Anti-pattern | Perché vietato |
|---|---|
| Modificare `US-YYY.md` rimuovendo `Q_NNN` da `blocked_by` | Violazione write-scope §2 |
| Riaprire la Q se la riconciliazione non avviene | Q resta in `[RISOLTE]`, il problema è il kanban stale |
| Emettere marker per Q ancora aperte | Skill opera *post-chiusura* |
| Marker senza riferimento `(gap [[<slug>]])` | Audit trail obbligatorio |
