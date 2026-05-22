'use client';

import { useState, useId, useCallback } from 'react';
import type { RuleSignal, Signal } from '@/lib/api/analysis';
import { cn } from '@/lib/utils/cn';

/**
 * RuleSignalCard — TSK-021 (US-014).
 *
 * Singolo "semaforo" cliccabile che mostra lo stato di una regola del
 * Rule Engine. Espandibile via `useState` + Tailwind transitions (no
 * dipendenza @radix-ui/react-collapsible — non in package.json, evitato
 * per ridurre superficie deps; refactor a Radix in TSK successivo
 * meccanico mantenendo le props pubbliche invariate).
 *
 * Riferimento design:
 *   design_&_architecture/components/frontend-components.md
 *   §analysis/RuleSignalCard + §Design system §Codifica colore Traffic Light.
 *
 * Accessibilità (WCAG AA — AC US-014):
 *  - `aria-label` completo include rule name humanized + label signal +
 *    observedValue + threshold (alternativa testuale al solo colore).
 *  - Label visibile testuale ("OK" / "Attenzione" / "Non soddisfatta" /
 *    "Indeterminato" / "Non calcolabile") affiancata al pallino: WCAG
 *    1.4.1 ("Use of Color") soddisfatto — il colore NON è l'unico
 *    canale informativo.
 *  - Contrasto: i tokens `bg-signal-*` da TSK-030 sono già WCAG AA
 *    verificati su sfondo bianco/scuro (vedi tailwind.config.ts
 *    §colors.signal — green #16a34a/text-white, yellow #d97706/text-black,
 *    red #dc2626/text-white, neutral #64748b/text-white).
 *  - Button con `aria-expanded` + `aria-controls` (Radix Collapsible
 *    pattern semantically equivalente).
 *  - Keyboard: <button> nativo → Enter/Space attivano expand/collapse.
 *
 * Performance: `useCallback` su toggle handler per stabilità ref;
 * memoization estensiva non necessaria (parent re-render → max 7-10 card,
 * cost trascurabile).
 */

export interface RuleSignalCardProps {
  readonly signal: RuleSignal;
  /** Stato iniziale (default `false`). */
  readonly defaultExpanded?: boolean;
}

interface SignalPresentation {
  readonly dotClassName: string;
  readonly badgeClassName: string;
  readonly label: string;
  readonly icon: string;
}

/**
 * Mapping LOCAL `Signal → presentation` — copre l'intero enum incluso
 * `NOT_APPLICABLE` (assente in `lib/utils/signal-color.ts` che gestisce
 * solo il subset 5-valori per le regole quantitative). Decisione:
 * NON modificare il file TSK-030 (boundary task) — duplicazione contenuta
 * (5 stati → 6 stati) tollerabile, ricomponibile in TSK futuro.
 */
const PRESENTATIONS: Readonly<Record<Signal, SignalPresentation>> = {
  GREEN: {
    dotClassName: 'bg-signal-green',
    badgeClassName: 'bg-signal-green text-white',
    label: 'OK',
    icon: '✓',
  },
  YELLOW: {
    dotClassName: 'bg-signal-yellow',
    badgeClassName: 'bg-signal-yellow text-black',
    label: 'Attenzione',
    icon: '!',
  },
  RED: {
    dotClassName: 'bg-signal-red',
    badgeClassName: 'bg-signal-red text-white',
    label: 'Non soddisfatta',
    icon: '✕',
  },
  INDETERMINATE: {
    dotClassName: 'bg-signal-neutral',
    badgeClassName: 'bg-signal-neutral text-white',
    label: 'Indeterminato',
    icon: '?',
  },
  NOT_APPLICABLE: {
    dotClassName: 'bg-signal-neutral',
    badgeClassName: 'bg-signal-neutral text-white',
    label: 'Non applicabile',
    icon: '–',
  },
  NOT_CALCULABLE: {
    dotClassName: 'bg-signal-neutral',
    badgeClassName: 'bg-signal-neutral text-white',
    label: 'Non calcolabile',
    icon: '?',
  },
};

/**
 * Humanize `ruleId` → label leggibile.
 *
 * Convenzioni adottate:
 *  - Il `.` (es. "profitability.roe") separa CATEGORIA e METRICA → reso
 *    come " — ".
 *  - Lo `_` (es. "ROE_10Y_AVG", "graham_number") separa parole interne
 *    alla metrica → reso come spazio.
 *  - Ogni segmento viene Title-Cased (prima lettera maiuscola, resto
 *    lowercase) — uniforma input MAIUSCOLI (Java-style) e minuscoli.
 *
 * Esempi:
 *   "profitability.roe"  → "Profitability — Roe"
 *   "ROE_10Y_AVG"        → "Roe 10y Avg"
 *   "graham_number"      → "Graham Number"
 *   "valuation.dcf_fcf"  → "Valuation — Dcf Fcf"
 */
function humanizeRuleId(ruleId: string): string {
  const titleCase = (s: string): string => {
    if (s.length === 0) return s;
    return s.charAt(0).toUpperCase() + s.slice(1).toLowerCase();
  };
  // Split prima sui dot (categoria.metrica), poi su underscore dentro ogni segmento.
  return ruleId
    .split('.')
    .map((segment) => segment.split('_').map(titleCase).join(' '))
    .join(' — ');
}

/**
 * Format `observedValue` per la UI espansa.
 * Strategia: numero "raw" 2-decimali (it-IT); la semantica
 * (percentuale / valuta / ratio) dipende dalla regola e NON è esposta
 * dal contratto — lasciamo al testo `threshold` la disambiguazione
 * (es. "ROE ≥ 15%" già contiene l'unità).
 */
function formatObservedValue(value: number | null): string {
  if (value === null || !Number.isFinite(value)) return '—';
  return new Intl.NumberFormat('it-IT', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 4,
  }).format(value);
}

export function RuleSignalCard(props: RuleSignalCardProps): React.ReactElement {
  const { signal: ruleSignal, defaultExpanded = false } = props;
  const [expanded, setExpanded] = useState<boolean>(defaultExpanded);
  const detailsId = useId();

  const presentation = PRESENTATIONS[ruleSignal.signal];
  const humanName = humanizeRuleId(ruleSignal.ruleId);
  const observedFormatted = formatObservedValue(ruleSignal.observedValue);

  const ariaLabel = `Regola ${humanName}: ${presentation.label}. Valore osservato ${observedFormatted}. Soglia: ${ruleSignal.threshold}.`;

  const toggle = useCallback((): void => {
    setExpanded((prev) => !prev);
  }, []);

  return (
    <div
      data-testid={`rule-signal-card-${ruleSignal.ruleId}`}
      data-signal={ruleSignal.signal}
      className="flex flex-col rounded-lg border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-900"
    >
      <button
        type="button"
        onClick={toggle}
        aria-expanded={expanded}
        aria-controls={detailsId}
        aria-label={ariaLabel}
        className="flex w-full items-center gap-3 rounded-lg p-4 text-left transition hover:bg-slate-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 dark:hover:bg-slate-800"
      >
        <span
          aria-hidden="true"
          data-testid={`rule-signal-dot-${ruleSignal.ruleId}`}
          className={cn(
            'inline-flex h-5 w-5 shrink-0 items-center justify-center rounded-full text-xs font-bold text-white',
            presentation.dotClassName,
          )}
        >
          {presentation.icon}
        </span>
        <div className="flex min-w-0 flex-1 flex-col">
          <span className="truncate text-sm font-semibold text-slate-900 dark:text-slate-100">
            {humanName}
          </span>
          <span
            data-testid={`rule-signal-label-${ruleSignal.ruleId}`}
            className={cn(
              'mt-1 inline-flex w-fit items-center rounded px-2 py-0.5 text-xs font-medium',
              presentation.badgeClassName,
            )}
          >
            {presentation.label}
          </span>
        </div>
        <span
          aria-hidden="true"
          className={cn(
            'text-slate-400 transition-transform',
            expanded ? 'rotate-180' : 'rotate-0',
          )}
        >
          ▾
        </span>
      </button>
      {expanded ? (
        <div
          id={detailsId}
          data-testid={`rule-signal-details-${ruleSignal.ruleId}`}
          className="border-t border-slate-200 px-4 py-3 text-sm text-slate-700 dark:border-slate-800 dark:text-slate-300"
        >
          <dl className="grid grid-cols-[max-content_1fr] gap-x-3 gap-y-1">
            <dt className="font-medium text-slate-500 dark:text-slate-400">
              Valore osservato
            </dt>
            <dd data-testid={`rule-signal-observed-${ruleSignal.ruleId}`}>
              {observedFormatted}
            </dd>
            <dt className="font-medium text-slate-500 dark:text-slate-400">
              Soglia
            </dt>
            <dd data-testid={`rule-signal-threshold-${ruleSignal.ruleId}`}>
              {ruleSignal.threshold}
            </dd>
            {ruleSignal.rationale !== undefined && ruleSignal.rationale.length > 0 ? (
              <>
                <dt className="font-medium text-slate-500 dark:text-slate-400">
                  Razionale
                </dt>
                <dd data-testid={`rule-signal-rationale-${ruleSignal.ruleId}`}>
                  {ruleSignal.rationale}
                </dd>
              </>
            ) : null}
          </dl>
        </div>
      ) : null}
    </div>
  );
}
