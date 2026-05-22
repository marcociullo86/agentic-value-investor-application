'use client';

import { Button } from '@/components/ui/Button';
import { formatMarketCap } from '@/lib/utils/formatters';
import type { WatchlistItem } from '@/lib/api/watchlist';

/**
 * Watchlist table (TSK-035). Columns: ticker, company, sector, market cap,
 * action. Click on the ticker navigates to /analysis/{ticker}.
 *
 * Reference: design_&_architecture/components/frontend-components.md
 *   §watchlist/WatchlistTable.
 */
interface Props {
  readonly items: ReadonlyArray<WatchlistItem>;
  readonly onRemove: (ticker: string) => void;
  readonly removingTicker?: string | null;
}

export function WatchlistTable({ items, onRemove, removingTicker }: Props): React.ReactElement {
  if (items.length === 0) {
    return (
      <p
        className="rounded-md border border-dashed border-slate-300 p-6 text-center text-sm text-slate-500"
        data-testid="watchlist-empty"
      >
        La watchlist è vuota. Aggiungi un titolo dall&apos;analisi di un ticker.
      </p>
    );
  }

  return (
    <table
      className="w-full border-collapse text-sm"
      data-testid="watchlist-table"
    >
      <thead>
        <tr className="border-b border-slate-200 text-left text-xs uppercase text-slate-500">
          <th className="px-3 py-2">Ticker</th>
          <th className="px-3 py-2">Nome</th>
          <th className="px-3 py-2">Settore</th>
          <th className="px-3 py-2 text-right">Market Cap</th>
          <th className="px-3 py-2"></th>
        </tr>
      </thead>
      <tbody>
        {items.map((item) => (
          <tr
            key={item.ticker}
            className="border-b border-slate-100 hover:bg-slate-50 dark:hover:bg-slate-900"
            data-testid={`watchlist-row-${item.ticker}`}
          >
            <td className="px-3 py-2 font-medium">
              {/* Plain <a> (hard navigation): vedi commento in SearchBar.tsx —
                  output:'export' richiede full page load per ticker arbitrari. */}
              <a
                href={`/analysis/${encodeURIComponent(item.ticker)}/`}
                className="text-blue-600 hover:underline"
                data-testid={`watchlist-link-${item.ticker}`}
              >
                {item.ticker}
              </a>
            </td>
            <td className="px-3 py-2 text-slate-700 dark:text-slate-300">
              {item.companyName ?? '—'}
            </td>
            <td className="px-3 py-2 text-slate-700 dark:text-slate-300">
              {item.sector ?? '—'}
            </td>
            <td className="px-3 py-2 text-right text-slate-700 dark:text-slate-300">
              {item.marketCapUsd != null
                ? formatMarketCap(item.marketCapUsd)
                : '—'}
            </td>
            <td className="px-3 py-2 text-right">
              <Button
                variant="ghost"
                size="sm"
                disabled={removingTicker === item.ticker}
                onClick={() => onRemove(item.ticker)}
                data-testid={`watchlist-remove-${item.ticker}`}
              >
                Rimuovi
              </Button>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
