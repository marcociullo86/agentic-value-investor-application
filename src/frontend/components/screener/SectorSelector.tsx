'use client';

import { useId } from 'react';
import { GICS_SECTORS, type GicsSector } from '@/lib/api/screener';

/**
 * SectorSelector — TSK-006 (US-002).
 *
 * Multi-select checkbox group sui 11 settori GICS (lista chiusa,
 * `wiki/sources/vi-07-risoluzione-q002-q003.md` §Classificazione Settoriale).
 *
 * Accessibilità: `<fieldset>` + `<legend>`, `aria-describedby` per il label
 * del singolo settore (WCAG 1.3.1, 3.3.2).
 */

export interface SectorSelectorProps {
  readonly value: ReadonlyArray<GicsSector>;
  readonly onChange: (next: ReadonlyArray<GicsSector>) => void;
  readonly disabled?: boolean;
}

export function SectorSelector({
  value,
  onChange,
  disabled = false,
}: SectorSelectorProps): React.ReactElement {
  const groupId = useId();

  function toggle(sector: GicsSector): void {
    if (value.includes(sector)) {
      onChange(value.filter((v) => v !== sector));
    } else {
      onChange([...value, sector]);
    }
  }

  return (
    <fieldset
      className="rounded-md border border-slate-200 p-4 dark:border-slate-800"
      disabled={disabled}
      aria-describedby={`${groupId}-hint`}
    >
      <legend className="px-1 text-sm font-semibold text-slate-700 dark:text-slate-200">
        Settori (GICS)
      </legend>
      <p
        id={`${groupId}-hint`}
        className="mb-3 text-xs text-slate-500 dark:text-slate-400"
      >
        Seleziona uno o più settori industriali (Circle of Competence).
      </p>
      <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
        {GICS_SECTORS.map((option) => {
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
