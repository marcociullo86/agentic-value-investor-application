import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { StaleDataBadge } from './StaleDataBadge';

/**
 * Test StaleDataBadge — TSK-038 DoD (US-005 + US-006).
 *
 * Copre:
 *  - isStale=false → componente non monta (null render).
 *  - isStale=true + dataSnapshotAt valido → badge visibile con testo
 *    formattato + role=alert + aria-live=polite.
 *  - isStale=true + dataSnapshotAt=null → badge visibile con fallback
 *    "data sconosciuta" (la condizione stale è informazione critica
 *    anche senza timestamp; vedi nota nel componente).
 */

describe('StaleDataBadge', () => {
  it('Test 1 — isStale=false → non renderizza nulla', () => {
    const { container } = render(
      <StaleDataBadge isStale={false} dataSnapshotAt="2026-05-20T14:30:00Z" />,
    );
    expect(container).toBeEmptyDOMElement();
    expect(screen.queryByTestId('stale-data-badge')).not.toBeInTheDocument();
  });

  it('Test 2 — isStale=true + dataSnapshotAt valido → badge visibile + accessibilità', () => {
    render(
      <StaleDataBadge isStale={true} dataSnapshotAt="2026-05-20T14:30:00Z" />,
    );
    const badge = screen.getByTestId('stale-data-badge');
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveAttribute('role', 'alert');
    expect(badge).toHaveAttribute('aria-live', 'polite');

    const text = screen.getByTestId('stale-data-badge-text').textContent ?? '';
    expect(text).toMatch(/^Dati al .+ — aggiornamento FMP non disponibile$/);
    // Il formatter usa locale it-IT; non vincoliamo la stringa esatta per
    // evitare flakiness su locale/timezone CI, ma escludiamo il fallback.
    expect(text).not.toContain('data sconosciuta');
  });

  it('Test 3 — isStale=true + dataSnapshotAt=null → badge visibile con fallback "data sconosciuta"', () => {
    render(<StaleDataBadge isStale={true} dataSnapshotAt={null} />);
    const badge = screen.getByTestId('stale-data-badge');
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveAttribute('role', 'alert');

    expect(screen.getByTestId('stale-data-badge-text')).toHaveTextContent(
      'Dati al data sconosciuta — aggiornamento FMP non disponibile',
    );
  });
});
