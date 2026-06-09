'use client';

import { useSearchParams } from 'next/navigation';
import { SummaryPageClient } from '@/components/summary';

/**
 * AnalysisRouteClient — TSK-342 (US-104, EP-024 Fase 2).
 *
 * Wrapper client-side per la rotta `/analysis?ticker=…` (landing del
 * dettaglio ticker). Dopo EP-024 Fase 2 la rotta canonica `/analysis`
 * mostra il **tab "Riepilogo"** come default — primo tab + attivo al
 * landing (US-104 §"Posizionamento del tab" + §AC).
 *
 * L'Analisi Base "vintage" (Rule Engine + DCF + MoS + Historical) si è
 * spostata sulla nuova rotta esplicita `/analysis/base?ticker=…`
 * (`app/analysis/base/page.tsx` — vedi `analysisBaseUrl()`).
 *
 * Deep-link compatibility (AC US-104):
 *  - `/analysis?ticker=AAPL`            → tab Riepilogo (qui)
 *  - `/analysis/summary?ticker=AAPL`    → tab Riepilogo (alias esplicito)
 *  - `/analysis/base?ticker=AAPL`       → tab Analisi Base (rotta nuova)
 *  - `/analysis/deep?ticker=AAPL`       → tab Deep Analysis (invariato)
 *  - `/analysis/technical?ticker=AAPL`  → tab Technical Analysis (invariato)
 *
 * Pattern boundary client/server invariato (Next 16 RSC):
 *  - `app/analysis/page.tsx` resta Server Component (ClientAuthGuard +
 *    Suspense).
 *  - `'use client'` vive qui, consumando `useSearchParams()`.
 *
 * Riferimento:
 *   design_&_architecture/decisions/ADR-013-fe-analysis-routing-static-export.md
 *   design_&_architecture/decisions/ADR-026-frontend-authguard-static-export-runtime.md
 *   design_&_architecture/decisions/ADR-030-...  (EP-024 Fase 2)
 */
export function AnalysisRouteClient(): React.ReactElement {
  const params = useSearchParams();
  const ticker = (params?.get('ticker') ?? '').trim().toUpperCase();

  if (!ticker) {
    return (
      <main className="mx-auto max-w-3xl px-6 py-12 text-center">
        <h1 className="sr-only">Analisi — Riepilogo</h1>
        <p className="text-sm text-on-surface/60">
          Specifica un ticker (es. <code>/analysis?ticker=AAPL</code>).
        </p>
      </main>
    );
  }

  return <SummaryPageClient ticker={ticker} />;
}
