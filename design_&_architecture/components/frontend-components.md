---
id: frontend-components
title: Frontend components — Next.js + React + Zustand
status: accepted
created: 2026-05-20
deciders: [lead-architect, marco.ciullo]
---
# Frontend components — Next.js + React + Zustand

> Decomposizione frontend SPA. Stack definito in [ADR-001](../decisions/ADR-001-frontend-stack.md). Consuma il contratto [api/openapi.yaml](../api/openapi.yaml).

## Struttura progetto (Next.js App Router)

```
src/frontend/
 ├── package.json
 ├── next.config.js
 ├── tsconfig.json
 ├── tailwind.config.ts
 ├── app/
 │    ├── layout.tsx                 # root layout, AuthProvider, Toaster
 │    ├── page.tsx                   # landing / search bar (US-001 entry)
 │    ├── (auth)/
 │    │    ├── login/page.tsx
 │    │    └── register/page.tsx
 │    ├── analysis/page.tsx          # dashboard analisi ?ticker= (US-014/015/016, ADR-013)
 │    ├── screener/page.tsx          # US-002 + US-003
 │    └── watchlist/page.tsx         # US-017
 ├── components/
 │    ├── ui/                        # design system base (Tailwind + Radix)
 │    │    ├── Button.tsx
 │    │    ├── Input.tsx
 │    │    ├── Card.tsx
 │    │    ├── Modal.tsx
 │    │    └── ...
 │    ├── search/
 │    │    ├── SearchBar.tsx
 │    │    └── ResultsList.tsx       # US-003
 │    ├── screener/
 │    │    ├── ScreenerForm.tsx
 │    │    └── MarketCapSelector.tsx
 │    ├── analysis/
 │    │    ├── TrafficLightPanel.tsx # US-014
 │    │    ├── RuleSignalCard.tsx    # singolo semaforo cliccabile
 │    │    ├── ValuationSummary.tsx  # Graham + DCF + MoS
 │    │    └── StaleDataBadge.tsx    # marker freschezza (US-005/006)
 │    ├── charts/
 │    │    └── HistoricalChart.tsx   # US-015, Recharts
 │    ├── moat/
 │    │    └── MoatChecklist.tsx     # US-016
 │    ├── watchlist/
 │    │    ├── WatchlistTable.tsx    # US-017
 │    │    └── AddToWatchlistButton.tsx
 │    └── layout/
 │         ├── Navbar.tsx
 │         └── Footer.tsx
 ├── lib/
 │    ├── api/                       # client tipizzato generato da openapi.yaml
 │    │    ├── client.ts             # axios/fetch wrapper con auth interceptor
 │    │    └── generated/            # output orval/openapi-typescript
 │    ├── store/                     # Zustand stores
 │    │    ├── useAuthStore.ts
 │    │    ├── useWatchlistStore.ts
 │    │    ├── useAnalysisStore.ts
 │    │    └── useScreenerStore.ts
 │    └── utils/
 │         ├── formatters.ts         # currency, percent, year
 │         └── signal-color.ts       # mapping Signal -> classe Tailwind + label accessibile
 └── public/                         # asset statici
```

## Routing (App Router)

| Path UI | Pagina | US |
|---|---|---|
| `/` | landing + SearchBar prominente | US-001 |
| `/search?q=...` | risultati ricerca | US-001 + US-003 |
| `/screener` | form screener + risultati | US-002 + US-003 |
| `/analysis?ticker={ticker}` | dashboard completa per ticker (static export, ticker arbitrario) | US-014 + US-015 + US-016 + US-023 |
| `/moat?ticker={ticker}` | checklist Moat standalone | US-016 |
| `/watchlist` | watchlist personale | US-017 |
| `/login`, `/register` | auth | EP-006 |

## Mappa componenti -> US

| Componente | US | API consumate |
|---|---|---|
| `SearchBar` | US-001 | `GET /api/search` |
| `ResultsList` | US-003 | (props dal parent) |
| `ScreenerForm` + `MarketCapSelector` | US-002 | `GET /api/screener` |
| `TrafficLightPanel` + `RuleSignalCard` | US-014 | `GET /api/analysis/{ticker}` |
| `ValuationSummary` | US-011 + US-012 + US-013 (via US-014) | idem |
| `HistoricalChart` | US-015 | `GET /api/historical/{ticker}` |
| `MoatChecklist` | US-016 | `GET/POST /api/moat-checklist/{ticker}` |
| `WatchlistTable` + `AddToWatchlistButton` | US-017 | `GET/POST/DELETE /api/watchlist/**` |
| `LoginForm`, `RegisterForm` | EP-006 | `POST /api/auth/login`, `/register` |
| `StaleDataBadge` | US-005 + US-006 cross-cutting | (legge headers + flag `isStale`) |

## State management (Zustand)

### `useAuthStore`

```ts
type AuthState = {
  accessToken: string | null         // in memoria
  user: UserProfile | null
  login(email, password): Promise<void>
  logout(): void
  refresh(): Promise<void>
}
```

Refresh token in `httpOnly` cookie (vedi [ADR-006](../decisions/ADR-006-authentication.md)); access token in memoria, mai persisitito.

### `useAnalysisStore`

```ts
type AnalysisState = {
  byTicker: Record<string, {
    result: RuleEngineResult | null
    loading: boolean
    error: ProblemDetails | null
    isStale: boolean
  }>
  fetchAnalysis(ticker: string): Promise<void>
}
```

### `useWatchlistStore`

```ts
type WatchlistState = {
  items: WatchlistItem[]
  loading: boolean
  fetchWatchlist(): Promise<void>
  addTicker(ticker: string): Promise<void>
  removeTicker(ticker: string): Promise<void>
}
```

### `useScreenerStore`

```ts
type ScreenerState = {
  filters: ScreenerFilters
  results: SearchResultItem[]
  cursor: string | null
  loading: boolean
  setFilters(f: Partial<ScreenerFilters>): void
  submit(): Promise<void>
  loadMore(): Promise<void>
}
```

## API client

- **Generazione**: `openapi-typescript` o `orval` legge `design_&_architecture/api/openapi.yaml` e genera in `src/frontend/lib/api/generated/` interfacce TS + funzioni client.
- **Wrapper**: `client.ts` espone una singola istanza axios/fetch con:
  - interceptor request: aggiunge `Authorization: Bearer ${accessToken}` se presente.
  - interceptor response 401: tenta `refresh()` una volta, poi forza logout.
  - estrazione headers `X-Data-Snapshot-At` / `X-Data-Stale` in metadata della response.

## Design system

- **Tailwind CSS** + componenti **Radix UI** headless per a11y (dialog, dropdown, tooltip).
- **Codifica colore Traffic Light** (US-014 AC accessibilita'): semaforo + icona + label testuale alternativa.
  - GREEN = `bg-green-500` + icona check + label "OK"
  - YELLOW = `bg-amber-500` + icona warning + label "Attenzione"
  - RED = `bg-red-500` + icona x + label "Non soddisfatta"
  - INDETERMINATE / NOT_CALCULABLE = `bg-slate-400` + icona "?" + label "Indeterminato"
- **Dark mode** opzionale (Tailwind `class` strategy).

## Accessibilita'

- US-014 AC: codifica colore con alternativa testuale/icona -> rispettata via `aria-label` + label visibile sotto al semaforo.
- Keyboard navigation completa via Radix UI.
- Contrasto colore: tutte le combinazioni testano WCAG AA (4.5:1).

## Build e deploy

- **Dev**: `next dev` (porta 3000) + proxy a backend (porta 8080) via `next.config.js`.
- **Prod**: `next build` con `output: 'export'` produce `out/` (static). Serviti dal backend Spring Boot (vedi [ADR-009](../decisions/ADR-009-deployment-target.md), [ADR-013](../decisions/ADR-013-fe-analysis-routing-static-export.md)).
- **Navigazione analisi:** SearchBar / ResultsList / watchlist linkano a `/analysis?ticker={T}` (maiuscolo normalizzato); non usare route dinamiche `[ticker]` con static export.
- **Variables**: `NEXT_PUBLIC_API_BASE_URL` (default `/api`), `NEXT_PUBLIC_BUILD_VERSION`.

## Testing strategy

| Tipo | Tooling | Scope |
|---|---|---|
| Unit | Vitest + React Testing Library | componenti, store Zustand, utils |
| Component | Storybook | UI library isolata |
| E2E | Playwright | flussi US-001 -> US-014, US-017 |
| Contract | TS check su tipi generati da openapi.yaml | drift schema |

## Pagine collegate

- [overview.md](../overview.md)
- [api/openapi.yaml](../api/openapi.yaml)
- [ADR-001](../decisions/ADR-001-frontend-stack.md)
- [ADR-013](../decisions/ADR-013-fe-analysis-routing-static-export.md)
- [[webapp-architecture-vi]]
- [[vi-07-risoluzione-q002-q003]]
