import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RuleSignalCard } from './RuleSignalCard';
import type { RuleSignal } from '@/lib/api/analysis';

/**
 * Test RuleSignalCard — aggiornato TSK-321 (US-095 / EP-021, ADR-028 §6).
 *
 * TSK-320 ha migrato RuleSignalCard a typed-driven formatting via
 * `formatRuleSignal()` (TSK-319). Le fixture pre-migrazione usavano il tipo
 * legacy hand-rolled `RuleSignal` di `lib/api/analysis` con ruleId dot-notation
 * (es. 'profitability.roe') che non appartengono all'union discriminata EP-021.
 * Per questi ruleId il formatter attiva `runtimeFallback()`, che produce:
 *  - `title` = ruleId raw (oppure humanizeRuleId per la logica del componente)
 *  - `subtitle` = rationale legacy (campo ancora nel payload transizione R+1/R+2)
 *  - `tooltip` = nota drift schema
 *
 * Comportamento post-TSK-320:
 *  - Pannello espanso: `rule-signal-observed-{ruleId}` mostra `typedSubtitle`
 *    (che per ruleId legacy = rationale text, NON il valore numerico 0.18).
 *  - `rule-signal-threshold-{ruleId}` rimosso dal JSX (soglia inclusa nel
 *    subtitle typed; testid non più presente).
 *  - `rule-signal-rationale-{ruleId}` mostra `typedTooltip`
 *    (nota drift, non il rationale legacy).
 *  - `aria-label` usa `typedSubtitle` al posto di observedValue + threshold.
 *
 * Per i test che richiedono formatter typed-driven (Tests 9/9b), si usano
 * i ruleId EP-021 canonici (es. 'PB_LATEST') che vengono narrowati nel
 * formatter. Questi test usano anche la prop `observedSubtitle`.
 *
 * Riferimenti: ADR-028 §5/§6, TSK-320 migration notes.
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

  it('Test 5 — click expand mostra typedSubtitle nel pannello espanso (post TSK-320)', async () => {
    /**
     * Post TSK-320: il pannello espanso mostra:
     *  - `rule-signal-observed-{ruleId}` → `typedSubtitle` (per ruleId legacy =
     *    rationale del segnale via runtimeFallback).
     *  - `rule-signal-rationale-{ruleId}` → `typedTooltip` (nota drift schema).
     *  - Il testid `rule-signal-threshold-{ruleId}` è RIMOSSO (soglia integrata
     *    nel subtitle typed; assertiamo che non sia presente).
     */
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

    // Post TSK-320: "observed" contiene typedSubtitle = rationale legacy
    // (runtimeFallback per ruleId dot-notation non EP-021)
    const observed = screen.getByTestId('rule-signal-observed-profitability.roe');
    expect(observed).toHaveTextContent('ROE 10-year average above threshold.');

    // Post TSK-320: il testid "threshold" NON esiste più (rimosso da TSK-320)
    expect(
      screen.queryByTestId('rule-signal-threshold-profitability.roe'),
    ).not.toBeInTheDocument();

    // Post TSK-320: "rationale" contiene typedTooltip (nota drift schema)
    const rationaleEl = screen.getByTestId('rule-signal-rationale-profitability.roe');
    expect(rationaleEl).toBeInTheDocument();
    expect(rationaleEl.textContent).toBeTruthy();
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

  it('Test 6 — aria-label contiene rule name humanized + signal label + typedSubtitle (post TSK-320)', () => {
    /**
     * Post TSK-320: aria-label = `Regola ${humanName}: ${label}. ${typedSubtitle}`
     * Per ruleId legacy 'profitability.roe' → humanName = "Profitability — Roe"
     * (humanizeRuleId), typedSubtitle = rationale legacy via runtimeFallback.
     */
    render(<RuleSignalCard signal={makeSignal({ signal: 'GREEN' })} />);
    const button = screen.getByRole('button');
    const ariaLabel = button.getAttribute('aria-label') ?? '';

    // Rule name humanized (dot-notation → "Profitability — Roe")
    expect(ariaLabel).toMatch(/Profitability — Roe/i);
    // Signal label
    expect(ariaLabel).toMatch(/OK/);
    // typedSubtitle = rationale legacy (runtimeFallback per ruleId non-EP021)
    expect(ariaLabel).toContain('ROE 10-year average above threshold.');
  });

  it('Test 7 — ruleId con observedValue=null: aria-label non crasha, subtitle fallback visibile', async () => {
    /**
     * Post TSK-320: il campo `observedValue` legacy non è più letto direttamente.
     * Il formatter usa `rationale` come subtitle fallback (runtimeFallback).
     * Verifica che la card si monti senza errori e il pannello espanso mostri
     * un valore non vuoto in rule-signal-observed.
     */
    const user = userEvent.setup();
    render(
      <RuleSignalCard
        signal={makeSignal({ signal: 'NOT_CALCULABLE', observedValue: null })}
      />,
    );
    await user.click(screen.getByRole('button'));
    const observed = screen.getByTestId('rule-signal-observed-profitability.roe');
    // rationale = 'ROE 10-year average above threshold.' via runtimeFallback
    expect(observed.textContent).toBeTruthy();
    expect(observed.textContent!.length).toBeGreaterThan(0);
  });

  it('Test 8 — defaultExpanded=true mostra details on mount', () => {
    render(<RuleSignalCard signal={makeSignal()} defaultExpanded />);
    expect(
      screen.getByTestId('rule-signal-details-profitability.roe'),
    ).toBeInTheDocument();
    expect(screen.getByRole('button')).toHaveAttribute('aria-expanded', 'true');
  });

  it('Test 9 — observedSubtitle visibile su faccia collapsed quando fornito (TSK-290)', () => {
    /**
     * Per il test del prop observedSubtitle usiamo un ruleId EP-021 canonico
     * (PB_LATEST) per coerenza con l'uso reale in TrafficLightPanel (deriveGrahamSubtitle).
     */
    render(
      <RuleSignalCard
        signal={makeSignal({ ruleId: 'PB_LATEST' })}
        observedSubtitle="P/B: 1.2"
      />,
    );
    // Card collapsed (defaultExpanded omesso = false).
    expect(screen.getByRole('button')).toHaveAttribute('aria-expanded', 'false');
    const subtitle = screen.getByTestId('rule-signal-subtitle-PB_LATEST');
    expect(subtitle).toBeInTheDocument();
    expect(subtitle).toHaveTextContent('P/B: 1.2');
  });

  it('Test 9b — observedSubtitle assente quando prop non fornita', () => {
    render(<RuleSignalCard signal={makeSignal()} />);
    expect(
      screen.queryByTestId('rule-signal-subtitle-profitability.roe'),
    ).not.toBeInTheDocument();
  });
});
