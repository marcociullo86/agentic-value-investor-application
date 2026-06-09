'use client';

import { ChevronRight } from 'lucide-react';
import { cn } from '@/lib/utils/cn';

/**
 * DecisionPathChip — TSK-343 (US-104, EP-024 Fase 2).
 *
 * Riga "Decision path" che esplicita il gate determinstico applicato dal BE
 * per arrivare al `summaryVerdict` (US-103 §"Decision path"):
 *
 *   "VI gate passed → TA gate: WAIT → WAIT_FOR_SETUP"
 *   "VI gate failed → AVOID"
 *   "Munger RISCHIO_ESTREMO override → AVOID"
 *
 * Il path testuale viene dal BE in `rationale.decisionPath` — il FE lo
 * splitta sui marker `→` per renderizzare ogni step come chip orizzontale.
 * Marker accettati: U+2192 (→), `->`, `=>`. Lo split è puramente
 * presentational — se il BE invia un singolo segmento, il chip è uno solo.
 *
 * Accessibility (WCAG 2.2 AA — EP-016, AC US-104):
 *  - VISTA PRINCIPALE: lista orizzontale di chip + chevron tra segmenti
 *    (`aria-hidden="true"` sui chevron).
 *  - ALTERNATIVA ACCESSIBILE: una `<table>` `class="sr-only"` espone la
 *    sequenza degli step in formato tabellare per screen reader che
 *    non gestiscono bene flussi orizzontali con icone decorative
 *    (AC US-104 §"Accessibilita": "tabella alternativa accessibile per
 *    chi non puo leggere il flusso orizzontale").
 *  - `aria-labelledby` punta a heading visibile.
 *
 * Sorgenti:
 *  - OpenAPI §schemas/SummaryRationale.decisionPath (US-103)
 *  - US-104 §"Layout" 4 (Decision path) + §"Accessibilita"
 *  - ADR-030 §4
 */

const SEPARATOR_REGEX = /\s*(?:→|->|=>)\s*/;

function splitPath(raw: string): ReadonlyArray<string> {
  return raw
    .split(SEPARATOR_REGEX)
    .map((s) => s.trim())
    .filter((s) => s.length > 0);
}

export interface DecisionPathChipProps {
  /** Testo deterministico del path emesso dal BE. */
  readonly path: string;
}

export function DecisionPathChip(
  props: DecisionPathChipProps,
): React.ReactElement {
  const steps = splitPath(props.path);

  return (
    <section
      data-testid="summary-decision-path"
      aria-labelledby="summary-decision-path-heading"
      className="flex flex-col gap-2"
    >
      <h3
        id="summary-decision-path-heading"
        className="text-sm font-semibold text-on-surface"
      >
        Decision path
      </h3>

      {/* Vista principale visiva: chip orizzontali + chevron decorativi. */}
      <ol
        data-testid="summary-decision-path-chips"
        aria-hidden="true"
        className="flex flex-wrap items-center gap-2"
      >
        {steps.map((step, idx) => (
          <li key={`${idx}-${step}`} className="flex items-center gap-2">
            <span
              className={cn(
                'inline-flex items-center rounded-full border ' +
                  'border-outline-variant bg-surface-container-high px-3 py-1 ' +
                  'text-xs font-medium text-on-surface',
              )}
            >
              {step}
            </span>
            {idx < steps.length - 1 ? (
              <ChevronRight
                aria-hidden="true"
                className="h-4 w-4 shrink-0 text-on-surface/40"
              />
            ) : null}
          </li>
        ))}
      </ol>

      {/* Alternativa accessibile (sr-only): tabella ordinata degli step. */}
      <table data-testid="summary-decision-path-table" className="sr-only">
        <caption>Sequenza decisionale del verdetto Riepilogo</caption>
        <thead>
          <tr>
            <th scope="col">Step</th>
            <th scope="col">Descrizione</th>
          </tr>
        </thead>
        <tbody>
          {steps.map((step, idx) => (
            <tr key={`row-${idx}-${step}`}>
              <th scope="row">{idx + 1}</th>
              <td>{step}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
