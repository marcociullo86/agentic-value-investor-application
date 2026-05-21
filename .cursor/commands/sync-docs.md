# /sync-docs — Estrazione PDF

Estrae testo e figure dai PDF in `raw/`.

## Esecuzione

Delega al subagent **`sync-docs`** (Task `subagent_type: sync-docs`).

1. Scansiona `raw/*.pdf` non presenti in `raw/.extraction-manifest.json`.
2. Scrive `raw/<data>-<nome>.txt` e `raw/images/<data>-<nome>-fig-NN.md`.
3. Aggiorna il manifest.
4. Append `wiki/log.md` (skill `wiki-log-entry`, template bootstrap).
5. Suggerisce ingest: invocare **`wiki-keeper`**.

`wiki-keeper` legge solo `.txt` estratti, mai i PDF.
