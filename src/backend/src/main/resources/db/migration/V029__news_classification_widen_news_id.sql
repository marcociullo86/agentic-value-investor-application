-- V029: widen news_classification.news_id from VARCHAR(200) to VARCHAR(512).
--
-- FMP /stable/news/stock non espone un campo `newsId` stabile (shape:
-- {symbol, publishedDate, title, image, site, text, url}), quindi
-- NewsSentimentService usa l'URL come chiave di dedup/cache. Gli URL delle
-- news superano regolarmente i 200 caratteri → INSERT rigettato con
--   ERROR: value too long for type character varying(200)
-- 512 copre con margine gli URL reali; resta sotto il limite btree (~2700 byte)
-- per la UNIQUE constraint uq_news_classification_newsid.
-- [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-042-news-sentiment-classifier/TSK-110.md]

ALTER TABLE news_classification
    ALTER COLUMN news_id TYPE VARCHAR(512);
