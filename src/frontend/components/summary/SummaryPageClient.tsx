'use client';

import { useSummary } from '@/lib/hooks/useSummary';
import { Button } from '@/components/ui/Button';
import {
  AnalysisTabNav,
  SummaryHero,
  AntiCopartBanner,
  SummaryFactorCards,
  DecisionPathChip,
  WikiCitationsSection,
  SummaryDisclaimerFooter,
} from '@/components/summary';
import { BacktestPanel } from '@/components/backtest';

/**
 * SummaryPageClient — TSK-342 + TSK-343 (US-104, EP-024 Fase 2).
 *
 * Orchestrator del tab "Riepilogo" (primo tab + default di landing del
 * dettaglio ticker):
 *
 *   [Header tabs: Riepilogo* | Analisi Base | Deep | Technical]
 *   [Hero verdetto: ENTRA ORA | ASPETTA | EVITA | DATI INSUFFICIENTI] ← TSK-342
 *   [Banner anti-COPART se warningAntiCopart non vuoto]                ← TSK-342
 *   [3 card fattori chiave: VI + Deep + TA]                            ← TSK-343
 *   [Decision path chip + tabella alternativa accessibile]             ← TSK-343
 *   [Sezione "Perché questo verdetto" (citazioni wiki cross-dominio)]  ← TSK-343
 *   [Footer disclaimer]                                                ← TSK-343
 *
 * Pattern coerente con `TechnicalAnalysisPageClient` (US-101) e
 * `DeepAnalysisPageClient` (US-046): rotta orchestrata da un componente
 * client unico, hook SWR `useSummary` con gating `enabled`, skeleton +
 * ErrorPanel coerenti con design system EP-016.
 *
 * Il verdetto arriva GIÀ deciso dal BE (ADR-030 §3+§5, gate VI primario
 * hardcoded Kotlin). Il FE NON ricalcola alcunché — solo renderizza.
 *
 * Lazy load: l'hook `useSummary` parte SOLO quando ticker è valido
 * (`enabled = true`) — il fetch a `/api/analysis/{ticker}/summary` non
 * parte se l'utente naviga a `/analysis/deep` o `/analysis/technical`
 * (quelle rotte NON istanziano il hook).
 *
 * Riferimento:
 *  - design_&_architecture/api/openapi.yaml §/api/analysis/{ticker}/summary
 *  - US-104 §Layout + §Comportamento + §AC
 *  - ADR-030 §3+§4+§5
 */

export interface SummaryPageClientProps {
  /** Ticker normalizzato (uppercase) già estratto dalla query string. */
  readonly ticker: string;
}

export function SummaryPageClient(
  props: SummaryPageClientProps,
): React.ReactElement {
  const { ticker } = props;
  const { data, isLoading, error, mutate } = useSummary(ticker, {
    enabled: ticker.length > 0,
  });

  return (
    <main
      data-testid="summary-page"
      className="mx-auto flex min-h-screen max-w-6xl flex-col gap-6 px-6 py-10"
    >
      <SummaryHeader ticker={ticker} />

      {isLoading ? <SummarySkeleton /> : null}

      {!isLoading && error !== undefined && data === undefined ? (
        <SummaryErrorPanel
          status={error.status}
          message={error.message}
          onRetry={() => void mutate()}
        />
      ) : null}

      {data !== undefined ? (
        <>
          {/* TSK-342 — Hero verdetto a 4 stati. */}
          <SummaryHero
            ticker={data.ticker}
            verdict={data.summaryVerdict}
            viVerdict={data.viVerdict}
            deepVerdict={data.deepVerdict}
            reentryCondition={data.reentryCondition}
            evaluatedAt={data.evaluatedAt}
          />

          {/* TSK-342 — Banner anti-COPART (solo se warningAntiCopart popolato). */}
          {data.warningAntiCopart !== null &&
          data.warningAntiCopart.length > 0 ? (
            <AntiCopartBanner warning={data.warningAntiCopart} />
          ) : null}

          {/* TSK-343 — 3 card fattori chiave. */}
          <SummaryFactorCards
            ticker={data.ticker}
            rationale={data.rationale}
            viVerdict={data.viVerdict}
            deepAnalysisStatus={data.deepAnalysisStatus}
            deepVerdict={data.deepVerdict}
            taVerdict={data.taVerdict}
          />

          {/* TSK-343 — Decision path + alternativa accessibile. */}
          <DecisionPathChip path={data.rationale.decisionPath} />

          {/* TSK-343 — Sezione citazioni wiki raggruppate per dominio. */}
          <WikiCitationsSection citations={data.wikiCitations} />

          {/* TSK-351 — Pannello "Verifica storica" (backtest on-demand,
              EP-024 Fase 3). Il bottone "BACKTEST" è la prima riga del
              pannello: prima del click nessuna chiamata a
              /api/analysis/{ticker}/backtest parte (gate `idle` dell'hook
              `useBacktest`). */}
          <BacktestPanel ticker={data.ticker} />

          {/* TSK-343 — Footer disclaimer. */}
          <SummaryDisclaimerFooter />
        </>
      ) : null}
    </main>
  );
}

/* ------------------------------------------------------------------ */
/*  Header (tab nav + titolo)                                          */
/* ------------------------------------------------------------------ */

function SummaryHeader({
  ticker,
}: {
  readonly ticker: string;
}): React.ReactElement {
  return (
    <header className="flex flex-col gap-3">
      <AnalysisTabNav ticker={ticker} current="summary" />
      <h1
        data-testid="summary-page-title"
        className="text-3xl font-bold tracking-tight text-slate-900 dark:text-slate-100"
      >
        Riepilogo — {ticker}
      </h1>
      <p className="text-sm text-on-surface/60">
        Verdetto azionabile cross-dominio (Value Investing + Deep Analysis +
        Technical Analysis). Il gate VI resta primario.
      </p>
    </header>
  );
}

/* ------------------------------------------------------------------ */
/*  Skeleton loader                                                    */
/* ------------------------------------------------------------------ */

function SummarySkeleton(): React.ReactElement {
  return (
    <div
      data-testid="summary-loading"
      role="status"
      aria-busy="true"
      aria-live="polite"
      className="flex flex-col gap-4"
    >
      <p className="text-sm font-medium text-on-surface/70">
        Sto compilando il Riepilogo cross-dominio…
      </p>
      <div className="h-32 animate-pulse rounded-lg bg-slate-100 dark:bg-slate-800" />
      <div className="h-16 animate-pulse rounded-lg bg-slate-100 dark:bg-slate-800" />
      <div className="grid gap-4 md:grid-cols-3">
        <div className="h-48 animate-pulse rounded-lg bg-slate-100 dark:bg-slate-800" />
        <div className="h-48 animate-pulse rounded-lg bg-slate-100 dark:bg-slate-800" />
        <div className="h-48 animate-pulse rounded-lg bg-slate-100 dark:bg-slate-800" />
      </div>
      <div className="h-20 animate-pulse rounded-lg bg-slate-100 dark:bg-slate-800" />
    </div>
  );
}

/* ------------------------------------------------------------------ */
/*  Error panel                                                        */
/* ------------------------------------------------------------------ */

function SummaryErrorPanel({
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
      data-testid="summary-error"
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
          data-testid="summary-error-retry"
        >
          Riprova
        </Button>
      </div>
    </div>
  );
}
