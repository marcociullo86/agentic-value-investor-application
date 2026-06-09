/**
 * Vitest — SummaryPageClient (TSK-344, US-104, EP-024 Fase 2).
 *
 * Copre l'integrazione dell'orchestratore `SummaryPageClient`:
 *  - stato loading → skeleton visibile
 *  - stato error → panel errore con pulsante retry
 *  - verdetto ENTER_NOW con warningAntiCopart = null → banner anti-COPART ASSENTE
 *  - verdetto WAIT_FOR_SETUP con warningAntiCopart popolato → banner PRESENTE
 *  - verdetto AVOID (VI gate failed) → hero "EVITA", banner ASSENTE
 *  - verdetto INSUFFICIENT_DATA → hero "DATI INSUFFICIENTI"
 *
 * AC US-104:
 *  - Banner anti-COPART appare SOLO quando `warningAntiCopart` non è vuoto.
 *  - Test Vitest coprono i 4 stati + presenza/assenza banner anti-COPART.
 *
 * Strategy:
 *  - Mock di `@/lib/hooks/useSummary` con `vi.fn()` per iniettare
 *    deterministicamente ogni stato senza dipendenze SWR / network.
 *  - Mock di componenti pesanti (BacktestPanel — ha dipendenze SWR/useBacktest)
 *    per isolare il client dalla catena di dipendenze.
 *  - Mock di next/navigation per evitare errori nell'hook useSummary.
 *
 * Pattern coerente con BacktestPanel.test.tsx (TSK-352): mock hook + asserzioni
 * su data-testid.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { SummaryPageClient } from '../SummaryPageClient';
import type { UseSummaryResult } from '@/lib/hooks/useSummary';
import type { SummaryVerdictResponse } from '@/lib/api/summary';

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

const mockMutate = vi.fn();

vi.mock('@/lib/hooks/useSummary', () => ({
  useSummary: vi.fn(),
}));

// BacktestPanel ha dipendenze SWR/useBacktest/useEquityLocalStorage — mockato
// per isolare SummaryPageClient.
vi.mock('@/components/backtest', () => ({
  BacktestPanel: ({ ticker }: { ticker: string }) => (
    <div data-testid="backtest-panel-mock">{ticker}</div>
  ),
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams('ticker=AAPL'),
  usePathname: () => '/analysis',
  useParams: () => ({}),
}));

vi.mock('next/link', () => ({
  __esModule: true,
  default: ({
    children,
    href,
    ...props
  }: {
    children: React.ReactNode;
    href: string;
    [k: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

// ---------------------------------------------------------------------------
// Fixture factory
// ---------------------------------------------------------------------------

function makeSummaryData(
  override: Partial<SummaryVerdictResponse> = {},
): SummaryVerdictResponse {
  return {
    ticker: 'AAPL',
    evaluatedAt: '2026-06-09T08:00:00Z',
    summaryVerdict: 'ENTER_NOW',
    viVerdict: 'GREEN_DOMINANT',
    deepAnalysisStatus: 'AVAILABLE',
    deepVerdict: 'OK',
    taVerdict: 'ENTRY_FAVORABLE',
    rationale: {
      viSummary: 'AAPL profilo VI eccellente.',
      deepSummary: 'Munger OK.',
      taSummary: 'UPTREND confermato.',
      decisionPath: 'VI gate passed → TA gate: ENTRY_FAVORABLE → ENTER_NOW',
    },
    reentryCondition: null,
    wikiCitations: [],
    warningAntiCopart: null,
    ...override,
  };
}

function stubUseSummary(partial: Partial<UseSummaryResult>) {
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const { useSummary } = require('@/lib/hooks/useSummary') as {
    useSummary: ReturnType<typeof vi.fn>;
  };
  useSummary.mockReturnValue({
    data: undefined,
    isLoading: false,
    error: undefined,
    mutate: mockMutate,
    ...partial,
  } as UseSummaryResult);
}

// ---------------------------------------------------------------------------
// Suite
// ---------------------------------------------------------------------------

describe('SummaryPageClient — stati hook + presenza/assenza banner anti-COPART', () => {
  beforeEach(() => {
    mockMutate.mockReset();
  });

  // -------------------------------------------------------------------------
  // Loading
  // -------------------------------------------------------------------------
  it('stato loading → skeleton visibile', () => {
    stubUseSummary({ isLoading: true });
    render(<SummaryPageClient ticker="AAPL" />);

    expect(screen.getByTestId('summary-loading')).toBeInTheDocument();
    expect(screen.getByTestId('summary-loading')).toHaveAttribute('role', 'status');
    expect(screen.getByTestId('summary-loading')).toHaveAttribute('aria-busy', 'true');
    // Hero e banner non mostrati durante loading
    expect(screen.queryByTestId('summary-hero')).not.toBeInTheDocument();
    expect(screen.queryByTestId('summary-anti-copart-banner')).not.toBeInTheDocument();
  });

  // -------------------------------------------------------------------------
  // Error
  // -------------------------------------------------------------------------
  it('stato error → panel errore visibile con pulsante retry', () => {
    stubUseSummary({
      isLoading: false,
      error: { status: 503, message: 'Riepilogo temporaneamente non disponibile.' },
    });
    render(<SummaryPageClient ticker="AAPL" />);

    const errorPanel = screen.getByTestId('summary-error');
    expect(errorPanel).toBeInTheDocument();
    expect(errorPanel).toHaveAttribute('role', 'alert');

    const retryBtn = screen.getByTestId('summary-error-retry');
    expect(retryBtn).toBeInTheDocument();
    fireEvent.click(retryBtn);
    expect(mockMutate).toHaveBeenCalledTimes(1);
  });

  // -------------------------------------------------------------------------
  // ENTER_NOW — warningAntiCopart null → banner ASSENTE
  // -------------------------------------------------------------------------
  it('ENTER_NOW con warningAntiCopart=null → hero "ENTRA ORA", banner anti-COPART ASSENTE', () => {
    stubUseSummary({
      data: makeSummaryData({
        summaryVerdict: 'ENTER_NOW',
        warningAntiCopart: null,
      }),
    });
    render(<SummaryPageClient ticker="AAPL" />);

    const hero = screen.getByTestId('summary-hero');
    expect(hero).toHaveAttribute('data-verdict', 'ENTER_NOW');
    expect(screen.getByTestId('summary-hero-badge')).toHaveTextContent('ENTRA ORA');

    // Banner anti-COPART ASSENTE quando warningAntiCopart è null (AC US-104)
    expect(screen.queryByTestId('summary-anti-copart-banner')).not.toBeInTheDocument();
  });

  // -------------------------------------------------------------------------
  // WAIT_FOR_SETUP — warningAntiCopart popolato → banner PRESENTE
  // -------------------------------------------------------------------------
  it('WAIT_FOR_SETUP con warningAntiCopart popolato → hero "ASPETTA", banner anti-COPART PRESENTE', () => {
    const WARNING_TEXT =
      'Verdetto fondamentale positivo ma timing tecnico sfavorevole. ' +
      'Acquistare ora rischia uno stop loss prematuro su una tesi VI corretta — ' +
      'situazione COPART. Attendere il setup tecnico migliore.';

    stubUseSummary({
      data: makeSummaryData({
        summaryVerdict: 'WAIT_FOR_SETUP',
        viVerdict: 'GREEN_DOMINANT',
        taVerdict: 'WAIT',
        reentryCondition: {
          code: 'RSI_BELOW_50',
          description: 'RSI 14d rientra sotto 50',
        },
        rationale: {
          viSummary: 'CPRT profilo VI solido.',
          deepSummary: 'Munger OK.',
          taSummary: 'RSI 72 overbought, MACD flat.',
          decisionPath: 'VI gate passed → TA gate: WAIT (RSI overbought) → WAIT_FOR_SETUP',
        },
        warningAntiCopart: WARNING_TEXT,
      }),
    });
    render(<SummaryPageClient ticker="CPRT" />);

    // Hero badge "ASPETTA"
    const hero = screen.getByTestId('summary-hero');
    expect(hero).toHaveAttribute('data-verdict', 'WAIT_FOR_SETUP');
    expect(screen.getByTestId('summary-hero-badge')).toHaveTextContent('ASPETTA');

    // Banner anti-COPART PRESENTE (warningAntiCopart non vuoto — AC US-104)
    const banner = screen.getByTestId('summary-anti-copart-banner');
    expect(banner).toBeInTheDocument();
    expect(banner).toHaveAttribute('role', 'alert');
    expect(screen.getByTestId('summary-anti-copart-text')).toHaveTextContent(WARNING_TEXT);
  });

  // -------------------------------------------------------------------------
  // AVOID — warningAntiCopart null → banner ASSENTE
  // -------------------------------------------------------------------------
  it('AVOID con viVerdict RED_DOMINANT → hero "EVITA", banner anti-COPART ASSENTE', () => {
    stubUseSummary({
      data: makeSummaryData({
        summaryVerdict: 'AVOID',
        viVerdict: 'RED_DOMINANT',
        taVerdict: 'ENTRY_UNFAVORABLE',
        rationale: {
          viSummary: 'XVIT profilo VI negativo.',
          deepSummary: null,
          taSummary: 'DOWNTREND confermato.',
          decisionPath: 'VI gate failed (RED_DOMINANT) → AVOID',
        },
        warningAntiCopart: null,
      }),
    });
    render(<SummaryPageClient ticker="XVIT" />);

    const hero = screen.getByTestId('summary-hero');
    expect(hero).toHaveAttribute('data-verdict', 'AVOID');
    expect(screen.getByTestId('summary-hero-badge')).toHaveTextContent('EVITA');

    // Banner assente (AVOID non è WAIT_FOR_SETUP)
    expect(screen.queryByTestId('summary-anti-copart-banner')).not.toBeInTheDocument();
  });

  // -------------------------------------------------------------------------
  // INSUFFICIENT_DATA → hero "DATI INSUFFICIENTI"
  // -------------------------------------------------------------------------
  it('INSUFFICIENT_DATA → hero "DATI INSUFFICIENTI", banner ASSENTE', () => {
    stubUseSummary({
      data: makeSummaryData({
        summaryVerdict: 'INSUFFICIENT_DATA',
        viVerdict: 'INDETERMINATE_DOMINANT',
        deepVerdict: null,
        taVerdict: null,
        warningAntiCopart: null,
      }),
    });
    render(<SummaryPageClient ticker="TINY" />);

    const hero = screen.getByTestId('summary-hero');
    expect(hero).toHaveAttribute('data-verdict', 'INSUFFICIENT_DATA');
    expect(screen.getByTestId('summary-hero-badge')).toHaveTextContent('DATI INSUFFICIENTI');

    expect(screen.queryByTestId('summary-anti-copart-banner')).not.toBeInTheDocument();
  });

  // -------------------------------------------------------------------------
  // Footer disclaimer sempre visibile (tutti gli stati con dati)
  // -------------------------------------------------------------------------
  it('footer disclaimer visibile in tutte le viste con dati', () => {
    stubUseSummary({
      data: makeSummaryData(),
    });
    render(<SummaryPageClient ticker="AAPL" />);

    const footer = screen.getByTestId('summary-disclaimer-footer');
    expect(footer).toBeInTheDocument();
    expect(footer.textContent).toMatch(/advisory/i);
  });

  // -------------------------------------------------------------------------
  // summary-page container sempre presente
  // -------------------------------------------------------------------------
  it('il container data-testid="summary-page" è sempre presente (anche loading)', () => {
    stubUseSummary({ isLoading: true });
    render(<SummaryPageClient ticker="AAPL" />);

    expect(screen.getByTestId('summary-page')).toBeInTheDocument();
  });

  // -------------------------------------------------------------------------
  // Titolo pagina contiene il ticker
  // -------------------------------------------------------------------------
  it('titolo pagina contiene il ticker normalizzato', () => {
    stubUseSummary({ isLoading: false });
    render(<SummaryPageClient ticker="AAPL" />);

    const title = screen.getByTestId('summary-page-title');
    expect(title).toBeInTheDocument();
    expect(title.textContent).toContain('AAPL');
  });
});
