'use client';

import { useId } from 'react';
import {
  MARKET_CAP_BANDS,
  type MarketCapBand,
} from '@/lib/api/screener';

/**
 * MarketCapSelector — TSK-006 (US-002).
 *
 * Multi-select checkbox group sulle 5 fasce di capitalizzazione
 * (`wiki/sources/vi-07-risoluzione-q002-q003.md` §Criteri Q_003).
 *
 * Accessibilità:
 *  - `<fieldset>` + `<legend>` per raggruppare il controllo (WCAG 1.3.1).
 *  - `aria-describedby` su ogni checkbox per legare label + range.
 *  - Etichette IT user-facing tramite `MARKET_CAP_BANDS`.
 */

export interface MarketCapSelectorProps {
  readonly value: ReadonlyArray<MarketCapBand>;
  readonly onChange: (next: ReadonlyArray<MarketCapBand>) => void;
  readonly disabled?: boolean;
}

export function MarketCapSelector({
  value,
  onChange,
  disabled = false,
}: MarketCapSelectorProps): React.ReactElement {
  const groupId = useId();

  function toggle(band: MarketCapBand): void {
    if (value.includes(band)) {
      onChange(value.filter((v) => v !== band));
    } else {
      onChange([...value, band]);
    }
  }

  return (
    <fieldset
      className="rounded-md border border-slate-200 p-4 dark:border-slate-800"
      disabled={disabled}
      aria-describedby={`${groupId}-hint`}
    >
      <legend className="px-1 text-sm font-semibold text-slate-700 dark:text-slate-200">
        Capitalizzazione di mercato
      </legend>
      <p
        id={`${groupId}-hint`}
        className="mb-3 text-xs text-slate-500 dark:text-slate-400"
      >
        Seleziona una o più fasce. Soglia minima $50M (Nano Cap escluse).
      </p>
      <div className="flex flex-col gap-2">
        {MARKET_CAP_BANDS.map((option) => {
          const checkboxId = `${groupId}-${option.value}`;
          const descriptionId = `${checkboxId}-desc`;
          const checked = value.includes(option.value);
          return (
            <label
              key={option.value}
              htmlFor={checkboxId}
              className="flex items-center gap-2 text-sm text-slate-700 dark:text-slate-200"
            >
              <input
                id={checkboxId}
                type="checkbox"
                className="h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500 dark:border-slate-600 dark:bg-slate-900"
                checked={checked}
                onChange={() => toggle(option.value)}
                aria-describedby={descriptionId}
              />
              <span id={descriptionId}>{option.label}</span>
            </label>
          );
        })}
      </div>
    </fieldset>
  );
}
