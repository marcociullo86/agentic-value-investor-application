/**
 * Vitest — BacktestCaveatBanner (TSK-352, US-106, EP-024 Fase 3).
 *
 * Copre:
 *  - Banner presente con tutti e 3 i caveat attivi.
 *  - Ogni caveat flag `true` produce un item nella lista.
 *  - `role="status"` + aria-live="polite" (a11y — AC TSK-352).
 *  - Caveat flag `false` → item assente.
 *
 * AC TSK-352:
 *  - Banner caveat presente in tutti i 3 casi timingEdge (testato nel
 *    BacktestPanel integration test — qui testiamo il componente isolato).
 *  - a11y: banner ruolo ARIA status.
 */

import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { BacktestCaveatBanner } from '../BacktestCaveatBanner';
import type { BacktestCaveats } from '@/lib/api/backtest';

const ALL_CAVEATS: BacktestCaveats = {
  lookAheadResidual: true,
  singleTicker: true,
  notPortfolioPerformance: true,
};

describe('BacktestCaveatBanner', () => {
  it('mostra il banner con tutti e 3 i caveat attivi', () => {
    render(<BacktestCaveatBanner caveats={ALL_CAVEATS} />);

    const banner = screen.getByTestId('backtest-caveat-banner');
    expect(banner).toBeInTheDocument();

    expect(screen.getByTestId('backtest-caveat-singleTicker')).toBeInTheDocument();
    expect(screen.getByTestId('backtest-caveat-notPortfolioPerformance')).toBeInTheDocument();
    expect(screen.getByTestId('backtest-caveat-lookAheadResidual')).toBeInTheDocument();
  });

  it('role="status" e aria-live="polite" (a11y WCAG 2.2 AA — AC TSK-352)', () => {
    render(<BacktestCaveatBanner caveats={ALL_CAVEATS} />);

    const banner = screen.getByTestId('backtest-caveat-banner');
    expect(banner).toHaveAttribute('role', 'status');
    expect(banner).toHaveAttribute('aria-live', 'polite');
  });

  it('flag singleTicker=false → item singleTicker assente', () => {
    render(
      <BacktestCaveatBanner
        caveats={{ ...ALL_CAVEATS, singleTicker: false }}
      />,
    );

    expect(screen.queryByTestId('backtest-caveat-singleTicker')).not.toBeInTheDocument();
    // Gli altri due restano
    expect(screen.getByTestId('backtest-caveat-notPortfolioPerformance')).toBeInTheDocument();
    expect(screen.getByTestId('backtest-caveat-lookAheadResidual')).toBeInTheDocument();
  });

  it('heading "Limiti della verifica storica" visibile', () => {
    render(<BacktestCaveatBanner caveats={ALL_CAVEATS} />);
    expect(screen.getByText(/limiti della verifica storica/i)).toBeInTheDocument();
  });
});
