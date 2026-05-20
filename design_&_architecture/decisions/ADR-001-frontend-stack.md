---
id: ADR-001
title: Stack frontend — React 18 + Next.js (SPA/SSG)
status: accepted
created: 2026-05-20
deciders: [lead-architect, marco.ciullo]
---
# ADR-001 — Stack frontend: React 18 + Next.js (SPA/SSG)

## Contesto

La FSD lascia aperta la scelta del framework SPA fra React, Vue.js e Angular [^src: wiki/concepts/webapp-architecture-vi.md §Livello 1: Frontend (Client)]. La risoluzione formale e' stata effettuata a monte (Q_002, risolta il 2026-05-20) e documentata in [[vi-07-risoluzione-q002-q003]] §ADR Q_002. Questo ADR formalizza la decisione nel layer architetturale L4.

## Decisione

Il frontend e' implementato come **Single Page Application** con **React 18** orchestrato da **Next.js 14+** in modalita' SPA/SSG (no SSR full-stack: l'API e' un servizio Kotlin separato).

- **State management**: **Zustand** come default (lightweight, store modulare). Alternativa Redux Toolkit ammessa per moduli complessi (es. screener) — decisione delegabile al team FE.
- **Charts**: **Recharts** per grafici storici (US-015).
- **Data grid**: **Ag-Grid Community** per lista risultati screener (US-003) e tabelle bilanci.
- **Styling**: **TailwindCSS** + componenti headless (Radix UI o equivalente) per il design system base.
- **Routing**: Next.js App Router (file-based).
- **Build artifact**: static export (`next export`) servito dal backend o da CDN.

## Motivazioni

1. **Ecosistema data-grid e charting**: l'interfaccia richiede tabelle decennali e grafici complessi; React offre la libreria piu' ampia di componenti pronti [^src: wiki/sources/vi-07-risoluzione-q002-q003.md §ADR Q_002: Scelta Framework Frontend].
2. **Componentizzazione modulare**: filosofia component-based allineata alla scomposizione modulare delle metriche finanziarie (un componente per ROE, uno per DCF, uno per MoS).
3. **Community e longevita'**: ecosistema mainstream con supporto Meta — riduce rischio "moat tecnologico".
4. **Next.js SPA/SSG**: bundle ottimizzato, file-based routing, supporto a static export che semplifica il deploy in container monolitico (vedi [ADR-009](ADR-009-deployment-target.md)).

## Alternative considerate

- **Vue.js 3**: ecosistema piu' piccolo per data-grid finanziari complessi.
- **Angular**: framework opinionato; learning curve piu' alta per un team che valorizza incremento iterativo.
- **SvelteKit**: maturita' ecosistemica inferiore per librerie data-grid enterprise.

## Conseguenze

- US-014, US-015, US-016 ora pienamente implementabili (gap `vi-webapp-spa-framework-decision` chiuso).
- Necessario configurare CI per build Next.js + deploy artefatto statico.
- Schema OpenAPI ([ADR-007](ADR-007-api-contract.md)) usato per generare client TypeScript tipizzato (es. via `openapi-typescript` o `orval`).

## Pagine collegate

- [[vi-07-risoluzione-q002-q003]]
- [[webapp-architecture-vi]]
- [overview.md](../overview.md)
- [components/frontend-components.md](../components/frontend-components.md)
