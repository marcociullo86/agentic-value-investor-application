'use client';

import { useEffect } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useAnalysisStore } from '@/lib/stores/useAnalysisStore';
import { useHistorical } from '@/lib/hooks/useHistorical';
import { TrafficLightPanel } from '@/components/analysis/TrafficLightPanel';
import { DcfOverridePanel } from '@/components/analysis/DcfOverridePanel';
import { ValuationSummary } from '@/components/analysis/ValuationSummary';
import { StaleDataBadge } from '@/components/analysis/StaleDataBadge';
import { HistoricalChart } from '@/components/charts/HistoricalChart';

/**
 * AnalysisPageClient — TSK-021 (US-014).
 *
 * Client Component che orchestra il fetch dell'analisi via
 * `useAnalysisStore.fetchAnalysis(ticker)` on mount (cache-aware), e
 * compone il layout della dashboard analisi:
 *  - Header con ticker.
 *  - `ValuationSummary` (Graham + DCF + MoS + prezzo + snapshot).
 *  - `TrafficLightPanel` (regole quantitative).
 *  - `HistoricalChart` (US-015 — TSK-024 integrato via `useHistorical`).
 *
 * Riferimento design:
 *   design_&_architecture/components/frontend-components.md
 *   §app/analysis/page.tsx §Routing (`/analysis?ticker=`).
 *
 * Loading / error:
 *  - Loading state: skeleton per-section (analysis + historical).
 *  - Error analisi: card rossa + button "Riprova" → refetch con force=true.
 *  - Error historical: gestito internamente da `HistoricalChart` via prop
 *    `loading`/`emptyMessage` (degrado grazioso).
 *
 * StaleDataBadge (TSK-038) — NON in scope:
 *  - Lasciamo placeholder conditional `{analysis?.isStale ? ... : null}`
 *    con TODO; verrà sostituito dal `<StaleDataBadge />` componente
 *    canonico quando TSK-038 atterrerà.
 */

export interface AnalysisPageClientProps {
  readonly ticker: string;
}

export function AnalysisPageClient(
  props: AnalysisPageClientProps,
): React.ReactElement {
  // `output: 'export'` produce un set finito di pagine pre-renderizzate
  // (generateStaticParams in page.tsx). Per supportare ticker arbitrari, il BE
  // forward `/analysis/{any-ticker}/` a un template; qui leggiamo il ticker
  // effettivo da `useParams()` (URL reale) invece che dal prop pre-bundleato
  // nel template HTML, così TTD ecc. funzionano anche senza pre-rendering.
  // Fallback su prop per i test che istanziano direttamente il componente.
  const routeParams = useParams<{ ticker?: string }>();
  const ticker = routeParams?.ticker ?? props.ticker;
  const normalized = ticker.trim().toUpperCase();

  const analysis = useAnalysisStore((s) => s.byTicker[normalized]);
  const loading = useAnalysisStore((s) => s.loading[normalized] === true);
  const error = useAnalysisStore((s) => s.errors[normalized] ?? null);
  const fetchAnalysis = useAnalysisStore((s) => s.fetchAnalysis);

  const historical = useHistorical(normalized);

  useEffect(() => {
    void fetchAnalysis(normalized);
  }, [normalized, fetchAnalysis]);

  const handleRetry = (): void => {
    void fetchAnalysis(normalized, { force: true });
  };

  return (
    <main
      data-testid="analysis-page"
      className="mx-auto flex min-h-screen max-w-6xl flex-col gap-6 px-6 py-10"
    >
      <header className="flex flex-col gap-3">
        <nav
          aria-label="Navigazione analisi"
          className="flex gap-1 border-b border-slate-200 dark:border-slate-800"
        >
          <span
            aria-current="page"
            className="border-b-2 border-blue-600 px-4 py-2 text-sm font-medium text-blue-600 dark:border-blue-400 dark:text-blue-400"
          >
            Analisi Base
          </span>
          <Link
            href={`/analysis/deep?ticker=${encodeURIComponent(normalized)}`}
            className="border-b-2 border-transparent px-4 py-2 text-sm font-medium text-slate-600 transition hover:text-slate-900 dark:text-slate-400 dark:hover:text-white"
            data-testid="tab-deep-analysis"
          >
            Deep Analysis
          </Link>
        </nav>
        <h1
          data-testid="analysis-page-title"
          className="text-3xl font-bold tracking-tight text-slate-900 dark:text-slate-100"
        >
          {normalized}
        </h1>
        <p className="text-sm text-slate-600 dark:text-slate-400">
          Verdetto Rule Engine + valutazione fondamentale.
        </p>
        <StaleDataBadge
          isStale={analysis?.isStale ?? false}
          dataSnapshotAt={analysis?.dataSnapshotAt ?? null}
        />
      </header>

      {loading && analysis === undefined ? (
        <div
          data-testid="analysis-loading"
          role="status"
          aria-busy="true"
          aria-live="polite"
          className="flex flex-col gap-4"
        >
          <div className="h-32 animate-pulse rounded-lg bg-slate-100 dark:bg-slate-800" />
          <div className="h-64 animate-pulse rounded-lg bg-slate-100 dark:bg-slate-800" />
        </div>
      ) : null}

      {error !== null && analysis === undefined ? (
        <div
          data-testid="analysis-error"
          role="alert"
          className="flex items-center justify-between gap-4 rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800 dark:border-red-900 dark:bg-red-950 dark:text-red-200"
        >
          <span>{error}</span>
          <button
            type="button"
            onClick={handleRetry}
            className="rounded-md bg-red-600 px-3 py-1 text-sm font-medium text-white transition hover:bg-red-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-red-500"
          >
            Riprova
          </button>
        </div>
      ) : null}

      {analysis !== undefined ? (
        <>
          <ValuationSummary
            grahamNumber={analysis.grahamNumber}
            dcfIntrinsicValue={analysis.dcfIntrinsicValue}
            dcfMethod={analysis.dcfMethod}
            mosSignal={analysis.mosSignal}
            currentPriceAtEval={analysis.currentPriceAtEval}
            dataSnapshotAt={analysis.dataSnapshotAt}
          />
          <DcfOverridePanel
            ticker={normalized}
            dcfMethodSource={analysis.dcfMethodSource}
            onAnalysisRefresh={() => void fetchAnalysis(normalized, { force: true })}
          />
          <TrafficLightPanel signals={analysis.signals} />
        </>
      ) : null}

      <section
        data-testid="analysis-historical-section"
        aria-label="Serie storica ricavi e utile netto"
        className="flex flex-col gap-3"
      >
        <h2 className="text-xl font-semibold tracking-tight text-slate-900 dark:text-slate-100">
          Serie storica decennale
        </h2>
        <HistoricalChart
          points={historical.data?.points ?? []}
          loading={historical.loading}
          dataSnapshotAt={historical.data?.dataSnapshotAt}
        />
      </section>
    </main>
  );
}
