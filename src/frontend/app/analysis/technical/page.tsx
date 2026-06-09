import { Suspense } from 'react';
import { ClientAuthGuard } from '@/components/auth/ClientAuthGuard';
import { TechnicalAnalysisPageClient } from './TechnicalAnalysisPageClient';

/**
 * Technical Analysis page — TSK-334 (US-101, EP-024 Fase 1).
 *
 * Terzo tab del dettaglio ticker (ordine Fase 1:
 *   `Analisi Base | Deep Analysis | Technical Analysis`).
 * Il tab "Riepilogo" diventerà primo solo dopo US-104 (Fase 2).
 *
 * Pattern boundary identico a `app/analysis/deep/page.tsx`:
 *  - `page.tsx` resta Server Component (Next 16 RSC).
 *  - `'use client'` vive solo nel `TechnicalAnalysisPageClient` (hook lazy
 *    SWR `useTechnicalAnalysis` + useSearchParams).
 *  - Rotta protetta da `ClientAuthGuard`: senza sessione →
 *    `/login?returnUrl=/analysis/technical?ticker=...`.
 *  - `Suspense` boundary attorno all'island client per static export.
 *
 * Route: /analysis/technical?ticker=AAPL (query param, no dynamic segment).
 * Aligned with ADR-013: static export constraint → query params per
 * ticker resolution (stesso pattern di /analysis?ticker= e
 * /analysis/deep?ticker=).
 *
 * NOTA lazy load (AC US-101 §Comportamento): l'hook
 * `useTechnicalAnalysis(..., { enabled: true })` parte SOLO dentro questa
 * rotta. Aprire `/analysis?ticker=…` (tab Analisi Base) NON innesca alcuna
 * chiamata a `/api/analysis/{ticker}/technical`.
 *
 * [^src: design_&_architecture/decisions/ADR-013-fe-analysis-routing-static-export.md]
 * [^src: design_&_architecture/decisions/ADR-026-frontend-authguard-static-export-runtime.md]
 * [^src: design_&_architecture/api/openapi.yaml §/api/analysis/{ticker}/technical]
 */

export default function TechnicalAnalysisPage(): React.ReactElement {
  return (
    <ClientAuthGuard fallback={<TechnicalAnalysisPlaceholder />}>
      <Suspense fallback={<TechnicalAnalysisPlaceholder />}>
        <TechnicalAnalysisPageClient />
      </Suspense>
    </ClientAuthGuard>
  );
}

function TechnicalAnalysisPlaceholder(): React.ReactElement {
  return (
    <main className="mx-auto max-w-3xl px-6 py-12 text-center text-sm text-on-surface/60">
      Caricamento…
    </main>
  );
}
