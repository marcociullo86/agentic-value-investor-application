'use client';

import { useId, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { isAxiosError } from 'axios';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import {
  searchTicker,
  normalizeTicker,
  type SearchResultItem,
} from '@/lib/api/search';

/**
 * SearchBar — TSK-003 (US-001).
 *
 * Riferimento design: design_&_architecture/components/frontend-components.md
 *   §search/SearchBar.
 * Riferimento contratto: design_&_architecture/api/openapi.yaml §/api/search.
 * Riferimento AC: management/kanban/EP-001-ricerca-e-screening/
 *   US-001-ricerca-ticker-simbolo/US-001.md §AC.
 *
 * Flow:
 *  1. user input → trim + uppercase (`normalizeTicker`)
 *  2. validation zod client-side (`[A-Z0-9.\-]+`, 1..32 chars) — allineato a
 *     SearchService BE
 *  3. fetch `/api/search?query=...`
 *  4. routing:
 *     - exact match (1 item, ticker === query) → `/analysis/{ticker}`
 *     - multi-match → mostra lista inline (fallback compatto; la pagina
 *       /search-results completa è TSK-007 con AG-Grid ResultsList)
 *     - empty/404 → inline error "Ticker non trovato"
 *
 * State locale (useState/useForm). NIENTE store globale per la search: il
 * ticker selezionato transita via URL (`/analysis/{ticker}`).
 *
 * Feedback errori: INLINE (non Toast). Il hook `useToast()` non è ancora
 * implementato (arriva con Track B / TSK-034) e creare un wrapper qui
 * sforerebbe il boundary. L'inline error è anche più accessibile (live
 * region implicita su `role="alert"`).
 */

const searchSchema = z.object({
  query: z
    .string()
    .trim()
    .min(1, 'Inserisci un ticker')
    .max(32, 'Massimo 32 caratteri')
    .regex(/^[a-zA-Z0-9.\-]+$/, 'Solo lettere, cifre, punto, trattino'),
});

type SearchFormValues = z.infer<typeof searchSchema>;

type UiState =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'multi'; items: ReadonlyArray<SearchResultItem> }
  | { kind: 'not-found' }
  | { kind: 'error'; message: string };

export function SearchBar(): React.ReactElement {
  const router = useRouter();
  const inputId = useId();
  const errorId = useId();
  const [uiState, setUiState] = useState<UiState>({ kind: 'idle' });

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<SearchFormValues>({
    resolver: zodResolver(searchSchema),
    mode: 'onSubmit',
  });

  async function onSubmit(values: SearchFormValues): Promise<void> {
    const normalized = normalizeTicker(values.query);
    setUiState({ kind: 'loading' });
    try {
      const result = await searchTicker(normalized);
      const items = result.items;
      if (items.length === 0) {
        setUiState({ kind: 'not-found' });
        return;
      }
      const exact = items.find((it) => it.ticker.toUpperCase() === normalized);
      if (items.length === 1 || exact) {
        const target = exact ?? items[0]!;
        router.push(`/analysis/${encodeURIComponent(target.ticker)}`);
        return;
      }
      setUiState({ kind: 'multi', items });
    } catch (err: unknown) {
      if (isAxiosError(err) && err.response?.status === 404) {
        setUiState({ kind: 'not-found' });
        return;
      }
      const message =
        isAxiosError(err) && err.response?.status
          ? `Errore server (${err.response.status}). Riprova.`
          : 'Errore di rete. Verifica la connessione.';
      setUiState({ kind: 'error', message });
    }
  }

  const validationError = errors.query?.message;
  const isLoading = uiState.kind === 'loading';

  return (
    <div className="w-full max-w-xl">
      <form
        onSubmit={handleSubmit(onSubmit)}
        className="flex flex-col gap-2 sm:flex-row sm:items-start"
        noValidate
      >
        <div className="flex-1">
          <label
            htmlFor={inputId}
            className="mb-1 block text-sm font-medium text-slate-700 dark:text-slate-200"
          >
            Cerca ticker o nome azienda
          </label>
          <Input
            id={inputId}
            type="text"
            inputMode="text"
            autoComplete="off"
            autoCapitalize="characters"
            spellCheck={false}
            placeholder="AAPL, MSFT, BRK.B..."
            aria-label="Cerca ticker o nome azienda"
            aria-invalid={Boolean(validationError) || undefined}
            aria-describedby={validationError ? errorId : undefined}
            error={Boolean(validationError)}
            disabled={isLoading}
            {...register('query')}
          />
          {validationError ? (
            <p
              id={errorId}
              role="alert"
              className="mt-1 text-sm text-red-600 dark:text-red-400"
            >
              {validationError}
            </p>
          ) : null}
        </div>
        <Button
          type="submit"
          variant="primary"
          size="lg"
          disabled={isLoading}
          className="sm:mt-6"
        >
          {isLoading ? 'Ricerca...' : 'Cerca'}
        </Button>
      </form>

      {uiState.kind === 'not-found' ? (
        <p
          role="alert"
          className="mt-3 text-sm text-amber-700 dark:text-amber-400"
        >
          Ticker non trovato.
        </p>
      ) : null}

      {uiState.kind === 'error' ? (
        <p
          role="alert"
          className="mt-3 text-sm text-red-600 dark:text-red-400"
        >
          {uiState.message}
        </p>
      ) : null}

      {uiState.kind === 'multi' ? (
        <ul
          aria-label="Risultati ricerca"
          className="mt-3 divide-y divide-slate-200 rounded-md border border-slate-200 bg-white dark:divide-slate-800 dark:border-slate-800 dark:bg-slate-900"
        >
          {uiState.items.slice(0, 8).map((it) => (
            <li key={it.ticker}>
              <button
                type="button"
                onClick={() =>
                  router.push(`/analysis/${encodeURIComponent(it.ticker)}`)
                }
                className="flex w-full items-center justify-between gap-3 px-3 py-2 text-left hover:bg-slate-50 focus-visible:bg-slate-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-blue-500 dark:hover:bg-slate-800 dark:focus-visible:bg-slate-800"
              >
                <span className="font-mono font-semibold">{it.ticker}</span>
                <span className="truncate text-sm text-slate-600 dark:text-slate-300">
                  {it.companyName}
                </span>
              </button>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}
