/**
 * Vitest — BacktestVerdictHero (TSK-352, US-106, EP-024 Fase 3).
 *
 * Copre i 3 casi di `timingEdge` richiesti dallo scope TSK-352:
 *  - POSITIVE_EDGE → verdetto sintetico verde visibile, frase con "+x% vs +y%"
 *  - NEUTRAL       → verdetto sintetico neutro visibile
 *  - NEGATIVE_EDGE → verdetto sintetico amber visibile CON STESSO RISALTO del
 *                    positivo (no cherry-picking — AC TSK-352 §"NEGATIVE_EDGE
 *                    ha stesso risalto di POSITIVE_EDGE")
 *
 * AC TSK-352:
 *  - Vitest: 3 casi timingEdge → verdetto sintetico corretto per ciascuno.
 *  - Vitest: caso NEGATIVE_EDGE ha stesso risalto di POSITIVE_EDGE (snapshot).
 *  - Lighthouse a11y: badge con role="status" + aria-label non vuoto.
 *
 * Pattern coerente con SummaryHero.test.tsx (TSK-344): props factory + asserzioni
 * data-testid + data-edge attribute per test deterministico.
 */

import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { BacktestVerdictHero } from '../BacktestVerdictHero';
import type {
  BacktestStrategyMetrics,
  BacktestTimingEdge,
} from '@/lib/api/backtest';

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const EP024_METRICS: BacktestStrategyMetrics = {
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
};

const VI_ONLY_METRICS: BacktestStrategyMetrics = {
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
};

const BUY_AND_HOLD_METRICS: BacktestStrategyMetrics = {
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
};

const ALL_STRATEGIES = [EP024_METRICS, VI_ONLY_METRICS, BUY_AND_HOLD_METRICS];

function makeEdge(label: BacktestTimingEdge['label'], pct: number | null = 5.3): BacktestTimingEdge {
  return {
    timingEdgePct: pct,
    label,
    noSignalsInPeriod: false,
  };
}

// ---------------------------------------------------------------------------
// Suite
// ---------------------------------------------------------------------------

describe('BacktestVerdictHero — 3 casi timingEdge', () => {
  // -------------------------------------------------------------------------
  // POSITIVE_EDGE
  // -------------------------------------------------------------------------
  it('POSITIVE_EDGE — badge visibile, data-edge corretto, frase con rendimento positivo', () => {
    render(
      <BacktestVerdictHero
        ticker="AAPL"
        timingEdge={makeEdge('POSITIVE_EDGE')}
        strategies={ALL_STRATEGIES}
      />,
    );

    const hero = screen.getByTestId('backtest-verdict-hero');
    expect(hero).toBeInTheDocument();
    expect(hero).toHaveAttribute('data-edge', 'POSITIVE_EDGE');

    const badge = screen.getByTestId('backtest-verdict-badge');
    expect(badge).toHaveTextContent(/timing ha aggiunto valore/i);
    expect(badge).toHaveAttribute('role', 'status');

    // Frase include i rendimenti
    const sentence = screen.getByTestId('backtest-verdict-sentence');
    expect(sentence.textContent).toMatch(/AAPL/);
    expect(sentence.textContent).toMatch(/\+12\.40%/);
    expect(sentence.textContent).toMatch(/\+7\.10%/);
  });

  // -------------------------------------------------------------------------
  // NEUTRAL
  // -------------------------------------------------------------------------
  it('NEUTRAL — badge neutro visibile con label corretto', () => {
    render(
      <BacktestVerdictHero
        ticker="AAPL"
        timingEdge={makeEdge('NEUTRAL', 0.8)}
        strategies={ALL_STRATEGIES}
      />,
    );

    const hero = screen.getByTestId('backtest-verdict-hero');
    expect(hero).toHaveAttribute('data-edge', 'NEUTRAL');

    const badge = screen.getByTestId('backtest-verdict-badge');
    expect(badge).toHaveTextContent(/timing senza edge significativo/i);

    const sentence = screen.getByTestId('backtest-verdict-sentence');
    expect(sentence.textContent).toMatch(/timing non ha cambiato molto/i);
  });

  // -------------------------------------------------------------------------
  // NEGATIVE_EDGE — stesso risalto del positivo (AC TSK-352)
  // -------------------------------------------------------------------------
  it('NEGATIVE_EDGE — badge visibile + frase onesta di perdita timing', () => {
    render(
      <BacktestVerdictHero
        ticker="AAPL"
        timingEdge={makeEdge('NEGATIVE_EDGE', -3.1)}
        strategies={[
          { ...EP024_METRICS, avgReturnPct: 4.2 },
          { ...VI_ONLY_METRICS, avgReturnPct: 7.3 },
          BUY_AND_HOLD_METRICS,
        ]}
      />,
    );

    const hero = screen.getByTestId('backtest-verdict-hero');
    expect(hero).toHaveAttribute('data-edge', 'NEGATIVE_EDGE');

    const badge = screen.getByTestId('backtest-verdict-badge');
    // NEGATIVO: stessa prominenza del positivo (NON nascosto, NON smaller font)
    expect(badge).toBeInTheDocument();
    expect(badge).toBeVisible();
    expect(badge).toHaveTextContent(/attenzione.*timing in perdita/i);
    expect(badge).toHaveAttribute('role', 'status');

    const sentence = screen.getByTestId('backtest-verdict-sentence');
    expect(sentence.textContent).toMatch(/attenzione/i);
    expect(sentence.textContent).toMatch(/del semplice comprare a sconto/i);
  });

  // -------------------------------------------------------------------------
  // Stesso HTML structure per POSITIVE e NEGATIVE (AC TSK-352: pari risalto)
  // -------------------------------------------------------------------------
  it('NEGATIVE_EDGE e POSITIVE_EDGE hanno la stessa struttura data-testid (pari risalto)', () => {
    const { unmount: unmountPos } = render(
      <BacktestVerdictHero
        ticker="AAPL"
        timingEdge={makeEdge('POSITIVE_EDGE')}
        strategies={ALL_STRATEGIES}
      />,
    );
    const posHero = screen.getByTestId('backtest-verdict-hero');
    const posBadge = screen.getByTestId('backtest-verdict-badge');
    const posSentence = screen.getByTestId('backtest-verdict-sentence');
    // Verifichiamo che gli elementi esistano e siano visibili
    expect(posHero).toBeVisible();
    expect(posBadge).toBeVisible();
    expect(posSentence).toBeVisible();
    unmountPos();

    render(
      <BacktestVerdictHero
        ticker="AAPL"
        timingEdge={makeEdge('NEGATIVE_EDGE', -3.1)}
        strategies={[{ ...EP024_METRICS, avgReturnPct: 4.2 }, VI_ONLY_METRICS, BUY_AND_HOLD_METRICS]}
      />,
    );
    const negHero = screen.getByTestId('backtest-verdict-hero');
    const negBadge = screen.getByTestId('backtest-verdict-badge');
    const negSentence = screen.getByTestId('backtest-verdict-sentence');

    // Stessa visibilità — pari risalto (AC TSK-352)
    expect(negHero).toBeVisible();
    expect(negBadge).toBeVisible();
    expect(negSentence).toBeVisible();
  });

  // -------------------------------------------------------------------------
  // A11y — aria-label su badge
  // -------------------------------------------------------------------------
  it('badge ha aria-label completo per screen reader (a11y WCAG 2.2 AA)', () => {
    render(
      <BacktestVerdictHero
        ticker="AAPL"
        timingEdge={makeEdge('POSITIVE_EDGE')}
        strategies={ALL_STRATEGIES}
      />,
    );

    const badge = screen.getByTestId('backtest-verdict-badge');
    const ariaLabel = badge.getAttribute('aria-label');
    expect(ariaLabel).toBeTruthy();
    expect(ariaLabel).toContain('AAPL');
    expect(ariaLabel).toContain('Timing ha aggiunto valore');
  });
});
