-- V033: generalizza filing_chunks a vector store multi-corpus (FILING + WIKI)
-- Secondo corpus RAG per le citazioni cross-dominio del Riepilogo (EP-024 / US-103).
-- [^src: design_&_architecture/decisions/ADR-030-decision-layer-vi-ta-summary.md §2]
-- [^src: management/kanban/EP-024-riepilogo-e-technical-analysis-tab/US-103-aggregatore-riepilogo-cross-dominio-be/TSK-337.md]
--
-- Migration ADDITIVA e non distruttiva: nessun DROP, nessuna perdita dati.
-- I record esistenti (chunk dei filing 10-K/10-Q, V014) restano corpus FILING
-- via DEFAULT. Convenzione Flyway forward-only dello stack (come V014..V032):
-- rollback = nuova migration correttiva, non file undo.
--
-- Schema reale di partenza (V014): filing_blob_id/ticker/filing_type erano NOT NULL.
-- I chunk WIKI non hanno filing_blob/ticker/filing_type, quindi i tre vincoli NOT NULL
-- vengono rilassati e l'integrità per-corpus è garantita dal CHECK chk_filing_chunks_corpus
-- (dettaglio non previsto in ADR-030 §2 ma necessario per ospitare il corpus WIKI).

-- 1. Discriminante di corpus (default FILING per i record esistenti = backfill banale)
ALTER TABLE filing_chunks
    ADD COLUMN corpus_kind VARCHAR(10) NOT NULL DEFAULT 'FILING';

-- 2. Rilascio dei NOT NULL specifici del corpus FILING (i WIKI li lasciano NULL)
ALTER TABLE filing_chunks ALTER COLUMN filing_blob_id DROP NOT NULL;
ALTER TABLE filing_chunks ALTER COLUMN ticker         DROP NOT NULL;
ALTER TABLE filing_chunks ALTER COLUMN filing_type    DROP NOT NULL;

-- 3. Colonne identificative della sorgente WIKI
ALTER TABLE filing_chunks ADD COLUMN wiki_source_id VARCHAR(255);  -- slug/path pagina, es. 'ta-vs-vi-decision-layer'
ALTER TABLE filing_chunks ADD COLUMN wiki_domain    VARCHAR(40);   -- es. 'value-investing' | 'technical-analysis-trading'

-- 4. Integrità per-corpus: i campi obbligatori dipendono dal corpus_kind
ALTER TABLE filing_chunks
    ADD CONSTRAINT chk_filing_chunks_corpus CHECK (
        (corpus_kind = 'FILING'
            AND filing_blob_id IS NOT NULL
            AND ticker         IS NOT NULL
            AND filing_type    IS NOT NULL)
        OR
        (corpus_kind = 'WIKI'
            AND wiki_source_id IS NOT NULL
            AND wiki_domain    IS NOT NULL
            AND filing_blob_id IS NULL)
    );

-- 5. Valori ammessi del discriminante
ALTER TABLE filing_chunks
    ADD CONSTRAINT chk_filing_chunks_corpus_kind CHECK (corpus_kind IN ('FILING', 'WIKI'));

-- 6. Unicità/idempotenza del corpus WIKI: (wiki_source_id, chunk_index).
--    L'unicità FILING resta su uq_filing_chunks_blob_chunk (V014); con filing_blob_id
--    ora nullable i record WIKI (filing_blob_id NULL) non collidono (NULL distinti in UNIQUE).
CREATE UNIQUE INDEX uq_wiki_chunks
    ON filing_chunks (wiki_source_id, chunk_index)
    WHERE corpus_kind = 'WIKI';

-- 7. Indice di filtro per la similarity search del Summary (corpus_kind + dominio).
--    L'indice HNSW unico (idx_filing_chunks_hnsw, V014) copre entrambi i corpus;
--    il filtro corpus_kind/wiki_domain è applicato come predicato nella query.
CREATE INDEX idx_filing_chunks_corpus ON filing_chunks (corpus_kind, wiki_domain);
