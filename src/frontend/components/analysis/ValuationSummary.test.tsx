import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ValuationSummary } from './ValuationSummary';
import type { ValuationSummaryProps } from './ValuationSummary';

/**
 * Test ValuationSummary — TSK-021 DoD (US-014).
 *
 * Copre:
 *  - Tutti i valori non-null → tutti i campi visibili (Graham, DCF, prezzo, MoS).
 *  - `grahamNumber=null` → "Non applicabile" + tooltip via attributo `title`.
 *  - `dcfMethod='NOT_APPLICABLE'` → "Non applicabile".
 *  - `mosSignal='NOT_APPLICABLE'` → label "Non applicabile" + classe neutra.
 *  - `currentPriceAtEval=null` → "—".
 *  - Footer snapshot date renderizzato.
 */

function baseProps(): ValuationSummaryProps {
  return {
    grahamNumber: 42.5,
    dcfIntrinsicValue: 51.2,
    dcfMethod: 'GREENWALD',
    mosSignal: 'GREEN',
    currentPriceAtEval: 38.0,
    dataSnapshotAt: '2026-05-22T10:00:00Z',
  };
}

describe('ValuationSummary', () => {
  it('Test 1 — tutti i valori non-null → render completo', () => {
    render(<ValuationSummary {...baseProps()} />);

    expect(screen.getByTestId('valuation-summary')).toBeInTheDocument();

    // Graham Number renderizzato
    const grahamValue = screen.getByTestId('valuation-graham-value');
    expect(grahamValue.textContent).toMatch(/42[,.]50/);
    expect(screen.queryByTestId('valuation-graham-na')).not.toBeInTheDocument();

    // DCF renderizzato + metodo Greenwald
    const dcfValue = screen.getByTestId('valuation-dcf-value');
    expect(dcfValue.textContent).toMatch(/51[,.]20/);
    expect(screen.getByTestId('valuation-dcf-method')).toHaveTextContent(
      'Greenwald EPV',
    );

    // Prezzo
    const priceValue = screen.getByTestId('valuation-price-value');
    expect(priceValue.textContent).toMatch(/38[,.]00/);

    // MoS GREEN
    const mosBadge = screen.getByTestId('valuation-mos-badge');
    expect(mosBadge).toHaveTextContent('OK');
    expect(mosBadge.className).toMatch(/bg-signal-green/);
    expect(mosBadge).toHaveAttribute('data-signal', 'GREEN');

    // Footer snapshot
    expect(screen.getByTestId('valuation-snapshot')).toHaveTextContent(/Dati al/);
  });

  it('Test 2 — grahamNumber=null → "Non applicabile" + tooltip', () => {
    render(<ValuationSummary {...baseProps()} grahamNumber={null} />);

    const na = screen.getByTestId('valuation-graham-na');
    expect(na).toHaveTextContent('Non applicabile');
    expect(na).toHaveAttribute(
      'title',
      'EPS o BVPS non utilizzabili per il calcolo di Graham',
    );
    expect(screen.queryByTestId('valuation-graham-value')).not.toBeInTheDocument();
  });

  it('Test 3 — dcfMethod=NOT_APPLICABLE → "Non applicabile"', () => {
    render(
      <ValuationSummary
        {...baseProps()}
        dcfIntrinsicValue={null}
        dcfMethod="NOT_APPLICABLE"
      />,
    );
    expect(screen.getByTestId('valuation-dcf-na')).toHaveTextContent(
      'Non applicabile',
    );
    expect(screen.queryByTestId('valuation-dcf-value')).not.toBeInTheDocument();
    expect(screen.queryByTestId('valuation-dcf-method')).not.toBeInTheDocument();
  });

  it('Test 3b — dcfMethod=FCF_FALLBACK → label "FCF Fallback"', () => {
    render(
      <ValuationSummary
        {...baseProps()}
        dcfMethod="FCF_FALLBACK"
        dcfIntrinsicValue={60.0}
      />,
    );
    expect(screen.getByTestId('valuation-dcf-method')).toHaveTextContent(
      'FCF Fallback',
    );
  });

  it('Test 4 — mosSignal=NOT_APPLICABLE → label "Non applicabile" + classe neutra', () => {
    render(<ValuationSummary {...baseProps()} mosSignal="NOT_APPLICABLE" />);
    const badge = screen.getByTestId('valuation-mos-badge');
    expect(badge).toHaveTextContent('Non applicabile');
    expect(badge.className).toMatch(/bg-signal-neutral/);
    expect(badge).toHaveAttribute('data-signal', 'NOT_APPLICABLE');

    // Aria-label include rule + description
    const ariaLabel = badge.getAttribute('aria-label') ?? '';
    expect(ariaLabel).toMatch(/Margin of Safety/);
    expect(ariaLabel).toMatch(/Non applicabile/);
  });

  it('Test 5 — currentPriceAtEval=null → "—"', () => {
    render(<ValuationSummary {...baseProps()} currentPriceAtEval={null} />);
    expect(screen.getByTestId('valuation-price-na')).toHaveTextContent('—');
    expect(screen.queryByTestId('valuation-price-value')).not.toBeInTheDocument();
  });

  it('Test 6 — mosSignal=RED → label "Non soddisfatta" + classe red', () => {
    render(<ValuationSummary {...baseProps()} mosSignal="RED" />);
    const badge = screen.getByTestId('valuation-mos-badge');
    expect(badge).toHaveTextContent('Non soddisfatta');
    expect(badge.className).toMatch(/bg-signal-red/);
  });
});
