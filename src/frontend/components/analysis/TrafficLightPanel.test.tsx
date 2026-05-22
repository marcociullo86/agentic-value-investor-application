import { describe, it, expect } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { TrafficLightPanel } from './TrafficLightPanel';
import type { RuleSignal } from '@/lib/api/analysis';

/**
 * Test TrafficLightPanel — TSK-021 DoD (US-014).
 *
 * Copre:
 *  - Render con 7 regole (set MVP US-014: ≥6 categorie) → 7 cards + counter
 *    aggregato corretto.
 *  - Counter header conta GREEN/YELLOW/RED/INDETERMINATE/NOT_CALCULABLE.
 *  - Sort lessicografico per ruleId (deterministic).
 *  - Empty state quando signals=[].
 */

function makeSignal(ruleId: string, signal: RuleSignal['signal']): RuleSignal {
  return {
    ruleId,
    signal,
    observedValue: signal === 'NOT_CALCULABLE' ? null : 0.18,
    threshold: 'threshold-text',
    rationale: 'rationale-text',
  };
}

describe('TrafficLightPanel', () => {
  it('Test 1 — render 7 regole → 7 cards + counter header', () => {
    const signals: ReadonlyArray<RuleSignal> = [
      makeSignal('profitability.roe', 'GREEN'),
      makeSignal('profitability.roic', 'GREEN'),
      makeSignal('pricing.gross_margin', 'GREEN'),
      makeSignal('solvency.current_ratio', 'YELLOW'),
      makeSignal('capital.debt', 'RED'),
      makeSignal('valuation.graham', 'INDETERMINATE'),
      makeSignal('valuation.dcf', 'NOT_CALCULABLE'),
    ];
    render(<TrafficLightPanel signals={signals} />);

    const grid = screen.getByTestId('traffic-light-panel-grid');
    expect(grid.children).toHaveLength(7);

    // Counter conta correttamente per ogni stato presente
    expect(
      screen.getByTestId('traffic-light-counter-GREEN'),
    ).toHaveTextContent('3 OK');
    expect(
      screen.getByTestId('traffic-light-counter-YELLOW'),
    ).toHaveTextContent('1 Attenzione');
    expect(
      screen.getByTestId('traffic-light-counter-RED'),
    ).toHaveTextContent('1 Non soddisfatta');
    expect(
      screen.getByTestId('traffic-light-counter-INDETERMINATE'),
    ).toHaveTextContent('1 Indeterminato');
    expect(
      screen.getByTestId('traffic-light-counter-NOT_CALCULABLE'),
    ).toHaveTextContent('1 Non calcolabile');
  });

  it('Test 2 — empty signals → empty message + role=status', () => {
    render(<TrafficLightPanel signals={[]} />);
    const empty = screen.getByTestId('traffic-light-panel-empty');
    expect(empty).toBeInTheDocument();
    expect(empty).toHaveAttribute('role', 'status');
    expect(empty).toHaveTextContent(/nessuna regola valutata/i);
    expect(screen.queryByTestId('traffic-light-panel-grid')).not.toBeInTheDocument();
  });

  it('Test 3 — sort lessicografico ascending per ruleId', () => {
    const signals: ReadonlyArray<RuleSignal> = [
      makeSignal('zeta', 'GREEN'),
      makeSignal('alpha', 'GREEN'),
      makeSignal('mike', 'GREEN'),
    ];
    render(<TrafficLightPanel signals={signals} />);
    const grid = screen.getByTestId('traffic-light-panel-grid');
    const cards = within(grid)
      .getAllByTestId(/^rule-signal-card-/)
      .map((el) => el.getAttribute('data-testid'));
    expect(cards).toEqual([
      'rule-signal-card-alpha',
      'rule-signal-card-mike',
      'rule-signal-card-zeta',
    ]);
  });

  it('Test 4 — counter omette stati con count=0', () => {
    const signals: ReadonlyArray<RuleSignal> = [
      makeSignal('a', 'GREEN'),
      makeSignal('b', 'GREEN'),
    ];
    render(<TrafficLightPanel signals={signals} />);
    expect(screen.getByTestId('traffic-light-counter-GREEN')).toBeInTheDocument();
    expect(screen.queryByTestId('traffic-light-counter-YELLOW')).not.toBeInTheDocument();
    expect(screen.queryByTestId('traffic-light-counter-RED')).not.toBeInTheDocument();
  });

  it('Test 5 — aria-label sul section', () => {
    render(<TrafficLightPanel signals={[makeSignal('a', 'GREEN')]} />);
    const section = screen.getByTestId('traffic-light-panel');
    expect(section).toHaveAttribute(
      'aria-label',
      'Pannello Traffic Light delle regole del Rule Engine',
    );
  });
});
