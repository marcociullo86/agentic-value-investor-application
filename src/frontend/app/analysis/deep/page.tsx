'use client';

import { Suspense } from 'react';
import { DeepAnalysisPageClient } from './DeepAnalysisPageClient';

/**
 * Deep Analysis page — TSK-122 + TSK-123 (US-046, EP-011).
 *
 * Route: /analysis/deep?ticker=AAPL (query param, no dynamic segment).
 * Aligned with ADR-013: static export constraint → query params for
 * ticker resolution, same pattern as /analysis?ticker=.
 *
 * [^src: design_&_architecture/decisions/ADR-013-fe-analysis-routing-static-export.md]
 * [^src: design_&_architecture/api/openapi.yaml §/api/analysis/{ticker}/deep]
 */

export default function DeepAnalysisPage(): React.ReactElement {
  return (
    <Suspense fallback={<DeepAnalysisPlaceholder />}>
      <DeepAnalysisPageClient />
    </Suspense>
  );
}

function DeepAnalysisPlaceholder(): React.ReactElement {
  return (
    <main className="mx-auto max-w-3xl px-6 py-12 text-center text-sm text-slate-500">
      Caricamento…
    </main>
  );
}
