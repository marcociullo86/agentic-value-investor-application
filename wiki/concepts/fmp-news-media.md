---
id: fmp-news-media
type: concept
sources: ["raw/fmp_docs.md", "raw/fmp_docs.json"]
status: draft
created: 2026-05-22
tags: [fmp, stable, news, media, press-release]
---
# FMP — News & Media (stable)

[^src: raw/fmp_docs.md §News] [^src: raw/fmp_docs.json sezione="News"]

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

**Integrata in EP-011 (Deep Analysis, 2026-05-25; pipeline a imbuto 2026-05-30):**
- `FmpAdapter.getStockNews(ticker, days=90)` wrappa `GET /stable/news/stock?symbols={ticker}&from={date}&to={oggi}&page=0&limit=50`. Finestra esplicita `from..to` + cap `limit` per limitare il costo a monte; il filtro 90gg è anche post-hoc. DTO: `StockNewsItem`. [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-042-news-sentiment-classifier/TSK-108.md]
- `NewsSentimentService` non classifica più ogni notizia in isolamento con voto di maggioranza (annegava un segnale strutturale reale nel rumore). Usa una **pipeline a imbuto**:
  1. **Pre-filtro deterministico** (zero LLM): dedup, scarto pattern di rumore (price target, listicle, premarket/movers…), ranking per **materialità** (keyword tipo SEC/lawsuit/fraud/guidance/restatement…) + recency, taglio ai **top 12**.
  2. **Sintesi LLM unica** (1 chiamata, non N): un solo prompt value-investing valuta il set curato in modo olistico → classe per-item + flag `impairment_permanente`.
  3. **Dominante asimmetrica** (capital preservation, Buffett rule #1): **un solo** `STRUCTURAL_DAMAGE` credibile vince sul conteggio. Questa dominante guida il veto `BOCCIATO_VALUE_TRAP` / il segnale `APPROVATO_PANIC_BUY` in `MungerDecisionService`.
  - Cache `news_classification` (V015/V029) con TTL 24h: se il set curato è già classificato fresco, il verdetto si ricostruisce senza nuove chiamate LLM.
- `ResilientFmpAdapter` applica la chain Resilience4j identica agli altri endpoint FMP (label `news/stock`).

> **Nota parametro (verificata sul campo, 2026-05-30):** l'endpoint stable usa `symbols`, **non** `tickers` (allineato ad agent.py v2.4 e all'esempio docs `?symbols=AAPL`). Con `tickers` il filtro per ticker viene ignorato.
> **Nota formato data:** `publishedDate` è `"yyyy-MM-dd HH:mm:ss"` (separatore spazio, non ISO-8601 `T`) → il DTO usa `@JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")`, altrimenti il `JavaTimeModule` fallisce il decode e l'adapter degrada a lista vuota.
> **Nota piano FMP:** `/stable/news/press-releases` **non** è disponibile sul piano Starter → non integrato. La deep analysis usa solo `/stable/news/stock`.

L'endpoint `stock-news-latest` (news generali senza ticker) non è ancora integrato; previsto per EP-012 (Batch Universe Screener — segnale "news scout").

---

## Cross-link

- Entity: [[fmp-api]]
- Synthesis: [[fmp-api-overview]]
- Pipeline deep: [[analysis-api-pipeline]]
