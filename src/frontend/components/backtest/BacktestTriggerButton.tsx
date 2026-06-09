'use client';

import { History, Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { cn } from '@/lib/utils/cn';

/**
 * BacktestTriggerButton — TSK-351 (US-106, EP-024 Fase 3).
 *
 * Bottone secondario "BACKTEST" (label utente: "Verifica storica") che
 * innesca il pannello on-demand.
 *
 * Comportamento:
 *  - Prima del click: bottone visibile (variant `secondary`), `disabled=false`.
 *  - Durante il fetch (`loading`): icona spinner + label "Calcolo…", disabled.
 *  - Dopo il primo trigger: il pannello sotto al bottone resta sempre visibile;
 *    il bottone diventa un "Re-esegui" implicito (cambia parametri = refetch
 *    automatico via hook; il riclick non è necessario).
 *
 * Accessibility (WCAG 2.2 AA — EP-016):
 *  - Bottone nativo `<button>` (via primitive `Button`), focus visibile.
 *  - `aria-live="polite"` sull'icona spinner per annunciare il cambio stato.
 *  - `aria-controls` punta all'`id` del pannello (passato dal consumer).
 *  - `aria-expanded` riflette se il pannello è già visibile.
 *
 * Posizionamento: usato in `SummaryPageClient` (vicino al hero verdetto) e
 * in `TechnicalAnalysisPageClient` (sotto al chart) — vedi US-106
 * §"Collocazione e trigger".
 */

export interface BacktestTriggerButtonProps {
  /** Click handler — il consumer chiama `useBacktest().trigger()`. */
  readonly onTrigger: () => void;
  /** True quando il fetch è in volo: disabilita il bottone + mostra spinner. */
  readonly isLoading: boolean;
  /** True quando un risultato è già visibile (cambia la label CTA). */
  readonly hasResult: boolean;
  /** `id` del pannello sotto, per `aria-controls`. */
  readonly panelId: string;
  /** Override `data-testid` per E2E (default: 'backtest-trigger-button'). */
  readonly testId?: string;
}

export function BacktestTriggerButton(
  props: BacktestTriggerButtonProps,
): React.ReactElement {
  const {
    onTrigger,
    isLoading,
    hasResult,
    panelId,
    testId = 'backtest-trigger-button',
  } = props;

  const label = isLoading
    ? 'Calcolo verifica storica…'
    : hasResult
      ? 'Aggiorna verifica storica'
      : 'BACKTEST — Verifica storica';

  return (
    <Button
      type="button"
      variant="secondary"
      size="md"
      onClick={onTrigger}
      disabled={isLoading}
      data-testid={testId}
      aria-controls={panelId}
      aria-expanded={hasResult}
      className={cn(
        'inline-flex items-center gap-2',
        'border border-outline-variant',
      )}
    >
      {isLoading ? (
        <Loader2
          aria-hidden="true"
          className="h-4 w-4 animate-spin"
          data-testid="backtest-trigger-spinner"
        />
      ) : (
        <History aria-hidden="true" className="h-4 w-4" />
      )}
      <span>{label}</span>
    </Button>
  );
}
