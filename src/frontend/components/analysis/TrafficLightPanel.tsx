'use client';

import { useMemo } from 'react';
import type { RuleSignal, Signal } from '@/lib/api/analysis';
import { RuleSignalCard } from './RuleSignalCard';

/**
 * TrafficLightPanel — TSK-021 (US-014).
 *
 * Riceve l'array di `RuleSignal` (subset di `RuleEngineResult.signals`)
 * e li renderizza come griglia responsive di `RuleSignalCard`.
 *
 * Riferimento design:
 *   design_&_architecture/components/frontend-components.md
 *   §analysis/TrafficLightPanel.
 *
 * Layout responsive:
 *  - mobile (<640px): 1 colonna (stack verticale, leggibilità touch).
 *  - sm-md (640-1024px): 2 colonne.
 *  - lg+ (≥1024px): 3 colonne (4 colonne possibili ma 3 dà respiro
 *    quando il rationale è lungo nello stato espanso).
 *
 * Sorting:
 *  - Le card sono ordinate per `ruleId` lessicografico ascending,
 *    coerente con `RuleEngineService.evaluateAll` (BE TSK-019)
 *    che restituisce le regole in ordine deterministico. Il sort lato
 *    FE è "best-effort defensive": se il BE già ordina, è no-op; se
 *    in futuro l'ordine BE cambia, qui rimane stabile.
 *
 * Header counter:
 *  - Aggrega per Signal e mostra "N OK · M Attenzione · K Non soddisfatta · ...".
 *  - WCAG: `aria-live="polite"` per annunciare cambi di counter quando
 *    il pannello viene rifetched (es. force refresh dal parent).
 */

export interface TrafficLightPanelProps {
  readonly signals: ReadonlyArray<RuleSignal>;
}

interface CounterEntry {
  readonly signal: Signal;
  readonly label: string;
  readonly count: number;
}

const COUNTER_ORDER: ReadonlyArray<{ readonly signal: Signal; readonly label: string }> = [
  { signal: 'GREEN', label: 'OK' },
  { signal: 'YELLOW', label: 'Attenzione' },
  { signal: 'RED', label: 'Non soddisfatta' },
  { signal: 'INDETERMINATE', label: 'Indeterminato' },
  { signal: 'NOT_CALCULABLE', label: 'Non calcolabile' },
  { signal: 'NOT_APPLICABLE', label: 'Non applicabile' },
];

function buildCounters(signals: ReadonlyArray<RuleSignal>): ReadonlyArray<CounterEntry> {
  const tally = new Map<Signal, number>();
  for (const s of signals) {
    tally.set(s.signal, (tally.get(s.signal) ?? 0) + 1);
  }
  return COUNTER_ORDER.map(({ signal, label }) => ({
    signal,
    label,
    count: tally.get(signal) ?? 0,
  })).filter((entry) => entry.count > 0);
}

export function TrafficLightPanel(
  props: TrafficLightPanelProps,
): React.ReactElement {
  const { signals } = props;

  const sortedSignals = useMemo<ReadonlyArray<RuleSignal>>(() => {
    return [...signals].sort((a, b) => a.ruleId.localeCompare(b.ruleId));
  }, [signals]);

  const counters = useMemo<ReadonlyArray<CounterEntry>>(
    () => buildCounters(sortedSignals),
    [sortedSignals],
  );

  if (sortedSignals.length === 0) {
    return (
      <section
        data-testid="traffic-light-panel-empty"
        role="status"
        aria-label="Pannello Traffic Light vuoto"
        className="rounded-lg border border-dashed border-slate-300 p-6 text-center text-sm text-slate-500 dark:border-slate-700"
      >
        Nessuna regola valutata per questo titolo.
      </section>
    );
  }

  return (
    <section
      data-testid="traffic-light-panel"
      aria-label="Pannello Traffic Light delle regole del Rule Engine"
      className="flex flex-col gap-4"
    >
      <header className="flex flex-col gap-1">
        <h2 className="text-xl font-semibold tracking-tight text-slate-900 dark:text-slate-100">
          Regole quantitative
        </h2>
        <p
          data-testid="traffic-light-panel-counter"
          aria-live="polite"
          className="text-sm text-slate-600 dark:text-slate-400"
        >
          {counters.map((entry, index) => (
            <span key={entry.signal}>
              <span
                data-testid={`traffic-light-counter-${entry.signal}`}
                className="font-medium"
              >
                {entry.count} {entry.label}
              </span>
              {index < counters.length - 1 ? (
                <span aria-hidden="true"> · </span>
              ) : null}
            </span>
          ))}
        </p>
      </header>
      <div
        data-testid="traffic-light-panel-grid"
        className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3"
      >
        {sortedSignals.map((ruleSignal) => (
          <RuleSignalCard
            key={ruleSignal.ruleId}
            signal={ruleSignal}
          />
        ))}
      </div>
    </section>
  );
}
