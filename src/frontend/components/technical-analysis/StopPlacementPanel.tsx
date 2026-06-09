'use client';

import { Shield, ShieldAlert } from 'lucide-react';
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from '@/components/ui/Card';
import { cn } from '@/lib/utils/cn';
import { formatCurrency, formatPercent } from '@/lib/utils/formatters';
import type { StopSuggestion, StopType } from '@/lib/api/technical';

/**
 * StopPlacementPanel — TSK-335 (US-101, EP-024 Fase 1).
 *
 * Pannello stop-placement (AC US-101 §Layout 6) — riassume `stopSuggestion`
 * di US-100:
 *  - type (SUPPORT_BASED / SMA200_BASED / ATR_BASED / NOT_CALCULABLE).
 *  - anchorReference human-readable (es. "support@$47.5 (SWING_LOW)").
 *  - stopPrice in USD.
 *  - stopDistancePct (0.05 → 5%).
 *  - rationale testuale citato dal BE (Murphy §Page 82 / Elder §50).
 *  - Caso NOT_CALCULABLE: messaggio esplicito dedicato (storico EOD
 *    insufficiente, nessun support/SMA200/ATR disponibile).
 *
 * Coerente con [[ta-stop-placement-position-sizing]] §"Principio 1: lo stop
 * risponde alla struttura": lo stop NON è arbitrario ma ancorato a livelli
 * structural (priorità: support > SMA200 > ATR > non calcolabile).
 *
 * Accessibility (WCAG 2.2 AA — EP-016):
 *  - `aria-label` sul badge stop-type esplicita il significato (non solo enum).
 *  - Colore non unico canale: icona `<Shield>` + testo + tono.
 *  - Lista descrittiva `<dl>` semantica per coppie label/value.
 */

const STOP_TYPE_LABEL: Readonly<Record<StopType, string>> = {
  SUPPORT_BASED: 'Support-based (priorità 1)',
  SMA200_BASED: 'SMA200-based (priorità 2)',
  ATR_BASED: 'ATR-based (priorità 3)',
  NOT_CALCULABLE: 'Non calcolabile',
};

const STOP_TYPE_CLASS: Readonly<Record<StopType, string>> = {
  SUPPORT_BASED:
    'bg-green-100 text-green-900 border-green-300 dark:bg-green-950 dark:text-green-200 dark:border-green-800',
  SMA200_BASED:
    'bg-blue-100 text-blue-900 border-blue-300 dark:bg-blue-950 dark:text-blue-200 dark:border-blue-800',
  ATR_BASED:
    'bg-amber-100 text-amber-900 border-amber-300 dark:bg-amber-950 dark:text-amber-100 dark:border-amber-800',
  NOT_CALCULABLE:
    'bg-slate-100 text-slate-700 border-slate-300 dark:bg-slate-900 dark:text-slate-300 dark:border-slate-700',
};

export interface StopPlacementPanelProps {
  readonly suggestion: StopSuggestion;
}

export function StopPlacementPanel(
  props: StopPlacementPanelProps,
): React.ReactElement {
  const { suggestion } = props;
  const isNotCalculable = suggestion.type === 'NOT_CALCULABLE';

  return (
    <Card data-testid="ta-stop-panel" data-stop-type={suggestion.type}>
      <CardHeader>
        <CardTitle as="h2">Stop suggerito</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <span
          data-testid="ta-stop-type-badge"
          className={cn(
            'inline-flex w-fit items-center gap-1.5 rounded-full border px-3 py-1 text-sm font-semibold',
            STOP_TYPE_CLASS[suggestion.type],
          )}
          aria-label={`Tipo stop: ${STOP_TYPE_LABEL[suggestion.type]}`}
        >
          {isNotCalculable ? (
            <ShieldAlert aria-hidden="true" className="h-4 w-4" />
          ) : (
            <Shield aria-hidden="true" className="h-4 w-4" />
          )}
          {STOP_TYPE_LABEL[suggestion.type]}
        </span>

        {isNotCalculable ? (
          <p
            data-testid="ta-stop-not-calculable"
            className="text-sm text-on-surface/80"
          >
            Lo stop non è calcolabile per questo ticker. Possibili cause:
            storico EOD insufficiente, nessun livello support sotto il prezzo,
            SMA200 non disponibile, ATR(14) mancante. Sospendi la valutazione
            di sizing finché il dato non sarà ricostituito.
          </p>
        ) : (
          <dl className="grid gap-3 sm:grid-cols-3">
            <StopRow
              label="Stop price"
              value={
                suggestion.stopPrice !== null
                  ? formatCurrency(suggestion.stopPrice, 'USD')
                  : '—'
              }
              testId="ta-stop-price"
            />
            <StopRow
              label="Distanza"
              value={
                suggestion.stopDistancePct !== null
                  ? formatPercent(suggestion.stopDistancePct, 2)
                  : '—'
              }
              testId="ta-stop-distance"
            />
            <StopRow
              label="Ancoraggio"
              value={suggestion.anchorReference ?? '—'}
              testId="ta-stop-anchor"
            />
          </dl>
        )}

        <div
          data-testid="ta-stop-rationale"
          className="rounded-md border-l-2 border-outline-variant bg-surface-container-high px-3 py-2 text-sm text-on-surface/80"
        >
          <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-on-surface/60">
            Rationale
          </p>
          <p>{suggestion.rationale}</p>
        </div>
      </CardContent>
    </Card>
  );
}

function StopRow({
  label,
  value,
  testId,
}: {
  readonly label: string;
  readonly value: string;
  readonly testId: string;
}): React.ReactElement {
  return (
    <div
      data-testid={testId}
      className="flex flex-col gap-1 rounded-md border border-outline-variant bg-surface-container-high p-3"
    >
      <dt className="text-xs font-semibold uppercase tracking-wide text-on-surface/60">
        {label}
      </dt>
      <dd className="text-sm font-medium text-on-surface">{value}</dd>
    </div>
  );
}
