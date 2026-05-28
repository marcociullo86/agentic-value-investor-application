import { Suspense } from 'react';
import { ClientAuthGuard } from '@/components/auth/ClientAuthGuard';
import { DeepAnalysisPageClient } from './DeepAnalysisPageClient';

/**
 * Deep Analysis page — TSK-122 + TSK-123 (US-046, EP-011) +
 * TSK-267 (US-087, ADR-026): rotta protetta dal `ClientAuthGuard`
 * client-side. Senza sessione → `/login?returnUrl=/analysis/deep?ticker=...`.
 *
 * Iter-2 boundary fix: il file `page.tsx` resta Server Component (Next 16
 * RSC); il `'use client'` vive solo nel `DeepAnalysisPageClient` che usa
 * `useSearchParams`/hooks SWR. Stesso pattern di `app/top-picks/page.tsx`
 * e `app/admin/page.tsx`.
 *
 * Route: /analysis/deep?ticker=AAPL (query param, no dynamic segment).
 * Aligned with ADR-013: static export constraint → query params for
 * ticker resolution, same pattern as /analysis?ticker=.
 *
 * [^src: design_&_architecture/decisions/ADR-013-fe-analysis-routing-static-export.md]
 * [^src: design_&_architecture/decisions/ADR-026-frontend-authguard-static-export-runtime.md]
 * [^src: design_&_architecture/api/openapi.yaml §/api/analysis/{ticker}/deep]
 */

export default function DeepAnalysisPage(): React.ReactElement {
  return (
    <ClientAuthGuard fallback={<DeepAnalysisPlaceholder />}>
      <Suspense fallback={<DeepAnalysisPlaceholder />}>
        <DeepAnalysisPageClient />
      </Suspense>
    </ClientAuthGuard>
  );
}

function DeepAnalysisPlaceholder(): React.ReactElement {
  return (
    <main className="mx-auto max-w-3xl px-6 py-12 text-center text-sm text-on-surface/60">
      Caricamento…
    </main>
  );
}
