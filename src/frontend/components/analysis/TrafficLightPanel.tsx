'use client';

import { useMemo } from 'react';
import type { RuleSignal, Signal } from '@/lib/api/analysis';
import { RuleSignalCard } from './RuleSignalCard';

/**
 * TrafficLightPanel — TSK-021 (US-014) + TSK-088 (US-032 / EP-010).
 *
 * Riceve l'array di `RuleSignal` (subset di `RuleEngineResult.signals`)
 * e li renderizza come griglia responsive di `RuleSignalCard`, organizzato
 * in due sezioni visivamente distinte:
 *  - "Criteri Buffett Quality" — 7 ruleId profittabilità/solidità (EP-003).
 *  - "Criteri Graham Defensive" — 6 ruleId screening difensivo (EP-010).
 *
 * Una terza sezione fallback "Altri criteri" raccoglie eventuali `ruleId`
 * non ancora classificati (forward-compat: se il BE introduce un nuovo
 * signal prima che il FE lo mappi, NON viene droppato silentemente).
 *
 * Riferimento design:
 *   design_&_architecture/components/frontend-components.md
 *   §analysis/TrafficLightPanel.
 * Riferimento contract:
 *   design_&_architecture/api/openapi.yaml §RuleSignal.ruleId (TSK-087:
 *   enum chiuso 13 valori + x-buffett-quality + x-graham-defensive).
 *
 * Layout responsive (preservato da TSK-021):
 *  - mobile (<640px): 1 colonna.
 *  - sm-md (640-1024px): 2 colonne.
 *  - lg+ (≥1024px): 3 colonne.
 *
 * Sorting per-sezione:
 *  - All'interno di ciascuna sezione le card sono ordinate per `ruleId`
 *    lessicografico ascending, coerente con il pattern TSK-021. Il sort
 *    inter-sezione è imposto dall'ordine dichiarativo Buffett → Graham →
 *    Altri (semantica EP-003 prima di EP-010, fallback in fondo).
 *
 * Header counter:
 *  - Aggregato su TUTTI i ruleId (somma 7+6=13 quando payload completo),
 *    invariante rispetto al raggruppamento (AC TSK-088).
 *  - `aria-live="polite"` per annunciare cambi su rifetch.
 *
 * Accessibility (WCAG AA):
 *  - h2 titolo macro panel, h3 per ogni sezione (Buffett / Graham /
 *    Altri) → struttura headings navigabile da screen reader.
 *  - Empty section: la sezione viene OMESSA quando il subset è vuoto,
 *    no rumore visivo né per AT.
 */

/**
 * 7 ruleId pertinenti al filone Buffett Quality (EP-003).
 *
 * FONTE VERITÀ duplicata da `design_&_architecture/api/openapi.yaml`
 * §components.schemas.RuleSignal.properties.ruleId.x-buffett-quality
 * (TSK-087). Se il BE aggiunge/rimuove un ruleId Buffett, questo Set
 * va aggiornato di pari passo. Alternative non scelte:
 *  - codegen via `openapi-typescript` + lettura di `x-buffett-quality`
 *    a build-time → out-of-scope qui (TSK chirurgico, no infra changes).
 *  - fetch JSON runtime di /api/openapi → costo network + cold-start.
 *
 * Mantenuto come ReadonlySet<string> per accesso O(1) e tipizzazione
 * lasca (RuleSignal.ruleId rimane `string` per forward-compat).
 */
export const BUFFETT_QUALITY_RULES: ReadonlySet<string> = new Set<string>([
  'ROE_10Y_AVG',
  'ROIC_10Y_AVG',
  'GROSS_MARGIN_10Y_AVG',
  'NET_MARGIN_10Y_AVG',
  'CURRENT_RATIO_LATEST',
  'DEBT_TO_INCOME_LATEST',
  'CAPEX_INTENSITY_10Y_AVG',
]);

/**
 * 6 ruleId pertinenti al filone Graham Defensive (EP-010).
 *
 * FONTE VERITÀ duplicata da `design_&_architecture/api/openapi.yaml`
 * §components.schemas.RuleSignal.properties.ruleId.x-graham-defensive
 * (TSK-087). Stesso protocollo di sync di {@link BUFFETT_QUALITY_RULES}.
 */
export const GRAHAM_DEFENSIVE_RULES: ReadonlySet<string> = new Set<string>([
  'SIZE_LATEST',
  'EARNINGS_STABILITY_10Y',
  'EPS_GROWTH_10Y',
  'PE_3Y_AVG',
  'PB_LATEST',
  'DIVIDEND_CONTINUITY_20Y',
]);

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

interface Section {
  readonly id: 'buffett' | 'graham' | 'other';
  readonly testId: string;
  readonly headingTestId: string;
  readonly gridTestId: string;
  readonly title: string;
  readonly signals: ReadonlyArray<RuleSignal>;
}

/**
 * Partiziona i signal in 3 bucket (buffett / graham / other) preservando
 * sort lessicografico per ruleId all'interno di ciascuno. Le sezioni con
 * 0 signal vengono filtrate dal caller (no rendering di sezione vuota).
 */
function partitionSignals(
  signals: ReadonlyArray<RuleSignal>,
): { buffett: RuleSignal[]; graham: RuleSignal[]; other: RuleSignal[] } {
  const buffett: RuleSignal[] = [];
  const graham: RuleSignal[] = [];
  const other: RuleSignal[] = [];
  for (const s of signals) {
    if (BUFFETT_QUALITY_RULES.has(s.ruleId)) {
      buffett.push(s);
    } else if (GRAHAM_DEFENSIVE_RULES.has(s.ruleId)) {
      graham.push(s);
    } else {
      other.push(s);
    }
  }
  const byRuleId = (a: RuleSignal, b: RuleSignal): number =>
    a.ruleId.localeCompare(b.ruleId);
  buffett.sort(byRuleId);
  graham.sort(byRuleId);
  other.sort(byRuleId);
  return { buffett, graham, other };
}

export function TrafficLightPanel(
  props: TrafficLightPanelProps,
): React.ReactElement {
  const { signals } = props;

  const sections = useMemo<ReadonlyArray<Section>>(() => {
    const { buffett, graham, other } = partitionSignals(signals);
    const all: Section[] = [
      {
        id: 'buffett',
        testId: 'traffic-light-section-buffett',
        headingTestId: 'traffic-light-section-buffett-heading',
        gridTestId: 'traffic-light-section-buffett-grid',
        title: 'Criteri Buffett Quality',
        signals: buffett,
      },
      {
        id: 'graham',
        testId: 'traffic-light-section-graham',
        headingTestId: 'traffic-light-section-graham-heading',
        gridTestId: 'traffic-light-section-graham-grid',
        title: 'Criteri Graham Defensive',
        signals: graham,
      },
      {
        id: 'other',
        testId: 'traffic-light-section-other',
        headingTestId: 'traffic-light-section-other-heading',
        gridTestId: 'traffic-light-section-other-grid',
        title: 'Altri criteri',
        signals: other,
      },
    ];
    return all.filter((sec) => sec.signals.length > 0);
  }, [signals]);

  // Counter aggregato (somma di tutte le sezioni; invariante = signals.length per
  // i 6 buckets di Signal). Costruito da `signals` direttamente (no dipendenza
  // dal sort) per chiarezza semantica.
  const counters = useMemo<ReadonlyArray<CounterEntry>>(
    () => buildCounters(signals),
    [signals],
  );

  if (signals.length === 0) {
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
      className="flex flex-col gap-6"
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

      {sections.map((section, idx) => (
        <div
          key={section.id}
          data-testid={section.testId}
          className="flex flex-col gap-3"
        >
          {idx > 0 ? (
            <hr
              aria-hidden="true"
              className="border-slate-200 dark:border-slate-800"
            />
          ) : null}
          <h3
            data-testid={section.headingTestId}
            className="text-base font-semibold tracking-tight text-slate-800 dark:text-slate-200"
          >
            {section.title}
          </h3>
          <div
            data-testid={section.gridTestId}
            className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3"
          >
            {section.signals.map((ruleSignal) => (
              <RuleSignalCard
                key={ruleSignal.ruleId}
                signal={ruleSignal}
              />
            ))}
          </div>
        </div>
      ))}
    </section>
  );
}
