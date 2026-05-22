'use client';

import { useEffect, useState } from 'react';
import { getHistorical, type HistoricalSeries } from '@/lib/api/historical';

/**
 * useHistorical — hook React per fetch lazy della serie storica (TSK-024).
 *
 * Pattern semplice senza SWR (zero dep aggiunte): `useEffect` + `AbortController`
 * gestiscono dedup base + cleanup. Se un futuro task vuole caching cross-mount
 * o revalidation, il refactor a `useSWR(`/api/historical/${ticker}`, ...)` è
 * meccanico — il contratto pubblico {data, loading, error} resta invariato.
 *
 * Riferimento ADR: design_&_architecture/decisions/ADR-001-frontend-stack.md
 *   §Decisione (SWR client-side disponibile, hook puro acceptable per MVP).
 *
 * Stato:
 *  - `loading=true` durante la prima richiesta (e re-fetch su cambio ticker).
 *  - `data` valorizzato a fetch riuscito, `null` altrimenti.
 *  - `error` valorizzato a fetch fallito (errore lanciato da `apiClient`).
 *
 * Nota AbortError: axios non genera AbortError standard ma `CanceledError`;
 * filtriamo entrambi i casi via `signal.aborted` post-await per evitare di
 * settare stato dopo unmount/cambio-ticker.
 */
export interface UseHistoricalResult {
  readonly data: HistoricalSeries | null;
  readonly loading: boolean;
  readonly error: Error | null;
}

export function useHistorical(ticker: string): UseHistoricalResult {
  const [data, setData] = useState<HistoricalSeries | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    const normalized = ticker.trim();
    if (normalized.length === 0) {
      setData(null);
      setLoading(false);
      setError(null);
      return;
    }
    const controller = new AbortController();
    setLoading(true);
    setError(null);
    setData(null);
    (async (): Promise<void> => {
      try {
        const result = await getHistorical(normalized);
        if (controller.signal.aborted) return;
        setData(result);
      } catch (err: unknown) {
        if (controller.signal.aborted) return;
        setError(err instanceof Error ? err : new Error(String(err)));
      } finally {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      }
    })();
    return (): void => {
      controller.abort();
    };
  }, [ticker]);

  return { data, loading, error };
}
