import { Suspense } from 'react';
import { ClientAuthGuard } from '@/components/auth/ClientAuthGuard';
import { TopPicksPageClient } from './TopPicksPageClient';

/**
 * Top Picks page — TSK-140 (US-051, EP-012) +
 * TSK-267 (US-087, ADR-026): la rotta è ora protetta, allineata alla
 * matrice US-073 / route-map dichiarativa (`requiresAuth: true`).
 *
 * Route: /top-picks (+ filtri via query string)
 *
 * Server Component (Next 16 RSC) che wrappa il Client Component
 * `TopPicksPageClient` in `<Suspense>` (richiesto da `useSearchParams`,
 * stesso pattern usato in `app/analysis/deep/page.tsx`) e in
 * `ClientAuthGuard` (Client Component) per applicare il guard
 * client-side compatibile con `output: 'export'`.
 *
 * [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/api/TopPicksController.kt]
 * [^src: management/kanban/EP-017-protezione-rotte-sessione/US-087-authguard-client-side-static-export/TSK-267.md]
 * [^src: design_&_architecture/decisions/ADR-026-frontend-authguard-static-export-runtime.md]
 */

export default function TopPicksPage(): React.ReactElement {
  return (
    <ClientAuthGuard fallback={<TopPicksPlaceholder />}>
      <Suspense fallback={<TopPicksPlaceholder />}>
        <TopPicksPageClient />
      </Suspense>
    </ClientAuthGuard>
  );
}

function TopPicksPlaceholder(): React.ReactElement {
  return (
    <main className="mx-auto max-w-3xl px-6 py-12 text-center text-sm text-on-surface/60">
      Caricamento classifica…
    </main>
  );
}
