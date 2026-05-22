'use client';

import { useEffect, useState, type FormEvent } from 'react';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { AuthGuard } from '@/components/auth/AuthGuard';
import { WatchlistTable } from '@/components/watchlist/WatchlistTable';
import { useWatchlistStore } from '@/lib/stores/useWatchlistStore';

/**
 * Watchlist page (TSK-035, US-017).
 *
 * Reference: design_&_architecture/components/frontend-components.md
 *   §app/watchlist/page.tsx.
 */
export default function WatchlistPage(): React.ReactElement {
  return (
    <AuthGuard
      fallback={
        <main className="mx-auto max-w-3xl px-6 py-12 text-center text-sm text-slate-500">
          Reindirizzamento al login…
        </main>
      }
    >
      <WatchlistInner />
    </AuthGuard>
  );
}

function WatchlistInner(): React.ReactElement {
  const items = useWatchlistStore((s) => s.items);
  const loading = useWatchlistStore((s) => s.loading);
  const error = useWatchlistStore((s) => s.error);
  const fetch = useWatchlistStore((s) => s.fetch);
  const add = useWatchlistStore((s) => s.add);
  const remove = useWatchlistStore((s) => s.remove);
  const [removingTicker, setRemovingTicker] = useState<string | null>(null);
  const [addTicker, setAddTicker] = useState('');
  const [addBusy, setAddBusy] = useState(false);
  const [addError, setAddError] = useState<string | null>(null);

  useEffect(() => {
    void fetch();
  }, [fetch]);

  async function handleRemove(ticker: string): Promise<void> {
    setRemovingTicker(ticker);
    try {
      await remove(ticker);
    } finally {
      setRemovingTicker(null);
    }
  }

  async function handleAdd(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    setAddError(null);
    if (!addTicker.trim()) return;
    setAddBusy(true);
    try {
      await add(addTicker.trim().toUpperCase());
      setAddTicker('');
    } catch (err) {
      setAddError(err instanceof Error ? err.message : 'add failed');
    } finally {
      setAddBusy(false);
    }
  }

  return (
    <main className="mx-auto max-w-5xl px-6 py-10">
      <h1 className="mb-6 text-3xl font-bold">La mia watchlist</h1>
      <form
        onSubmit={handleAdd}
        className="mb-4 flex flex-wrap items-center gap-2"
        data-testid="watchlist-add-form"
      >
        <Input
          type="text"
          placeholder="Es. AAPL"
          maxLength={10}
          value={addTicker}
          onChange={(e) => setAddTicker(e.target.value)}
          className="max-w-xs"
          data-testid="watchlist-add-input"
        />
        <Button
          type="submit"
          disabled={addBusy || !addTicker.trim()}
          data-testid="watchlist-add-submit"
        >
          {addBusy ? 'Aggiunta…' : 'Aggiungi ticker'}
        </Button>
        {addError && (
          <span role="alert" className="text-sm text-red-600">
            {addError}
          </span>
        )}
      </form>
      <Card className="overflow-hidden p-4">
        {loading && items.length === 0 ? (
          <p className="px-3 py-6 text-center text-sm text-slate-500">
            Caricamento…
          </p>
        ) : error ? (
          <p
            role="alert"
            className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700"
          >
            {error}
          </p>
        ) : (
          <WatchlistTable
            items={items}
            onRemove={handleRemove}
            removingTicker={removingTicker}
          />
        )}
      </Card>
    </main>
  );
}
