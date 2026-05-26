'use client';

import { Suspense } from 'react';
import { useSearchParams } from 'next/navigation';
import { AnalysisPageClient } from '@/components/analysis/AnalysisPageClient';

/**
 * Pagina `/analysis?ticker=AAPL` — TSK-055 (US-023, ADR-013).
 *
 * Static export (`output: 'export'`) non supporta segmenti dinamici senza
 * whitelist `generateStaticParams`. Il ticker arriva via query string,
 * allineato a `/moat?ticker=`.
 *
 * Riferimento design:
 *   design_&_architecture/decisions/ADR-013-fe-analysis-routing-static-export.md
 * REST invariato: GET /api/analysis/{ticker}.
 */
export default function AnalysisPage(): React.ReactElement {
  return (
    <Suspense fallback={<AnalysisPlaceholder />}>
      <AnalysisPageInner />
    </Suspense>
  );
}

function AnalysisPageInner(): React.ReactElement {
  const params = useSearchParams();
  const ticker = (params?.get('ticker') ?? '').trim().toUpperCase();

  if (!ticker) {
    return (
      <main className="mx-auto max-w-3xl px-6 py-12 text-center">
        <h1 className="sr-only">Analisi</h1>
        <p className="text-sm text-slate-500">
          Specifica un ticker (es. <code>/analysis?ticker=AAPL</code>).
        </p>
      </main>
    );
  }

  return <AnalysisPageClient ticker={ticker} />;
}

function AnalysisPlaceholder(): React.ReactElement {
  return (
    <main className="mx-auto max-w-3xl px-6 py-12 text-center">
      <h1 className="sr-only">Analisi</h1>
      <p className="text-sm text-slate-500">Caricamento…</p>
    </main>
  );
}
