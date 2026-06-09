import { Suspense } from 'react';
import { ClientAuthGuard } from '@/components/auth/ClientAuthGuard';
import { AnalysisBaseRouteClient } from './AnalysisBaseRouteClient';

/**
 * Analisi Base page — TSK-342 (US-104, EP-024 Fase 2).
 *
 * Rotta esplicita per il tab "Analisi Base" (Rule Engine + DCF + MoS +
 * Historical). Prima di EP-024 Fase 2 il tab Analisi Base viveva
 * direttamente sulla landing `/analysis?ticker=…`; con l'introduzione del
 * Riepilogo come primo tab + default, l'Analisi Base si è spostata qui per
 * mantenere il deep-link compat:
 *
 *   /analysis?ticker=…           → Riepilogo (US-104, default)
 *   /analysis/base?ticker=…      → Analisi Base (qui)
 *   /analysis/deep?ticker=…      → Deep Analysis (invariato)
 *   /analysis/technical?ticker=… → Technical Analysis (invariato)
 *
 * Pattern boundary identico a `app/analysis/deep/page.tsx` +
 * `app/analysis/technical/page.tsx`:
 *  - `page.tsx` resta Server Component (Next 16 RSC).
 *  - `'use client'` vive solo nel `AnalysisBaseRouteClient`.
 *  - Rotta protetta da `ClientAuthGuard`: senza sessione →
 *    `/login?returnUrl=/analysis/base?ticker=...`.
 *  - `Suspense` boundary attorno all'island client per static export.
 *
 * Route: /analysis/base?ticker=AAPL (query param, no dynamic segment) —
 * aligned with ADR-013 static export constraint.
 *
 * [^src: design_&_architecture/decisions/ADR-013-fe-analysis-routing-static-export.md]
 * [^src: design_&_architecture/decisions/ADR-026-frontend-authguard-static-export-runtime.md]
 * [^src: management/kanban/EP-024-...US-104 §"Posizionamento del tab"]
 */
export default function AnalysisBasePage(): React.ReactElement {
  return (
    <ClientAuthGuard fallback={<AnalysisBasePlaceholder />}>
      <Suspense fallback={<AnalysisBasePlaceholder />}>
        <AnalysisBaseRouteClient />
      </Suspense>
    </ClientAuthGuard>
  );
}

function AnalysisBasePlaceholder(): React.ReactElement {
  return (
    <main className="mx-auto max-w-3xl px-6 py-12 text-center text-sm text-on-surface/60">
      Caricamento…
    </main>
  );
}
