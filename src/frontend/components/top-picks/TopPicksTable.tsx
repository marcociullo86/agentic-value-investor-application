'use client';

import { useMemo, useState } from 'react';
import Link from 'next/link';
import { CheckCircle, AlertTriangle } from 'lucide-react';
import type { TopPickItem } from '@/lib/api/top-picks';

/**
 * TopPicksTable — tabella ordinabile per colonna (TSK-140 — US-051).
 *
 * Pattern:
 *  - Stato locale `sortBy` + `sortDir` (non in URL: ordinamento è UI-only).
 *  - Click su header → toggle ASC/DESC sulla colonna.
 *  - Default order: `rankPosition` ASC (= MoS DESC).
 *
 * Riusa pattern colore badge da `DeepVerdictBadge.tsx`:
 *  - APPROVATO_PANIC_BUY → green-100
 *  - APPROVATO → green-50
 *  - WATCHLIST → amber-50
 *  - Altri (BOCCIATO_*) → red-50 fallback (ma il job non li persiste).
 *
 * Accessibilità:
 *  - `aria-sort` su header colonna ordinata.
 *  - Badge verdict con `aria-label` testuale + icona aria-hidden.
 *  - Tabella `aria-busy` quando isLoading=true (gestito dal genitore via skeleton).
 */

type SortKey =
  | 'rankPosition'
  | 'ticker'
  | 'verdettoClasse'
  | 'marginOfSafety'
  | 'marketCapUsd'
  | 'sector';

type SortDir = 'asc' | 'desc';

export interface TopPicksTableProps {
  readonly items: readonly TopPickItem[];
}

interface VerdictPresentation {
  readonly badgeClasses: string;
  readonly icon: React.ReactNode;
  readonly ariaLabel: string;
}

const VERDICT_PRESENTATION: Readonly<Record<string, VerdictPresentation>> = {
  APPROVATO_PANIC_BUY: {
    badgeClasses:
      'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200',
    icon: <CheckCircle className="h-3.5 w-3.5" aria-hidden="true" />,
    ariaLabel: 'Approvato Panic Buy',
  },
  APPROVATO: {
    badgeClasses:
      'bg-green-50 text-green-700 dark:bg-green-950 dark:text-green-300',
    icon: <CheckCircle className="h-3.5 w-3.5" aria-hidden="true" />,
    ariaLabel: 'Approvato',
  },
  WATCHLIST: {
    badgeClasses:
      'bg-amber-50 text-amber-700 dark:bg-amber-950 dark:text-amber-300',
    icon: <AlertTriangle className="h-3.5 w-3.5" aria-hidden="true" />,
    ariaLabel: 'Watchlist',
  },
};

function getVerdictPresentation(classe: string): VerdictPresentation {
  return (
    VERDICT_PRESENTATION[classe] ?? {
      badgeClasses:
        'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-200',
      icon: null,
      ariaLabel: classe,
    }
  );
}

function compareNullableNumber(
  a: number | null,
  b: number | null,
  dir: SortDir,
): number {
  if (a == null && b == null) return 0;
  if (a == null) return 1; // nulls last
  if (b == null) return -1;
  return dir === 'asc' ? a - b : b - a;
}

function compareNullableString(
  a: string | null,
  b: string | null,
  dir: SortDir,
): number {
  if (a == null && b == null) return 0;
  if (a == null) return 1;
  if (b == null) return -1;
  return dir === 'asc' ? a.localeCompare(b) : b.localeCompare(a);
}

function formatMarketCap(usd: number | null): string {
  if (usd == null) return '—';
  if (usd >= 1_000_000_000_000) return `${(usd / 1_000_000_000_000).toFixed(2)}T`;
  if (usd >= 1_000_000_000) return `${(usd / 1_000_000_000).toFixed(2)}B`;
  if (usd >= 1_000_000) return `${(usd / 1_000_000).toFixed(2)}M`;
  return usd.toLocaleString('en-US');
}

function formatMoS(mos: number | null): string {
  if (mos == null) return '—';
  return `${mos.toFixed(1)}%`;
}

function ariaSortValue(
  column: SortKey,
  sortBy: SortKey,
  sortDir: SortDir,
): 'ascending' | 'descending' | 'none' {
  if (column !== sortBy) return 'none';
  return sortDir === 'asc' ? 'ascending' : 'descending';
}

export function TopPicksTable({
  items,
}: TopPicksTableProps): React.ReactElement {
  const [sortBy, setSortBy] = useState<SortKey>('rankPosition');
  const [sortDir, setSortDir] = useState<SortDir>('asc');

  const sortedItems = useMemo<readonly TopPickItem[]>(() => {
    const copy = [...items];
    copy.sort((a, b) => {
      switch (sortBy) {
        case 'rankPosition':
          return sortDir === 'asc'
            ? a.rankPosition - b.rankPosition
            : b.rankPosition - a.rankPosition;
        case 'ticker':
          return sortDir === 'asc'
            ? a.ticker.localeCompare(b.ticker)
            : b.ticker.localeCompare(a.ticker);
        case 'verdettoClasse':
          return sortDir === 'asc'
            ? a.verdettoClasse.localeCompare(b.verdettoClasse)
            : b.verdettoClasse.localeCompare(a.verdettoClasse);
        case 'marginOfSafety':
          return compareNullableNumber(
            a.marginOfSafety,
            b.marginOfSafety,
            sortDir,
          );
        case 'marketCapUsd':
          return compareNullableNumber(a.marketCapUsd, b.marketCapUsd, sortDir);
        case 'sector':
          return compareNullableString(a.sector, b.sector, sortDir);
        default:
          return 0;
      }
    });
    return copy;
  }, [items, sortBy, sortDir]);

  const toggleSort = (key: SortKey): void => {
    if (key === sortBy) {
      setSortDir(sortDir === 'asc' ? 'desc' : 'asc');
    } else {
      setSortBy(key);
      setSortDir(key === 'rankPosition' ? 'asc' : 'desc');
    }
  };

  const HeaderButton = ({
    column,
    label,
  }: {
    readonly column: SortKey;
    readonly label: string;
  }): React.ReactElement => {
    const isActive = sortBy === column;
    const indicator = isActive ? (sortDir === 'asc' ? '▲' : '▼') : '';
    return (
      <button
        type="button"
        onClick={() => toggleSort(column)}
        className="flex items-center gap-1 font-semibold text-slate-700 hover:text-slate-900 dark:text-slate-200 dark:hover:text-white"
        data-testid={`sort-${column}`}
      >
        {label}
        {indicator ? (
          <span aria-hidden="true" className="text-xs">
            {indicator}
          </span>
        ) : null}
      </button>
    );
  };

  return (
    <div className="overflow-x-auto rounded-md border border-slate-200 dark:border-slate-800">
      <table
        className="min-w-full divide-y divide-slate-200 text-sm dark:divide-slate-800"
        data-testid="top-picks-table"
      >
        <thead className="bg-slate-50 dark:bg-slate-900">
          <tr>
            <th
              scope="col"
              className="px-3 py-2 text-left"
              aria-sort={ariaSortValue('rankPosition', sortBy, sortDir)}
            >
              <HeaderButton column="rankPosition" label="Rank" />
            </th>
            <th
              scope="col"
              className="px-3 py-2 text-left"
              aria-sort={ariaSortValue('ticker', sortBy, sortDir)}
            >
              <HeaderButton column="ticker" label="Ticker" />
            </th>
            <th
              scope="col"
              className="px-3 py-2 text-left"
              aria-sort={ariaSortValue('verdettoClasse', sortBy, sortDir)}
            >
              <HeaderButton column="verdettoClasse" label="Verdetto" />
            </th>
            <th
              scope="col"
              className="px-3 py-2 text-right"
              aria-sort={ariaSortValue('marginOfSafety', sortBy, sortDir)}
            >
              <HeaderButton column="marginOfSafety" label="MoS %" />
            </th>
            <th
              scope="col"
              className="px-3 py-2 text-right"
              aria-sort={ariaSortValue('marketCapUsd', sortBy, sortDir)}
            >
              <HeaderButton column="marketCapUsd" label="Market Cap" />
            </th>
            <th
              scope="col"
              className="px-3 py-2 text-left"
              aria-sort={ariaSortValue('sector', sortBy, sortDir)}
            >
              <HeaderButton column="sector" label="Settore" />
            </th>
            <th scope="col" className="px-3 py-2 text-left">
              Sources
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100 bg-white dark:divide-slate-800 dark:bg-slate-950">
          {sortedItems.map((item) => {
            const presentation = getVerdictPresentation(item.verdettoClasse);
            const label = item.verdettoClasse.replaceAll('_', ' ');
            return (
              <tr
                key={`${item.ticker}-${item.rankPosition}`}
                data-testid={`top-pick-row-${item.ticker}`}
                className="hover:bg-slate-50 dark:hover:bg-slate-900"
              >
                <td className="px-3 py-2 font-medium tabular-nums">
                  {item.rankPosition}
                </td>
                <td className="px-3 py-2">
                  <Link
                    href={`/analysis/deep?ticker=${encodeURIComponent(item.ticker)}`}
                    className="font-medium text-blue-600 hover:underline dark:text-blue-400"
                    data-testid={`ticker-link-${item.ticker}`}
                  >
                    {item.ticker}
                  </Link>
                  {item.companyName ? (
                    <span className="ml-2 text-xs text-slate-500 dark:text-slate-400">
                      {item.companyName}
                    </span>
                  ) : null}
                </td>
                <td className="px-3 py-2">
                  <span
                    className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-semibold ${presentation.badgeClasses}`}
                    role="status"
                    aria-label={`Verdetto: ${presentation.ariaLabel}`}
                    data-testid={`verdict-badge-${item.ticker}`}
                  >
                    {presentation.icon}
                    {label}
                  </span>
                </td>
                <td className="px-3 py-2 text-right tabular-nums">
                  {formatMoS(item.marginOfSafety)}
                </td>
                <td className="px-3 py-2 text-right tabular-nums">
                  {formatMarketCap(item.marketCapUsd)}
                </td>
                <td className="px-3 py-2 text-slate-700 dark:text-slate-300">
                  {item.sector ?? '—'}
                </td>
                <td className="px-3 py-2 text-xs text-slate-500 dark:text-slate-400">
                  {item.source}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
