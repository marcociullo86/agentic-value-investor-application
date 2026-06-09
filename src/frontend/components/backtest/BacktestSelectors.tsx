'use client';

import { useCallback } from 'react';
import { cn } from '@/lib/utils/cn';
import {
  BACKTEST_HORIZON_OPTIONS,
  type BacktestHorizonMonths,
  type BacktestYearsOption,
} from '@/lib/api/backtest';
import { TA_EQUITY_MIN } from '@/lib/hooks/useEquityLocalStorage';

/**
 * BacktestSelectors — TSK-350/351 (US-106, EP-024 Fase 3).
 *
 * Selettori controllati per il backtest:
 *  - `years` (3 / 5 / max) — default 5.
 *  - `horizonMonths` (1 / 3 / 6 / 12) — default 6.
 *  - `equity` (numerico, opzionale) — persistito in localStorage dal consumer
 *    via `useEquityLocalStorage` (riuso TSK-335 / US-100). MAI inviata al
 *    server come body persistente.
 *
 * Render come tre `radio-group`-like (segmented control accessibile) +
 * input numerico per equity. Il cambio innesca il refetch automatico via
 * SWR key del `useBacktest` — non serve un bottone "Applica".
 *
 * Accessibility (WCAG 2.2 AA — EP-016):
 *  - Ogni gruppo è un `<fieldset>` con `<legend>`.
 *  - I bottoni del segmented control hanno `aria-pressed` per riflettere lo
 *    stato selezionato (pattern Radix-like).
 *  - L'input numerico ha `<label>` esplicita + `inputmode="decimal"`.
 *  - Focus management nativo via `<button>`.
 */

const YEARS_OPTIONS: ReadonlyArray<{
  readonly value: BacktestYearsOption;
  readonly label: string;
}> = [
  { value: 3, label: '3 anni' },
  { value: 5, label: '5 anni' },
  { value: 'max', label: 'Max' },
];

export interface BacktestSelectorsProps {
  readonly years: BacktestYearsOption;
  readonly onYearsChange: (value: BacktestYearsOption) => void;
  readonly horizonMonths: BacktestHorizonMonths;
  readonly onHorizonChange: (value: BacktestHorizonMonths) => void;
  /** Equity corrente (default da `useEquityLocalStorage`). */
  readonly equity: number;
  readonly onEquityChange: (value: number) => void;
  /** True dopo `useEquityLocalStorage` hydration: evita flash valori stale. */
  readonly equityHydrated: boolean;
  /** Disabilita i controlli durante il fetch (no race condition sui params). */
  readonly disabled?: boolean;
}

export function BacktestSelectors(
  props: BacktestSelectorsProps,
): React.ReactElement {
  const {
    years,
    onYearsChange,
    horizonMonths,
    onHorizonChange,
    equity,
    onEquityChange,
    equityHydrated,
    disabled = false,
  } = props;

  const handleEquityChange = useCallback(
    (event: React.ChangeEvent<HTMLInputElement>): void => {
      const raw = event.target.value.trim();
      if (raw.length === 0) return;
      const parsed = Number(raw);
      if (!Number.isFinite(parsed) || parsed < TA_EQUITY_MIN) return;
      onEquityChange(parsed);
    },
    [onEquityChange],
  );

  return (
    <div
      data-testid="backtest-selectors"
      className="flex flex-wrap items-end gap-4"
    >
      <Segmented
        legend="Lookback"
        testId="backtest-years"
        options={YEARS_OPTIONS}
        selected={years}
        onSelect={onYearsChange}
        disabled={disabled}
      />
      <Segmented
        legend="Orizzonte holding"
        testId="backtest-horizon"
        options={BACKTEST_HORIZON_OPTIONS.map((m) => ({
          value: m,
          label: `${m}m`,
        }))}
        selected={horizonMonths}
        onSelect={onHorizonChange}
        disabled={disabled}
      />
      <label
        className="flex flex-col gap-1 text-sm text-on-surface"
        htmlFor="backtest-equity-input"
      >
        <span className="font-medium">Capitale (USD)</span>
        <input
          id="backtest-equity-input"
          data-testid="backtest-equity-input"
          type="number"
          inputMode="decimal"
          min={TA_EQUITY_MIN}
          step="100"
          value={equityHydrated ? equity : ''}
          placeholder="50000"
          onChange={handleEquityChange}
          disabled={disabled || !equityHydrated}
          className={cn(
            'h-9 w-32 rounded-md border border-outline-variant bg-surface ' +
              'px-2 text-sm text-on-surface ' +
              'focus-visible:outline-none focus-visible:ring-2 ' +
              'focus-visible:ring-primary disabled:opacity-50',
          )}
          aria-describedby="backtest-equity-hint"
        />
        <span
          id="backtest-equity-hint"
          className="text-xs text-on-surface/60"
        >
          Locale, mai persistito server-side.
        </span>
      </label>
    </div>
  );
}

interface SegmentedProps<T extends string | number> {
  readonly legend: string;
  readonly testId: string;
  readonly options: ReadonlyArray<{ readonly value: T; readonly label: string }>;
  readonly selected: T;
  readonly onSelect: (value: T) => void;
  readonly disabled?: boolean;
}

function Segmented<T extends string | number>(
  props: SegmentedProps<T>,
): React.ReactElement {
  const { legend, testId, options, selected, onSelect, disabled = false } =
    props;
  return (
    <fieldset
      className="flex flex-col gap-1"
      data-testid={testId}
      disabled={disabled}
    >
      <legend className="text-sm font-medium text-on-surface">{legend}</legend>
      <div
        role="group"
        aria-label={legend}
        className="inline-flex overflow-hidden rounded-md border border-outline-variant"
      >
        {options.map((opt) => {
          const isSelected = opt.value === selected;
          return (
            <button
              key={String(opt.value)}
              type="button"
              data-testid={`${testId}-${String(opt.value)}`}
              aria-pressed={isSelected}
              onClick={() => onSelect(opt.value)}
              disabled={disabled}
              className={cn(
                'px-3 py-1.5 text-sm font-medium transition ' +
                  'focus-visible:outline-none focus-visible:ring-2 ' +
                  'focus-visible:ring-primary disabled:opacity-50',
                isSelected
                  ? 'bg-primary text-on-primary'
                  : 'bg-surface text-on-surface hover:bg-surface-container',
              )}
            >
              {opt.label}
            </button>
          );
        })}
      </div>
    </fieldset>
  );
}
