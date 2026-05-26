import { Suspense } from 'react';
import { TopPicksPageClient } from './TopPicksPageClient';

/**
 * Top Picks page — TSK-140 (US-051, EP-012).
 *
 * Route: /top-picks
 *
 * Server Component (Next 16 RSC) che wrappa il Client Component
 * `TopPicksPageClient` in `<Suspense>` (richiesto da `useSearchParams`,
 * stesso pattern usato in `app/analysis/deep/page.tsx`).
 *
 * Endpoint pubblico (no auth): chi entra senza login vede comunque la
 * classifica giornaliera. Niente `AuthGuard`.
 *
 * [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/api/TopPicksController.kt]
 * [^src: management/kanban/EP-012-batch-top-value-picks/US-051-frontend-top-picks/TSK-140.md]
 */

export default function TopPicksPage(): React.ReactElement {
  return (
    <Suspense fallback={<TopPicksPlaceholder />}>
      <TopPicksPageClient />
    </Suspense>
  );
}

function TopPicksPlaceholder(): React.ReactElement {
  return (
    <main className="mx-auto max-w-3xl px-6 py-12 text-center text-sm text-slate-500">
      Caricamento classifica…
    </main>
  );
}
