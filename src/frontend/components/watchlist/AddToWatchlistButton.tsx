'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/Button';
import { useAuthStore } from '@/lib/stores/useAuthStore';
import { useWatchlistStore } from '@/lib/stores/useWatchlistStore';

/**
 * AddToWatchlistButton (TSK-035). Mounted on the analysis page; only visible
 * for authenticated users. Posts to `POST /api/watchlist/items`.
 *
 * Reference: design_&_architecture/components/frontend-components.md
 *   §watchlist/AddToWatchlistButton.
 */
export function AddToWatchlistButton({
  ticker,
}: {
  readonly ticker: string;
}): React.ReactElement | null {
  const accessToken = useAuthStore((s) => s.accessToken);
  const add = useWatchlistStore((s) => s.add);
  const items = useWatchlistStore((s) => s.items);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!accessToken) return null;

  const alreadyIn = items.some((it) => it.ticker === ticker.toUpperCase());

  async function handleClick(): Promise<void> {
    setError(null);
    setSubmitting(true);
    try {
      await add(ticker);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Errore aggiunta');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="inline-flex flex-col items-end gap-1">
      <Button
        variant={alreadyIn ? 'ghost' : 'primary'}
        size="sm"
        disabled={submitting || alreadyIn}
        onClick={handleClick}
        data-testid="add-to-watchlist"
      >
        {alreadyIn
          ? '✓ In watchlist'
          : submitting
            ? 'Aggiunta…'
            : 'Aggiungi alla watchlist'}
      </Button>
      {error && (
        <span role="alert" className="text-xs text-red-600">
          {error}
        </span>
      )}
    </div>
  );
}
