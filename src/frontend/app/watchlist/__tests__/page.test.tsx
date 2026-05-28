import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import WatchlistPage from '../page';

const pushMock = vi.fn();
const replaceMock = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: pushMock, replace: replaceMock }),
  usePathname: () => '/watchlist',
  useSearchParams: () => new URLSearchParams(''),
}));

vi.mock('@/lib/stores/useAuthStore', () => ({
  useAuthStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({ accessToken: 'fake-token', rehydrationStatus: 'done' }),
}));

vi.mock('@/lib/stores/useWatchlistStore', () => ({
  useWatchlistStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({
      items: [],
      loading: false,
      error: null,
      fetch: vi.fn(),
      add: vi.fn(),
      remove: vi.fn(),
    }),
}));

describe('WatchlistPage — US-067 form validation', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows inline ticker error when submitting empty ticker', async () => {
    const user = userEvent.setup();
    render(<WatchlistPage />);

    await user.click(screen.getByTestId('watchlist-add-submit'));

    expect(
      await screen.findByText('Il ticker è obbligatorio'),
    ).toBeInTheDocument();

    const tickerInput = screen.getByTestId('watchlist-add-input');
    expect(tickerInput).toHaveAttribute('aria-describedby', 'ticker-error');
  });

  it('renders error summary with Ticker label on validation failure', async () => {
    const user = userEvent.setup();
    render(<WatchlistPage />);

    await user.click(screen.getByTestId('watchlist-add-submit'));

    const summary = await screen.findByText(/errore nel modulo/);
    expect(summary.closest('[aria-live="assertive"]')).toBeInTheDocument();
    expect(summary.closest('[aria-live]')!.textContent).toContain('Ticker');
  });
});
