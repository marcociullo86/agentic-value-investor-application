# /heal — Ripara ERROR meccanici

Ripara ERROR flaggati `heal-eligible` da un lint report. Loop evaluator-optimizer, max 3 iterazioni, gate umano bulk.

## Argomenti

`<lint-report-path>` opzionale (default: report più recente in `wiki/lint/`).

## Esecuzione

Delega al subagent **`wiki-keeper`** in modalità heal.

- Procedura: skill **`heal-protocol`**
- Whitelist: `broken-wikilink` (fuzzy ≥ 0.90), `missing-frontmatter-field`, `citation-section-mismatch` (edit-distance ≤ 3)
- Mai `id-duplicate`
- Gate umano obbligatorio prima di applicare fix in batch
