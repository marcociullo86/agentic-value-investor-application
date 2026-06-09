/**
 * Vitest — BacktestPanel stati (TSK-352, US-106, EP-024 Fase 3).
 *
 * Testa gli stati dell'hook `useBacktest` come osservabili dall'esterno:
 *  - idle    → bottone "BACKTEST" visibile, nessun pannello risultato.
 *  - loading → spinner visibile, bottone disabilitato.
 *  - empty   → messaggio "Nessun momento d'ingresso" + tabella baseline.
 *  - error   → NotificationProvider / panel errore visibile.
 *  - INSUFFICIENT_HISTORY → messaggio dedicato, nessun grafico.
 *
 * AC TSK-352:
 *  - Vitest: stati idle/loading/empty/error coperti.
 *  - Vitest: INSUFFICIENT_HISTORY → messaggio visibile, tabella e grafico assenti.
 *  - Vitest: banner caveat presente in tutti i 3 casi timingEdge.
 *
 * Strategy:
 *  - Mock di `@/lib/hooks/useBacktest` con `vi.fn()` per iniettare
 *    deterministicamente ogni stato senza dipendenze SWR / network.
 *  - Mock di `@/lib/hooks/useEquityLocalStorage` — restituisce un valore statico.
 *  - Mock dei componenti grafici pesanti (BacktestTradesChart) per non dipendere
 *    da Recharts/canvas in jsdom.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { BacktestPanel } from '../BacktestPanel';
import { useBacktest, type UseBacktestResult } from '@/lib/hooks/useBacktest';
import type { BacktestResponse } from '@/lib/api/backtest';

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

const mockTrigger = vi.fn();
const mockRetry = vi.fn();

vi.mock('@/lib/hooks/useBacktest', () => ({
  useBacktest: vi.fn(),
}));

vi.mock('@/lib/hooks/useEquityLocalStorage', () => ({
  useEquityLocalStorage: vi.fn(() => ({
    equity: 50000,
    hydrated: true,
    setEquity: vi.fn(),
    reset: vi.fn(),
  })),
}));

// Recharts usa canvas APIs non disponibili in jsdom: mocka il componente chart
// per evitare errori non pertinenti al test del pannello.
vi.mock('../BacktestTradesChart', () => ({
  BacktestTradesChart: () => (
    <div data-testid="backtest-trades-chart-mock">chart</div>
  ),
}));

// ---------------------------------------------------------------------------
// Fixture
// ---------------------------------------------------------------------------

const CAVEATS = {
  lookAheadResidual: true,
  singleTicker: true,
  notPortfolioPerformance: true,
};

const STRATEGIES_OK = [
  {
    strategy: 'EP024_ENTER_NOW' as const,
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
    strategy: 'VI_ONLY' as const,
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
    strategy: 'BUY_AND_HOLD' as const,
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

function makeResult(override: Partial<BacktestResponse> = {}): BacktestResponse {
  return {
    ticker: 'AAPL',
    evaluatedAt: '2026-06-09T08:00:00Z',
    status: 'OK',
    insufficientHistoryReason: null,
    window: {
      fromDate: '2021-06-09',
      toDate: '2026-06-09',
      years: 5,
      horizonMonths: 6,
    },
    strategies: STRATEGIES_OK,
    timingEdge: { timingEdgePct: 5.3, label: 'POSITIVE_EDGE', noSignalsInPeriod: false },
    trades: [],
    caveats: CAVEATS,
    ...override,
  };
}

function stubBacktest(partial: Partial<UseBacktestResult>) {
  vi.mocked(useBacktest).mockReturnValue({
    status: 'idle',
    data: undefined,
    error: undefined,
    trigger: mockTrigger,
    retry: mockRetry,
    triggered: false,
    ...partial,
  } as UseBacktestResult);
}

// ---------------------------------------------------------------------------
// Suite
// ---------------------------------------------------------------------------

describe('BacktestPanel — stati hook', () => {
  beforeEach(() => {
    mockTrigger.mockReset();
    mockRetry.mockReset();
  });

  // -------------------------------------------------------------------------
  // idle
  // -------------------------------------------------------------------------
  it('stato idle — bottone BACKTEST visibile, nessun pannello risultati', () => {
    stubBacktest({ status: 'idle', triggered: false });
    render(<BacktestPanel ticker="AAPL" />);

    // Bottone principale presente
    const btn = screen.getByTestId('backtest-trigger-button');
    expect(btn).toBeInTheDocument();
    expect(btn).not.toBeDisabled();

    // Hint pre-trigger
    expect(screen.getByTestId('backtest-idle-hint')).toBeInTheDocument();

    // Nessun risultato
    expect(screen.queryByTestId('backtest-verdict-hero')).not.toBeInTheDocument();
    expect(screen.queryByTestId('backtest-loading')).not.toBeInTheDocument();
    expect(screen.queryByTestId('backtest-error')).not.toBeInTheDocument();
  });

  it('stato idle — click sul bottone chiama trigger()', () => {
    stubBacktest({ status: 'idle', triggered: false });
    render(<BacktestPanel ticker="AAPL" />);

    fireEvent.click(screen.getByTestId('backtest-trigger-button'));
    expect(mockTrigger).toHaveBeenCalledTimes(1);
  });

  // -------------------------------------------------------------------------
  // loading
  // -------------------------------------------------------------------------
  it('stato loading — skeleton visibile, bottone disabilitato', () => {
    stubBacktest({ status: 'loading', triggered: true });
    render(<BacktestPanel ticker="AAPL" />);

    expect(screen.getByTestId('backtest-loading')).toBeInTheDocument();
    expect(screen.getByTestId('backtest-loading')).toHaveAttribute('role', 'status');
    expect(screen.getByTestId('backtest-loading')).toHaveAttribute('aria-busy', 'true');

    // Bottone disabilitato durante loading
    const btn = screen.getByTestId('backtest-trigger-button');
    expect(btn).toBeDisabled();
  });

  // -------------------------------------------------------------------------
  // result — POSITIVE_EDGE
  // -------------------------------------------------------------------------
  it('stato result POSITIVE_EDGE — verdetto, tabella e caveat banner visibili', () => {
    stubBacktest({
      status: 'result',
      triggered: true,
      data: makeResult({ timingEdge: { timingEdgePct: 5.3, label: 'POSITIVE_EDGE', noSignalsInPeriod: false } }),
    });
    render(<BacktestPanel ticker="AAPL" />);

    expect(screen.getByTestId('backtest-verdict-hero')).toBeInTheDocument();
    expect(screen.getByTestId('backtest-strategy-table')).toBeInTheDocument();
    // Banner caveat SEMPRE presente con il risultato
    expect(screen.getByTestId('backtest-caveat-banner')).toBeInTheDocument();
  });

  // -------------------------------------------------------------------------
  // result — NEUTRAL
  // -------------------------------------------------------------------------
  it('stato result NEUTRAL — verdetto neutro + caveat banner', () => {
    stubBacktest({
      status: 'result',
      triggered: true,
      data: makeResult({ timingEdge: { timingEdgePct: 0.8, label: 'NEUTRAL', noSignalsInPeriod: false } }),
    });
    render(<BacktestPanel ticker="AAPL" />);

    const hero = screen.getByTestId('backtest-verdict-hero');
    expect(hero).toHaveAttribute('data-edge', 'NEUTRAL');
    expect(screen.getByTestId('backtest-caveat-banner')).toBeInTheDocument();
  });

  // -------------------------------------------------------------------------
  // result — NEGATIVE_EDGE
  // -------------------------------------------------------------------------
  it('stato result NEGATIVE_EDGE — verdetto amber + caveat banner (pari risalto)', () => {
    stubBacktest({
      status: 'result',
      triggered: true,
      data: makeResult({ timingEdge: { timingEdgePct: -3.1, label: 'NEGATIVE_EDGE', noSignalsInPeriod: false } }),
    });
    render(<BacktestPanel ticker="AAPL" />);

    const hero = screen.getByTestId('backtest-verdict-hero');
    expect(hero).toBeInTheDocument();
    expect(hero).toHaveAttribute('data-edge', 'NEGATIVE_EDGE');
    expect(screen.getByTestId('backtest-caveat-banner')).toBeInTheDocument();
  });

  // -------------------------------------------------------------------------
  // empty (0 segnali EP-024)
  // -------------------------------------------------------------------------
  it('stato empty — messaggio "Nessun momento d\'ingresso" + tabella baseline visibile', () => {
    const emptyData = makeResult({
      strategies: STRATEGIES_OK.map((s) =>
        s.strategy === 'EP024_ENTER_NOW'
          ? { ...s, noSignalsInPeriod: true }
          : s,
      ),
      timingEdge: { timingEdgePct: null, label: 'NEUTRAL', noSignalsInPeriod: true },
    });
    stubBacktest({ status: 'empty', triggered: true, data: emptyData });
    render(<BacktestPanel ticker="AAPL" />);

    const emptyMsg = screen.getByTestId('backtest-empty');
    expect(emptyMsg).toBeInTheDocument();
    expect(emptyMsg.textContent).toMatch(/Nessun momento d.ingresso/i);

    // Tabella baseline ancora visibile
    expect(screen.getByTestId('backtest-strategy-table')).toBeInTheDocument();
    // Caveat banner
    expect(screen.getByTestId('backtest-caveat-banner')).toBeInTheDocument();
  });

  // -------------------------------------------------------------------------
  // error
  // -------------------------------------------------------------------------
  it('stato error — panel errore con messaggio + bottone retry', () => {
    stubBacktest({
      status: 'error',
      triggered: true,
      error: { status: 503, message: 'Servizio dati di mercato temporaneamente non disponibile. Riprova più tardi.' },
    });
    render(<BacktestPanel ticker="AAPL" />);

    const errorPanel = screen.getByTestId('backtest-error');
    expect(errorPanel).toBeInTheDocument();
    expect(errorPanel).toHaveAttribute('role', 'alert');
    expect(errorPanel.textContent).toMatch(/servizio dati di mercato/i);

    const retryBtn = screen.getByTestId('backtest-error-retry');
    expect(retryBtn).toBeInTheDocument();
    fireEvent.click(retryBtn);
    expect(mockRetry).toHaveBeenCalledTimes(1);
  });

  // -------------------------------------------------------------------------
  // INSUFFICIENT_HISTORY (payload BE con status != OK)
  // -------------------------------------------------------------------------
  it('INSUFFICIENT_HISTORY — messaggio dedicato, nessun grafico parziale (AC TSK-352)', () => {
    const insufficientData: BacktestResponse = {
      ticker: 'NEWIPOQ',
      evaluatedAt: '2026-06-09T08:00:00Z',
      status: 'INSUFFICIENT_HISTORY',
      insufficientHistoryReason: 'Storico FMP copre solo 18 mesi.',
      window: null,
      strategies: null,
      timingEdge: null,
      trades: null,
      caveats: CAVEATS,
    };
    stubBacktest({ status: 'result', triggered: true, data: insufficientData });
    render(<BacktestPanel ticker="NEWIPOQ" />);

    const insufficientPanel = screen.getByTestId('backtest-insufficient-history');
    expect(insufficientPanel).toBeInTheDocument();
    expect(insufficientPanel.textContent).toMatch(/storico insufficiente/i);
    expect(insufficientPanel.textContent).toMatch(/Storico FMP copre solo 18 mesi/i);

    // Nessuna tabella, nessun chart (AC TSK-352: no grafici parziali fuorvianti)
    expect(screen.queryByTestId('backtest-strategy-table')).not.toBeInTheDocument();
    expect(screen.queryByTestId('backtest-trades-chart-mock')).not.toBeInTheDocument();
    expect(screen.queryByTestId('backtest-verdict-hero')).not.toBeInTheDocument();

    // Caveat banner anche in INSUFFICIENT_HISTORY
    expect(screen.getByTestId('backtest-caveat-banner')).toBeInTheDocument();
  });
});
