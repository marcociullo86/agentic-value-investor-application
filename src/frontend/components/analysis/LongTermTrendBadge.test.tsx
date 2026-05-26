import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { LongTermTrendBadge } from './LongTermTrendBadge';
import type { LongTermTrendFlag, LongTermTrendSignal } from '@/lib/api/analysis';

/**
 * Test LongTermTrendBadge — TSK-169 DoD (US-057, EP-013).
 *
 * Copre:
 *  - 4 stati: BELOW_TREND / NEAR_TREND / ABOVE_TREND / INDETERMINATE.
 *  - `flag === null` → stato fallback ("Trend lungo periodo non disponibile").
 *  - `aria-label` contiene label + disclaimer advisory.
 *  - Format pct: `(value * 100).toFixed(1)`; per ABOVE_TREND `+` esplicito.
 *  - Snapshot output stabile.
 */

function makeFlag(
  overrides: Partial<LongTermTrendFlag> = {},
): LongTermTrendFlag {
  return {
    flag: 'NEAR_TREND' as LongTermTrendSignal,
    sma200Latest: 100.0,
    currentPrice: 100.0,
    priceVsSmaPct: 0.0,
    smaTimestamp: '2026-05-26T00:00:00Z',
    periodLength: 200,
    timeframe: '1day',
    ...overrides,
  };
}

describe('LongTermTrendBadge', () => {
  it('Test 1 — BELOW_TREND: icona TrendingDown + label "Sotto SMA200 (-20.0%)"', () => {
    render(
      <LongTermTrendBadge
        flag={makeFlag({
          flag: 'BELOW_TREND',
          currentPrice: 80,
          sma200Latest: 100,
          priceVsSmaPct: -0.2,
        })}
      />,
    );
    const badge = screen.getByTestId('long-term-trend-badge');
    expect(badge).toHaveAttribute('data-signal', 'BELOW_TREND');
    expect(screen.getByTestId('long-term-trend-badge-text')).toHaveTextContent(
      'Sotto SMA200 (-20.0%)',
    );
    // Tooltip BELOW_TREND specifico contiene "deep value"
    expect(badge.getAttribute('title') ?? '').toMatch(/deep value/i);
  });

  it('Test 2 — NEAR_TREND: icona Minus + label "In linea con SMA200 (2.0%)"', () => {
    render(
      <LongTermTrendBadge
        flag={makeFlag({
          flag: 'NEAR_TREND',
          currentPrice: 102,
          sma200Latest: 100,
          priceVsSmaPct: 0.02,
        })}
      />,
    );
    const badge = screen.getByTestId('long-term-trend-badge');
    expect(badge).toHaveAttribute('data-signal', 'NEAR_TREND');
    expect(screen.getByTestId('long-term-trend-badge-text')).toHaveTextContent(
      'In linea con SMA200 (2.0%)',
    );
  });

  it('Test 2b — NEAR_TREND con pct negativo dentro range: segno naturale', () => {
    render(
      <LongTermTrendBadge
        flag={makeFlag({
          flag: 'NEAR_TREND',
          currentPrice: 97,
          sma200Latest: 100,
          priceVsSmaPct: -0.03,
        })}
      />,
    );
    expect(screen.getByTestId('long-term-trend-badge-text')).toHaveTextContent(
      'In linea con SMA200 (-3.0%)',
    );
  });

  it('Test 3 — ABOVE_TREND: icona TrendingUp + label con "+" esplicito', () => {
    render(
      <LongTermTrendBadge
        flag={makeFlag({
          flag: 'ABOVE_TREND',
          currentPrice: 150,
          sma200Latest: 100,
          priceVsSmaPct: 0.5,
        })}
      />,
    );
    const badge = screen.getByTestId('long-term-trend-badge');
    expect(badge).toHaveAttribute('data-signal', 'ABOVE_TREND');
    expect(screen.getByTestId('long-term-trend-badge-text')).toHaveTextContent(
      'Sopra SMA200 (+50.0%)',
    );
    // Tooltip ABOVE_TREND specifico contiene "Cautela"
    expect(badge.getAttribute('title') ?? '').toMatch(/Cautela/i);
  });

  it('Test 4 — INDETERMINATE: fallback tenue', () => {
    render(
      <LongTermTrendBadge
        flag={makeFlag({
          flag: 'INDETERMINATE',
          sma200Latest: null,
          currentPrice: null,
          priceVsSmaPct: null,
          smaTimestamp: null,
        })}
      />,
    );
    const badge = screen.getByTestId('long-term-trend-badge');
    expect(badge).toHaveAttribute('data-signal', 'INDETERMINATE');
    expect(badge.className).toMatch(/bg-slate-50/);
    expect(badge.className).toMatch(/text-slate-400/);
    expect(screen.getByTestId('long-term-trend-badge-text')).toHaveTextContent(
      'Trend lungo periodo non disponibile',
    );
  });

  it('Test 5 — flag === null: stesso fallback di INDETERMINATE', () => {
    render(<LongTermTrendBadge flag={null} />);
    const badge = screen.getByTestId('long-term-trend-badge');
    expect(badge).toHaveAttribute('data-signal', 'NULL');
    expect(screen.getByTestId('long-term-trend-badge-text')).toHaveTextContent(
      'Trend lungo periodo non disponibile',
    );
  });

  it('Test 6 — aria-label contiene label + disclaimer advisory', () => {
    render(
      <LongTermTrendBadge
        flag={makeFlag({
          flag: 'BELOW_TREND',
          priceVsSmaPct: -0.2,
        })}
      />,
    );
    const badge = screen.getByTestId('long-term-trend-badge');
    const ariaLabel = badge.getAttribute('aria-label') ?? '';
    expect(ariaLabel).toMatch(/Sotto SMA200 \(-20\.0%\)/);
    expect(ariaLabel).toMatch(/SMA200/);
    expect(ariaLabel).toMatch(/advisory/i);
  });

  it('Test 7 — priceVsSmaPct=null con flag non-INDETERMINATE → format "—"', () => {
    // Edge: BE potrebbe inviare flag valido + pct=null (anomalia).
    // Componente non crasha: rende "—".
    render(
      <LongTermTrendBadge
        flag={makeFlag({ flag: 'BELOW_TREND', priceVsSmaPct: null })}
      />,
    );
    expect(screen.getByTestId('long-term-trend-badge-text')).toHaveTextContent(
      'Sotto SMA200 (—%)',
    );
  });

  it('Snapshot — BELOW_TREND rendering', () => {
    const { container } = render(
      <LongTermTrendBadge
        flag={makeFlag({ flag: 'BELOW_TREND', priceVsSmaPct: -0.2 })}
      />,
    );
    expect(container.firstChild).toMatchSnapshot();
  });

  it('Snapshot — NEAR_TREND rendering', () => {
    const { container } = render(
      <LongTermTrendBadge
        flag={makeFlag({ flag: 'NEAR_TREND', priceVsSmaPct: 0.02 })}
      />,
    );
    expect(container.firstChild).toMatchSnapshot();
  });

  it('Snapshot — ABOVE_TREND rendering', () => {
    const { container } = render(
      <LongTermTrendBadge
        flag={makeFlag({ flag: 'ABOVE_TREND', priceVsSmaPct: 0.5 })}
      />,
    );
    expect(container.firstChild).toMatchSnapshot();
  });

  it('Snapshot — INDETERMINATE rendering', () => {
    const { container } = render(
      <LongTermTrendBadge
        flag={makeFlag({
          flag: 'INDETERMINATE',
          sma200Latest: null,
          currentPrice: null,
          priceVsSmaPct: null,
        })}
      />,
    );
    expect(container.firstChild).toMatchSnapshot();
  });
});
