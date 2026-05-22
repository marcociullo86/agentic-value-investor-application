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

Non integrata nel rule engine MVP R1.0. Potenzialmente utile per future feature di news feed nella UI (EP futura).

---

## Cross-link

- Entity: [[fmp-api]]
- Synthesis: [[fmp-api-overview]]
