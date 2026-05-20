---
name: promote-status
description: Procedura canonica per la transizione di status delle pagine wiki (draft → review → approved). Eccezione di scrittura su wiki/, riservata all'orchestrator.
---
# Operazione `/promote` (canonica)

Riferimenti: `wiki-log-entry` (template `promote`), `PATTERN.md §3` + `§10`.

## Chi può eseguirla

**Solo l'orchestrator.** Unica eccezione strutturata di scrittura su wiki/ da
parte di un agente diverso da `wiki-keeper`. Modifica **meccanica** ristretta
a 2 campi: `status:` e `updated:`.

## Trigger

L'umano invoca `/promote <path> [<new-status>]`.

## Transizioni legali

```
draft → review → approved
```

- Mai salti (no `draft → approved`).
- Mai retrocessione senza passare per `deprecated`.
- `approved → deprecated` legale.
- `deprecated → archived` legale.

Se l'umano non specifica `<new-status>`, applica la transizione successiva naturale.

## Procedura

1. **Read** della pagina target.
2. Estrai `status:` corrente dal frontmatter YAML.
3. Calcola target legale. Se illegale → **STOP**: rifiuta in chat.
4. **Edit meccanico**: cambia **solo** `status:` e `updated:` (= oggi) nel frontmatter. **Mai toccare il corpo**, mai altri campi.
5. Append a `wiki/log.md` secondo `wiki-log-entry` (template `promote`).

## Refusal cases

- Path non esiste → rifiuta.
- `status:` non trovato → rifiuta.
- Transizione illegale → rifiuta, mostra il passo intermedio.
- Pagina è `log.md`, `gaps.md`, `index.md` o in `query/`/`lint/` → rifiuta.

## Anti-pattern (vietati)

| Anti-pattern | Correzione |
|---|---|
| Modificare il corpo della pagina | Solo `status:` e `updated:`. |
| Salto (`draft → approved`) | Richiede 2 invocazioni separate. |
| Promuovere senza loggare | Log obbligatorio. |
| Promuovere pagine in `query/` o `lint/` | Vietato. |
