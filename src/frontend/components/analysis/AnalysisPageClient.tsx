'use client';

import { useEffect } from 'react';
import { useParams } from 'next/navigation';
import { useAnalysisStore } from '@/lib/stores/useAnalysisStore';
import { useHistorical } from '@/lib/hooks/useHistorical';
import { TrafficLightPanel } from '@/components/analysis/TrafficLightPanel';
import { DcfOverridePanel } from '@/components/analysis/DcfOverridePanel';
import { ValuationSummary } from '@/components/analysis/ValuationSummary';
import { StaleDataBadge } from '@/components/analysis/StaleDataBadge';
import { MrMarketSentimentBadge } from '@/components/analysis/MrMarketSentimentBadge';
import { LongTermTrendBadge } from '@/components/analysis/LongTermTrendBadge';
import { NetNetBadge } from '@/components/analysis/NetNetBadge';
import { HistoricalChart } from '@/components/charts/HistoricalChart';
import { AnalysisTabNav } from '@/components/summary';

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
        {/*
          EP-024 Fase 2 / TSK-342 — la nav tab è ora centralizzata in
          `AnalysisTabNav` con l'ordine canonico:
            Riepilogo | Analisi Base* | Deep Analysis | Technical Analysis
          (`current="base"` perché questo componente vive sulla rotta
          `/analysis/base?ticker=…` post-EP-024 Fase 2). Lazy load garantito:
          il Link verso il tab Riepilogo non innesca alcun fetch a
          `/api/analysis/{ticker}/summary` finché l'utente non ci naviga.
        */}
        <AnalysisTabNav ticker={normalized} current="base" />
        <div className="flex flex-wrap items-center gap-3">
          <h1
            data-testid="analysis-page-title"
            className="text-3xl font-bold tracking-tight text-slate-900 dark:text-slate-100"
          >
            {normalized}
          </h1>
          {/*
            EP-023 / TSK-322 — NetNetBadge (ADR-029 §6).
            Visibile solo se NET_NET_RATIO === GREEN; in tutti gli altri
            casi (RED, INDETERMINATE, NOT_CALCULABLE, signal assente,
            response cache pre-EP-023) il componente ritorna null e
            il flex layout si comporta come prima (header con solo h1).
          */}
          <NetNetBadge signals={analysis?.signals ?? []} />
        </div>
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
          {/*
            EP-013 — Context Flags (advisory) sopra il TrafficLight.
            Render condizionale: solo se il BE ha popolato `contextFlags`
            (backward-compat per response cache pre-EP-013).
          */}
          {analysis.contextFlags ? (
            <section
              data-testid="context-flags-section"
              aria-labelledby="context-flags-heading"
              className="rounded-lg border border-slate-200 bg-slate-50/50 p-4 dark:border-slate-800 dark:bg-slate-900/40"
            >
              <h2
                id="context-flags-heading"
                className="text-sm font-semibold text-slate-900 dark:text-slate-100"
              >
                Mr. Market & Trend (Advisory)
              </h2>
              <p className="mb-3 mt-1 text-xs text-slate-600 dark:text-slate-400">
                Indicatori tecnici complementari — NON rule signals fondamentali
              </p>
              <div className="flex flex-wrap gap-3">
                <MrMarketSentimentBadge
                  flag={analysis.contextFlags.mrMarketRsi}
                />
                <LongTermTrendBadge
                  flag={analysis.contextFlags.longTermTrend}
                />
              </div>
            </section>
          ) : null}
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
