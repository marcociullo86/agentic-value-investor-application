'use client';

import { Hourglass } from 'lucide-react';
import { cn } from '@/lib/utils/cn';
import type { ReentryCondition } from '@/lib/api/technical';

/**
 * ReentryConditionBanner — TSK-334 (US-101, EP-024 Fase 1).
 *
 * Banner visibile SOLO quando il verdetto entry-timing è WAIT (US-099 §
 * "verdict === WAIT implica reentryCondition popolato"). Mostra la condizione
 * tecnica che sblocca la rivalutazione dell'entry (es. "RSI 14d rientra
 * sotto 50") in modo evidente — l'utente NON deve indovinare quando
 * tornare a controllare.
 *
 * Coerente con il pattern anti-COPART (memory/semantic/copart-timing-gap-ta-layer):
 * il titolo è VI-positivo, ma il timing TA non è ancora pronto → l'utente
 * vede esplicitamente la condizione di sblocco invece di affidarsi a
 * "ricontrolla quando ti viene in mente".
 *
 * Accessibility (WCAG 2.2 AA — EP-016):
 *  - `role="status"` + `aria-live="polite"`: lo screen reader annuncia la
 *    condizione di re-entry quando il componente appare.
 *  - Colore non unico canale: icona `<Hourglass>` + testo + tono blu.
 *
 * Sorgenti:
 *  - OpenAPI §schemas/ReentryCondition + ReentryConditionCode (US-099)
 *  - US-101 §Layout 3 (Banner re-entry condition)
 */

export interface ReentryConditionBannerProps {
  readonly condition: ReentryCondition;
}

export function ReentryConditionBanner(
  props: ReentryConditionBannerProps,
): React.ReactElement {
  const { condition } = props;

  return (
    <div
      data-testid="ta-reentry-banner"
      data-reentry-code={condition.code}
      role="status"
      aria-live="polite"
      aria-label={`Condizione di re-entry: ${condition.description}`}
      className={cn(
        'flex items-start gap-3 rounded-lg border-l-4 border-blue-500 bg-blue-50 p-4',
        'dark:bg-blue-950/40',
      )}
    >
      <Hourglass
        aria-hidden="true"
        className="mt-0.5 h-5 w-5 shrink-0 text-blue-700 dark:text-blue-300"
      />
      <div className="flex-1">
        <h3 className="text-sm font-semibold text-blue-900 dark:text-blue-100">
          Condizione di re-entry
        </h3>
        <p
          data-testid="ta-reentry-description"
          className="mt-1 text-sm text-blue-900/90 dark:text-blue-100/90"
        >
          {condition.description}
        </p>
        <p className="mt-1 text-xs text-blue-800/80 dark:text-blue-200/80">
          Quando la condizione si verifica, ricarica questa pagina per
          ottenere un nuovo verdetto di timing.
        </p>
      </div>
    </div>
  );
}
