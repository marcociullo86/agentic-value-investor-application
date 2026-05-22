'use client';

import { formatDate } from '@/lib/utils/formatters';

/**
 * StaleDataBadge — TSK-038 (US-005 + US-006 cross-cutting).
 *
 * Marker visuale che indica che i dati mostrati provengono da cache
 * scaduta (fallback stale: la chiamata live al provider FMP è fallita
 * e si sta servendo l'ultimo snapshot disponibile).
 *
 * Riferimento design:
 *   design_&_architecture/components/frontend-components.md
 *   §analysis/StaleDataBadge, §API client.
 *
 * Sorgente dati:
 *  - `isStale` e `dataSnapshotAt` provengono da `useAnalysisStore
 *    .byTicker[ticker]` (campi presenti nel body API e cross-validated
 *    con gli header `X-Data-Stale` / `X-Data-Snapshot-At` estratti
 *    dal client axios — TSK-030).
 *
 * Accessibilità:
 *  - `role="alert"` + `aria-live="polite"` quando visibile, così gli
 *    screen reader notificano la condizione di dati non aggiornati
 *    senza interrompere altre live region attive (es. loading skeleton).
 *
 * Fallback `dataSnapshotAt=null`:
 *  - Il badge resta visibile (il fatto che i dati siano stale è
 *    informazione critica anche se lo snapshot timestamp è ignoto).
 *  - Si mostra "Dati al [data sconosciuta] — ..." come fallback
 *    esplicito: comunichiamo all'utente sia lo stato (stale) sia il
 *    limite informativo (timestamp non disponibile), senza nascondere
 *    né l'uno né l'altro.
 */

export interface StaleDataBadgeProps {
  readonly isStale: boolean;
  readonly dataSnapshotAt: string | null;
}

const SNAPSHOT_UNKNOWN_LABEL = 'data sconosciuta';

export function StaleDataBadge(
  props: StaleDataBadgeProps,
): React.ReactElement | null {
  const { isStale, dataSnapshotAt } = props;

  if (!isStale) return null;

  const snapshotLabel =
    dataSnapshotAt !== null && dataSnapshotAt !== ''
      ? formatDate(dataSnapshotAt)
      : SNAPSHOT_UNKNOWN_LABEL;

  const message = `Dati al ${snapshotLabel} — aggiornamento FMP non disponibile`;

  return (
    <span
      data-testid="stale-data-badge"
      role="alert"
      aria-live="polite"
      aria-label={message}
      className="inline-flex w-fit items-center gap-2 rounded-full border border-amber-400 bg-amber-100 px-3 py-1 text-sm font-medium text-amber-900 dark:border-amber-500 dark:bg-amber-900/30 dark:text-amber-100"
    >
      <span aria-hidden="true" className="text-base leading-none">
        {'⚠️'}
      </span>
      <span data-testid="stale-data-badge-text">{message}</span>
    </span>
  );
}
