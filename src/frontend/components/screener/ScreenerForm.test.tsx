import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ScreenerForm } from './ScreenerForm';
import { ResultsListInline } from './ResultsListInline';
import { useScreenerStore } from '@/lib/stores/useScreenerStore';
import { EMPTY_SCREENER_CRITERIA } from '@/lib/api/screener';

/**
 * Test ScreenerForm (TSK-006 DoD).
 *
 * Mock strategy:
 *  - `@/lib/api/screener.screen` → vi.fn() controlla la response page.
 *  - `next/navigation` → mock useRouter (pushMock per asserire navigazione,
 *    sufficiente anche se i test del form non navigano direttamente).
 *  - Store: reset prima di ogni test per isolamento.
 *
 * Test coperti:
 *  1. submit form vuoto (no filtri) → screen() chiamato con criteri vuoti
 *     + limit=50 default (DEFAULT_SCREENER_LIMIT).
 *  2. submit con marketCap=LARGE + sector=INFORMATION_TECHNOLOGY →
 *     argomenti corretti passati a screen().
 *  3. risultato vuoto (mock items: []) → messaggio
 *     "Nessun titolo soddisfa i criteri" visibile (US-002 AC).
 */

const screenMock = vi.fn();
const pushMock = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: pushMock }),
}));

vi.mock('@/lib/api/screener', async () => {
  const actual =
    await vi.importActual<typeof import('@/lib/api/screener')>(
      '@/lib/api/screener',
    );
  return {
    ...actual,
    screen: (...args: unknown[]) => screenMock(...args),
  };
});

function resetStore(): void {
  useScreenerStore.setState({
    filters: { ...EMPTY_SCREENER_CRITERIA },
    results: [],
    cursor: null,
    loading: false,
    error: null,
    hasSubmitted: false,
  });
}

describe('ScreenerForm', () => {
  beforeEach(() => {
    screenMock.mockReset();
    pushMock.mockReset();
    resetStore();
  });

  it('submit form vuoto chiama screen() con criteri vuoti + limit=50', async () => {
    screenMock.mockResolvedValueOnce({ items: [], nextCursor: null });
    const user = userEvent.setup();
    render(<ScreenerForm />);

    await user.click(screen.getByRole('button', { name: /applica filtri/i }));

    await waitFor(() => {
      expect(screenMock).toHaveBeenCalledTimes(1);
    });
    const [criteria, cursor] = screenMock.mock.calls[0] as [
      typeof EMPTY_SCREENER_CRITERIA,
      string | undefined,
    ];
    expect(criteria.marketCap).toEqual([]);
    expect(criteria.sector).toEqual([]);
    expect(criteria.excludeHardToPredict).toBe(false);
    expect(criteria.limit).toBe(50);
    expect(cursor).toBeUndefined();
  });

  it('submit con marketCap=LARGE + sector=INFORMATION_TECHNOLOGY passa query corretta', async () => {
    screenMock.mockResolvedValueOnce({
      items: [
        {
          ticker: 'AAPL',
          companyName: 'Apple Inc.',
          sector: 'Information Technology',
          marketCapUsd: 3_000_000_000_000,
        },
      ],
      nextCursor: null,
    });
    const user = userEvent.setup();
    render(<ScreenerForm />);

    await user.click(screen.getByLabelText(/Large Cap/i));
    await user.click(screen.getByLabelText(/Tecnologia/i));
    await user.click(screen.getByRole('button', { name: /applica filtri/i }));

    await waitFor(() => {
      expect(screenMock).toHaveBeenCalledTimes(1);
    });
    const [criteria] = screenMock.mock.calls[0] as [
      typeof EMPTY_SCREENER_CRITERIA,
    ];
    expect(criteria.marketCap).toEqual(['LARGE']);
    expect(criteria.sector).toEqual(['INFORMATION_TECHNOLOGY']);
    expect(criteria.excludeHardToPredict).toBe(false);
    expect(criteria.limit).toBe(50);
  });

  it('risultato vuoto mostra "Nessun titolo soddisfa i criteri"', async () => {
    screenMock.mockResolvedValueOnce({ items: [], nextCursor: null });
    const user = userEvent.setup();
    render(
      <>
        <ScreenerForm />
        <ResultsListInline />
      </>,
    );

    await user.click(screen.getByRole('button', { name: /applica filtri/i }));

    expect(
      await screen.findByText(/nessun titolo soddisfa i criteri/i),
    ).toBeInTheDocument();
  });
});
