'use client';

import { useState } from 'react';
import { AlertCircle, Info } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from '@/components/ui/Card';
import { cn } from '@/lib/utils/cn';
import { useBacktest } from '@/lib/hooks/useBacktest';
import { useEquityLocalStorage } from '@/lib/hooks/useEquityLocalStorage';
import type {
  BacktestHorizonMonths,
  BacktestResponse,
  BacktestYearsOption,
} from '@/lib/api/backtest';
import { BacktestTriggerButton } from './BacktestTriggerButton';
import { BacktestSelectors } from './BacktestSelectors';
import { BacktestVerdictHero } from './BacktestVerdictHero';
import { BacktestStrategyTable } from './BacktestStrategyTable';
import { BacktestTradesChart } from './BacktestTradesChart';
import { BacktestCaveatBanner } from './BacktestCaveatBanner';

/**
 * BacktestPanel — TSK-351 (US-106, EP-024 Fase 3).
 *
 * Orchestrator del pannello "Verifica storica":
 *
 *   [Selettori years / horizon / equity + bottone BACKTEST]
 *   ── on-demand: prima del click solo il bottone è visibile ──
 *   [Verdetto sintetico timingEdge — 3 casi: POSITIVE/NEUTRAL/NEGATIVE_EDGE]
 *   [Tabella confronto 3 strategie con exitBreakdown]
 *   [Timeline marker entry/exit sul price chart]
 *   [Banner caveat SEMPRE visibile]
 *
 * Stati del pannello (driven dal hook `useBacktest`):
 *  - `idle`    → solo bottone "BACKTEST" + selettori abilitati.
 *  - `loading` → skeleton (selettori disabilitati).
 *  - `result`  → verdetto + tabella + chart + caveat banner.
 *  - `empty`   → caso 0 segnali EP-024 nella finestra: messaggio dedicato +
 *                tabella baseline (VI puro / buy&hold) + caveat banner.
 *  - `error`   → ErrorPanel inline + retry esplicito + caveat banner restano
 *                ASSENTI (il banner si mostra solo quando esiste un risultato
 *                a cui riferirsi).
 *
 * Caso `status = INSUFFICIENT_HISTORY` (US-105): payload OK ma status diverso.
 * Il pannello mostra un messaggio dedicato "Storico insufficiente" + caveat
 * banner; nessun chart parziale fuorviante (AC US-106).
 *
 * Lazy-trigger garantito: `useBacktest` parte in `idle` con SWR key = null.
 * NESSUNA chiamata a `/api/analysis/{ticker}/backtest` parte al montaggio
 * del componente. Il primo fetch avviene solo dopo `trigger()`.
 *
 * Cambio parametri (years / horizonMonths / equity):
 *  - Prima del trigger → cambia solo lo stato locale (nessun fetch).
 *  - Dopo il trigger   → SWR key cambia → refetch automatico (AC US-106).
 *
 * Sorgenti:
 *  - OpenAPI §/api/analysis/{ticker}/backtest + schemas (US-105)
 *  - US-106 §"Pannello esiti", §"Stati non-happy", §"Controlli"
 *  - ADR-030
 */

const PANEL_ID = 'backtest-panel-content';

export interface BacktestPanelProps {
  readonly ticker: string;
  /** Override `data-testid` per E2E (default: 'backtest-panel'). */
  readonly testId?: string;
}

export function BacktestPanel(props: BacktestPanelProps): React.ReactElement {
  const { ticker, testId = 'backtest-panel' } = props;

  const [years, setYears] = useState<BacktestYearsOption>(5);
  const [horizonMonths, setHorizonMonths] =
    useState<BacktestHorizonMonths>(6);
  const {
    equity,
    hydrated: equityHydrated,
    setEquity,
  } = useEquityLocalStorage();

  const backtest = useBacktest({
    ticker,
    years,
    horizonMonths,
    // Forwarda l'equity solo dopo l'hydration per evitare un primo fetch con
    // valore default seguito da un refetch al cambio (stessa logica usata per
    // `useTechnicalAnalysis` lato TSK-335).
    equity: equityHydrated ? equity : undefined,
  });

  const { status, data, error, trigger, retry } = backtest;
  const isLoading = status === 'loading';
  const hasResult = status === 'result' || status === 'empty';

  return (
    <Card data-testid={testId} className="border-l-4 border-slate-300 dark:border-slate-700">
      <CardHeader>
        <CardTitle as="h2">Verifica storica — {ticker}</CardTitle>
        <p className="text-sm text-on-surface/70">
          Misura se il layer di timing EP-024 ha davvero aggiunto soldi rispetto
          a comprare appena il titolo era a sconto. Verifica falsificabile —
          non promessa.
        </p>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <BacktestSelectors
            years={years}
            onYearsChange={setYears}
            horizonMonths={horizonMonths}
            onHorizonChange={setHorizonMonths}
            equity={equity}
            equityHydrated={equityHydrated}
            onEquityChange={setEquity}
            disabled={isLoading}
          />
          <BacktestTriggerButton
            onTrigger={trigger}
            isLoading={isLoading}
            hasResult={hasResult}
            panelId={PANEL_ID}
          />
        </div>

        <div
          id={PANEL_ID}
          role="region"
          aria-label="Risultati della verifica storica"
          className="flex flex-col gap-4"
        >
          {status === 'idle' ? <IdleHint /> : null}

          {status === 'loading' ? <LoadingSkeleton /> : null}

          {status === 'error' && error !== undefined ? (
            <ErrorPanel
              status={error.status}
              message={error.message}
              onRetry={retry}
            />
          ) : null}

          {status === 'result' && data !== undefined ? (
            <SuccessContent ticker={ticker} data={data} />
          ) : null}

          {status === 'empty' && data !== undefined ? (
            <EmptyContent ticker={ticker} data={data} />
          ) : null}

          {/* INSUFFICIENT_HISTORY è uno stato del payload BE: dentro
              `SuccessContent` viene reindirizzato a `InsufficientHistoryPanel`
              (status hook = 'result', ma data.status = 'INSUFFICIENT_HISTORY'). */}
        </div>
      </CardContent>
    </Card>
  );
}

/* ------------------------------------------------------------------ */
/*  Hint pre-trigger                                                    */
/* ------------------------------------------------------------------ */

function IdleHint(): React.ReactElement {
  return (
    <p
      data-testid="backtest-idle-hint"
      className="text-sm text-on-surface/70"
    >
      Clicca <strong>BACKTEST</strong> per simulare la strategia EP-024 su questo
      ticker negli anni scelti. Il calcolo richiede alcuni secondi.
    </p>
  );
}

/* ------------------------------------------------------------------ */
/*  Loading skeleton                                                    */
/* ------------------------------------------------------------------ */

function LoadingSkeleton(): React.ReactElement {
  return (
    <div
      data-testid="backtest-loading"
      role="status"
      aria-busy="true"
      aria-live="polite"
      className="flex flex-col gap-3"
    >
      <p className="text-sm font-medium text-on-surface/70">
        Sto ricostruendo lo storico point-in-time…
      </p>
      <div className="h-24 animate-pulse rounded-lg bg-slate-100 dark:bg-slate-800" />
      <div className="h-32 animate-pulse rounded-lg bg-slate-100 dark:bg-slate-800" />
      <div className="h-64 animate-pulse rounded-lg bg-slate-100 dark:bg-slate-800" />
    </div>
  );
}

/* ------------------------------------------------------------------ */
/*  Error                                                               */
/* ------------------------------------------------------------------ */

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
      data-testid="backtest-error"
      role="alert"
      className={cn(
        'flex flex-col gap-3 rounded-lg border border-red-200 bg-red-50 p-4 ' +
          'dark:border-red-900 dark:bg-red-950',
      )}
    >
      <p className="inline-flex items-center gap-2 text-sm font-medium text-red-800 dark:text-red-200">
        <AlertCircle aria-hidden="true" className="h-4 w-4" />
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
          data-testid="backtest-error-retry"
        >
          Riprova
        </Button>
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/*  Success — status=OK & EP-024 ha segnali                             */
/* ------------------------------------------------------------------ */

function SuccessContent({
  ticker,
  data,
}: {
  readonly ticker: string;
  readonly data: BacktestResponse;
}): React.ReactElement {
  // Status del payload BE: distinguiamo OK (3 strategie + timingEdge popolati)
  // da INSUFFICIENT_HISTORY (status di dominio, NON errore HTTP).
  if (data.status === 'INSUFFICIENT_HISTORY') {
    return <InsufficientHistoryPanel data={data} />;
  }
  // Difesa profonda: schema OpenAPI dichiara nullable per strategies/timingEdge/
  // trades/window quando INSUFFICIENT_HISTORY — qui status=OK ma per type
  // safety verifichiamo comunque.
  if (
    data.strategies === null ||
    data.timingEdge === null ||
    data.trades === null ||
    data.window === null
  ) {
    return (
      <div
        data-testid="backtest-success-malformed"
        role="alert"
        className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800 dark:border-red-900 dark:bg-red-950 dark:text-red-200"
      >
        Payload backtest incoerente (status=OK ma campi obbligatori mancanti).
        Riprova più tardi.
      </div>
    );
  }
  return (
    <>
      <BacktestVerdictHero
        ticker={ticker}
        timingEdge={data.timingEdge}
        strategies={data.strategies}
      />
      <BacktestStrategyTable strategies={data.strategies} />
      <BacktestTradesChart
        ticker={ticker}
        trades={data.trades}
        window={data.window}
      />
      <BacktestCaveatBanner caveats={data.caveats} />
    </>
  );
}

/* ------------------------------------------------------------------ */
/*  Empty — 0 segnali EP024_ENTER_NOW nella finestra                    */
/* ------------------------------------------------------------------ */

function EmptyContent({
  ticker,
  data,
}: {
  readonly ticker: string;
  readonly data: BacktestResponse;
}): React.ReactElement {
  return (
    <>
      <div
        data-testid="backtest-empty"
        role="status"
        aria-live="polite"
        className={cn(
          'flex items-start gap-3 rounded-lg border border-blue-200 ' +
            'bg-blue-50 p-4 dark:border-blue-900 dark:bg-blue-950/40',
        )}
      >
        <Info
          aria-hidden="true"
          className="mt-0.5 h-5 w-5 shrink-0 text-blue-700 dark:text-blue-300"
        />
        <div>
          <h3 className="text-sm font-bold uppercase tracking-wide text-blue-900 dark:text-blue-100">
            Nessun momento d&apos;ingresso EP-024 in questo periodo
          </h3>
          <p className="mt-1 text-sm text-blue-900/90 dark:text-blue-100/90">
            Sul ticker {ticker}, nella finestra scelta, il rule engine non ha
            mai prodotto un verdetto <code>ENTER_NOW</code>. Mostriamo comunque
            le baseline (solo sconto VI, buy &amp; hold) per dare contesto:
            sono i rendimenti che si sarebbero ottenuti senza il layer di
            timing EP-024.
          </p>
        </div>
      </div>
      {data.strategies !== null ? (
        <BacktestStrategyTable strategies={data.strategies} />
      ) : null}
      <BacktestCaveatBanner caveats={data.caveats} />
    </>
  );
}

/* ------------------------------------------------------------------ */
/*  INSUFFICIENT_HISTORY                                                */
/* ------------------------------------------------------------------ */

function InsufficientHistoryPanel({
  data,
}: {
  readonly data: BacktestResponse;
}): React.ReactElement {
  const reason =
    data.insufficientHistoryReason ??
    'Storico FMP non sufficiente per coprire la finestra richiesta.';
  return (
    <>
      <div
        data-testid="backtest-insufficient-history"
        role="status"
        aria-live="polite"
        className={cn(
          'flex items-start gap-3 rounded-lg border border-slate-200 ' +
            'bg-slate-50 p-4 dark:border-slate-800 dark:bg-slate-900/40',
        )}
      >
        <Info
          aria-hidden="true"
          className="mt-0.5 h-5 w-5 shrink-0 text-slate-600 dark:text-slate-300"
        />
        <div>
          <h3 className="text-sm font-bold uppercase tracking-wide text-slate-800 dark:text-slate-100">
            Storico insufficiente per un backtest affidabile
          </h3>
          <p className="mt-1 text-sm text-slate-700 dark:text-slate-200">
            {reason} Riduci la finestra di lookback oppure verifica un ticker
            con storico più lungo. Niente grafici parziali — i risultati su
            uno storico troppo corto sarebbero fuorvianti.
          </p>
        </div>
      </div>
      <BacktestCaveatBanner caveats={data.caveats} />
    </>
  );
}
