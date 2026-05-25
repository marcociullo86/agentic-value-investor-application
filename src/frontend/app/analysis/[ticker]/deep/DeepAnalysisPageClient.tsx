'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
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
 * Client-side Deep Analysis page — TSK-122 + TSK-123 (US-046, EP-011).
 *
 * Renders 5 real components (TSK-123):
 *   1. DeepVerdictBadge — verdict class + position size
 *   2. MungerReportCollapsible — rischi / punti di forza / segnali
 *   3. NewsSentimentChip — distribuzione news
 *   4. DrawdownChart — grafico prezzi 52w
 *   5. EdgarFilingLinks — lista filing SEC
 */

export function DeepAnalysisPageClient(): React.ReactElement {
  const params = useParams<{ ticker: string }>();
  const ticker = (params?.ticker ?? '').trim().toUpperCase();

  if (!ticker) {
    return (
      <main className="mx-auto max-w-3xl px-6 py-12 text-center text-sm text-slate-500">
        Specifica un ticker (es. <code>/analysis/AAPL/deep</code>).
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
  const { data, error, isLoading, isValidating, isFrozenByAdmin, invokeLlm, refresh } =
    useDeepAnalysis(ticker);

  return (
    <main
      data-testid="deep-analysis-page"
      className="mx-auto flex min-h-screen max-w-6xl flex-col gap-6 px-6 py-10"
    >
      <DeepAnalysisHeader ticker={ticker} />

      {isLoading ? <SkeletonLoader /> : null}

      {error !== undefined && data === undefined ? (
        <ErrorPanel status={error.status} message={error.message} />
      ) : null}

      {data !== undefined ? (
        <>
          <DeepVerdictBadge
            data={data}
            isValidating={isValidating}
            isFrozenByAdmin={isFrozenByAdmin}
            onInvokeLlm={invokeLlm}
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
              {new Date(data.generatedAt).toLocaleString('it-IT')} — LLM
              status: {data.llmStatus} — Pipeline: {data.totalDurationMs}ms
            </span>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => void refresh()}
              disabled={isValidating}
              data-testid="regenerate-button"
            >
              {isValidating ? 'Rigenerazione…' : 'Rigenera'}
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
        Sto analizzando i filing SEC, potrebbero servire fino a due minuti…
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
