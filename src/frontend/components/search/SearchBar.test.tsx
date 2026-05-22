import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SearchBar } from './SearchBar';

/**
 * Test SearchBar (TSK-003 DoD).
 *
 * Mock strategy:
 *  - `@/lib/api/search` → mock `searchTicker` con `vi.fn()` per controllare
 *    risposte (single match / multi / empty / error).
 *  - `next/navigation` → mock `useRouter` per asserire `router.push`.
 */

const pushMock = vi.fn();
const searchTickerMock = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: pushMock }),
}));

vi.mock('@/lib/api/search', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api/search')>(
    '@/lib/api/search',
  );
  return {
    ...actual,
    searchTicker: (...args: unknown[]) => searchTickerMock(...args),
  };
});

describe('SearchBar', () => {
  beforeEach(() => {
    pushMock.mockReset();
    searchTickerMock.mockReset();
  });

  it('normalizza input lowercase a uppercase prima del fetch', async () => {
    searchTickerMock.mockResolvedValueOnce({
      items: [{ ticker: 'AAPL', companyName: 'Apple Inc.' }],
    });
    const user = userEvent.setup();
    render(<SearchBar />);

    const input = screen.getByLabelText(/cerca ticker/i);
    await user.type(input, 'aapl');
    await user.click(screen.getByRole('button', { name: /cerca/i }));

    await waitFor(() => {
      expect(searchTickerMock).toHaveBeenCalledWith('AAPL');
    });
  });

  it('su singolo match esatto naviga a /analysis/{ticker}', async () => {
    searchTickerMock.mockResolvedValueOnce({
      items: [{ ticker: 'AAPL', companyName: 'Apple Inc.' }],
    });
    const user = userEvent.setup();
    render(<SearchBar />);

    await user.type(screen.getByLabelText(/cerca ticker/i), 'AAPL');
    await user.keyboard('{Enter}');

    await waitFor(() => {
      expect(pushMock).toHaveBeenCalledWith('/analysis/?ticker=AAPL');
    });
  });

  it('su lista vuota mostra "Ticker non trovato"', async () => {
    searchTickerMock.mockResolvedValueOnce({ items: [] });
    const user = userEvent.setup();
    render(<SearchBar />);

    await user.type(screen.getByLabelText(/cerca ticker/i), 'ZZZZ');
    await user.click(screen.getByRole('button', { name: /cerca/i }));

    expect(await screen.findByText(/ticker non trovato/i)).toBeInTheDocument();
    expect(pushMock).not.toHaveBeenCalled();
  });

  it('su 404 axios mostra "Ticker non trovato"', async () => {
    const axiosErr = Object.assign(new Error('Not Found'), {
      isAxiosError: true,
      response: { status: 404, data: {} },
      config: {},
      toJSON: () => ({}),
    });
    searchTickerMock.mockRejectedValueOnce(axiosErr);
    const user = userEvent.setup();
    render(<SearchBar />);

    await user.type(screen.getByLabelText(/cerca ticker/i), 'NOPE');
    await user.click(screen.getByRole('button', { name: /cerca/i }));

    expect(await screen.findByText(/ticker non trovato/i)).toBeInTheDocument();
  });

  it('validazione client-side blocca caratteri invalidi (no fetch)', async () => {
    const user = userEvent.setup();
    render(<SearchBar />);

    await user.type(screen.getByLabelText(/cerca ticker/i), '!!!');
    await user.click(screen.getByRole('button', { name: /cerca/i }));

    expect(
      await screen.findByText(/solo lettere, cifre, punto, trattino/i),
    ).toBeInTheDocument();
    expect(searchTickerMock).not.toHaveBeenCalled();
  });
});
