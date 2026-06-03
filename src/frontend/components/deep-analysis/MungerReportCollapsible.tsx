'use client';

import { useState, useCallback, useId } from 'react';
import { ChevronDown, AlertTriangle, Shield, Radio } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { cn } from '@/lib/utils/cn';
import type {
  MungerReportBlock,
  InversionItem,
  LivelloRischio,
} from '@/lib/api/deep-analysis';

export interface MungerReportCollapsibleProps {
  readonly report: MungerReportBlock | null;
}

interface RiskPresentation {
  readonly colorClasses: string;
  readonly label: string;
}

const RISK_MAP: Readonly<Record<LivelloRischio, RiskPresentation>> = {
  RISCHIO_BASSO: {
    colorClasses: 'text-green-700 dark:text-green-400',
    label: 'Rischio Basso',
  },
  RISCHIO_MODERATO: {
    colorClasses: 'text-amber-700 dark:text-amber-400',
    label: 'Rischio Moderato',
  },
  RISCHIO_ALTO: {
    colorClasses: 'text-orange-700 dark:text-orange-400',
    label: 'Rischio Alto',
  },
  RISCHIO_ESTREMO: {
    colorClasses: 'text-red-700 dark:text-red-400',
    label: 'Rischio Estremo',
  },
};

function InversionItemList({
  items,
  icon,
  emptyText,
  testIdPrefix,
}: {
  readonly items: readonly InversionItem[];
  readonly icon: React.ReactNode;
  readonly emptyText: string;
  readonly testIdPrefix: string;
}): React.ReactElement {
  if (items.length === 0) {
    return (
      <p className="text-sm text-slate-500 dark:text-slate-400">{emptyText}</p>
    );
  }

  return (
    <ul className="flex flex-col gap-2" data-testid={testIdPrefix}>
      {items.map((item, idx) => (
        <li
          key={`${item.chunkIndex}-${idx}`}
          className="flex items-start gap-2 text-sm text-slate-700 dark:text-slate-300"
        >
          <span className="mt-0.5 shrink-0" aria-hidden="true">
            {icon}
          </span>
          <span>
            {item.testo}
            <span
              className="ml-1.5 inline-block rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-500 dark:bg-slate-800 dark:text-slate-400"
              title={`Chunk index: ${item.chunkIndex}`}
            >
              §{item.chunkIndex}
            </span>
          </span>
        </li>
      ))}
    </ul>
  );
}

export function MungerReportCollapsible({
  report,
}: MungerReportCollapsibleProps): React.ReactElement {
  const [expanded, setExpanded] = useState(false);
  const contentId = useId();

  const toggle = useCallback(() => {
    setExpanded((prev) => !prev);
  }, []);

  const hasReport = report !== null;

  return (
    <Card data-testid="munger-report-section">
      <CardHeader>
        <CardTitle as="h2">Rapporto Munger Inversion</CardTitle>
      </CardHeader>
      <CardContent>
        {hasReport ? (
          <div className="flex flex-col gap-3">
            <div className="flex items-center justify-between">
              <span
                className={cn(
                  'text-sm font-semibold',
                  RISK_MAP[report.livelloRischio].colorClasses,
                )}
                data-testid="munger-risk-level"
                role="status"
                aria-label={`Livello rischio: ${RISK_MAP[report.livelloRischio].label}`}
              >
                {RISK_MAP[report.livelloRischio].label}
              </span>
              <button
                type="button"
                onClick={toggle}
                aria-expanded={expanded}
                aria-controls={contentId}
                className="inline-flex items-center gap-1 rounded px-2 py-1 text-sm font-medium text-slate-600 transition hover:bg-slate-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 dark:text-slate-400 dark:hover:bg-slate-800"
                data-testid="munger-toggle-button"
              >
                {expanded ? 'Nascondi' : 'Espandi'}
                <ChevronDown
                  className={cn(
                    'h-4 w-4 transition-transform',
                    expanded ? 'rotate-180' : 'rotate-0',
                  )}
                  aria-hidden="true"
                />
              </button>
            </div>

            {expanded ? (
              <div
                id={contentId}
                className="flex flex-col gap-4"
                data-testid="munger-report-details"
              >
                {report.sintesi ? (
                  <section data-testid="munger-synthesis">
                    <h3 className="mb-2 text-sm font-semibold text-slate-800 dark:text-slate-200">
                      Motivazione
                    </h3>
                    <p className="whitespace-pre-wrap break-words text-sm text-slate-700 dark:text-slate-300">
                      {report.sintesi}
                    </p>
                  </section>
                ) : null}

                <section>
                  <h3 className="mb-2 text-sm font-semibold text-slate-800 dark:text-slate-200">
                    Top Rischi
                  </h3>
                  <InversionItemList
                    items={report.rischiPrincipali}
                    icon={
                      <AlertTriangle className="h-4 w-4 text-red-500" />
                    }
                    emptyText="Nessun rischio identificato."
                    testIdPrefix="munger-risks-list"
                  />
                </section>

                <section>
                  <h3 className="mb-2 text-sm font-semibold text-slate-800 dark:text-slate-200">
                    Punti di Forza
                  </h3>
                  <InversionItemList
                    items={report.puntiDiForza}
                    icon={<Shield className="h-4 w-4 text-green-500" />}
                    emptyText="Nessun punto di forza identificato."
                    testIdPrefix="munger-strengths-list"
                  />
                </section>

                <section>
                  <h3 className="mb-2 text-sm font-semibold text-slate-800 dark:text-slate-200">
                    Segnali Recenti 10-Q
                  </h3>
                  <InversionItemList
                    items={report.segnaliRecenti10Q}
                    icon={<Radio className="h-4 w-4 text-blue-500" />}
                    emptyText="Nessun segnale recente."
                    testIdPrefix="munger-signals-list"
                  />
                </section>
              </div>
            ) : null}
          </div>
        ) : (
          <p className="text-sm text-slate-500 dark:text-slate-400">
            Premi &quot;Avvia analisi LLM&quot; per il rapporto Munger.
          </p>
        )}
      </CardContent>
    </Card>
  );
}
