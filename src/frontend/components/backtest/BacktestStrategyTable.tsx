'use client';

import { cn } from '@/lib/utils/cn';
import type {
  BacktestExitBreakdown,
  BacktestStrategyMetrics,
  BacktestStrategy,
} from '@/lib/api/backtest';

/**
 * BacktestStrategyTable — TSK-351 (US-106, EP-024 Fase 3).
 *
 * Tabella di confronto delle 3 strategie simulate (US-105):
 *
 *   Strategia        | Trade | Win% | Rend.medio | Totale | Holding medio | Exit
 *   EP-024 (timing)  |   N   |  X%  |   +x.xx%   | +y.yy% |  Zg          | breakdown
 *   Solo sconto (VI) |   N   |  X%  |   +x.xx%   | +y.yy% |  Zg          | breakdown
 *   Buy & hold       |   1   |  —   |     —      | +y.yy% |  Zg          | —
 *
 * `exitBreakdown` (VI_TARGET / STOP_HIT / HORIZON) è renderizzato in una riga
 * espandibile sotto, tramite `<details>`. Non sovraffolla la tabella
 * principale ma rimane raggiungibile (US-106 §AC).
 *
 * Accessibility (WCAG 2.2 AA — EP-016):
 *  - `<table>` semantica con `<caption>`, `<thead>`/`<tbody>`,
 *    `scope="col"` sugli header di colonna e `scope="row"` sui label di riga.
 *  - Numeri in `tabular-nums` per allineamento ottico.
 *  - Color contrast verificato (slate shades + dark mode).
 *  - `<details>` cliccabile via tastiera + focus visibile.
 *
 * BUY_AND_HOLD edge cases (US-105 §schema):
 *  - winRate / avgRealizedRewardRisk / exitBreakdown = null → render '—'.
 *  - avgReturnPct = totalReturnPct (per trade unico) ma il BE può comunque
 *    inviarlo separatamente; rendiamo verbatim.
 */

const STRATEGY_LABELS: Readonly<Record<BacktestStrategy, string>> = {
  EP024_ENTER_NOW: 'EP-024 (timing)',
  VI_ONLY: 'Solo sconto (VI)',
  BUY_AND_HOLD: 'Buy & hold',
};

const STRATEGY_DESCRIPTIONS: Readonly<Record<BacktestStrategy, string>> = {
  EP024_ENTER_NOW:
    'Entra sui segnali EP-024 ENTER_NOW (gate VI + TA), esce su VI_TARGET / STOP_HIT / HORIZON.',
  VI_ONLY:
    'Entra ad ogni t con gate VI positivo (ignora il timing TA). Baseline per isolare il valore del layer di timing.',
  BUY_AND_HOLD:
    'Compra al primo EOD della finestra, vende all\'ultimo. Trade unico — niente metriche di win-rate.',
};

const STRATEGY_ORDER: ReadonlyArray<BacktestStrategy> = [
  'EP024_ENTER_NOW',
  'VI_ONLY',
  'BUY_AND_HOLD',
];

function formatSignedPct(value: number | null): string {
  if (value === null || !Number.isFinite(value)) return '—';
  const sign = value > 0 ? '+' : '';
  return `${sign}${value.toFixed(2)}%`;
}

function formatPctNoSign(value: number | null): string {
  if (value === null || !Number.isFinite(value)) return '—';
  return `${value.toFixed(1)}%`;
}

function formatDays(value: number | null): string {
  if (value === null || !Number.isFinite(value)) return '—';
  return `${Math.round(value)}g`;
}

function formatWinRate(value: number | null): string {
  if (value === null || !Number.isFinite(value)) return '—';
  // BE espone winRate come frazione 0..1 (description "Quota di trade con
  // returnPct > 0"); rendiamo come percent.
  if (value <= 1) {
    return `${(value * 100).toFixed(0)}%`;
  }
  // Tolleriamo l'eventualità che il BE lo serva già in punti percentuali.
  return `${value.toFixed(0)}%`;
}

function formatExitBreakdown(breakdown: BacktestExitBreakdown | null): string {
  if (breakdown === null) return '—';
  const parts: Array<string> = [];
  if (breakdown.viTarget > 0) parts.push(`target ${breakdown.viTarget}`);
  if (breakdown.stopHit > 0) parts.push(`stop ${breakdown.stopHit}`);
  if (breakdown.horizon > 0) parts.push(`orizzonte ${breakdown.horizon}`);
  return parts.length === 0 ? '—' : parts.join(' · ');
}

export interface BacktestStrategyTableProps {
  readonly strategies: ReadonlyArray<BacktestStrategyMetrics>;
}

export function BacktestStrategyTable(
  props: BacktestStrategyTableProps,
): React.ReactElement {
  const { strategies } = props;

  // Manteniamo l'ordine canonico (EP-024 / VI / B&H). Se il BE varia
  // l'ordine, lo normalizziamo qui — la tabella è il prodotto FE finale.
  const rows = STRATEGY_ORDER.map((key) =>
    strategies.find((s) => s.strategy === key),
  ).filter((s): s is BacktestStrategyMetrics => s !== undefined);

  return (
    <section
      data-testid="backtest-strategy-table-section"
      className="flex flex-col gap-3"
      aria-labelledby="backtest-strategy-table-heading"
    >
      <h3
        id="backtest-strategy-table-heading"
        className="text-base font-semibold text-on-surface"
      >
        Confronto strategie
      </h3>
      <div className="overflow-x-auto rounded-md border border-outline-variant">
        <table
          data-testid="backtest-strategy-table"
          className="w-full table-auto text-sm"
        >
          <caption className="sr-only">
            Confronto delle metriche aggregate per le 3 strategie simulate
            (EP-024 timing, solo sconto VI, buy &amp; hold) sulla stessa finestra
            di lookback.
          </caption>
          <thead className="bg-surface-container-high text-xs uppercase tracking-wide text-on-surface/70">
            <tr>
              <th scope="col" className="px-3 py-2 text-left">
                Strategia
              </th>
              <th scope="col" className="px-3 py-2 text-right">
                Trade
              </th>
              <th scope="col" className="px-3 py-2 text-right">
                Win%
              </th>
              <th scope="col" className="px-3 py-2 text-right">
                Rend. medio
              </th>
              <th scope="col" className="px-3 py-2 text-right">
                Totale
              </th>
              <th scope="col" className="px-3 py-2 text-right">
                Holding medio
              </th>
              <th scope="col" className="px-3 py-2 text-left">
                Exit breakdown
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr
                key={row.strategy}
                data-testid={`backtest-strategy-row-${row.strategy}`}
                data-no-signals={row.noSignalsInPeriod ? 'true' : 'false'}
                className={cn(
                  'border-t border-outline-variant/60',
                  row.noSignalsInPeriod
                    ? 'bg-amber-50/40 dark:bg-amber-950/20'
                    : '',
                )}
              >
                <th
                  scope="row"
                  className="px-3 py-2 text-left font-medium text-on-surface"
                >
                  <span className="block">
                    {STRATEGY_LABELS[row.strategy]}
                  </span>
                  <span className="block text-xs font-normal text-on-surface/60">
                    {STRATEGY_DESCRIPTIONS[row.strategy]}
                  </span>
                </th>
                <td className="px-3 py-2 text-right tabular-nums text-on-surface">
                  {row.trades}
                </td>
                <td className="px-3 py-2 text-right tabular-nums text-on-surface">
                  {formatWinRate(row.winRate)}
                </td>
                <td className="px-3 py-2 text-right tabular-nums text-on-surface">
                  {formatSignedPct(row.avgReturnPct)}
                </td>
                <td className="px-3 py-2 text-right tabular-nums text-on-surface">
                  {formatSignedPct(row.totalReturnPct)}
                </td>
                <td className="px-3 py-2 text-right tabular-nums text-on-surface">
                  {formatDays(row.avgHoldingDays)}
                </td>
                <td className="px-3 py-2 text-left text-xs text-on-surface/80">
                  {formatExitBreakdown(row.exitBreakdown)}
                  {row.noSignalsInPeriod ? (
                    <span
                      data-testid={`backtest-strategy-row-${row.strategy}-no-signals`}
                      className="ml-2 inline-flex items-center rounded bg-amber-100 px-1.5 py-0.5 text-amber-900 dark:bg-amber-900/40 dark:text-amber-100"
                    >
                      0 segnali
                    </span>
                  ) : null}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <details
        data-testid="backtest-strategy-details"
        className="rounded-md border border-outline-variant bg-surface-container p-3 text-sm"
      >
        <summary className="cursor-pointer font-medium text-on-surface focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary">
          Mediana, drawdown massimo e reward/risk realizzato
        </summary>
        <table className="mt-3 w-full table-auto text-sm">
          <caption className="sr-only">
            Metriche di robustezza aggiuntive per ciascuna strategia (mediana
            del rendimento, drawdown intra-trade massimo, reward/risk medio
            realizzato).
          </caption>
          <thead>
            <tr className="border-b border-outline-variant text-xs uppercase tracking-wide text-on-surface/70">
              <th scope="col" className="px-2 py-1 text-left">
                Strategia
              </th>
              <th scope="col" className="px-2 py-1 text-right">
                Mediana rend.
              </th>
              <th scope="col" className="px-2 py-1 text-right">
                Max drawdown intra-trade
              </th>
              <th scope="col" className="px-2 py-1 text-right">
                Reward/Risk medio
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr
                key={`${row.strategy}-aux`}
                className="border-b border-outline-variant/40"
              >
                <th
                  scope="row"
                  className="px-2 py-1 text-left font-medium text-on-surface"
                >
                  {STRATEGY_LABELS[row.strategy]}
                </th>
                <td className="px-2 py-1 text-right tabular-nums">
                  {formatSignedPct(row.medianReturnPct)}
                </td>
                <td className="px-2 py-1 text-right tabular-nums">
                  {formatPctNoSign(row.maxTradeDrawdownPct)}
                </td>
                <td className="px-2 py-1 text-right tabular-nums">
                  {row.avgRealizedRewardRisk !== null &&
                  Number.isFinite(row.avgRealizedRewardRisk)
                    ? row.avgRealizedRewardRisk.toFixed(2)
                    : '—'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </section>
  );
}
