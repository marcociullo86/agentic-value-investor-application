---
id: fmp-news-media
type: concept
sources: ["raw/fmp_docs.md", "raw/fmp_docs.json"]
status: draft
created: 2026-05-22
tags: [fmp, stable, news, media, press-release]
---
# FMP — News & Media (stable)

^src: raw/fmp_docs.md §News & Media — ^src: raw/fmp_docs.json sezione="News & Media"

Sezione dell'API FMP stable con notizie finanziarie, articoli FMP, comunicati stampa e notizie per ticker specifico.

---

## Endpoint principali

### 1. FMP Articles
- **Path**: `GET /stable/fmp-articles`
- **Parametri**: `page` (number), `size` (number)
- **Response**: articoli editoriali FMP con titolo, testo, immagine, URL, data

### 2. Stock News
- **Path**: `GET /stable/stock-news`
- **Parametri**: `tickers` (comma-separated), `limit`, `from`, `to`
- **Response**: `[{symbol, publishedDate, title, image, site, text, url}]`
- **Uso**: news per ticker specifico — feed notizie nella UI

### 3. General News
- **Path**: `GET /stable/general-news`
- **Parametri**: `page`, `size`
- **Response**: notizie generali di mercato

### 4. Press Releases
- **Path**: `GET /stable/press-releases`
- **Parametri**: `symbol*`, `limit`
- **Response**: comunicati stampa aziendali

### 5. Stock News Sentiments RSS
- **Path**: `GET /stable/stock-news-sentiments-rss`
- **Response**: feed RSS con sentiment delle notizie

---

## Nota sulla v3

La vecchia sezione "News & Estimates" v3 includeva anche stime degli analisti (consensus EPS, revenue estimates, price target). Nella stable la sezione e' ora "News & Media" (solo news). Le stime analisti sono in sezioni separate se presenti. Vedi gap `fmp-stable-analyst-estimates` se questa funzionalita' e' necessaria.

---

## Uso nel progetto

**Integrata in EP-011 (Deep Analysis, 2026-05-25):**
- `FmpAdapter.getStockNews(ticker, days=90)` wrappa `GET /stable/news/stock?tickers={ticker}&from={date}` con filtro temporale 90 giorni post-hoc. DTO: `StockNewsItem`. [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-042-news-sentiment-classifier/TSK-108.md]
- `NewsSentimentService` classifica le news in TEMPORARY_PANIC / STRUCTURAL_DAMAGE / NEUTRAL tramite LLM (max 50 call/ticker). Cache su `news_classification` (V015).
- `ResilientFmpAdapter` applica la chain Resilience4j identica agli altri endpoint FMP.

L'endpoint `stock-news-latest` (news generali senza ticker) non è ancora integrato; previsto per EP-012 (Batch Universe Screener — segnale "news scout").

---

## Cross-link

- Entity: [[fmp-api]]
- Synthesis: [[fmp-api-overview]]
- Pipeline deep: [[analysis-api-pipeline]]
