/**
 * Vitest — BacktestStrategyTable (TSK-352, US-106, EP-024 Fase 3).
 *
 * Copre:
 *  - Tabella renderizzata con 3 righe strategia.
 *  - Colonne con scope="col" (a11y WCAG 2.2 AA — AC TSK-352).
 *  - Riga scope="row" sulle celle strategia (a11y semantica tabella).
 *  - Valori formattati (win%, rend. medio, holding).
 *  - Strategia BUY_AND_HOLD mostra "—" per win% e rend.medio null.
 *
 * AC TSK-352:
 *  - a11y: tabella header semantici scope=col; screen reader può navigare righe e colonne.
 */

import { describe, it, expect } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { BacktestStrategyTable } from '../BacktestStrategyTable';
import type { BacktestStrategyMetrics } from '@/lib/api/backtest';

const STRATEGIES: BacktestStrategyMetrics[] = [
  {
    strategy: 'EP024_ENTER_NOW',
    trades: 7,
    winRate: 0.71,
    avgReturnPct: 12.4,
    medianReturnPct: 11.8,
    avgHoldingDays: 142,
    avgRealizedRewardRisk: 2.3,
    totalReturnPct: 41.2,
    maxTradeDrawdownPct: 8.5,
    exitBreakdown: { viTarget: 4, stopHit: 1, horizon: 2 },
    noSignalsInPeriod: false,
  },
  {
    strategy: 'VI_ONLY',
    trades: 14,
    winRate: 0.57,
    avgReturnPct: 7.1,
    medianReturnPct: 6.5,
    avgHoldingDays: 198,
    avgRealizedRewardRisk: 1.4,
    totalReturnPct: 38.0,
    maxTradeDrawdownPct: 14.2,
    exitBreakdown: { viTarget: 6, stopHit: 3, horizon: 5 },
    noSignalsInPeriod: false,
  },
  {
    strategy: 'BUY_AND_HOLD',
    trades: 1,
    winRate: null,
    avgReturnPct: null,
    medianReturnPct: null,
    avgHoldingDays: 1826,
    avgRealizedRewardRisk: null,
    totalReturnPct: 33.5,
    maxTradeDrawdownPct: null,
    exitBreakdown: null,
    noSignalsInPeriod: false,
  },
];

describe('BacktestStrategyTable', () => {
  it('renderizza la tabella con 3 righe strategia', () => {
    render(<BacktestStrategyTable strategies={STRATEGIES} />);

    const table = screen.getByTestId('backtest-strategy-table');
    expect(table).toBeInTheDocument();

    expect(screen.getByTestId('backtest-strategy-row-EP024_ENTER_NOW')).toBeInTheDocument();
    expect(screen.getByTestId('backtest-strategy-row-VI_ONLY')).toBeInTheDocument();
    expect(screen.getByTestId('backtest-strategy-row-BUY_AND_HOLD')).toBeInTheDocument();
  });

  it('header colonne hanno scope="col" (a11y WCAG 2.2 AA)', () => {
    render(<BacktestStrategyTable strategies={STRATEGIES} />);

    const table = screen.getByTestId('backtest-strategy-table');
    const colHeaders = table.querySelectorAll('th[scope="col"]');
    expect(colHeaders.length).toBeGreaterThanOrEqual(4); // Strategia, Trade, Win%, Rend. medio, Totale, Holding, Exit
  });

  it('riga EP024 ha scope="row" sulla cella strategia (a11y semantica)', () => {
    render(<BacktestStrategyTable strategies={STRATEGIES} />);

    const ep024Row = screen.getByTestId('backtest-strategy-row-EP024_ENTER_NOW');
    const rowHeader = ep024Row.querySelector('th[scope="row"]');
    expect(rowHeader).not.toBeNull();
  });

  it('EP024 mostra 7 trade + 71% win + +12.40% medio', () => {
    render(<BacktestStrategyTable strategies={STRATEGIES} />);

    const ep024Row = screen.getByTestId('backtest-strategy-row-EP024_ENTER_NOW');
    expect(within(ep024Row).getByText('7')).toBeInTheDocument();
    expect(within(ep024Row).getByText('71%')).toBeInTheDocument();
    expect(within(ep024Row).getByText('+12.40%')).toBeInTheDocument();
  });

  it('BUY_AND_HOLD mostra "—" per win% e rendimento medio null', () => {
    render(<BacktestStrategyTable strategies={STRATEGIES} />);

    const buhRow = screen.getByTestId('backtest-strategy-row-BUY_AND_HOLD');
    // I campi null appaiono come '—'
    const cells = buhRow.querySelectorAll('td');
    // win% cell e avgReturnPct cell devono contenere '—'
    const emDashCells = Array.from(cells).filter((c) => c.textContent?.trim() === '—');
    expect(emDashCells.length).toBeGreaterThanOrEqual(2);
  });

  it('tabella ha <caption> per screen reader (a11y)', () => {
    render(<BacktestStrategyTable strategies={STRATEGIES} />);

    const table = screen.getByTestId('backtest-strategy-table');
    const caption = table.querySelector('caption');
    expect(caption).not.toBeNull();
    expect(caption!.textContent).toMatch(/confronto/i);
  });
});
