'use client';

import { useId } from 'react';
import { Button } from '@/components/ui/Button';
import { MarketCapSelector } from '@/components/screener/MarketCapSelector';
import { SectorSelector } from '@/components/screener/SectorSelector';
import { useScreenerStore } from '@/lib/stores/useScreenerStore';
import type { GicsSector, MarketCapBand } from '@/lib/api/screener';

/**
 * ScreenerForm — TSK-006 (US-002).
 *
 * Compone MarketCapSelector + SectorSelector + toggle `excludeHardToPredict`.
 *
 * Riferimento design: design_&_architecture/components/frontend-components.md
 *   §screener/ScreenerForm.
 * Riferimento AC: management/kanban/EP-001-ricerca-e-screening/
 *   US-002-screener-parametrico/US-002.md.
 *
 * State: usa direttamente `useScreenerStore` (no react-hook-form qui — i
 * filtri sono inerentemente uno shared state cross-componente, e l'UX
 * "applica al submit" è semplice da modellare con i selectors Zustand).
 *
 * Reset → `useScreenerStore.reset()` riporta filters allo stato vuoto e
 * pulisce results.
 */

export function ScreenerForm(): React.ReactElement {
  const filters = useScreenerStore((s) => s.filters);
  const loading = useScreenerStore((s) => s.loading);
  const setFilters = useScreenerStore((s) => s.setFilters);
  const submit = useScreenerStore((s) => s.submit);
  const reset = useScreenerStore((s) => s.reset);

  const excludeId = useId();

  function handleMarketCapChange(next: ReadonlyArray<MarketCapBand>): void {
    setFilters({ marketCap: next });
  }

  function handleSectorChange(next: ReadonlyArray<GicsSector>): void {
    setFilters({ sector: next });
  }

  function handleExcludeToggle(e: React.ChangeEvent<HTMLInputElement>): void {
    setFilters({ excludeHardToPredict: e.target.checked });
  }

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>): Promise<void> {
    e.preventDefault();
    await submit();
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="flex flex-col gap-4"
      aria-label="Filtri screener"
      noValidate
    >
      <MarketCapSelector
        value={filters.marketCap}
        onChange={handleMarketCapChange}
        disabled={loading}
      />

      <SectorSelector
        value={filters.sector}
        onChange={handleSectorChange}
        disabled={loading}
      />

      <div className="flex items-center gap-2 rounded-md border border-slate-200 p-4 dark:border-slate-800">
        <input
          id={excludeId}
          type="checkbox"
          className="h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500 dark:border-slate-600 dark:bg-slate-900"
          checked={filters.excludeHardToPredict}
          onChange={handleExcludeToggle}
          disabled={loading}
        />
        <label
          htmlFor={excludeId}
          className="text-sm text-slate-700 dark:text-slate-200"
        >
          Escludi settori difficili da prevedere (Circle of Competence)
        </label>
      </div>

      <div className="flex flex-wrap gap-2">
        <Button type="submit" variant="primary" size="md" disabled={loading}>
          {loading ? 'Ricerca...' : 'Applica filtri'}
        </Button>
        <Button
          type="button"
          variant="secondary"
          size="md"
          onClick={reset}
          disabled={loading}
        >
          Reset
        </Button>
      </div>
    </form>
  );
}
