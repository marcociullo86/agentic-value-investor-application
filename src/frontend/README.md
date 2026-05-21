# Frontend — Value Investing WebApp

SPA in **Next.js 16 (App Router) + React 19**, esportata come bundle statico
(`output: 'export'`) e servita in produzione dal backend Spring Boot
(vedi `design_&_architecture/decisions/ADR-009-deployment-target.md`).

## Stack

- Next.js 16.x + React 19 (Turbopack, RSC stabile)
- State: Zustand 4.5
- UI: Tailwind CSS 3.4 + Radix UI primitives
- Forms: React Hook Form + Zod
- Charting: Recharts (TSK-024)
- Grid: AG Grid Community (TSK-007)
- HTTP: axios (+ SWR opzionale)
- Tipi API: `openapi-typescript` (consuma `design_&_architecture/api/openapi.yaml`)
- Test: Vitest + React Testing Library

Versioni canoniche in `raw/tech_stack.md` (priorità assoluta, PATTERN §7 r.10).

## Quick start

```bash
cd src/frontend
npm install
npm run generate:api   # genera lib/api/generated/schema.ts (gitignored)
npm run dev            # http://localhost:3000
```

Backend richiesto su `http://localhost:8080` (vedi `src/docker/docker-compose.yml`).
CORS è già configurato lato BE (TSK-031 `CorsConfig`), quindi il frontend chiama
direttamente l'origin del backend tramite `NEXT_PUBLIC_API_BASE_URL` — niente
proxy Next runtime (incompatibile con `output: 'export'`).

## Build statico

```bash
npm run build   # produce out/ (static export)
```

Il bundle `out/` viene servito dal backend (vedi ADR-009 `§Dev locale` /
`§Serving`).

## Env vars

| Variabile | Default | Note |
|---|---|---|
| `NEXT_PUBLIC_API_BASE_URL` | `http://localhost:8080` | Origin del backend |
| `NEXT_PUBLIC_BUILD_VERSION` | `0.1.0-dev` | Mostrato in footer landing |

Copia `.env.example` → `.env.local` per personalizzare.

## Struttura

```
src/frontend/
├── app/                    # App Router (layout, page, /search, /screener, ...)
├── components/
│   ├── ui/                 # Button, Input, Card, Modal, Toast (Tailwind + Radix)
│   └── providers/          # AuthProvider, ToastProvider
├── lib/
│   ├── api/
│   │   ├── client.ts       # axios + interceptor auth + X-Data-Stale wrapper
│   │   └── generated/      # schema.ts (gitignored, prodotto da generate:api)
│   ├── stores/             # Zustand stores (auth, watchlist, ...)
│   └── utils/              # formatters, signal-color, cn
├── next.config.js          # static export + env passthrough
├── tailwind.config.ts      # design tokens (colors.signal.*)
└── package.json
```

## TSK successivi

- **TSK-003** SearchBar / ResultsList (US-001)
- **TSK-021** TrafficLightPanel (US-014)
- **TSK-024** HistoricalChart (US-015)
- **TSK-027** MoatChecklist (US-016)
- **TSK-007** WatchlistTable (US-017)
- **TSK-034** Login / Register / auth completa (EP-006)
- **TSK-032** Dockerfile FE (multi-stage build + serve `out/` o serve via BE)
