import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RuleSignalCard } from './RuleSignalCard';
import type { RuleSignal } from '@/lib/api/analysis';

/**
 * Test RuleSignalCard — TSK-021 DoD (US-014).
 *
 * Copre:
 *  - Rendering label/colore per ogni `Signal` valore.
 *  - Click expand/collapse → mostra/nasconde observedValue + threshold.
 *  - `aria-label` accessibile contiene rule name + signal + observed + threshold.
 *  - Stato espanso mostra anche `rationale` se valorizzato.
 */

function makeSignal(overrides: Partial<RuleSignal> = {}): RuleSignal {
  return {
    ruleId: 'profitability.roe',
    signal: 'GREEN',
    observedValue: 0.182,
    threshold: 'ROE ≥ 15%',
    rationale: 'ROE 10-year average above threshold.',
    ...overrides,
  };
}

describe('RuleSignalCard', () => {
  it('Test 1 — GREEN signal: render label "OK" + classe bg-signal-green', () => {
    render(<RuleSignalCard signal={makeSignal({ signal: 'GREEN' })} />);
    const card = screen.getByTestId('rule-signal-card-profitability.roe');
    expect(card).toHaveAttribute('data-signal', 'GREEN');

    const label = screen.getByTestId('rule-signal-label-profitability.roe');
    expect(label).toHaveTextContent('OK');
    expect(label.className).toMatch(/bg-signal-green/);

    const dot = screen.getByTestId('rule-signal-dot-profitability.roe');
    expect(dot.className).toMatch(/bg-signal-green/);
  });

  it('Test 2 — YELLOW signal: label "Attenzione"', () => {
    render(
      <RuleSignalCard
        signal={makeSignal({
          ruleId: 'solvency.current_ratio',
          signal: 'YELLOW',
        })}
      />,
    );
    const label = screen.getByTestId('rule-signal-label-solvency.current_ratio');
    expect(label).toHaveTextContent('Attenzione');
    expect(label.className).toMatch(/bg-signal-yellow/);
  });

  it('Test 3 — RED signal: label "Non soddisfatta"', () => {
    render(
      <RuleSignalCard
        signal={makeSignal({ ruleId: 'capital.debt', signal: 'RED' })}
      />,
    );
    const label = screen.getByTestId('rule-signal-label-capital.debt');
    expect(label).toHaveTextContent('Non soddisfatta');
    expect(label.className).toMatch(/bg-signal-red/);
  });

  it('Test 4 — INDETERMINATE signal: label "Indeterminato"', () => {
    render(
      <RuleSignalCard
        signal={makeSignal({
          ruleId: 'pricing.gross_margin',
          signal: 'INDETERMINATE',
          observedValue: null,
        })}
      />,
    );
    const label = screen.getByTestId('rule-signal-label-pricing.gross_margin');
    expect(label).toHaveTextContent('Indeterminato');
    expect(label.className).toMatch(/bg-signal-neutral/);
  });

  it('Test 4b — NOT_CALCULABLE signal: label "Non calcolabile"', () => {
    render(
      <RuleSignalCard
        signal={makeSignal({
          ruleId: 'graham.number',
          signal: 'NOT_CALCULABLE',
          observedValue: null,
        })}
      />,
    );
    const label = screen.getByTestId('rule-signal-label-graham.number');
    expect(label).toHaveTextContent('Non calcolabile');
  });

  it('Test 5 — click expand mostra observedValue + threshold', async () => {
    const user = userEvent.setup();
    render(<RuleSignalCard signal={makeSignal()} />);

    // Stato iniziale: details NOT presente
    expect(
      screen.queryByTestId('rule-signal-details-profitability.roe'),
    ).not.toBeInTheDocument();

    const button = screen.getByRole('button');
    expect(button).toHaveAttribute('aria-expanded', 'false');

    await user.click(button);

    expect(button).toHaveAttribute('aria-expanded', 'true');
    const details = screen.getByTestId('rule-signal-details-profitability.roe');
    expect(details).toBeInTheDocument();
    expect(
      screen.getByTestId('rule-signal-observed-profitability.roe'),
    ).toHaveTextContent('0,18'); // it-IT, 2-4 decimali
    expect(
      screen.getByTestId('rule-signal-threshold-profitability.roe'),
    ).toHaveTextContent('ROE ≥ 15%');
    expect(
      screen.getByTestId('rule-signal-rationale-profitability.roe'),
    ).toHaveTextContent('ROE 10-year average above threshold.');
  });

  it('Test 5b — click expand poi collapse riporta a stato iniziale', async () => {
    const user = userEvent.setup();
    render(<RuleSignalCard signal={makeSignal()} />);

    const button = screen.getByRole('button');
    await user.click(button);
    expect(button).toHaveAttribute('aria-expanded', 'true');

    await user.click(button);
    expect(button).toHaveAttribute('aria-expanded', 'false');
    expect(
      screen.queryByTestId('rule-signal-details-profitability.roe'),
    ).not.toBeInTheDocument();
  });

  it('Test 6 — aria-label contiene rule name + signal + observedValue + threshold', () => {
    render(<RuleSignalCard signal={makeSignal({ signal: 'GREEN' })} />);
    const button = screen.getByRole('button');
    const ariaLabel = button.getAttribute('aria-label') ?? '';

    // Rule name humanized
    expect(ariaLabel).toMatch(/Profitability — Roe/i);
    // Signal label
    expect(ariaLabel).toMatch(/OK/);
    // Observed value (it-IT format 0,18...)
    expect(ariaLabel).toMatch(/0,18/);
    // Threshold testuale
    expect(ariaLabel).toMatch(/ROE ≥ 15%/);
  });

  it('Test 7 — observedValue=null formattato come "—"', async () => {
    const user = userEvent.setup();
    render(
      <RuleSignalCard
        signal={makeSignal({ signal: 'NOT_CALCULABLE', observedValue: null })}
      />,
    );
    await user.click(screen.getByRole('button'));
    expect(
      screen.getByTestId('rule-signal-observed-profitability.roe'),
    ).toHaveTextContent('—');
  });

  it('Test 8 — defaultExpanded=true mostra details on mount', () => {
    render(<RuleSignalCard signal={makeSignal()} defaultExpanded />);
    expect(
      screen.getByTestId('rule-signal-details-profitability.roe'),
    ).toBeInTheDocument();
    expect(screen.getByRole('button')).toHaveAttribute('aria-expanded', 'true');
  });
});
