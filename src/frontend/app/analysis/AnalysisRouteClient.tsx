'use client';

import { useSearchParams } from 'next/navigation';
import { AnalysisPageClient } from '@/components/analysis/AnalysisPageClient';

/**
 * AnalysisRouteClient — TSK-267 iter-2 (US-087, ADR-026).
 *
 * Wrapper client-side per la rotta `/analysis?ticker=…`. Isola l'uso di
 * `useSearchParams` (Client-only hook) in modo che `app/analysis/page.tsx`
 * possa rimanere un Server Component (Next 16 RSC), allineato allo stesso
 * boundary pattern già adottato da `app/top-picks/page.tsx` +
 * `TopPicksPageClient.tsx` e da `app/admin/page.tsx` +
 * `LlmBudgetAdminPanel`.
 *
 * Senza ticker mostra il placeholder canonico; con ticker valido delega
 * il rendering al `<AnalysisPageClient />` esistente.
 *
 * Riferimento:
 *   design_&_architecture/decisions/ADR-013-fe-analysis-routing-static-export.md
 *   design_&_architecture/decisions/ADR-026-frontend-authguard-static-export-runtime.md
 */
export function AnalysisRouteClient(): React.ReactElement {
  const params = useSearchParams();
  const ticker = (params?.get('ticker') ?? '').trim().toUpperCase();

  if (!ticker) {
    return (
      <main className="mx-auto max-w-3xl px-6 py-12 text-center">
        <h1 className="sr-only">Analisi</h1>
        <p className="text-sm text-on-surface/60">
          Specifica un ticker (es. <code>/analysis?ticker=AAPL</code>).
        </p>
      </main>
    );
  }

  return <AnalysisPageClient ticker={ticker} />;
}
