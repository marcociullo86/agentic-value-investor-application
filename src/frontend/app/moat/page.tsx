'use client';

import { Suspense } from 'react';
import { useSearchParams } from 'next/navigation';
import { AuthGuard } from '@/components/auth/AuthGuard';
import { MoatChecklist } from '@/components/moat/MoatChecklist';

/**
 * Standalone Moat checklist page (TSK-027) — reachable as `/moat?ticker=AAPL`.
 *
 * Design (frontend-components.md §Routing) places the Moat checklist
 * underneath the analysis dashboard at `/analysis/[ticker]`. That page is
 * owned by Track A (TSK-021). To keep Track B independent and to honor the
 * `output: 'export'` constraint (no dynamic route segments without
 * `generateStaticParams`), the component is also reachable here via a query
 * param. Track A can later `import { MoatChecklist }` from
 * `@/components/moat/MoatChecklist` and embed it in the analysis page.
 */
export default function MoatPage(): React.ReactElement {
  return (
    <AuthGuard
      fallback={
        <main className="mx-auto max-w-3xl px-6 py-12 text-center text-sm text-slate-500">
          Reindirizzamento al login…
        </main>
      }
    >
      <Suspense fallback={<MoatPlaceholder />}>
        <MoatPageInner />
      </Suspense>
    </AuthGuard>
  );
}

function MoatPageInner(): React.ReactElement {
  const params = useSearchParams();
  const ticker = (params?.get('ticker') ?? '').toUpperCase();

  if (!ticker) {
    return (
      <main className="mx-auto max-w-3xl px-6 py-12 text-center text-sm text-slate-500">
        Specifica un ticker (es. <code>/moat?ticker=AAPL</code>).
      </main>
    );
  }

  return (
    <main className="mx-auto max-w-5xl px-6 py-10">
      <h1 className="mb-6 text-3xl font-bold">Moat — {ticker}</h1>
      <MoatChecklist ticker={ticker} />
    </main>
  );
}

function MoatPlaceholder(): React.ReactElement {
  return (
    <main className="mx-auto max-w-3xl px-6 py-12 text-center text-sm text-slate-500">
      Caricamento…
    </main>
  );
}
