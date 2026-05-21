---
name: heal-protocol
description: Ciclo evaluator-optimizer vincolato per riparare ERROR meccanici flaggati heal-eligible dal wiki-lint. Whitelist chiusa, gate umano bulk, max 3 iterazioni.
---
# Heal Protocol (v2.5)

Riferimenti: `lint-checks`, `citation-rules`, `wiki-log-entry`.

## Chi può eseguirla

**Solo il `wiki-keeper`** in modalità heal (single-committer §7 r.12 invariato).
Trigger: comando `/heal [<lint-report-path>]`.

## Whitelist chiusa (categorie heal-eligible)

| Categoria | Definizione | Soglia |
|---|---|---|
| `broken-wikilink` | Wikilink non risolto con candidato match | Fuzzy ratio ≥ 0.90 (Levenshtein) |
| `missing-frontmatter-field` | Frontmatter mancante di campo deducibile dal path | Deducibilità deterministica (es. `type:` dal path `wiki/concepts/`) |
| `citation-section-mismatch` | `[^src: path §sez]` con `sez` non esistente ma simile | Edit-distance ≤ 3 char dal nome sezione reale |

**Escluso esplicitamente**: `id-duplicate` (richiede riconciliazione semantica, fuori scope heal).

## Procedura (5 fasi)

### Fase 0 — Read del report

```
Read <lint-report-path> (default: wiki/lint/<più-recente>.md)
```

Estrai sezione `## ERROR meccanici (heal-eligible)`. Conta categorie.

### Fase 1 — Proposta bulk (STOP)

```
HEAL PROPOSTO
=============
Report: <path>
ERROR heal-eligible: N
- broken-wikilink: N
- missing-frontmatter-field: N
- citation-section-mismatch: N

Fix proposti (preview):
1. wiki/concepts/foo.md L42: [[bar-niente]] → [[bar-presente]] (fuzzy 0.95)
2. wiki/concepts/baz.md frontmatter: aggiungo type: concept (deducibile da path)
...

Procedi? (sì/no/parziale)
```

**Attendi conferma esplicita.** Gate umano non bypassabile.

### Fase 2 — Loop iter (max 3)

Per ogni iterazione:

1. Applica i fix proposti (Edit puntuali).
2. Re-run `wiki-lint` (programmaticamente: chiama skill `lint-checks`).
3. Confronta: nuovi ERROR introdotti? Stesso conteggio o regressione?
   - **Regressione** (nuovi ERROR > 0) → STOP, rollback dei fix di questa iterazione.
   - **No progress** (conteggio invariato) → STOP, segnala blocco semantico.
   - **Progress** (conteggio diminuito) → continua.
4. Append `heal-iter-N` a `wiki/log.md`.

**STOP conditions** (qualsiasi una):
- Iterazione 3 raggiunta.
- Regressione rilevata.
- No-progress per 1 iterazione.
- Diff vuoto (nessun fix applicato).
- User-rejected mid-loop.

### Fase 3 — Report finale

```
HEAL COMPLETATO
===============
Iterazioni: N
ERROR heal-eligible iniziali: K
ERROR heal-eligible finali: K'
Fix applicati: M
Regressioni: 0 | Y (rollback)
```

### Fase 4 — Log entry

Append a `wiki/log.md` (template `heal-iter-N` + summary finale).

## Regole inviolabili

- **Mai fuori whitelist.** Categoria non in whitelist → skippa, segnala in report.
- **Mai bulk silent.** Gate Fase 1 obbligatorio.
- **Mai `id-duplicate`** (escluso esplicitamente).
- **STOP prima di Edit** in Fase 2 se l'utente non ha confermato la fase 1.

## Anti-pattern

| Anti-pattern | Correzione |
|---|---|
| Heal `WARNING` (claim non citati, orphan) | Vietato: solo ERROR meccanici. |
| Heal senza gate umano | Vietato: Fase 1 STOP è inviolabile. |
| Heal > 3 iterazioni | Vietato: budget chiuso. |
| Heal di `id-duplicate` | Vietato: richiede semantica. |
