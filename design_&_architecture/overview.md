---
id: overview
title: Architettura WebApp Value Investing — Overview
status: accepted
created: 2026-05-20
deciders: [lead-architect, marco.ciullo]
---
# Architettura WebApp Value Investing — Overview

> Visione architetturale d'insieme per la WebApp Value Investing: SPA React/Next.js in front-end, REST API Kotlin/Spring Boot in back-end, PostgreSQL come storage, integrazione con il provider esterno FMP (Financial Modeling Prep) come unica fonte di dati finanziari.

## Contesto

L'applicazione automatizza il workflow di analisi Value Investing (Graham/Buffett) trasformando dati di bilancio decennali in un verdetto strutturato (Traffic Light) e in una stima di valore intrinseco con segnale di Margin of Safety. La specifica funzionale di riferimento e' documentata in [[webapp-value-investing-spec]] e [[vi-06-webapp-value-investing-fsd]]. L'architettura a tre livelli e' canonica nella FSD [^src: wiki/concepts/webapp-architecture-vi.md §Contesto].

## Macro-architettura (3-layer)

```
+----------------------------+
| L1 — Frontend SPA          |  React 19 + Next.js 16.x (SPA/SSG static export)
| Pages, charts, traffic     |  State: Zustand (default)
| light, watchlist UI        |  Charts: Recharts; Grid: Ag-Grid Community
+-------------|--------------+
              | HTTPS / REST JSON (OpenAPI 3.1)
              v
+----------------------------+
| L2 — Backend API           |  Kotlin 2.2 + Spring Boot 3.5.x
| Controllers / Services /   |  Spring Web, Spring Data JPA,
| Rule Engine / FMP Adapter  |  Spring Security (JWT), Resilience4j
+------|-------------|-------+
       |             |
       v             v
+--------------+  +------------------+
| L3a —        |  | L3b — FMP API    |
| PostgreSQL   |  | provider esterno |
| Users,       |  | (REST JSON)      |
| Watchlists,  |  | financialmodel-  |
| FMP cache,   |  | ingprep.com      |
| Moat notes,  |  +------------------+
| Engine runs  |
+--------------+
```

Flusso di interazione canonico documentato in [[webapp-architecture-vi]] §Flusso di Interazione.

## Livello 1 — Frontend SPA

- **Stack:** React 19 + Next.js 16.x (modalita' SPA/SSG static export, no SSR full-stack). Decisione formalizzata in [ADR-001](decisions/ADR-001-frontend-stack.md) (appendice stack v2026) e in [[vi-07-risoluzione-q002-q003]] §ADR Q_002.
- **R1.1 routing analisi:** `/analysis?ticker={SYMBOL}` — [ADR-013](decisions/ADR-013-fe-analysis-routing-static-export.md).
- **State management:** Zustand come default (lightweight, store modulare per ticker/watchlist/auth). Alternativa Redux Toolkit lasciata a discrezione del team.
- **Librerie chiave:** Recharts (grafici storici US-015), Ag-Grid Community (tabelle bilanci e screener US-003), TailwindCSS o equivalente per design system.
- **Responsabilita':** ricerca (US-001), screener (US-002), lista risultati (US-003), dashboard Traffic Light (US-014), grafici (US-015), checklist Moat (US-016), watchlist (US-017).

## Livello 2 — Backend API

- **Stack:** Kotlin 2.2 + Spring Boot 3.5.x. Decisione tecnologica formalizzata in [ADR-002](decisions/ADR-002-backend-stack.md) (appendice stack v2026), gia' espressa nella FSD [^src: wiki/concepts/webapp-architecture-vi.md §Livello 2: Backend (Server)].
- **Errori API:** RFC 9457 con extension al top-level — [ADR-012](decisions/ADR-012-problemdetail-rfc9457-flatten.md).
- **Moduli principali (vedi [components/backend-components.md](components/backend-components.md)):**
  - **Controller layer**: endpoint REST (vedi [api/openapi.yaml](api/openapi.yaml) per il contratto completo).
  - **Service layer**: orchestrazione caso d'uso (es. `AnalyzeTickerService`, `ScreenerService`, `WatchlistService`).
  - **FMP Adapter**: client tipizzato (Spring `RestClient` o `WebClient`) verso FMP con cache, throttle, retry, fallback ([ADR-004](decisions/ADR-004-fmp-integration.md)).
  - **Rule Engine module**: pacchetto isolato che implementa le quattro categorie di regole + DCF + Graham Number ([ADR-005](decisions/ADR-005-rule-engine-design.md), [[value-investing-rule-engine]]).
  - **Persistence layer**: Spring Data JPA su PostgreSQL ([ADR-003](decisions/ADR-003-database-postgresql.md)).
  - **Security layer**: JWT stateless + BCrypt per autenticazione utenti EP-006 ([ADR-006](decisions/ADR-006-authentication.md)).
- **Null safety:** sfruttamento del type system Kotlin per campi finanziari opzionali (data class `nullable` esplicita), come richiesto dalla FSD [^src: wiki/concepts/webapp-architecture-vi.md §Livello 2: Backend (Server)].

## Livello 3a — Database (PostgreSQL)

- **Stack:** PostgreSQL 17 + Spring Data JPA + Flyway per migrations. Decisione in [ADR-003](decisions/ADR-003-database-postgresql.md) (appendice stack v2026).
- **Schema canonico:** definito in [data/er-diagram.md](data/er-diagram.md). Entita' principali: `users`, `watchlists`, `watchlist_items`, `fmp_financial_snapshot` (cache), `fmp_profile_snapshot` (cache profilo), `rule_engine_result`, `moat_checklist_entry`, `dcf_method_override`.
- **Cache 24h (bilanci):** `fmp_financial_snapshot` — TTL 24h (US-005).
- **Cache 1h (profilo/prezzo):** `fmp_profile_snapshot` — [ADR-014](decisions/ADR-014-fmp-profile-snapshot-ttl.md).

## Livello 3b — FMP API (provider esterno)

- **Provider:** Financial Modeling Prep — vedi [[fmp-api]] e [[fmp-api-overview]].
- **Endpoint usati nel MVP R1.0:**
  - `GET /api/v3/income-statement/{ticker}?limit=10` (US-004, US-007, US-008, US-010, US-015)
  - `GET /api/v3/balance-sheet-statement/{ticker}?limit=10` (US-004, US-009, US-011)
  - `GET /api/v3/cash-flow-statement/{ticker}?limit=10` (US-004, US-010, US-012)
  - `GET /api/v3/key-metrics/{ticker}?limit=10` (US-004, US-007, US-011)
  - `GET /api/v3/profile/{ticker}` (US-001, US-013 per prezzo corrente)
  - `GET /api/v3/search?query={...}` (US-001 fuzzy lookup) — vedi [[fmp-search]]
  - `GET /api/v3/stock-screener?marketCapMoreThan=...&sector=...` (US-002) — vedi [[fmp-stock-directory]]
- **Modalita':** REST JSON; il backend agisce da proxy con cache e adapter tipizzato.
- **Limiti noti (gap aperti, vedi `wiki/gaps.md`):** `fmp-rate-limiting`, `fmp-endpoint-base-urls`, `fmp-error-codes` — l'implementazione applica valori conservativi e flag di tracciabilita'.

## Cross-cutting concerns

| Concern | Soluzione | ADR |
|---|---|---|
| Autenticazione utenti | JWT stateless + Spring Security + BCrypt | [ADR-006](decisions/ADR-006-authentication.md) |
| Contratto API | REST + OpenAPI 3.1 generato | [ADR-007](decisions/ADR-007-api-contract.md) |
| Cache FMP | TTL 24h in PostgreSQL (single source of truth) | [ADR-004](decisions/ADR-004-fmp-integration.md) |
| Resilienza FMP | Resilience4j (retry + circuit breaker + rate limiter) | [ADR-004](decisions/ADR-004-fmp-integration.md) |
| Logging / metrics | SLF4J + Logback structured + Micrometer + Actuator | [ADR-008](decisions/ADR-008-observability-logging.md) |
| Deploy | Docker monorepo (BE jar + FE static export) + profili Spring | [ADR-009](decisions/ADR-009-deployment-target.md) |
| Deploy prod R1.1 | VM + Docker Compose + nginx TLS | [ADR-015](decisions/ADR-015-deployment-target-r11.md) |
| Problem Details | Extension RFC 9457 top-level | [ADR-012](decisions/ADR-012-problemdetail-rfc9457-flatten.md) |
| FMP ops / throttle | Policy conservativa + mapping errori | [ADR-016](decisions/ADR-016-fmp-operations-throttling.md) |

## Mappa US -> componenti

| US | Frontend | Backend | DB | FMP |
|---|---|---|---|---|
| US-001 ricerca ticker | `SearchBar` page | `SearchController`, `FmpAdapter.profile/search` | — | `/profile`, `/search` |
| US-002 screener | `ScreenerForm` page | `ScreenerController`, `FmpAdapter.screener` | — | `/stock-screener` |
| US-003 lista risultati | `ResultsList` | (riusa US-001/002) | — | — |
| US-004 recupero bilancio | — | `FmpAdapter.statements`, `FinancialDataService` | `fmp_financial_snapshot` | 4 endpoint bilancio |
| US-005 cache 24h | — | `FmpCacheService` (TTL + invalidation) | `fmp_financial_snapshot` | conditional |
| US-006 resilienza | — | Resilience4j config + fallback su cache | `fmp_financial_snapshot` | retry |
| US-007/008/009/010 regole | (consumo via US-014) | `RuleEngineService` + 4 strategy class | `rule_engine_result` | (via cache) |
| US-011 Graham Number | (consumo via US-014) | `GrahamNumberCalculator` | `rule_engine_result` | (via cache) |
| US-012 DCF Owner Earnings | (consumo via US-014) | `DcfCalculator` + `GreenwaldMaintenanceCapexEstimator` | `rule_engine_result`, `dcf_method_override` | (via cache) |
| US-013 Margin of Safety | (consumo via US-014) | `MarginOfSafetyEvaluator` | `rule_engine_result` | `/profile` (prezzo) |
| US-014 Traffic Light panel | `TrafficLightPanel` | (riusa US-007..013) | — | — |
| US-015 grafici storici | `HistoricalChart` (Recharts) | `HistoricalSeriesService` | `fmp_financial_snapshot` | (via cache) |
| US-016 checklist Moat | `MoatChecklist` page | `MoatChecklistController` | `moat_checklist_entry` | — |
| US-017 watchlist | `WatchlistPage` | `WatchlistController`, `WatchlistService` | `watchlists`, `watchlist_items` | — |

### R1.1 — US → ADR (EP-007…009)

| US | ADR / artefatto L4 |
|---|---|
| US-021 RFC 9457 flatten | [ADR-012](decisions/ADR-012-problemdetail-rfc9457-flatten.md) |
| US-022 dipendenze FE | [ADR-001](decisions/ADR-001-frontend-stack.md) appendice stack v2026 |
| US-023 analisi ticker | [ADR-013](decisions/ADR-013-fe-analysis-routing-static-export.md) |
| US-024 TTL profilo | [ADR-014](decisions/ADR-014-fmp-profile-snapshot-ttl.md) + [ADR-004](decisions/ADR-004-fmp-integration.md) §2b |
| US-025 sync versioni ADR | Appendici ADR-001/002/003 |
| US-026 deploy target | [ADR-015](decisions/ADR-015-deployment-target-r11.md) + runbook |
| US-027 backup / retention log | [deploy-runbook-r11.md](operations/deploy-runbook-r11.md) |
| US-028 checklist cutover | [deploy-runbook-r11.md](operations/deploy-runbook-r11.md) |
| US-029 doc FMP wiki | [ADR-016](decisions/ADR-016-fmp-operations-throttling.md) (L4); wiki US-029 |
| US-030 throttling BE | [ADR-016](decisions/ADR-016-fmp-operations-throttling.md) |

## Roadmap di rilascio (input PM)

- **R1.0** (chiuso): EP-001 … EP-006 — analisi end-to-end, dashboard, watchlist, auth.
- **R1.1** (in corso): EP-007 hardening (US-021…025), EP-008 deploy/ops (US-026…028), EP-009 FMP runbook/throttle (US-029…030). Runbook: [operations/deploy-runbook-r11.md](operations/deploy-runbook-r11.md).

## Concetti wiki di riferimento

- [[webapp-architecture-vi]] — architettura 3-layer canonica
- [[value-investing-rule-engine]] — regole quantitative + DCF
- [[fmp-api]] / [[fmp-api-overview]] — provider esterno
- [[graham-number]], [[intrinsic-value]], [[margin-of-safety]], [[economic-moat]] — concetti di dominio
- [[sec-filings-analysis]] — gap noto (narrative SEC non in FMP, vedi `wiki/gaps.md`)
- [[vi-07-risoluzione-q002-q003]] — risoluzione Q_002/Q_003
- [[vi-08-risoluzione-q001-owner-earnings]] — risoluzione Q_001

## Pagine collegate

- [decisions/ADR-001-frontend-stack.md](decisions/ADR-001-frontend-stack.md) … ADR-016, [operations/deploy-runbook-r11.md](operations/deploy-runbook-r11.md)
- [api/openapi.yaml](api/openapi.yaml)
- [data/er-diagram.md](data/er-diagram.md)
- [components/backend-components.md](components/backend-components.md)
- [components/frontend-components.md](components/frontend-components.md)
