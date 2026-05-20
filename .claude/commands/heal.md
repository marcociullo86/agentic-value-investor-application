---
description: Ripara ERROR meccanici flaggati heal-eligible da un lint report. Loop evaluator-optimizer vincolato, gated, max 3 iterazioni.
---

Argomento: `<lint-report-path>` (default: il più recente in `wiki/lint/`).

Invoca `wiki-keeper` in modalità heal. Procedura: vedi skill `heal-protocol`.

Whitelist chiusa (mai correzione fuori categoria):

- `broken-wikilink` (fuzzy ≥ 0.90)
- `missing-frontmatter-field` (deducibile dal path)
- `citation-section-mismatch` (edit-distance ≤ 3)

Mai `id-duplicate`. Gate umano bulk obbligatorio.
