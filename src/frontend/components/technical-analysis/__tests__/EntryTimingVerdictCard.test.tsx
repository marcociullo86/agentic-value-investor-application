/**
 * Vitest — EntryTimingVerdictCard (TSK-336, US-102, EP-024 Fase 1).
 *
 * Copre i 5 verdetti EntryTimingVerdict:
 *  - ENTRY_FAVORABLE  → badge verde "ENTRY FAVOREVOLE"
 *  - ENTRY_NEUTRAL    → badge amber "ENTRY NEUTRO"
 *  - ENTRY_UNFAVORABLE → badge rosso "ENTRY SFAVOREVOLE"
 *  - WAIT             → badge blu "ASPETTA"
 *  - INDETERMINATE    → badge grigio "INDETERMINATO"
 *
 * Verifica i 3 Screen del rationale Elder (screen1/2/3) leggibili.
 * A11y: role="status" + aria-label completo.
 *
 * AC US-102 §"Contract test" + TSK-336 §"E2E Playwright / A11y".
 */

import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { EntryTimingVerdictCard } from '../EntryTimingVerdictCard';
import type { EntryTimingAdvisor, EntryTimingVerdict } from '@/lib/api/technical';

// ---------------------------------------------------------------------------
// Factory
// ---------------------------------------------------------------------------

function makeAdvisor(
  verdict: EntryTimingVerdict,
  reentryCode?: string,
): EntryTimingAdvisor {
  return {
    verdict,
    reentryCondition:
      verdict === 'WAIT' && reentryCode
        ? { code: reentryCode as import('@/lib/api/technical').ReentryConditionCode, description: `Re-entry quando ${reentryCode}` }
        : null,
    rationale: {
      screen1: `Screen 1 testo per ${verdict}`,
      screen2: `Screen 2 testo per ${verdict}`,
      screen3: `Screen 3 testo per ${verdict}`,
      wikiCitations: ['ta-entry-timing-stock-detail'],
    },
    viGate: 'Layer advisory di timing.',
  };
}

// ---------------------------------------------------------------------------
// Suite
// ---------------------------------------------------------------------------

describe('EntryTimingVerdictCard — 5 verdetti', () => {
  const cases: Array<{ verdict: EntryTimingVerdict; expectedLabel: RegExp }> = [
    { verdict: 'ENTRY_FAVORABLE',   expectedLabel: /ENTRY FAVOREVOLE/i },
    { verdict: 'ENTRY_NEUTRAL',     expectedLabel: /ENTRY NEUTRO/i },
    { verdict: 'ENTRY_UNFAVORABLE', expectedLabel: /ENTRY SFAVOREVOLE/i },
    { verdict: 'WAIT',              expectedLabel: /ASPETTA/i },
    { verdict: 'INDETERMINATE',     expectedLabel: /INDETERMINATO/i },
  ];

  for (const { verdict, expectedLabel } of cases) {
    it(`verdetto ${verdict} — badge corretto + data-verdict`, () => {
      render(<EntryTimingVerdictCard advisor={makeAdvisor(verdict, 'RSI_BELOW_50')} />);

      const card = screen.getByTestId('ta-entry-timing-card');
      expect(card).toHaveAttribute('data-verdict', verdict);

      const badge = screen.getByTestId('ta-entry-timing-badge');
      expect(badge).toHaveTextContent(expectedLabel);
      expect(badge).toHaveAttribute('role', 'status');
    });
  }

  it('screen 1/2/3 del rationale Elder visibili', () => {
    render(<EntryTimingVerdictCard advisor={makeAdvisor('ENTRY_FAVORABLE')} />);

    expect(screen.getByTestId('ta-screen-1').textContent).toMatch(/screen 1 testo/i);
    expect(screen.getByTestId('ta-screen-2').textContent).toMatch(/screen 2 testo/i);
    expect(screen.getByTestId('ta-screen-3').textContent).toMatch(/screen 3 testo/i);
  });

  it('badge ha aria-label completo per screen reader (a11y WCAG 2.2 AA)', () => {
    render(<EntryTimingVerdictCard advisor={makeAdvisor('ENTRY_FAVORABLE')} />);

    const badge = screen.getByTestId('ta-entry-timing-badge');
    const ariaLabel = badge.getAttribute('aria-label') ?? '';
    expect(ariaLabel).toContain('Verdetto entry-timing');
    expect(ariaLabel).toContain('Screen 1');
    expect(ariaLabel).toContain('Screen 2');
    expect(ariaLabel).toContain('Screen 3');
  });
});
