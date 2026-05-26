'use client';

import { useRouter, useSearchParams, usePathname } from 'next/navigation';

/**
 * TopPicksFilters — verdict + sector + MoS minimo (TSK-141 — US-051).
 *
 * URL deep-linkable: ogni cambio filtro scrive nei query param via
 * `router.replace(pathname + '?' + nextParams)` — incollare l'URL in finestra
 * nuova ripristina la stessa vista (AC US-051).
 *
 * Verdetto: single-select dropdown (MVP) tra i 3 valori effettivamente
 * presenti nell'output del job (`keepVerdicts` filter — vedi TSK-131 log
 * 2026-05-26 14:00). NON espongo SCARTATO/INDETERMINATO: non sono mai
 * persistiti dal job.
 *
 * Sector: derivato dagli items della pagina corrente (`availableSectors`
 * unique values). Su classifiche con > 30 items per pagina i settori
 * potrebbero essere parziali — accettabile per MVP.
 *
 * MoS minimo: slider 0..100 step 5 (percentuale di Margin of Safety).
 * Quando =0 il param è rimosso dall'URL (filtro inattivo).
 *
 * Reset paginazione: ogni cambio filtro azzera `page=0` per evitare di
 * mostrare "Pagina 3 di 1".
 */

const VERDICTS = ['APPROVATO', 'APPROVATO_PANIC_BUY', 'WATCHLIST'] as const;

export interface TopPicksFiltersProps {
  readonly availableSectors: readonly string[];
}

export function TopPicksFilters({
  availableSectors,
}: TopPicksFiltersProps): React.ReactElement {
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();

  const setParam = (key: string, value: string | null): void => {
    const next = new URLSearchParams(params?.toString() ?? '');
    if (value == null || value === '') {
      next.delete(key);
    } else {
      next.set(key, value);
    }
    next.set('page', '0');
    const qs = next.toString();
    router.replace(qs.length > 0 ? `${pathname}?${qs}` : pathname);
  };

  const verdictValue = params?.get('verdict') ?? '';
  const sectorValue = params?.get('sector') ?? '';
  const mosValueRaw = params?.get('min_mos') ?? '0';
  const mosValue = Number.isFinite(Number(mosValueRaw))
    ? Number(mosValueRaw)
    : 0;

  return (
    <div
      data-testid="top-picks-filters"
      className="flex flex-wrap items-end gap-4 rounded-md border border-slate-200 bg-slate-50 p-4 dark:border-slate-800 dark:bg-slate-900"
    >
      <label className="flex flex-col gap-1">
        <span className="text-sm font-medium text-slate-700 dark:text-slate-200">
          Verdetto
        </span>
        <select
          value={verdictValue}
          onChange={(e) => setParam('verdict', e.target.value || null)}
          className="rounded border border-slate-300 bg-white px-2 py-1 text-sm dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
          aria-label="Filtra per verdetto"
          data-testid="filter-verdict"
        >
          <option value="">Tutti</option>
          {VERDICTS.map((v) => (
            <option key={v} value={v} aria-label={v.replaceAll('_', ' ')}>
              {v.replaceAll('_', ' ')}
            </option>
          ))}
        </select>
      </label>

      <label className="flex flex-col gap-1">
        <span className="text-sm font-medium text-slate-700 dark:text-slate-200">
          Settore
        </span>
        <select
          value={sectorValue}
          onChange={(e) => setParam('sector', e.target.value || null)}
          className="rounded border border-slate-300 bg-white px-2 py-1 text-sm dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
          aria-label="Filtra per settore"
          data-testid="filter-sector"
        >
          <option value="">Tutti</option>
          {availableSectors.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
      </label>

      <label className="flex flex-col gap-1">
        <span className="text-sm font-medium text-slate-700 dark:text-slate-200">
          Margin of Safety minimo
        </span>
        <div className="flex items-center gap-2">
          <input
            type="range"
            min={0}
            max={100}
            step={5}
            value={mosValue}
            onChange={(e) =>
              setParam(
                'min_mos',
                e.target.value === '0' ? null : e.target.value,
              )
            }
            aria-label="Margin of Safety minimo, percentuale"
            data-testid="filter-min-mos"
            className="w-40"
          />
          <span className="text-xs tabular-nums text-slate-600 dark:text-slate-300">
            {mosValue}%
          </span>
        </div>
      </label>
    </div>
  );
}
