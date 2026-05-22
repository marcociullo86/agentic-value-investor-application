'use client';

import { useRouter } from 'next/navigation';
import { Button } from '@/components/ui/Button';
import { useScreenerStore } from '@/lib/stores/useScreenerStore';
import { formatMarketCap } from '@/lib/utils/formatters';
import type { ScreenerResultItem } from '@/lib/api/screener';

/**
 * ResultsListInline — TSK-006 (US-002).
 *
 * MVP semplificato (table HTML semantica) — NON Ag-Grid completa.
 * Quella atterra in TSK-007 con `ResultsList` (US-003: ordinamento,
 * pinning colonne, export). Mantenuto un nome distinto `ResultsListInline`
 * per evitare collisione di esportazione; TSK-007 introdurrà
 * `components/search/ResultsList.tsx` (search/screener-grid) e questa
 * vista compatta resta come fallback / utilizzo lite.
 *
 * Stati gestiti:
 *  - `loading` → skeleton rows
 *  - `error` → messaggio inline (role="alert")
 *  - `hasSubmitted` + `results.length === 0` → empty state US-002 AC
 *  - `cursor !== null` → bottone "Carica altri" → `loadMore()`
 *
 * Click su riga → `router.push('/analysis/{ticker}')` (US-002 AC).
 */

const SKELETON_ROWS = 5;

export function ResultsListInline(): React.ReactElement {
  const router = useRouter();
  const results = useScreenerStore((s) => s.results);
  const loading = useScreenerStore((s) => s.loading);
  const error = useScreenerStore((s) => s.error);
  const cursor = useScreenerStore((s) => s.cursor);
  const hasSubmitted = useScreenerStore((s) => s.hasSubmitted);
  const loadMore = useScreenerStore((s) => s.loadMore);

  function handleRowClick(ticker: string): void {
    router.push(`/analysis/${encodeURIComponent(ticker)}`);
  }

  // Loading iniziale (no risultati ancora) → skeleton
  if (loading && results.length === 0) {
    return (
      <div
        className="flex flex-col gap-2"
        aria-busy="true"
        aria-live="polite"
      >
        {Array.from({ length: SKELETON_ROWS }).map((_, idx) => (
          <div
            key={idx}
            className="h-10 animate-pulse rounded-md bg-slate-100 dark:bg-slate-800"
          />
        ))}
      </div>
    );
  }

  if (error !== null) {
    return (
      <p
        role="alert"
        className="rounded-md border border-red-300 bg-red-50 p-3 text-sm text-red-700 dark:border-red-800 dark:bg-red-950 dark:text-red-300"
      >
        {error}
      </p>
    );
  }

  if (hasSubmitted && results.length === 0) {
    return (
      <p
        role="status"
        className="rounded-md border border-amber-300 bg-amber-50 p-3 text-sm text-amber-800 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-300"
      >
        Nessun titolo soddisfa i criteri.
      </p>
    );
  }

  if (!hasSubmitted) {
    return (
      <p className="text-sm text-slate-500 dark:text-slate-400">
        Seleziona i filtri e clicca &quot;Applica filtri&quot; per avviare lo screener.
      </p>
    );
  }

  return (
    <div className="flex flex-col gap-3">
      <table
        className="w-full table-auto border-collapse text-left text-sm"
        aria-label="Risultati screener"
      >
        <caption className="sr-only">
          {results.length} risultati screener. Clicca su una riga per aprire
          l&apos;analisi del titolo.
        </caption>
        <thead className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500 dark:border-slate-800 dark:text-slate-400">
          <tr>
            <th scope="col" className="px-3 py-2">Ticker</th>
            <th scope="col" className="px-3 py-2">Nome</th>
            <th scope="col" className="px-3 py-2">Settore</th>
            <th scope="col" className="px-3 py-2 text-right">Market Cap</th>
          </tr>
        </thead>
        <tbody>
          {results.map((item: ScreenerResultItem) => (
            <tr
              key={item.ticker}
              tabIndex={0}
              role="button"
              aria-label={`Apri analisi ${item.ticker} — ${item.companyName}`}
              onClick={() => handleRowClick(item.ticker)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  handleRowClick(item.ticker);
                }
              }}
              className="cursor-pointer border-b border-slate-100 hover:bg-slate-50 focus-visible:bg-slate-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-blue-500 dark:border-slate-900 dark:hover:bg-slate-800 dark:focus-visible:bg-slate-800"
            >
              <td className="px-3 py-2 font-mono font-semibold">
                {item.ticker}
              </td>
              <td className="px-3 py-2 text-slate-700 dark:text-slate-200">
                {item.companyName}
              </td>
              <td className="px-3 py-2 text-slate-600 dark:text-slate-300">
                {item.sector ?? '—'}
              </td>
              <td className="px-3 py-2 text-right font-mono text-slate-600 dark:text-slate-300">
                {item.marketCapUsd != null
                  ? formatMarketCap(item.marketCapUsd)
                  : '—'}
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {cursor !== null ? (
        <div className="flex justify-center">
          <Button
            type="button"
            variant="secondary"
            size="md"
            onClick={() => {
              void loadMore();
            }}
            disabled={loading}
          >
            {loading ? 'Carico...' : 'Carica altri'}
          </Button>
        </div>
      ) : null}
    </div>
  );
}
