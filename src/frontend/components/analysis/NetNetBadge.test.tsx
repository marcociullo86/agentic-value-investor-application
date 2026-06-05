import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { NetNetBadge } from './NetNetBadge';
import type { RuleSignal, Signal } from '@/lib/api/analysis';

/**
 * Test NetNetBadge — TSK-322 DoD (US-097, EP-023, ADR-029 §6).
 *
 * Copre il render contract dichiarato in ADR-029 §6:
 *  - GREEN → badge visibile, palette verde, testo "Net-Net".
 *  - RED, INDETERMINATE, NOT_CALCULABLE → badge nascosto (null).
 *  - signals[] privo di NET_NET_RATIO → badge nascosto (forward-compat:
 *    risposta cache pre-EP-023, BE non ancora deployato).
 *  - aria-label include "Criterio Graham Net-Net soddisfatto" + tooltip
 *    canonico (WCAG AA, ADR-029 §6).
 */

function makeSignal(
  overrides: Partial<RuleSignal> = {},
): RuleSignal {
  return {
    ruleId: 'NET_NET_RATIO',
    signal: 'GREEN' as Signal,
    observedValue: 0.5,
    threshold: '<0.6667',
    rationale: 'Prezzo 50% del NCAV per share',
    ...overrides,
  };
}

describe('NetNetBadge', () => {
  it('Test 1 — NET_NET_RATIO GREEN: badge visibile con palette signal-green', () => {
    render(<NetNetBadge signals={[makeSignal({ signal: 'GREEN' })]} />);
    const badge = screen.getByTestId('net-net-badge');
    expect(badge).toHaveAttribute('data-signal', 'GREEN');
    expect(badge.className).toMatch(/bg-signal-green/);
    expect(badge.className).toMatch(/text-white/);
    expect(screen.getByTestId('net-net-badge-text')).toHaveTextContent('Net-Net');
  });

  it('Test 2 — NET_NET_RATIO RED: badge nascosto', () => {
    const { container } = render(
      <NetNetBadge signals={[makeSignal({ signal: 'RED' })]} />,
    );
    expect(container.firstChild).toBeNull();
    expect(screen.queryByTestId('net-net-badge')).not.toBeInTheDocument();
  });

  it('Test 3 — NET_NET_RATIO INDETERMINATE: badge nascosto', () => {
    const { container } = render(
      <NetNetBadge signals={[makeSignal({ signal: 'INDETERMINATE' })]} />,
    );
    expect(container.firstChild).toBeNull();
    expect(screen.queryByTestId('net-net-badge')).not.toBeInTheDocument();
  });

  it('Test 4 — NET_NET_RATIO NOT_CALCULABLE (NCAV<=0): badge nascosto', () => {
    const { container } = render(
      <NetNetBadge signals={[makeSignal({ signal: 'NOT_CALCULABLE' })]} />,
    );
    expect(container.firstChild).toBeNull();
  });

  it('Test 5 — signals[] privo di NET_NET_RATIO: badge nascosto (forward-compat pre-EP-023)', () => {
    const otherSignals: ReadonlyArray<RuleSignal> = [
      makeSignal({ ruleId: 'PE_3Y_AVG', signal: 'GREEN' }),
      makeSignal({ ruleId: 'PB_LATEST', signal: 'GREEN' }),
      makeSignal({ ruleId: 'NCAV_LATEST', signal: 'GREEN' }),
    ];
    const { container } = render(<NetNetBadge signals={otherSignals} />);
    expect(container.firstChild).toBeNull();
  });

  it('Test 6 — signals[] vuoto: badge nascosto (no crash)', () => {
    const { container } = render(<NetNetBadge signals={[]} />);
    expect(container.firstChild).toBeNull();
  });

  it('Test 7 — GREEN tra altri signal: badge visibile (find selettivo per ruleId)', () => {
    const signals: ReadonlyArray<RuleSignal> = [
      makeSignal({ ruleId: 'PE_3Y_AVG', signal: 'RED' }),
      makeSignal({ ruleId: 'NCAV_LATEST', signal: 'GREEN' }),
      makeSignal({ ruleId: 'NET_NET_RATIO', signal: 'GREEN' }),
      makeSignal({ ruleId: 'PB_LATEST', signal: 'YELLOW' }),
    ];
    render(<NetNetBadge signals={signals} />);
    expect(screen.getByTestId('net-net-badge')).toBeInTheDocument();
  });

  it('Test 8 — aria-label contiene "Criterio Graham Net-Net soddisfatto" + tooltip canonico', () => {
    render(<NetNetBadge signals={[makeSignal({ signal: 'GREEN' })]} />);
    const badge = screen.getByTestId('net-net-badge');
    const ariaLabel = badge.getAttribute('aria-label') ?? '';
    expect(ariaLabel).toMatch(/Criterio Graham Net-Net soddisfatto/);
    expect(ariaLabel).toMatch(/2\/3 del Net Current Asset Value/);
    expect(ariaLabel).toMatch(/Graham Cap\.15/);
  });

  it('Test 9 — title attribute = tooltip canonico (hover desktop + long-press mobile)', () => {
    render(<NetNetBadge signals={[makeSignal({ signal: 'GREEN' })]} />);
    const badge = screen.getByTestId('net-net-badge');
    expect(badge).toHaveAttribute(
      'title',
      'Prezzo inferiore ai 2/3 del Net Current Asset Value — criterio Graham Cap.15.',
    );
  });

  it('Test 10 — role="img" (unita informativa discreta, non interattiva)', () => {
    render(<NetNetBadge signals={[makeSignal({ signal: 'GREEN' })]} />);
    expect(screen.getByTestId('net-net-badge')).toHaveAttribute('role', 'img');
  });

  it('Snapshot — GREEN rendering', () => {
    const { container } = render(
      <NetNetBadge signals={[makeSignal({ signal: 'GREEN' })]} />,
    );
    expect(container.firstChild).toMatchSnapshot();
  });
});
