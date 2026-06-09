'use client';

import { useSearchParams } from 'next/navigation';
import { SummaryPageClient } from '@/components/summary';

/**
 * SummaryRouteClient — TSK-342 (US-104, EP-024 Fase 2).
 *
 * Client island per la rotta alias `/analysis/summary?ticker=…`. Equivalente
 * funzionalmente al landing `/analysis?ticker=…` (`AnalysisRouteClient`).
 * Centralizziamo la logica di rendering in `SummaryPageClient` — entrambe
 * le rotte (alias + landing) la consumano.
 */
export function SummaryRouteClient(): React.ReactElement {
  const params = useSearchParams();
  const ticker = (params?.get('ticker') ?? '').trim().toUpperCase();

  if (!ticker) {
    return (
      <main className="mx-auto max-w-3xl px-6 py-12 text-center">
        <h1 className="sr-only">Riepilogo</h1>
        <p className="text-sm text-on-surface/60">
          Specifica un ticker (es. <code>/analysis/summary?ticker=AAPL</code>).
        </p>
      </main>
    );
  }

  return <SummaryPageClient ticker={ticker} />;
}
