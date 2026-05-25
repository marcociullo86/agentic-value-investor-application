---
id: ADR-001-v2
title: Frontend stack versions 2026 — React 19 + Next.js 16.x
status: accepted
created: 2026-05-25
accepted: 2026-05-25
deciders: [lead-architect, marco.ciullo]
supersedes: [ADR-001]
supersedes_scope: "Solo sezione 'Decisione' di ADR-001 limitatamente alle versioni di React, Next.js e dipendenze peer FE. ADR-001 resta accepted come contesto storico R1.0 e per le motivazioni di ecosistema/data-grid/charting."
pending_clarification: []
---
# ADR-001-v2 — Frontend stack versions 2026 (React 19 + Next.js 16.x)

## Contesto

`raw/tech_stack.md` (approvato 2026-05-20 da marco.ciullo, status `approved`) dichiara come stack frontend canonico **React 19 + Next.js 16.x (App Router + RSC stabile + Turbopack default)**. ADR-001 originale (2026-05-20) documenta versioni inferiori (React 18, Next.js 14+) coerenti con il momento di stesura R1.0 ma successivamente superate dallo stack canonico.

Gap `arch-adr-version-sync` (`wiki/gaps.md`) richiede l'allineamento formale L4 → L5 senza edit-in-place sugli ADR `accepted` (PATTERN.md §7 r.7: ADR accepted immutabile, modifiche solo via appendice o ADR successore). Questo ADR-v2 è l'**appendice non-distruttiva** che formalizza le versioni 2026.

[^src: raw/tech_stack.md §Frontend, §Follow-up (gap aperti per Arch)]
[^src: wiki/gaps.md §arch-adr-version-sync]

## Decisione

Lo stack FE è allineato alle versioni di `raw/tech_stack.md`:

| Componente | Versione canonica 2026 | Note |
|---|---|---|
| **React** | **19.x** (current) | Vincolo verbatim `raw/tech_stack.md` §Frontend |
| **Next.js** | **16.x** | App Router, React Server Components stabile, Turbopack default |
| **TypeScript** | current (allineato a Next.js 16) | Versione corrente supportata da Next.js 16 toolchain |
| **State management** | Zustand (invariato) | Conferma ADR-001 |
| **Styling** | Tailwind CSS + Radix UI primitives | Conferma ADR-001 + esplicitazione Radix UI |
| **Charting** | Recharts (invariato) | Conferma ADR-001 |
| **Forms** | React Hook Form + Zod | Esplicitato da `raw/tech_stack.md` |
| **HTTP client** | fetch (Server) + SWR (Client) | Esplicitato da `raw/tech_stack.md` |

[^src: raw/tech_stack.md §Frontend]

Tutte le altre decisioni di ADR-001 (routing App Router, build artifact, ecosistema data-grid, motivazioni) **restano valide e immutate**.

## Conseguenze

- I dev-agent (fe-dev) implementano e mantengono il codice secondo queste versioni (PATTERN §7 r.10: `raw/tech_stack.md` prevale per i dev-agent).
- ADR-001 originale rimane `accepted` come contesto storico R1.0. La sua sezione "Decisione" è superata limitatamente alle versioni di React/Next.js da questo ADR-v2.
- Sicurezza: mantenere Next.js sempre patchato (CVE-2025-55184 DoS, CVE-2025-66478 RCE su RSC, Dec 2025) come prescritto in `raw/tech_stack.md`.
- Gap `arch-adr-version-sync` (sezione FE) si considera risolto a L4; chiusura formale a cura di `wiki-keeper`.
- Eventuali ADR futuri che modifichino versioni FE devono superseding questo ADR-v2 (non ADR-001 originale).

## Pagine collegate

- [ADR-001](ADR-001-frontend-stack.md) (contesto storico R1.0)
- [ADR-013](ADR-013-fe-analysis-routing-static-export.md)
- `raw/tech_stack.md` §Frontend
- `wiki/gaps.md` §arch-adr-version-sync
