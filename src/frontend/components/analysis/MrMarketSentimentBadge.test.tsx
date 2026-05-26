import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MrMarketSentimentBadge } from './MrMarketSentimentBadge';
import type { MrMarketRsiFlag, MrMarketRsiSignal } from '@/lib/api/analysis';

/**
 * Test MrMarketSentimentBadge — TSK-168 DoD (US-056, EP-013).
 *
 * Copre:
 *  - 4 stati: OVERSOLD / NEUTRAL / OVERBOUGHT / INDETERMINATE.
 *  - `flag === null` → stato fallback ("Sentiment non disponibile").
 *  - `aria-label` contiene label + disclaimer advisory.
 *  - Format RSI 1-decimale (`toFixed(1)`).
 *  - Snapshot output stabile.
 */

function makeFlag(
  overrides: Partial<MrMarketRsiFlag> = {},
): MrMarketRsiFlag {
  return {
    flag: 'NEUTRAL' as MrMarketRsiSignal,
    rsiLatest: 50.0,
    rsiTimestamp: '2026-05-26T00:00:00Z',
    periodLength: 14,
    timeframe: '1day',
    ...overrides,
  };
}

describe('MrMarketSentimentBadge', () => {
  it('Test 1 — OVERSOLD: badge blu + label con RSI 1-decimale', () => {
    render(
      <MrMarketSentimentBadge
        flag={makeFlag({ flag: 'OVERSOLD', rsiLatest: 25.12 })}
      />,
    );
    const badge = screen.getByTestId('mr-market-sentiment-badge');
    expect(badge).toHaveAttribute('data-signal', 'OVERSOLD');
    expect(badge.className).toMatch(/bg-blue-100/);
    expect(badge.className).toMatch(/text-blue-900/);

    const text = screen.getByTestId('mr-market-sentiment-badge-text');
    expect(text).toHaveTextContent('Mr. Market: oversold (RSI 25.1)');
  });

  it('Test 2 — NEUTRAL: badge grigio', () => {
    render(
      <MrMarketSentimentBadge
        flag={makeFlag({ flag: 'NEUTRAL', rsiLatest: 50.0 })}
      />,
    );
    const badge = screen.getByTestId('mr-market-sentiment-badge');
    expect(badge).toHaveAttribute('data-signal', 'NEUTRAL');
    expect(badge.className).toMatch(/bg-slate-100/);
    expect(screen.getByTestId('mr-market-sentiment-badge-text')).toHaveTextContent(
      'Mr. Market: neutro (RSI 50.0)',
    );
  });

  it('Test 3 — OVERBOUGHT: badge giallo', () => {
    render(
      <MrMarketSentimentBadge
        flag={makeFlag({ flag: 'OVERBOUGHT', rsiLatest: 75.5 })}
      />,
    );
    const badge = screen.getByTestId('mr-market-sentiment-badge');
    expect(badge).toHaveAttribute('data-signal', 'OVERBOUGHT');
    expect(badge.className).toMatch(/bg-amber-100/);
    expect(screen.getByTestId('mr-market-sentiment-badge-text')).toHaveTextContent(
      'Mr. Market: overbought (RSI 75.5)',
    );
  });

  it('Test 4 — INDETERMINATE: badge tenue "Sentiment non disponibile"', () => {
    render(
      <MrMarketSentimentBadge
        flag={makeFlag({ flag: 'INDETERMINATE', rsiLatest: null, rsiTimestamp: null })}
      />,
    );
    const badge = screen.getByTestId('mr-market-sentiment-badge');
    expect(badge).toHaveAttribute('data-signal', 'INDETERMINATE');
    expect(badge.className).toMatch(/bg-slate-50/);
    expect(badge.className).toMatch(/text-slate-400/);
    expect(screen.getByTestId('mr-market-sentiment-badge-text')).toHaveTextContent(
      'Sentiment non disponibile',
    );
  });

  it('Test 5 — flag === null: stesso fallback di INDETERMINATE', () => {
    render(<MrMarketSentimentBadge flag={null} />);
    const badge = screen.getByTestId('mr-market-sentiment-badge');
    expect(badge).toHaveAttribute('data-signal', 'NULL');
    expect(screen.getByTestId('mr-market-sentiment-badge-text')).toHaveTextContent(
      'Sentiment non disponibile',
    );
  });

  it('Test 6 — aria-label contiene label + disclaimer advisory', () => {
    render(
      <MrMarketSentimentBadge
        flag={makeFlag({ flag: 'OVERSOLD', rsiLatest: 25.0 })}
      />,
    );
    const badge = screen.getByTestId('mr-market-sentiment-badge');
    const ariaLabel = badge.getAttribute('aria-label') ?? '';
    expect(ariaLabel).toMatch(/Mr\. Market: oversold \(RSI 25\.0\)/);
    expect(ariaLabel).toMatch(/Advisory/);
    expect(ariaLabel).toMatch(/non sostituisce/i);
  });

  it('Test 7 — rsiLatest=null con flag non-INDETERMINATE → format "—"', () => {
    // Edge BE-driven: il BE potrebbe ipoteticamente passare un flag valido
    // ma rsiLatest=null (post-mapping anomalo). Il componente non crasha:
    // formatta come "—" e mantiene il signal class.
    render(
      <MrMarketSentimentBadge
        flag={makeFlag({ flag: 'OVERSOLD', rsiLatest: null })}
      />,
    );
    expect(screen.getByTestId('mr-market-sentiment-badge-text')).toHaveTextContent(
      'Mr. Market: oversold (RSI —)',
    );
  });

  it('Snapshot — OVERSOLD rendering', () => {
    const { container } = render(
      <MrMarketSentimentBadge
        flag={makeFlag({ flag: 'OVERSOLD', rsiLatest: 25.0 })}
      />,
    );
    expect(container.firstChild).toMatchSnapshot();
  });

  it('Snapshot — NEUTRAL rendering', () => {
    const { container } = render(
      <MrMarketSentimentBadge flag={makeFlag({ flag: 'NEUTRAL' })} />,
    );
    expect(container.firstChild).toMatchSnapshot();
  });

  it('Snapshot — OVERBOUGHT rendering', () => {
    const { container } = render(
      <MrMarketSentimentBadge
        flag={makeFlag({ flag: 'OVERBOUGHT', rsiLatest: 75.5 })}
      />,
    );
    expect(container.firstChild).toMatchSnapshot();
  });

  it('Snapshot — INDETERMINATE rendering', () => {
    const { container } = render(
      <MrMarketSentimentBadge
        flag={makeFlag({ flag: 'INDETERMINATE', rsiLatest: null })}
      />,
    );
    expect(container.firstChild).toMatchSnapshot();
  });
});
