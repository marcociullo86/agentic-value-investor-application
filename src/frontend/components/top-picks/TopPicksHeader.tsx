'use client';

import { useRouter, useSearchParams, usePathname } from 'next/navigation';

/**
 * TopPicksHeader — titolo "Classifica del {runDate}" + datepicker + banner
 * aggiornamento notturno (TSK-140 — US-051).
 *
 * Datepicker:
 *  - HTML5 `<input type="date">` (no shadcn/ui Calendar in classpath — verificato
 *    `components/ui/` contiene solo Button/Input/Card/Modal/Toast).
 *  - Cambio data → `router.replace(?date=YYYY-MM-DD&page=0)` (reset page).
 *  - Bound `max` = today (no future dates: il BE risponderebbe 400).
 *
 * Banner: "Aggiornata ogni notte alle 02:00 UTC — ultima run: {runDate}".
 */

export interface TopPicksHeaderProps {
  /** ISO yyyy-mm-dd; `null` se DB vuoto. */
  readonly runDate: string | null;
}

export function TopPicksHeader({
  runDate,
}: TopPicksHeaderProps): React.ReactElement {
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();

  const selectedDate = params?.get('date') ?? runDate ?? '';
  const today = new Date().toISOString().slice(0, 10);

  const handleDateChange = (value: string): void => {
    const next = new URLSearchParams(params?.toString() ?? '');
    if (value === '') {
      next.delete('date');
    } else {
      next.set('date', value);
    }
    next.set('page', '0');
    const qs = next.toString();
    router.replace(qs.length > 0 ? `${pathname}?${qs}` : pathname);
  };

  const titleDate = runDate ?? '—';

  return (
    <header className="flex flex-col gap-3" data-testid="top-picks-header">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-3xl font-bold tracking-tight text-slate-900 dark:text-slate-100">
            Top Value Picks
          </h1>
          <p
            className="text-sm text-slate-600 dark:text-slate-400"
            data-testid="top-picks-runDate-label"
          >
            Classifica del{' '}
            <span className="font-medium text-slate-800 dark:text-slate-200">
              {titleDate}
            </span>
          </p>
        </div>
        <label className="flex flex-col gap-1">
          <span className="text-sm font-medium text-slate-700 dark:text-slate-200">
            Seleziona data classifica
          </span>
          <input
            type="date"
            value={selectedDate}
            max={today}
            onChange={(e) => handleDateChange(e.target.value)}
            aria-label="Seleziona data classifica storica"
            data-testid="top-picks-datepicker"
            className="rounded border border-slate-300 bg-white px-2 py-1 text-sm dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
          />
        </label>
      </div>
      <div
        role="note"
        className="rounded-md border border-blue-200 bg-blue-50 px-4 py-2 text-sm text-blue-800 dark:border-blue-900 dark:bg-blue-950 dark:text-blue-200"
        data-testid="top-picks-banner"
      >
        Aggiornata ogni notte alle 02:00 UTC
        {runDate ? ` — ultima run: ${runDate}` : ''}
      </div>
    </header>
  );
}
