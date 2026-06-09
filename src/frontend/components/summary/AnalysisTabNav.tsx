'use client';

import Link from 'next/link';
import {
  summaryUrl,
  analysisBaseUrl,
  deepAnalysisUrl,
  technicalAnalysisUrl,
} from '@/lib/utils/analysis-url';
import { cn } from '@/lib/utils/cn';

/**
 * AnalysisTabNav — TSK-342 (US-104, EP-024 Fase 2).
 *
 * Nav tab condivisa dalle 4 rotte del dettaglio ticker:
 *
 *   [ Riepilogo* ] | [ Analisi Base ] | [ Deep Analysis ] | [ Technical Analysis ]
 *      primo, default attivo all'apertura della pagina (US-104 §AC)
 *
 * Centralizziamo qui l'ordine + i marker `aria-current="page"` per evitare
 * drift tra le 4 rotte (pattern precedente: ciascuna rotta duplicava la nav,
 * con rischio di ordine inconsistente al landing). I componenti consumer
 * passano il `current` tab — il resto è puro presentational.
 *
 * Routing static-export:
 *  - tutte le rotte usano query param `?ticker=…` (ADR-013).
 *  - "Riepilogo" linka a `summaryUrl()` (= `/analysis?ticker=…`, alias).
 *  - "Analisi Base" linka a `analysisBaseUrl()` (= `/analysis/base?ticker=…`,
 *    nuova rotta introdotta da US-104 — `app/analysis/base/page.tsx`).
 *  - "Deep Analysis" e "Technical Analysis" linkano alle rotte esistenti
 *    invariate (US-101 / US-046).
 *
 * Accessibility (WCAG 2.2 AA — EP-016):
 *  - `<nav aria-label>` esplicito.
 *  - tab attivo: `<span aria-current="page">` (no `<Link>` su sé stesso).
 *  - focus visibile su tutti i `<Link>`.
 */

type TabKey = 'summary' | 'base' | 'deep' | 'technical';

interface TabItem {
  readonly key: TabKey;
  readonly label: string;
  readonly href: (ticker: string) => string;
  readonly testId: string;
}

const TABS: ReadonlyArray<TabItem> = [
  {
    key: 'summary',
    label: 'Riepilogo',
    href: summaryUrl,
    testId: 'tab-summary',
  },
  {
    key: 'base',
    label: 'Analisi Base',
    href: analysisBaseUrl,
    testId: 'tab-analisi-base',
  },
  {
    key: 'deep',
    label: 'Deep Analysis',
    href: deepAnalysisUrl,
    testId: 'tab-deep-analysis',
  },
  {
    key: 'technical',
    label: 'Technical Analysis',
    href: technicalAnalysisUrl,
    testId: 'tab-technical-analysis',
  },
];

export interface AnalysisTabNavProps {
  readonly ticker: string;
  readonly current: TabKey;
}

export function AnalysisTabNav(
  props: AnalysisTabNavProps,
): React.ReactElement {
  const { ticker, current } = props;
  return (
    <nav
      aria-label="Navigazione analisi"
      data-testid="analysis-tab-nav"
      className="flex gap-1 border-b border-slate-200 dark:border-slate-800"
    >
      {TABS.map((tab) => {
        const isActive = tab.key === current;
        if (isActive) {
          return (
            <span
              key={tab.key}
              aria-current="page"
              data-testid={`${tab.testId}-active`}
              className={cn(
                'border-b-2 border-blue-600 px-4 py-2 text-sm font-medium ' +
                  'text-blue-600 dark:border-blue-400 dark:text-blue-400',
              )}
            >
              {tab.label}
            </span>
          );
        }
        return (
          <Link
            key={tab.key}
            href={tab.href(ticker)}
            data-testid={tab.testId}
            className={cn(
              'border-b-2 border-transparent px-4 py-2 text-sm font-medium ' +
                'text-slate-600 transition hover:text-slate-900 ' +
                'focus-visible:outline-none focus-visible:ring-2 ' +
                'focus-visible:ring-blue-500 dark:text-slate-400 ' +
                'dark:hover:text-white',
            )}
          >
            {tab.label}
          </Link>
        );
      })}
    </nav>
  );
}
