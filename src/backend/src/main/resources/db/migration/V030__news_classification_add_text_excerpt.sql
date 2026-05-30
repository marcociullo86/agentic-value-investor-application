-- V030: aggiunge news_classification.text_excerpt (US-091 / TSK-305).
--
-- Snippet (testo) della notizia usato per mostrare titolo + testo nel blocco
-- Sentiment News della pagina di dettaglio. Persistito così la lista delle
-- notizie analizzate è ricostruibile anche su cache-hit (oggi si salvano solo
-- headline/url/classe/motivazione, NON il testo). Nullable: retrocompatibile
-- con le righe già presenti. 400 char copre SNIPPET_LEN (300) con margine.
-- [^src: management/kanban/EP-020-trasparenza-analisi-llm/US-091-sentiment-news-titolo-testo/TSK-305.md]

ALTER TABLE news_classification
    ADD COLUMN text_excerpt VARCHAR(400);
