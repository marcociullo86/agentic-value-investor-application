import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { AxiosError, AxiosHeaders } from 'axios';
import { useAuthStore } from '@/lib/stores/useAuthStore';

const addMock = vi.fn();

vi.mock('@/lib/stores/useWatchlistStore', () => ({
  useWatchlistStore: (
    selector: (s: Record<string, unknown>) => unknown,
  ): unknown =>
    selector({
      items: [],
      add: addMock,
    }),
}));

import { AddToWatchlistButton } from './AddToWatchlistButton';

function makeAxiosError(status: number, data?: unknown): AxiosError {
  const err = new AxiosError(`Request failed with status code ${status}`, 'ERR');
  err.response = {
    status,
    statusText: '',
    headers: {},
    config: { headers: new AxiosHeaders() },
    data,
  };
  return err;
}

describe('AddToWatchlistButton', () => {
  beforeEach(() => {
    useAuthStore.setState({
      accessToken: 'token-123',
      user: { id: '1', email: 'a@b.c', displayName: null, createdAt: '' },
    });
    addMock.mockReset();
  });

  afterEach(() => {
    vi.resetAllMocks();
  });

  it('renders nothing when not authenticated', () => {
    useAuthStore.setState({ accessToken: null, user: null });
    const { container } = render(<AddToWatchlistButton ticker="AAPL" />);
    expect(container.firstChild).toBeNull();
  });

  it('calls add(ticker) on click and disables button while submitting', async () => {
    let resolveAdd: () => void = () => undefined;
    addMock.mockImplementationOnce(
      () =>
        new Promise<void>((res) => {
          resolveAdd = res;
        }),
    );

    render(<AddToWatchlistButton ticker="AAPL" />);

    const button = screen.getByTestId('add-to-watchlist');
    fireEvent.click(button);

    expect(addMock).toHaveBeenCalledWith('AAPL');
    await waitFor(() => {
      expect(button).toBeDisabled();
    });

    resolveAdd();
    await waitFor(() => {
      expect(button).not.toBeDisabled();
    });
  });

  it('shows user-safe message on 409 conflict (no raw err.message)', async () => {
    addMock.mockRejectedValueOnce(makeAxiosError(409));

    render(<AddToWatchlistButton ticker="AAPL" />);
    fireEvent.click(screen.getByTestId('add-to-watchlist'));

    const alert = await screen.findByRole('alert');
    expect(alert.textContent).toBe('Ticker già presente in watchlist.');
    expect(alert.textContent).not.toContain('409');
    expect(alert.textContent).not.toMatch(/Request failed/i);
  });

  it('shows user-safe message on 5xx server error', async () => {
    addMock.mockRejectedValueOnce(makeAxiosError(500));

    render(<AddToWatchlistButton ticker="AAPL" />);
    fireEvent.click(screen.getByTestId('add-to-watchlist'));

    const alert = await screen.findByRole('alert');
    expect(alert.textContent).not.toContain('500');
    expect(alert.textContent).not.toMatch(/Request failed/i);
    expect(alert.textContent).toMatch(/server|riprova/i);
  });

  it('shows user-safe message on non-axios error (no raw err.message)', async () => {
    addMock.mockRejectedValueOnce(new Error('TypeError: store mutated'));

    render(<AddToWatchlistButton ticker="AAPL" />);
    fireEvent.click(screen.getByTestId('add-to-watchlist'));

    const alert = await screen.findByRole('alert');
    expect(alert.textContent).not.toContain('TypeError');
    expect(alert.textContent).not.toContain('mutated');
    expect(alert.textContent).toMatch(/Aggiunta|Riprova/);
  });
});
