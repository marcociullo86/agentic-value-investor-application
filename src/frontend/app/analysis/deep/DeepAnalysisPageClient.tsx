'use client';

import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useDeepAnalysis } from '@/lib/hooks/useDeepAnalysis';
import { Button } from '@/components/ui/Button';
import { analysisUrl } from '@/lib/utils/analysis-url';
import {
  DeepVerdictBadge,
  MungerReportCollapsible,
  NewsSentimentChip,
  DrawdownChart,
  EdgarFilingLinks,
} from '@/components/deep-analysis';

/**
 * Client-side Deep Analysis page — async flow.
 *
 * Ticker from query param (?ticker=AAPL), aligned with ADR-013.
 *
 * Behaviour:
 *  - On mount / return to the page, fetch GET /latest and render whatever the
 *    backend has (SUCCESS → full result, FAILED → error, RUNNING → banner +
 *    polling, NONE → empty state). No auto-rerun.
 *  - "Esegui ora" → POST runs with invoke_llm=false then 3s polling.
 *  - "Esegui + LLM" → POST runs with invoke_llm=true then 3s polling.
 *  - Buttons disabled while isRunning (backend deduplicates anyway).
 */

export function DeepAnalysisPageClient(): React.ReactElement {
  const searchParams = useSearchParams();
  const ticker = (searchParams?.get('ticker') ?? '').trim().toUpperCase();

  if (!ticker) {
    return (
      <main className="mx-auto max-w-3xl px-6 py-12 text-center">
        <h1 className="sr-only">Deep Analysis</h1>
        <p className="text-sm text-slate-500">
          Specifica un ticker (es. <code>/analysis/deep?ticker=AAPL</code>).
        </p>
      </main>
    );
  }

  return <DeepAnalysisContent ticker={ticker} />;
}

function DeepAnalysisContent({
  ticker,
}: {
  readonly ticker: string;
}): React.ReactElement {
  const {
    data,
    latestStatus,
    isRunning,
    isLoading,
    error,
    isFrozenByAdmin,
    requestedAt,
    completedAt,
    runNow,
    runWithLlm,
  } = useDeepAnalysis(ticker);

  return (
    <main
      data-testid="deep-analysis-page"
      className="mx-auto flex min-h-screen max-w-6xl flex-col gap-6 px-6 py-10"
    >
      <DeepAnalysisHeader ticker={ticker} />

      <ManualRunBar
        ticker={ticker}
        isRunning={isRunning}
        onRun={() => void runNow()}
        onRunWithLlm={() => void runWithLlm()}
      />

      {isRunning ? (
        <RunningBanner requestedAt={requestedAt} />
      ) : null}

      {isLoading && !isRunning ? <SkeletonLoader /> : null}

      {!isRunning && error !== undefined && data === undefined ? (
        <ErrorPanel status={error.status} message={error.message} />
      ) : null}

      {!isRunning &&
      data === undefined &&
      error === undefined &&
      !isLoading &&
      latestStatus === 'NONE' ? (
        <EmptyState />
      ) : null}

      {data !== undefined ? (
        <>
          <DeepVerdictBadge
            data={data}
            isValidating={isRunning}
            isFrozenByAdmin={isFrozenByAdmin}
            onInvokeLlm={runWithLlm}
          />
          <MungerReportCollapsible report={data.mungerReport} />
          <NewsSentimentChip sentiment={data.newsSentiment} />
          <DrawdownChart priceAction={data.priceAction} />
          <EdgarFilingLinks filings={data.filingsUsed} />
          <footer
            className="flex items-center justify-between border-t border-slate-200 pt-4 text-xs text-slate-500 dark:border-slate-800 dark:text-slate-400"
            data-testid="deep-analysis-footer"
          >
            <span>
              Generato il{' '}
              {new Date(
                completedAt ?? data.generatedAt,
              ).toLocaleString('it-IT')}{' '}
              — LLM status: {data.llmStatus} — Pipeline:{' '}
              {data.totalDurationMs}ms
            </span>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => void runNow()}
              disabled={isRunning}
              data-testid="regenerate-button"
            >
              {isRunning ? 'Rigenerazione…' : 'Rigenera'}
            </Button>
          </footer>
        </>
      ) : null}
    </main>
  );
}

/* ------------------------------------------------------------------ */
/*  Header with tab navigation                                        */
/* ------------------------------------------------------------------ */

function DeepAnalysisHeader({
  ticker,
}: {
  readonly ticker: string;
}): React.ReactElement {
  return (
    <header className="flex flex-col gap-3">
      <nav
        aria-label="Navigazione analisi"
        className="flex gap-1 border-b border-slate-200 dark:border-slate-800"
      >
        <Link
          href={analysisUrl(ticker)}
          className="border-b-2 border-transparent px-4 py-2 text-sm font-medium text-slate-600 transition hover:text-slate-900 dark:text-slate-400 dark:hover:text-white"
        >
          Analisi Base
        </Link>
        <span
          aria-current="page"
          className="border-b-2 border-blue-600 px-4 py-2 text-sm font-medium text-blue-600 dark:border-blue-400 dark:text-blue-400"
        >
          Deep Analysis
        </span>
      </nav>
      <h1
        data-testid="deep-analysis-title"
        className="text-3xl font-bold tracking-tight text-slate-900 dark:text-slate-100"
      >
        Deep Analysis — {ticker}
      </h1>
      <p className="text-sm text-slate-600 dark:text-slate-400">
        Analisi approfondita SEC filing 10-K/10-Q + verdetto Munger.
      </p>
    </header>
  );
}

/* ------------------------------------------------------------------ */
/*  Manual run bar                                                     */
/* ------------------------------------------------------------------ */

function ManualRunBar({
  ticker,
  isRunning,
  onRun,
  onRunWithLlm,
}: {
  readonly ticker: string;
  readonly isRunning: boolean;
  readonly onRun: () => void;
  readonly onRunWithLlm: () => void;
}): React.ReactElement {
  return (
    <section
      data-testid="deep-analysis-manual-run-bar"
      aria-label={`Esecuzione manuale deep analysis ${ticker}`}
      className="flex flex-wrap items-center gap-3 rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 dark:border-slate-800 dark:bg-slate-900"
    >
      <div className="flex flex-col">
        <span className="text-sm font-medium text-slate-900 dark:text-slate-100">
          Esegui job on-demand
        </span>
        <span className="text-xs text-slate-600 dark:text-slate-400">
          Rilancia la pipeline deep analysis per <code>{ticker}</code> senza
          attendere il batch schedulato.
        </span>
      </div>
      <div className="ml-auto flex gap-2">
        <Button
          type="button"
          variant="secondary"
          size="sm"
          onClick={onRun}
          disabled={isRunning}
          data-testid="deep-analysis-manual-run"
        >
          {isRunning ? 'In esecuzione…' : 'Esegui ora'}
        </Button>
        <Button
          type="button"
          variant="primary"
          size="sm"
          onClick={onRunWithLlm}
          disabled={isRunning}
          data-testid="deep-analysis-manual-run-llm"
          title="Include Munger LLM (più lento, costo)"
        >
          {isRunning ? 'In esecuzione…' : 'Esegui + LLM'}
        </Button>
      </div>
    </section>
  );
}

/* ------------------------------------------------------------------ */
/*  Running banner (async polling state)                              */
/* ------------------------------------------------------------------ */

function RunningBanner({
  requestedAt,
}: {
  readonly requestedAt: string | null;
}): React.ReactElement {
  const startedLabel =
    requestedAt !== null
      ? new Date(requestedAt).toLocaleTimeString('it-IT')
      : null;
  return (
    <div
      data-testid="deep-analysis-running"
      role="status"
      aria-live="polite"
      aria-busy="true"
      className="flex items-center gap-3 rounded-lg border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-800 dark:border-blue-900 dark:bg-blue-950 dark:text-blue-200"
    >
      <span
        aria-hidden="true"
        className="inline-block h-4 w-4 animate-spin rounded-full border-2 border-blue-500 border-t-transparent"
      />
      <span>
        Esecuzione in corso…
        {startedLabel !== null ? ` (avviata alle ${startedLabel})` : ''}
      </span>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/*  Empty state (latestStatus === 'NONE')                              */
/* ------------------------------------------------------------------ */

function EmptyState(): React.ReactElement {
  return (
    <div
      data-testid="deep-analysis-empty"
      className="flex flex-col gap-2 rounded-lg border border-dashed border-slate-300 bg-slate-50 p-6 text-sm text-slate-600 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300"
    >
      <p className="font-medium text-slate-800 dark:text-slate-200">
        Nessuna esecuzione disponibile.
      </p>
      <p>
        Premi <strong>Esegui ora</strong> per avviare la deep analysis senza
        LLM, oppure <strong>Esegui + LLM</strong> per includere il report Munger
        e il sentiment news.
      </p>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/*  Skeleton loader                                                    */
/* ------------------------------------------------------------------ */

function SkeletonLoader(): React.ReactElement {
  return (
    <div
      data-testid="deep-analysis-loading"
      role="status"
      aria-busy="true"
      aria-live="polite"
      className="flex flex-col gap-4"
    >
      <p className="text-sm font-medium text-slate-600 dark:text-slate-400">
        Sto recuperando lo stato dell&apos;ultima esecuzione…
      </p>
      <div className="h-24 animate-pulse rounded-lg bg-slate-100 dark:bg-slate-800" />
      <div className="h-40 animate-pulse rounded-lg bg-slate-100 dark:bg-slate-800" />
      <div className="h-32 animate-pulse rounded-lg bg-slate-100 dark:bg-slate-800" />
      <div className="h-48 animate-pulse rounded-lg bg-slate-100 dark:bg-slate-800" />
      <div className="h-28 animate-pulse rounded-lg bg-slate-100 dark:bg-slate-800" />
    </div>
  );
}

/* ------------------------------------------------------------------ */
/*  Error panel                                                        */
/* ------------------------------------------------------------------ */

function ErrorPanel({
  status,
  message,
}: {
  readonly status: number | null;
  readonly message: string;
}): React.ReactElement {
  const icon = status === 404 ? '🔍' : status === 422 ? '📄' : '⚠️';

  return (
    <div
      data-testid="deep-analysis-error"
      role="alert"
      className="flex flex-col gap-3 rounded-lg border border-red-200 bg-red-50 p-6 dark:border-red-900 dark:bg-red-950"
    >
      <p className="text-sm font-medium text-red-800 dark:text-red-200">
        <span className="mr-2" aria-hidden="true">
          {icon}
        </span>
        {message}
      </p>
      {status === 404 ? (
        <Link
          href="/screener"
          className="text-sm font-medium text-blue-600 underline hover:text-blue-800 dark:text-blue-400 dark:hover:text-blue-300"
        >
          Cerca un altro ticker
        </Link>
      ) : null}
    </div>
  );
}
