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

## Perimetro auth e routing (ADR-026)

> TL;DR: in **produzione** l'AuthGuard è **client-side** (UX) e l'enforcement
> reale è **sul backend** (security boundary). `middleware.ts` è **dev-only**.

Con `output: 'export'` non esiste un runtime Next.js in produzione: il
bundle statico viene servito dal backend Spring Boot, quindi nessun
middleware Edge/Node FE può intercettare le richieste. ADR-026 formalizza
la separazione fra layer di guard e layer di sicurezza:

| Concern | Produzione | Dev locale (`next dev`) |
|---|---|---|
| Redirect non autenticato → `/login?returnUrl=…` | `ClientAuthGuard` + `useAuthGuard` | Idem, più fallback `middleware.ts` |
| Redirect ruolo insufficiente → `/403` | `ClientAuthGuard` | Idem, più fallback `middleware.ts` |
| Sessione scaduta → logout + login | `useAuthGuard` (`session-expired`) | Idem, più `middleware.ts` |
| Authz reale (security boundary) | Backend (`@PreAuthorize`, sessione, ruoli) | Idem |
| CSP | `SecurityHeadersConfig` lato BE (TSK-221) | `middleware.ts` con nonce per-request (parità minima) |

### Componenti chiave

- `components/auth/ClientAuthGuard.tsx` — wrapper riusabile applicato ai
  layout/pagine protetti (`/analysis`, `/analysis/deep`, `/watchlist`,
  `/top-picks`, `/moat`, `/profile`, `/admin`).
- `hooks/use-auth-guard.ts` — implementa la matrice decisionale (`loading`,
  `allow`, `unauthenticated`, `forbidden`, `session-expired`) e collega il
  redirect al router Next.
- `lib/auth/auth-guard-decision.ts` — funzione pura `evaluateAuthGuard`
  testabile in isolamento (input → decisione).
- `lib/auth/route-config.ts` — mappa dichiarativa unica dei requisiti
  `requiresAuth` / `roles` (TSK-205 / US-074), consumata sia dal guard
  client-side che dal middleware dev.

### `middleware.ts` (dev-only)

`middleware.ts` resta solo come convenienza per `next dev`: con
`output: 'export'` non viene mai incluso nel bundle prodotto in `out/`.
Per evitare che diventi accidentalmente "il" controllo auth di produzione
(es. se in futuro qualcuno rimuove `output: 'export'` senza ridisegnare il
perimetro), TSK-268 ha introdotto un guard-rail esplicito:

```ts
if (process.env.NODE_ENV !== "development") {
  return NextResponse.next();
}
```

Conseguenze operative:

- Build/export statica (`npm run build`) NON dipende da `middleware.ts`.
- I test Vitest (`NODE_ENV=test`) di default vedono solo il pass-through;
  i test dello scenario dev stubbano esplicitamente
  `vi.stubEnv('NODE_ENV', 'development')`.
- In produzione il file non viene caricato; la sua sola esistenza non ha
  effetto runtime.

Riferimenti:
[`design_&_architecture/decisions/ADR-026-frontend-authguard-static-export-runtime.md`](../../design_%26_architecture/decisions/ADR-026-frontend-authguard-static-export-runtime.md)
·
[`management/kanban/EP-017-protezione-rotte-sessione/US-087-authguard-client-side-static-export/`](../../management/kanban/EP-017-protezione-rotte-sessione/US-087-authguard-client-side-static-export/).

### Test E2E AuthGuard (static export)

Valida `ClientAuthGuard` sul bundle `out/` **senza** `next dev` né middleware:

```bash
cd src/frontend
npm run build
npm run test:e2e:static
```

Config dedicata: `playwright.config.static.ts` (spec `e2e/auth-guard-static-export.spec.ts`).
In CI: job `fe-e2e-static` in `.github/workflows/ci.yml`.

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
