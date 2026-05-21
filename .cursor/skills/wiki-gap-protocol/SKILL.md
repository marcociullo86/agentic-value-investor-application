---
name: wiki-gap-protocol
description: Formato canonico e ciclo di vita di un gap in wiki/gaps.md. Riferimento unico per PM/Arch/TPM/wiki-query/dev-agent (apertura) e wiki-keeper (chiusura).
---
# Protocollo gap (canonico)

`wiki/gaps.md` è il **canale formale del feedback loop** della wiki (vedi
`PATTERN.md §10`).

## Caratteristiche

- **Append-only condiviso in scrittura** fra `product-manager`, `lead-architect`,
  `tpm`, `wiki-query`, dev-agent (v2.7). Lettura: tutti, ma `wiki-keeper` lo legge
  **obbligatoriamente all'inizio di ogni run**.
- **Chiusura riservata a `wiki-keeper`**.
- Vietato editare gap altrui, vietato cancellare gap risolti.

## Formato gap (apertura)

```markdown
## YYYY-MM-DD HH:MM — <slug-gap>
**Origine:** <agente> @ <artefatto in lavorazione>
**Gap:** <cosa manca in wiki/>
**Sospetta fonte:** <raw da ingerire | "nessuna fonte chiara, serve nuovo raw">
**Impatto:** <quale produzione è frenata>
```

## Bloccante vs non-bloccante

| Tipo | Azione apertura | Azione lavoro |
|---|---|---|
| **Non-bloccante** | Append a `gaps.md` | Continua il run citando lo stato corrente |
| **Bloccante** | Append a `gaps.md` + apri `Q_NNN` con `/apri-question` | STOP: l'artefatto impattato passa in `status: blocked` |

## Chiusura (riservata a wiki-keeper)

Per ogni gap aperto, all'inizio di un run, `wiki-keeper` decide:

1. **Coperto da raw esistente** → ingerisci il raw, scrivi le pagine, chiudi il gap.
2. **Richiede nuovo raw** → segnala in chat all'umano. Il gap **resta aperto**.
3. **Risolvibile con synthesis** → crea `wiki/syntheses/<question-slug>.md`, chiudi il gap.

Per chiudere:

```markdown
**Risolto:** YYYY-MM-DD — [[<pagina-nuova-o-aggiornata>]]
```

Mai cancellare il gap. Mai modificare righe precedenti. Append a `wiki/log.md` (vedi `wiki-log-entry`).

## Eccezione di scrittura su `wiki/`

Append-only e meccanica: aggiungere una sezione `## YYYY-MM-DD HH:MM — ...` in coda al file.
Mai editare contenuto esistente, mai chiudere gap (riservato a `wiki-keeper`).

## Anti-pattern (vietati)

| Anti-pattern | Correzione |
|---|---|
| Gap senza **Origine** | Aggiungi `<agente> @ <artefatto>` |
| Chiudere un gap senza essere `wiki-keeper` | Vietato. Aspetta il prossimo run del keeper. |
| Editare gap aperti da altri agenti | Vietato. Apri un nuovo gap per raffinare. |
| Usare `gaps.md` per TODO interni | Vietato. È un canale formale. |
| Cancellare gap risolti per "fare ordine" | Vietato. È archivio storico. |
