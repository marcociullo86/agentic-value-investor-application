import { Suspense } from 'react';
import { ClientAuthGuard } from '@/components/auth/ClientAuthGuard';
import { AnalysisRouteClient } from './AnalysisRouteClient';

/**
 * Pagina `/analysis?ticker=AAPL` — TSK-055 (US-023, ADR-013) +
 * TSK-267 (US-087, ADR-026): rotta ora protetta dal `ClientAuthGuard`
 * client-side (static-export-compatible). Senza sessione l'utente
 * viene rediretto a `/login?returnUrl=/analysis?ticker=...`.
 *
 * Iter-2 boundary fix: il file `page.tsx` resta Server Component (Next 16
 * RSC); il consumo di `useSearchParams` è isolato in `AnalysisRouteClient`,
 * stesso pattern usato in `app/top-picks/page.tsx` e `app/admin/page.tsx`.
 *
 * Static export (`output: 'export'`) non supporta segmenti dinamici senza
 * whitelist `generateStaticParams`. Il ticker arriva via query string,
 * allineato a `/moat?ticker=`.
 *
 * Riferimento design:
 *   design_&_architecture/decisions/ADR-013-fe-analysis-routing-static-export.md
 *   design_&_architecture/decisions/ADR-026-frontend-authguard-static-export-runtime.md
 * REST invariato: GET /api/analysis/{ticker}.
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
