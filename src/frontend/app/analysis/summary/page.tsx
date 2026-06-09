import { Suspense } from 'react';
import { ClientAuthGuard } from '@/components/auth/ClientAuthGuard';
import { SummaryRouteClient } from './SummaryRouteClient';

/**
 * Riepilogo page — TSK-342 (US-104, EP-024 Fase 2).
 *
 * Alias esplicito del tab "Riepilogo". Equivalente al landing
 * `/analysis?ticker=…` (anch'esso renderizza il Riepilogo), ma esposto come
 * rotta esplicita per:
 *  - deep-link nomenclatura coerente: tutti gli altri tab hanno una rotta
 *    `/analysis/{tab}` (deep, technical, base), il Riepilogo non poteva fare
 *    eccezione.
 *  - chiarezza in test / link sharing: l'utente che riceve un URL
 *    `/analysis/summary?ticker=AAPL` capisce immediatamente che vede il
 *    Riepilogo, mentre `/analysis?ticker=AAPL` potrebbe essere interpretato
 *    come "Analisi Base" da utenti legacy.
 *
 * Pattern boundary identico a `app/analysis/page.tsx`:
 *  - `page.tsx` resta Server Component (Next 16 RSC).
 *  - `'use client'` vive solo nel `SummaryRouteClient`.
 *  - Rotta protetta da `ClientAuthGuard`: senza sessione →
 *    `/login?returnUrl=/analysis/summary?ticker=...`.
 *
 * Route: /analysis/summary?ticker=AAPL (query param, no dynamic segment) —
 * aligned with ADR-013 static export constraint.
 *
 * [^src: design_&_architecture/decisions/ADR-013-fe-analysis-routing-static-export.md]
 * [^src: design_&_architecture/decisions/ADR-026-frontend-authguard-static-export-runtime.md]
 * [^src: design_&_architecture/api/openapi.yaml §/api/analysis/{ticker}/summary]
 */
export default function SummaryPage(): React.ReactElement {
  return (
    <ClientAuthGuard fallback={<SummaryPlaceholder />}>
      <Suspense fallback={<SummaryPlaceholder />}>
        <SummaryRouteClient />
      </Suspense>
    </ClientAuthGuard>
  );
}

function SummaryPlaceholder(): React.ReactElement {
  return (
    <main className="mx-auto max-w-3xl px-6 py-12 text-center text-sm text-on-surface/60">
      Caricamento…
    </main>
  );
}
