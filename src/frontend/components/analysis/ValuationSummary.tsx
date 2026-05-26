'use client';

import type { DcfMethod, MosSignal } from '@/lib/api/analysis';
import { formatCurrency, formatDate } from '@/lib/utils/formatters';
import { cn } from '@/lib/utils/cn';
import { Card, CardContent, CardHeader, CardTitle, CardFooter } from '@/components/ui/Card';

/**
 * ValuationSummary — TSK-021 (US-014).
 *
 * Mostra Graham Number + DCF + Margin of Safety + prezzo corrente per il
 * ticker analizzato. Componente puro (props-only, no fetch interna).
 *
 * Riferimento design:
 *   design_&_architecture/components/frontend-components.md
 *   §analysis/ValuationSummary.
 * Riferimento US: US-011 (Graham), US-012 (DCF), US-013 (MoS), consumati
 *   via US-014.
 *
 * Decisioni rendering:
 *  - `grahamNumber === null` → "Non applicabile" + tooltip "EPS o BVPS
 *    non utilizzabili" (US-011 AC). Tooltip implementato con `title`
 *    nativo per zero-dep (Radix Tooltip disponibile ma overkill qui).
 *  - `dcfIntrinsicValue === null` o `dcfMethod === 'NOT_APPLICABLE'` →
 *    "Non applicabile". Quando applicabile, label metodo umana
 *    (`GREENWALD` → "Greenwald EPV", `FCF_FALLBACK` → "FCF Fallback").
 *  - `currentPriceAtEval === null` → "—" (segnaposto neutro).
 *  - `mosSignal` usa il subset `Signal` esteso incluso `NOT_APPLICABLE`;
 *    mapping locale (analogo a `RuleSignalCard`) per coprire l'enum
 *    completo senza modificare `lib/utils/signal-color.ts` (boundary
 *    TSK-030).
 *  - Footer: `Dati al {formatDate(dataSnapshotAt)}`.
 */

export interface ValuationSummaryProps {
  readonly grahamNumber: number | null;
  readonly dcfIntrinsicValue: number | null;
  readonly dcfMethod: DcfMethod;
  readonly mosSignal: MosSignal;
  readonly currentPriceAtEval: number | null;
  /** ISO-8601 — momento snapshot dati upstream (FMP). */
  readonly dataSnapshotAt: string;
}

interface MosPresentation {
  readonly badgeClassName: string;
  readonly label: string;
  readonly description: string;
}

const MOS_PRESENTATIONS: Readonly<Record<MosSignal, MosPresentation>> = {
  GREEN: {
    badgeClassName: 'bg-signal-green text-white',
    label: 'OK',
    description: 'Margine di sicurezza adeguato',
  },
  YELLOW: {
    badgeClassName: 'bg-signal-yellow text-black',
    label: 'Attenzione',
    description: 'Margine di sicurezza marginale',
  },
  RED: {
    badgeClassName: 'bg-signal-red text-white',
    label: 'Non soddisfatta',
    description: 'Prezzo sopra il valore intrinseco',
  },
  INDETERMINATE: {
    badgeClassName: 'bg-signal-neutral text-white',
    label: 'Indeterminato',
    description: 'Margine di sicurezza non determinabile',
  },
  NOT_APPLICABLE: {
    badgeClassName: 'bg-signal-neutral text-white',
    label: 'Non applicabile',
    description: 'Né Graham né DCF utilizzabili',
  },
  NOT_CALCULABLE: {
    badgeClassName: 'bg-signal-neutral text-white',
    label: 'Non calcolabile',
    description: 'Dati insufficienti per il calcolo',
  },
};

function humanizeDcfMethod(method: DcfMethod): string {
  switch (method) {
    case 'GREENWALD':
      return 'Greenwald EPV';
    case 'FCF_FALLBACK':
      return 'FCF Fallback';
    case 'NOT_APPLICABLE':
      return 'Non applicabile';
  }
}

export function ValuationSummary(
  props: ValuationSummaryProps,
): React.ReactElement {
  const {
    grahamNumber,
    dcfIntrinsicValue,
    dcfMethod,
    mosSignal,
    currentPriceAtEval,
    dataSnapshotAt,
  } = props;

  const mosPresentation = MOS_PRESENTATIONS[mosSignal];
  const dcfApplicable =
    dcfMethod !== 'NOT_APPLICABLE' && dcfIntrinsicValue !== null;

  return (
    <Card data-testid="valuation-summary" className="w-full">
      <CardHeader>
        <CardTitle as="h2">Sintesi valutativa</CardTitle>
      </CardHeader>
      <CardContent>
        <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div data-testid="valuation-graham" className="flex flex-col gap-1">
            <dt className="text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
              Graham Number
            </dt>
            <dd className="text-lg font-semibold text-slate-900 dark:text-slate-100">
              {grahamNumber !== null ? (
                <span data-testid="valuation-graham-value">
                  {formatCurrency(grahamNumber, 'USD')}
                </span>
              ) : (
                <span
                  data-testid="valuation-graham-na"
                  className="text-slate-500"
                  title="EPS o BVPS non utilizzabili per il calcolo di Graham"
                >
                  Non applicabile
                </span>
              )}
            </dd>
          </div>

          <div data-testid="valuation-dcf" className="flex flex-col gap-1">
            <dt className="text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
              DCF — Valore intrinseco
            </dt>
            <dd className="text-lg font-semibold text-slate-900 dark:text-slate-100">
              {dcfApplicable && dcfIntrinsicValue !== null ? (
                <>
                  <span data-testid="valuation-dcf-value">
                    {formatCurrency(dcfIntrinsicValue, 'USD')}
                  </span>
                  <span
                    data-testid="valuation-dcf-method"
                    className="ml-2 inline-flex items-center rounded bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-700 dark:bg-slate-800 dark:text-slate-300"
                  >
                    {humanizeDcfMethod(dcfMethod)}
                  </span>
                </>
              ) : (
                <span
                  data-testid="valuation-dcf-na"
                  className="text-slate-500"
                  title="DCF non applicabile: nessun metodo utilizzabile sui dati disponibili"
                >
                  Non applicabile
                </span>
              )}
            </dd>
          </div>

          <div data-testid="valuation-price" className="flex flex-col gap-1">
            <dt className="text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
              Prezzo corrente
            </dt>
            <dd className="text-lg font-semibold text-slate-900 dark:text-slate-100">
              {currentPriceAtEval !== null ? (
                <span data-testid="valuation-price-value">
                  {formatCurrency(currentPriceAtEval, 'USD')}
                </span>
              ) : (
                <span data-testid="valuation-price-na" className="text-slate-500">
                  —
                </span>
              )}
            </dd>
          </div>

          <div data-testid="valuation-mos" className="flex flex-col gap-1">
            <dt className="text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
              Margin of Safety
            </dt>
            <dd className="flex items-center gap-2">
              <span
                aria-label={`Margin of Safety: ${mosPresentation.label}. ${mosPresentation.description}.`}
                data-testid="valuation-mos-badge"
                data-signal={mosSignal}
                className={cn(
                  'inline-flex items-center rounded px-2 py-1 text-sm font-medium',
                  mosPresentation.badgeClassName,
                )}
              >
                {mosPresentation.label}
              </span>
              <span
                data-testid="valuation-mos-description"
                className="text-sm text-slate-600 dark:text-slate-400"
              >
                {mosPresentation.description}
              </span>
            </dd>
          </div>
        </dl>
      </CardContent>
      <CardFooter>
        <p
          data-testid="valuation-snapshot"
          className="text-xs text-slate-500 dark:text-slate-400"
        >
          Dati al {formatDate(dataSnapshotAt)}
        </p>
      </CardFooter>
    </Card>
  );
}
