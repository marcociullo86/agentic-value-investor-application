/**
 * Vitest — AntiCopartBanner (TSK-344, US-104, EP-024 Fase 2).
 *
 * Copre:
 *  - Banner visibile con testo warning corretto (warningAntiCopart popolato).
 *  - Role ARIA `alert` + aria-live `assertive` (a11y WCAG 2.2 AA).
 *  - Link "Approfondisci →" presente e puntante al wiki.
 *  - Heading "Attenzione — trappola COPART" visibile.
 *
 * AC US-104:
 *  - Banner anti-COPART appare SOLO quando `warningAntiCopart` non è vuoto,
 *    con ruolo ARIA `alert` e link "Approfondisci →".
 *  - (L'assenza quando warningAntiCopart è null è testata in SummaryPageClient.test.tsx)
 */

import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { AntiCopartBanner } from '../AntiCopartBanner';

const CPRT_WARNING =
  'Verdetto fondamentale positivo ma timing tecnico sfavorevole. ' +
  'Acquistare ora rischia uno stop loss prematuro su una tesi VI corretta — ' +
  'situazione COPART. Attendere il setup tecnico migliore.';

describe('AntiCopartBanner', () => {
  it('mostra il testo del warning fornito dal BE (verbatim)', () => {
    render(<AntiCopartBanner warning={CPRT_WARNING} />);

    const banner = screen.getByTestId('summary-anti-copart-banner');
    expect(banner).toBeInTheDocument();

    const text = screen.getByTestId('summary-anti-copart-text');
    expect(text).toHaveTextContent(CPRT_WARNING);
  });

  it('ha role="alert" e aria-live="assertive" (a11y WCAG 2.2 AA)', () => {
    render(<AntiCopartBanner warning={CPRT_WARNING} />);

    const banner = screen.getByTestId('summary-anti-copart-banner');
    expect(banner).toHaveAttribute('role', 'alert');
    expect(banner).toHaveAttribute('aria-live', 'assertive');
  });

  it('link "Approfondisci" presente e accessibile via tastiera', () => {
    render(<AntiCopartBanner warning={CPRT_WARNING} />);

    const link = screen.getByTestId('summary-anti-copart-deeplink');
    expect(link).toBeInTheDocument();
    expect(link.textContent).toMatch(/approfondisci/i);
    // Il link punta a un percorso wiki non-vuoto
    const href = link.getAttribute('href');
    expect(href).toBeTruthy();
    expect(href).toContain('ta-vs-vi-decision-layer');
  });

  it('heading "trappola COPART" visibile (non solo colore come canale)', () => {
    render(<AntiCopartBanner warning={CPRT_WARNING} />);

    // L'heading è presente per screen reader (non solo colore+icona)
    expect(screen.getByText(/trappola copart/i)).toBeInTheDocument();
  });
});
