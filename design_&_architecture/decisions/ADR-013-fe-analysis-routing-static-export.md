---
id: ADR-013
title: Analisi ticker arbitrario con static export — query param
status: accepted
created: 2026-05-22
deciders: [lead-architect, marco.ciullo]
---
# ADR-013 — Analisi ticker arbitrario con `output: 'export'`

## Contesto

[ADR-009](ADR-009-deployment-target.md) e [ADR-001](ADR-001-frontend-stack.md) mantengono **Next.js static export** (`output: 'export'`) servito dal monolite Spring Boot — nessun runtime Node in produzione.

La route dinamica `app/analysis/[ticker]/page.tsx` richiede `generateStaticParams()`; oggi espone una whitelist di 8 ticker demo. Qualunque altro simbolo → **404 statico** al deploy, in contrasto con ricerca/screener che restituiscono ticker arbitrari dal backend [^src: management/kanban/EP-007-hardening-produzione/US-023-analisi-ticker-arbitrario-deploy-statico/US-023.md §Descrizione] [^src: wiki/gaps.md §fe-static-export-tickers].

La pagina Moat (`/moat?ticker=`) ha già adottato **query string** ed evita il vincolo SSG [^src: wiki/gaps.md §fe-static-export-tickers].

## Decisione

### Opzione scelta: **B — `/analysis?ticker={SYMBOL}`** (query param, pagina statica singola)

| Aspetto | Scelta |
|---|---|
| Route FE | `app/analysis/page.tsx` (statica, **senza** `[ticker]` dinamico) |
| Navigazione | `/analysis?ticker=AAPL` (uppercase normalizzato lato client) |
| Rimozione | Eliminare `app/analysis/[ticker]/` e `generateStaticParams()` |
| Coerenza UX | Allineata a `/moat?ticker=` già in produzione |
| Deploy | **Mantiene** `output: 'export'` e modello ADR-009 (monolite + static `out/`) |

**Redirect opzionale (non bloccante R1.1):** middleware o `next.config.js` `redirects` da `/analysis/{ticker}` → `/analysis?ticker={ticker}` solo in `next dev`; in export statico i redirect server-side non esistono — i link interni vanno aggiornati alla forma query (SearchBar, ResultsList, watchlist).

### Flusso dati

1. Utente seleziona ticker da ricerca/screener/watchlist.
2. FE naviga a `/analysis?ticker={T}`.
3. `AnalysisPageClient` legge `searchParams.ticker`, valida non-vuoto, chiama `GET /api/analysis/{ticker}` (path REST invariato).
4. E2E: aggiornare URL target (AAPL e almeno un ticker fuori ex-whitelist demo).

### Aggiornamenti L4 collegati

- [components/frontend-components.md](../components/frontend-components.md) — tabella routing.
- [overview.md](../overview.md) — nota R1.1 hardening FE.

## Alternative considerate

| Opzione | Descrizione | Motivo scarto |
|---|---|---|
| **A** | Feed build-time da catalogo titoli (DB/API → `generateStaticParams` massivo) | Richiede step CI pre-build + lista ticker instabile; costo operativo alto per MVP |
| **C** | Abbandonare `output: 'export'` (SSR/Node runtime) | Rompe ADR-009 monolite; aumenta superficie attack e complessità deploy |
| **A-lite** | Whitelist estesa manualmente | Non risolve US-023 (ticker arbitrario) |

## Dipendenze UI (US-022)

Static export non impatta US-022 (`fe-swr-peer-r19`). Allineamento dipendenze: appendice [ADR-001](ADR-001-frontend-stack.md) §Allineamento stack v2026 — dipendenze client.

## Conseguenze

- US-023: taskizzabile `fe-dev` (refactor route + link + Playwright).
- Gap `fe-static-export-tickers`: chiudibile post-implementazione.
- REST API `/api/analysis/{ticker}`: **invariata** (solo URL UI cambia).
- Bookmarks esterni a `/analysis/AAPL`: rottura accettata; documentare in release notes R1.1.

## Pagine collegate

- [ADR-001](ADR-001-frontend-stack.md)
- [ADR-009](ADR-009-deployment-target.md)
- [components/frontend-components.md](../components/frontend-components.md)
