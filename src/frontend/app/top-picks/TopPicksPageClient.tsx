'use client';

import { useMemo } from 'react';
import { useSearchParams } from 'next/navigation';
import { useTopPicks } from '@/lib/hooks/useTopPicks';
import { TopPicksHeader } from '@/components/top-picks/TopPicksHeader';
import { TopPicksFilters } from '@/components/top-picks/TopPicksFilters';
import { TopPicksTable } from '@/components/top-picks/TopPicksTable';
import { TopPicksPagination } from '@/components/top-picks/TopPicksPagination';

/**
 * TopPicksPageClient — orchestratore stato URL params + tabella (TSK-140).
 *
 * Pattern:
 *  - URL `searchParams` = single source of truth per filtri + page + date.
 *  - Hook `useTopPicks` chiama BE con i param normalizzati.
 *  - Componenti figli scrivono nei searchParams via `router.replace`.
 *
 * Stati renderizzati:
 *  - Loading: skeleton (5 righe placeholder).
 *  - Error 400: banner "Data non valida".
 *  - Error 503: banner "Servizio non disponibile".
 *  - Empty (`total=0`): messaggio + suggerimento cambio data.
 *  - Success: header + filters + table + pagination.
 */

const DEFAULT_PAGE_SIZE = 30;

export function TopPicksPageClient(): React.ReactElement {
  const params = useSearchParams();

  const queryParams = useMemo(() => {
    const dateRaw = params?.get('date') ?? undefined;
    const verdictRaw = params?.get('verdict') ?? undefined;
    const sectorRaw = params?.get('sector') ?? undefined;
    const minMosRaw = params?.get('min_mos');
    const pageRaw = params?.get('page');
    const minMos =
      minMosRaw != null && minMosRaw !== '' && Number.isFinite(Number(minMosRaw))
        ? Number(minMosRaw)
        : undefined;
    const page =
      pageRaw != null && pageRaw !== '' && Number.isFinite(Number(pageRaw))
        ? Number(pageRaw)
        : 0;
    return {
      date: dateRaw,
      verdict: verdictRaw,
      sector: sectorRaw,
      minMos,
      page,
      size: DEFAULT_PAGE_SIZE,
    };
  }, [params]);

  const { data, error, isLoading } = useTopPicks(queryParams);

  const availableSectors = useMemo<readonly string[]>(() => {
    if (!data) return [];
    const set = new Set<string>();
    for (const item of data.items) {
      if (item.sector != null && item.sector !== '') set.add(item.sector);
    }
    return Array.from(set).sort((a, b) => a.localeCompare(b));
  }, [data]);

  return (
    <main
      data-testid="top-picks-page"
      className="mx-auto flex min-h-screen max-w-7xl flex-col gap-6 px-6 py-10"
    >
      <TopPicksHeader runDate={data?.runDate ?? null} />

      <TopPicksFilters availableSectors={availableSectors} />

      {isLoading && data === undefined ? <SkeletonRows /> : null}

      {error !== undefined && data === undefined ? (
        <ErrorBanner status={error.status} message={error.message} />
      ) : null}

      {data !== undefined && data.total === 0 ? (
        <EmptyState />
      ) : null}

      {data !== undefined && data.total > 0 ? (
        <>
          <TopPicksTable items={data.items} />
          <TopPicksPagination
            page={data.page}
            size={data.size}
            total={data.total}
          />
        </>
      ) : null}
    </main>
  );
}

function SkeletonRows(): React.ReactElement {
  return (
    <div
      data-testid="top-picks-loading"
      role="status"
      aria-busy="true"
      aria-live="polite"
      className="flex flex-col gap-2"
    >
      <span className="sr-only">Caricamento classifica…</span>
      <div className="h-10 animate-pulse rounded bg-slate-100 dark:bg-slate-800" />
      <div className="h-10 animate-pulse rounded bg-slate-100 dark:bg-slate-800" />
      <div className="h-10 animate-pulse rounded bg-slate-100 dark:bg-slate-800" />
      <div className="h-10 animate-pulse rounded bg-slate-100 dark:bg-slate-800" />
      <div className="h-10 animate-pulse rounded bg-slate-100 dark:bg-slate-800" />
    </div>
  );
}

function ErrorBanner({
  status,
  message,
}: {
  readonly status: number | null;
  readonly message: string;
}): React.ReactElement {
  return (
    <div
      role="alert"
      data-testid="top-picks-error"
      className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800 dark:border-red-900 dark:bg-red-950 dark:text-red-200"
    >
      <span className="mr-2 font-semibold">
        {status ? `Errore ${status}` : 'Errore di rete'}:
      </span>
      {message}
    </div>
  );
}

function EmptyState(): React.ReactElement {
  return (
    <div
      data-testid="top-picks-empty"
      className="rounded-md border border-slate-200 bg-slate-50 px-6 py-8 text-center text-sm text-slate-600 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-300"
    >
      <p className="font-medium">Nessuna classifica disponibile per questa data.</p>
      <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
        Prova a selezionare una data più recente o lascia il datepicker vuoto
        per caricare l&apos;ultima classifica disponibile.
      </p>
    </div>
  );
}
