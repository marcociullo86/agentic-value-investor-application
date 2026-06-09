import { Suspense } from 'react';
import { ClientAuthGuard } from '@/components/auth/ClientAuthGuard';
import { AnalysisRouteClient } from './AnalysisRouteClient';

/**
 * Pagina `/analysis?ticker=AAPL` — landing del dettaglio ticker.
 *
 * Storia delle versioni:
 *  - TSK-055 (US-023, ADR-013): introduzione static-export con query param.
 *  - TSK-267 (US-087, ADR-026): protezione `ClientAuthGuard` client-side.
 *  - TSK-267 iter-2: boundary RSC — `'use client'` solo in
 *    `AnalysisRouteClient`, stesso pattern di `app/top-picks/page.tsx`
 *    e `app/admin/page.tsx`.
 *  - TSK-342 (US-104, EP-024 Fase 2): **il landing è ora il tab "Riepilogo"**
 *    (primo tab + default attivo). L'Analisi Base "vintage" è spostata su
 *    `/analysis/base?ticker=…`. I deep-link `/analysis/deep` e
 *    `/analysis/technical` restano invariati.
 *
 * Static export (`output: 'export'`) non supporta segmenti dinamici senza
 * whitelist `generateStaticParams`. Il ticker arriva via query string,
 * allineato a `/moat?ticker=` e alle altre rotte tab del dettaglio.
 *
 * Riferimento design:
 *   design_&_architecture/decisions/ADR-013-fe-analysis-routing-static-export.md
 *   design_&_architecture/decisions/ADR-026-frontend-authguard-static-export-runtime.md
 *   design_&_architecture/decisions/ADR-030 (EP-024 Fase 2)
 * REST: GET /api/analysis/{ticker}/summary (US-103).
 */
export default function AnalysisPage(): React.ReactElement {
  return (
    <ClientAuthGuard fallback={<AnalysisPlaceholder />}>
      <Suspense fallback={<AnalysisPlaceholder />}>
        <AnalysisRouteClient />
      </Suspense>
    </ClientAuthGuard>
  );
}

function AnalysisPlaceholder(): React.ReactElement {
  return (
    <main className="mx-auto max-w-3xl px-6 py-12 text-center">
      <h1 className="sr-only">Analisi</h1>
      <p className="text-sm text-on-surface/60">Caricamento…</p>
    </main>
  );
}
