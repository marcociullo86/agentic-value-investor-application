'use client';

import { Info } from 'lucide-react';
import { cn } from '@/lib/utils/cn';
import type { BacktestCaveats } from '@/lib/api/backtest';

/**
 * BacktestCaveatBanner — TSK-351 (US-106, EP-024 Fase 3).
 *
 * Banner caveat SEMPRE VISIBILE quando il pannello backtest è aperto,
 * indipendentemente dal `timingEdge`. Rende espliciti i limiti del payload
 * (`caveats`):
 *
 *  - `singleTicker`            → "Verifica storica su un singolo titolo."
 *  - `notPortfolioPerformance` → "Non è una promessa di rendimento futuro
 *                                 né la performance di un portafoglio."
 *  - `lookAheadResidual`       → "I fondamentali storici possono includere
 *                                 revisioni successive."
 *
 * Lente di valore (memory/semantic/value-investing-design-lens.md):
 * un backtest che nasconde i bias residui è marketing, non evidenza.
 *
 * SCELTA UX — alert NON rosso, ruolo ARIA `status`:
 *  - Rosso è riservato al verdetto AVOID / errori critici. Per il caveat
 *    usiamo `amber/blue` tone informativo (non drammatico).
 *  - `role="status"` (non `alert`) — il banner è informativo persistente,
 *    NON deve interrompere lo screen reader. Spec US-106:
 *      "Stile: alert/warning non-rosso; ruolo ARIA `status` (non `alert`
 *       che interrompe i lettori)."
 *  - Icona `Info` semantica + aria-hidden (testo già presente).
 *
 * Sorgenti:
 *  - OpenAPI §schemas/BacktestCaveats (US-105)
 *  - US-106 §"Banner caveat" + §AC
 */

export interface BacktestCaveatBannerProps {
  readonly caveats: BacktestCaveats;
}

export function BacktestCaveatBanner(
  props: BacktestCaveatBannerProps,
): React.ReactElement {
  const { caveats } = props;

  // Costruiamo gli items dal payload BE. Il principio: ogni flag a `true`
  // diventa una linea del banner. I tre flag sono booleani con default `true`
  // (US-105 schema) — un backtest senza caveat è considerato sospetto.
  const items: ReadonlyArray<{
    readonly id: string;
    readonly text: string;
    readonly active: boolean;
  }> = [
    {
      id: 'singleTicker',
      text: 'Verifica storica su un singolo titolo (no survivorship bias, ma risultato NON generalizzabile a un portafoglio).',
      active: caveats.singleTicker,
    },
    {
      id: 'notPortfolioPerformance',
      text: 'Non è una promessa di rendimento futuro né la performance di un portafoglio: è il timing su QUESTO ticker.',
      active: caveats.notPortfolioPerformance,
    },
    {
      id: 'lookAheadResidual',
      text: 'I fondamentali FMP storici possono includere revisioni successive: il look-ahead grossolano è eliminato via filingDate, ma le revisioni non lo sono.',
      active: caveats.lookAheadResidual,
    },
  ];

  return (
    <div
      data-testid="backtest-caveat-banner"
      role="status"
      aria-live="polite"
      aria-labelledby="backtest-caveat-heading"
      className={cn(
        'flex items-start gap-3 rounded-lg border-l-4 border-amber-400 ' +
          'bg-amber-50 p-4 dark:bg-amber-950/30 dark:border-amber-500',
      )}
    >
      <Info
        aria-hidden="true"
        className="mt-0.5 h-5 w-5 shrink-0 text-amber-700 dark:text-amber-300"
      />
      <div className="flex-1">
        <h4
          id="backtest-caveat-heading"
          className="text-sm font-bold uppercase tracking-wide text-amber-900 dark:text-amber-100"
        >
          Limiti della verifica storica
        </h4>
        <ul
          data-testid="backtest-caveat-list"
          className="mt-1 list-disc space-y-1 pl-5 text-sm text-amber-900/90 dark:text-amber-100/90"
        >
          {items
            .filter((item) => item.active)
            .map((item) => (
              <li
                key={item.id}
                data-testid={`backtest-caveat-${item.id}`}
              >
                {item.text}
              </li>
            ))}
        </ul>
      </div>
    </div>
  );
}
