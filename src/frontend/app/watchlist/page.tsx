'use client';

import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { AuthGuard } from '@/components/auth/AuthGuard';
import { WatchlistTable } from '@/components/watchlist/WatchlistTable';
import { FormErrorSummary } from '@/components/forms/form-error-summary';
import { FormField } from '@/components/forms/form-field';
import { useWatchlistStore } from '@/lib/stores/useWatchlistStore';
import { toUserMessage } from '@/lib/to-user-message';

const tickerSchema = z.object({
  ticker: z
    .string()
    .min(1, 'Il ticker è obbligatorio')
    .max(10, 'Il ticker può avere al massimo 10 caratteri')
    .regex(/^[A-Z0-9.]+$/i, 'Inserisci un ticker valido (es. AAPL)')
    .transform((v) => v.toUpperCase()),
});

type TickerFormValues = z.infer<typeof tickerSchema>;

const FIELD_LABELS: Record<string, string> = {
  ticker: 'Ticker',
};

/**
 * Watchlist page (TSK-035, US-017, TSK-201).
 *
 * Reference: design_&_architecture/components/frontend-components.md
 *   §app/watchlist/page.tsx.
 */
export default function WatchlistPage(): React.ReactElement {
  return (
    <AuthGuard
      fallback={
        <main className="mx-auto max-w-3xl px-6 py-12 text-center">
          <h1 className="sr-only">Watchlist</h1>
          <p className="text-sm text-slate-500">Reindirizzamento al login…</p>
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
  const storeError = useWatchlistStore((s) => s.error);
  const fetchItems = useWatchlistStore((s) => s.fetch);
  const add = useWatchlistStore((s) => s.add);
  const remove = useWatchlistStore((s) => s.remove);
  const [removingTicker, setRemovingTicker] = useState<string | null>(null);
  const [addError, setAddError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<TickerFormValues>({
    resolver: zodResolver(tickerSchema),
    mode: 'onSubmit',
  });

  useEffect(() => {
    void fetchItems();
  }, [fetchItems]);

  async function handleRemove(ticker: string): Promise<void> {
    setRemovingTicker(ticker);
    try {
      await remove(ticker);
    } finally {
      setRemovingTicker(null);
    }
  }

  async function onSubmit(data: TickerFormValues): Promise<void> {
    setAddError(null);
    try {
      await add(data.ticker);
      reset();
    } catch (err) {
      setAddError(
        toUserMessage(err, {
          fallback: 'Aggiunta alla watchlist non riuscita. Riprova.',
          statusOverrides: {
            409: 'Ticker già presente in watchlist.',
            404: 'Ticker non trovato.',
          },
        }),
      );
    }
  }

  return (
    <main className="mx-auto max-w-5xl px-6 py-10">
      <h1 className="mb-6 text-3xl font-bold">La mia watchlist</h1>
      <form
        onSubmit={handleSubmit(onSubmit)}
        className="mb-4 flex flex-col gap-2"
        data-testid="watchlist-add-form"
        noValidate
      >
        <FormErrorSummary errors={errors} fieldLabels={FIELD_LABELS} />

        {addError && (
          <div
            role="alert"
            className="rounded-md border border-error/30 bg-error/5 px-3 py-2 text-sm text-error"
          >
            {addError}
          </div>
        )}

        <div className="flex flex-wrap items-end gap-2">
          <FormField
            name="ticker"
            label="Ticker"
            error={errors.ticker?.message}
            className="max-w-xs"
          >
            <Input
              id="ticker"
              type="text"
              placeholder="Es. AAPL"
              maxLength={10}
              error={!!errors.ticker}
              aria-describedby={errors.ticker ? 'ticker-error' : undefined}
              data-testid="watchlist-add-input"
              {...register('ticker')}
            />
          </FormField>
          <Button
            type="submit"
            disabled={isSubmitting}
            data-testid="watchlist-add-submit"
            className="h-10"
          >
            {isSubmitting ? 'Aggiunta…' : 'Aggiungi ticker'}
          </Button>
        </div>
      </form>
      <Card className="overflow-hidden p-4">
        {loading && items.length === 0 ? (
          <p className="px-3 py-6 text-center text-sm text-slate-500">
            Caricamento…
          </p>
        ) : storeError ? (
          <p
            role="alert"
            className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700"
          >
            {storeError}
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
