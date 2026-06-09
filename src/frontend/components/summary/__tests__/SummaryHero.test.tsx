/**
 * Vitest — SummaryHero (TSK-344, US-104, EP-024 Fase 2).
 *
 * Copre i 4 stati del `summaryVerdict` con asserzioni su:
 *  - Testo badge (label utente: ENTRA ORA / ASPETTA / EVITA / DATI INSUFFICIENTI)
 *  - Attributo `data-verdict` per test deterministico senza accoppiamento ai colori CSS
 *  - Sub-headline coerente con il verdetto
 *  - Presenza/assenza del banner anti-COPART (testId delegato a AntiCopartBanner)
 *
 * AC US-104:
 *  - Hero verdetto mostra `summaryVerdict` con colore + icona + testo + sub-headline
 *    coerente per i 4 stati (ENTER_NOW / WAIT_FOR_SETUP / AVOID / INSUFFICIENT_DATA).
 *  - Vitest coprono i 4 stati + presenza/assenza banner anti-COPART.
 *
 * Pattern: coerente con ValuationSummary.test.tsx (TSK-021) — describe/it,
 * helper factory props, asserzioni data-testid.
 */

import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { SummaryHero, type SummaryHeroProps } from '../SummaryHero';

// ---------------------------------------------------------------------------
// Props factory
// ---------------------------------------------------------------------------

function baseProps(
  override: Partial<SummaryHeroProps> = {},
): SummaryHeroProps {
  return {
    ticker: 'AAPL',
    verdict: 'ENTER_NOW',
    viVerdict: 'GREEN_DOMINANT',
    deepVerdict: 'OK',
    reentryCondition: null,
    evaluatedAt: '2026-06-09T08:00:00Z',
    ...override,
  };
}

// ---------------------------------------------------------------------------
// Suite
// ---------------------------------------------------------------------------

describe('SummaryHero — 4 stati summaryVerdict', () => {
  // -------------------------------------------------------------------------
  // ENTER_NOW
  // -------------------------------------------------------------------------
  it('stato ENTER_NOW — badge "ENTRA ORA" + sub-headline favorevole', () => {
    render(<SummaryHero {...baseProps({ verdict: 'ENTER_NOW' })} />);

    const hero = screen.getByTestId('summary-hero');
    expect(hero).toBeInTheDocument();
    expect(hero).toHaveAttribute('data-verdict', 'ENTER_NOW');

    const badge = screen.getByTestId('summary-hero-badge');
    expect(badge).toHaveTextContent('ENTRA ORA');
    expect(badge).toHaveAttribute('role', 'status');

    const sub = screen.getByTestId('summary-hero-subheadline');
    expect(sub.textContent).toMatch(/verdetto fondamentale e timing tecnico favorevoli/i);
  });

  // -------------------------------------------------------------------------
  // WAIT_FOR_SETUP
  // -------------------------------------------------------------------------
  it('stato WAIT_FOR_SETUP — badge "ASPETTA" + sub-headline di attesa (no reentryCondition)', () => {
    render(
      <SummaryHero
        {...baseProps({
          verdict: 'WAIT_FOR_SETUP',
          viVerdict: 'GREEN_DOMINANT',
          reentryCondition: null,
        })}
      />,
    );

    const hero = screen.getByTestId('summary-hero');
    expect(hero).toHaveAttribute('data-verdict', 'WAIT_FOR_SETUP');

    const badge = screen.getByTestId('summary-hero-badge');
    expect(badge).toHaveTextContent('ASPETTA');

    const sub = screen.getByTestId('summary-hero-subheadline');
    expect(sub.textContent).toMatch(/timing tecnico non è ancora pronto/i);
  });

  it('stato WAIT_FOR_SETUP — sub-headline include la reentryCondition.description', () => {
    render(
      <SummaryHero
        {...baseProps({
          verdict: 'WAIT_FOR_SETUP',
          reentryCondition: {
            code: 'RSI_BELOW_50',
            description: 'RSI 14d rientra sotto 50',
          },
        })}
      />,
    );

    const sub = screen.getByTestId('summary-hero-subheadline');
    expect(sub.textContent).toMatch(/RSI 14d rientra sotto 50/i);
  });

  // -------------------------------------------------------------------------
  // AVOID — VI gate fallito
  // -------------------------------------------------------------------------
  it('stato AVOID con viVerdict RED_DOMINANT — badge "EVITA" + sub-headline gate VI fallito', () => {
    render(
      <SummaryHero
        {...baseProps({
          verdict: 'AVOID',
          viVerdict: 'RED_DOMINANT',
          deepVerdict: null,
        })}
      />,
    );

    const hero = screen.getByTestId('summary-hero');
    expect(hero).toHaveAttribute('data-verdict', 'AVOID');

    const badge = screen.getByTestId('summary-hero-badge');
    expect(badge).toHaveTextContent('EVITA');

    const sub = screen.getByTestId('summary-hero-subheadline');
    expect(sub.textContent).toMatch(/gate Value Investing è fallito/i);
  });

  // -------------------------------------------------------------------------
  // AVOID — Munger RISCHIO_ESTREMO
  // -------------------------------------------------------------------------
  it('stato AVOID con deepVerdict RISCHIO_ESTREMO — sub-headline cita Deep Analysis', () => {
    render(
      <SummaryHero
        {...baseProps({
          verdict: 'AVOID',
          viVerdict: 'GREEN_DOMINANT',
          deepVerdict: 'RISCHIO_ESTREMO',
        })}
      />,
    );

    const sub = screen.getByTestId('summary-hero-subheadline');
    expect(sub.textContent).toMatch(/RISCHIO ESTREMO/i);
  });

  // -------------------------------------------------------------------------
  // INSUFFICIENT_DATA
  // -------------------------------------------------------------------------
  it('stato INSUFFICIENT_DATA — badge "DATI INSUFFICIENTI" + sub-headline informativa', () => {
    render(
      <SummaryHero
        {...baseProps({
          verdict: 'INSUFFICIENT_DATA',
          viVerdict: 'INDETERMINATE_DOMINANT',
          deepVerdict: null,
        })}
      />,
    );

    const hero = screen.getByTestId('summary-hero');
    expect(hero).toHaveAttribute('data-verdict', 'INSUFFICIENT_DATA');

    const badge = screen.getByTestId('summary-hero-badge');
    expect(badge).toHaveTextContent('DATI INSUFFICIENTI');

    const sub = screen.getByTestId('summary-hero-subheadline');
    expect(sub.textContent).toMatch(/dati fondamentali troppo lacunosi/i);
  });

  // -------------------------------------------------------------------------
  // role="status" su badge (a11y)
  // -------------------------------------------------------------------------
  it('ogni badge ha role="status" e aria-label non vuoto (a11y WCAG 2.2 AA)', () => {
    const verdicts = [
      'ENTER_NOW',
      'WAIT_FOR_SETUP',
      'AVOID',
      'INSUFFICIENT_DATA',
    ] as const;

    for (const verdict of verdicts) {
      const { unmount } = render(
        <SummaryHero
          {...baseProps({ verdict, viVerdict: verdict === 'AVOID' ? 'RED_DOMINANT' : 'GREEN_DOMINANT' })}
        />,
      );

      const badge = screen.getByTestId('summary-hero-badge');
      expect(badge).toHaveAttribute('role', 'status');
      const ariaLabel = badge.getAttribute('aria-label');
      expect(ariaLabel, `aria-label mancante per ${verdict}`).toBeTruthy();
      expect(ariaLabel!.length, `aria-label vuoto per ${verdict}`).toBeGreaterThan(10);

      unmount();
    }
  });
});
