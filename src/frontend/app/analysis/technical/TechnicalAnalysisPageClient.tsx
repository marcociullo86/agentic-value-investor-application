'use client';

import { useSearchParams } from 'next/navigation';
import { useTechnicalAnalysis } from '@/lib/hooks/useTechnicalAnalysis';
import { useEquityLocalStorage } from '@/lib/hooks/useEquityLocalStorage';
import { Button } from '@/components/ui/Button';
import {
  TechnicalAnalysisDisclaimer,
  EntryTimingVerdictCard,
  ReentryConditionBanner,
  PriceChartWithOverlays,
  MomentumPanel,
  StopPlacementPanel,
  PositionSizingPanel,
  WikiCitationsFooter,
  ConfidenceReducedBanner,
} from '@/components/technical-analysis';
import { AnalysisTabNav } from '@/components/summary';
import { BacktestPanel } from '@/components/backtest';

/**
 * TechnicalAnalysisPageClient — TSK-334 + TSK-335 (US-101, EP-024 Fase 1).
 *
 * Orchestrator del tab "Technical Analysis":
 *
 *   [Disclaimer banner]                    ← TSK-334
 *   [Header tabs: Analisi Base | Deep | Technical*]
 *   [Confidence reduced banner se applicabile] ← TSK-335
 *   [Card verdetto entry-timing]           ← TSK-334
 *   [Banner re-entry se verdict === WAIT]  ← TSK-334
 *   [Price chart con overlay]              ← TSK-334
 *   [Pannello momentum]                    ← TSK-335
 *   [Pannello stop-placement]              ← TSK-335
 *   [Pannello position-sizing (equity)]    ← TSK-335
 *   [Footer citazioni wiki TA]             ← TSK-335
 *
 * Lazy load — AC US-101 §Comportamento: il fetch di
 * `/api/analysis/{ticker}/technical` parte SOLO quando questo componente
 * monta (rotta `/analysis/technical?ticker=…`). L'hook
 * `useTechnicalAnalysis` riceve `enabled: true` solo qui — le altre rotte
 * NON istanziano il hook → nessuna chiamata a `/technical` finché l'utente
 * non clicca esplicitamente sul tab.
 *
 * `equity` (input client-side, persistito in localStorage) entra nel
 * `useTechnicalAnalysis` come parte della SWR key → cambiarla nel pannello
 * sizing fa scattare il refetch automatico dei calcoli 2%/6%.
 *
 * Loading/Error: pattern coerente con `DeepAnalysisPageClient` (skeleton
 * + ErrorPanel + Retry button). Niente NotificationProvider toast (l'errore
 * è già dentro al main content; il toast sarebbe ridondante).
 */

export function TechnicalAnalysisPageClient(): React.ReactElement {
  const searchParams = useSearchParams();
  const ticker = (searchParams?.get('ticker') ?? '').trim().toUpperCase();

  if (ticker.length === 0) {
    return (
      <main className="mx-auto max-w-3xl px-6 py-12 text-center">
        <h1 className="sr-only">Technical Analysis</h1>
        <p className="text-sm text-on-surface/60">
          Specifica un ticker (es.{' '}
          <code>/analysis/technical?ticker=AAPL</code>).
        </p>
      </main>
    );
  }

  return <TechnicalAnalysisContent ticker={ticker} />;
}

function TechnicalAnalysisContent({
  ticker,
}: {
  readonly ticker: string;
}): React.ReactElement {
  const { equity, hydrated: equityHydrated, setEquity, reset } =
    useEquityLocalStorage();

  const { data, isLoading, error, mutate } = useTechnicalAnalysis(ticker, {
    enabled: equityHydrated, // wait for equity hydration to lock the SWR key
    equity,
  });

  return (
    <main
      data-testid="ta-page"
      className="mx-auto flex min-h-screen max-w-6xl flex-col gap-6 px-6 py-10"
    >
      <TechnicalAnalysisHeader ticker={ticker} />

      <TechnicalAnalysisDisclaimer />

      {isLoading ? <SkeletonLoader /> : null}

      {!isLoading && error !== undefined && data === undefined ? (
        <ErrorPanel
          status={error.status}
          message={error.message}
          onRetry={() => void mutate()}
        />
      ) : null}

      {data !== undefined ? (
        <>
          <AnyConfidenceReducedBanner data={data} />

          {data.entryTimingAdvisor !== null ? (
            <>
              <EntryTimingVerdictCard advisor={data.entryTimingAdvisor} />
              {data.entryTimingAdvisor.verdict === 'WAIT' &&
              data.entryTimingAdvisor.reentryCondition !== null ? (
                <ReentryConditionBanner
                  condition={data.entryTimingAdvisor.reentryCondition}
                />
              ) : null}
            </>
          ) : null}

          <PriceChartWithOverlays
            ticker={data.ticker}
            trend={data.trend}
            levels={data.levels}
            priceContext={data.priceContext}
            stopSuggestion={data.stopSuggestion}
          />

          <MomentumPanel momentum={data.momentum} />

          {data.stopSuggestion !== null ? (
            <StopPlacementPanel suggestion={data.stopSuggestion} />
          ) : null}

          {data.positionSizing !== null ? (
            <PositionSizingPanel
              sizing={data.positionSizing}
              rewardRisk={data.rewardRiskRatio}
              equity={equity}
              equityHydrated={equityHydrated}
              onEquityChange={setEquity}
              onEquityReset={reset}
            />
          ) : null}

          <WikiCitationsFooter
            citations={data.entryTimingAdvisor?.rationale.wikiCitations ?? []}
          />

          {/* TSK-351 — Pannello "Verifica storica" (backtest on-demand,
              EP-024 Fase 3). Collocazione duale (Riepilogo + Technical
              Analysis) come da US-106 §"Collocazione e trigger". Il fetch
              parte solo al click del bottone "BACKTEST" — nessuna chiamata
              automatica al mount del tab. */}
          <BacktestPanel ticker={data.ticker} />

          <footer className="border-t border-outline-variant pt-4 text-xs text-on-surface/60">
            <span>
              Valutato il {new Date(data.evaluatedAt).toLocaleString('it-IT')} —
              ticker {data.ticker}
            </span>
          </footer>
        </>
      ) : null}
    </main>
  );
}

/* ------------------------------------------------------------------ */
/*  Header tab navigation                                              */
/* ------------------------------------------------------------------ */

function TechnicalAnalysisHeader({
  ticker,
}: {
  readonly ticker: string;
}): React.ReactElement {
  return (
    <header className="flex flex-col gap-3">
      {/*
        EP-024 Fase 2 / TSK-342 — la nav tab è centralizzata in
        `AnalysisTabNav` (Riepilogo | Analisi Base | Deep | Technical*).
        Lazy load garantito sul tab Riepilogo: il Link non innesca alcun
        fetch a `/api/analysis/{ticker}/summary` finché l'utente non
        ci naviga.
      */}
      <AnalysisTabNav ticker={ticker} current="technical" />
      <h1
        data-testid="ta-page-title"
        className="text-3xl font-bold tracking-tight text-slate-900 dark:text-slate-100"
      >
        Technical Analysis — {ticker}
      </h1>
      <p className="text-sm text-slate-600 dark:text-slate-400">
        Trend, momentum, livelli structural, entry-timing Triple-Screen e
        sizing 2%/6% Rule. Layer ADVISORY di timing.
      </p>
    </header>
  );
}

/* ------------------------------------------------------------------ */
/*  Confidence reduced (TSK-335 banner)                                */
/* ------------------------------------------------------------------ */

function AnyConfidenceReducedBanner({
  data,
}: {
  readonly data: {
    readonly trend: { readonly confidenceReduced: boolean };
    readonly momentum: { readonly confidenceReduced: boolean };
    readonly volatility: { readonly confidenceReduced: boolean };
    readonly volume: { readonly confidenceReduced: boolean };
    readonly levels: { readonly confidenceReduced: boolean };
    readonly priceContext: { readonly confidenceReduced: boolean };
  };
}): React.ReactElement | null {
  const reduced =
    data.trend.confidenceReduced ||
    data.momentum.confidenceReduced ||
    data.volatility.confidenceReduced ||
    data.volume.confidenceReduced ||
    data.levels.confidenceReduced ||
    data.priceContext.confidenceReduced;
  if (!reduced) return null;
  return <ConfidenceReducedBanner />;
}

/* ------------------------------------------------------------------ */
/*  Skeleton + Error                                                   */
/* ------------------------------------------------------------------ */

function SkeletonLoader(): React.ReactElement {
  return (
    <div
      data-testid="ta-loading"
      role="status"
      aria-busy="true"
      aria-live="polite"
      className="flex flex-col gap-4"
    >
      <p className="text-sm font-medium text-slate-600 dark:text-slate-400">
        Sto recuperando l&apos;analisi tecnica…
      </p>
      <div className="h-24 animate-pulse rounded-lg bg-slate-100 dark:bg-slate-800" />
      <div className="h-40 animate-pulse rounded-lg bg-slate-100 dark:bg-slate-800" />
      <div className="h-64 animate-pulse rounded-lg bg-slate-100 dark:bg-slate-800" />
      <div className="h-32 animate-pulse rounded-lg bg-slate-100 dark:bg-slate-800" />
      <div className="h-32 animate-pulse rounded-lg bg-slate-100 dark:bg-slate-800" />
    </div>
  );
}

function ErrorPanel({
  status,
  message,
  onRetry,
}: {
  readonly status: number | null;
  readonly message: string;
  readonly onRetry: () => void;
}): React.ReactElement {
  return (
    <div
      data-testid="ta-error"
      role="alert"
      className="flex flex-col gap-3 rounded-lg border border-red-200 bg-red-50 p-6 dark:border-red-900 dark:bg-red-950"
    >
      <p className="text-sm font-medium text-red-800 dark:text-red-200">
        {message}
        {status !== null ? (
          <span className="ml-2 opacity-70">(HTTP {status})</span>
        ) : null}
      </p>
      <div>
        <Button
          type="button"
          variant="primary"
          size="sm"
          onClick={onRetry}
          data-testid="ta-error-retry"
        >
          Riprova
        </Button>
      </div>
    </div>
  );
}
