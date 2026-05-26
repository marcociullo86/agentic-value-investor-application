import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { useAuthStore } from '@/lib/stores/useAuthStore';

vi.mock('@/lib/api/moat', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api/moat')>(
    '@/lib/api/moat',
  );
  return {
    ...actual,
    fetchMoatChecklist: vi.fn(),
    upsertMoatEntry: vi.fn(),
  };
});

import { MoatChecklist } from './MoatChecklist';
import * as moatApi from '@/lib/api/moat';

const mockedFetch = moatApi.fetchMoatChecklist as ReturnType<typeof vi.fn>;
const mockedUpsert = moatApi.upsertMoatEntry as ReturnType<typeof vi.fn>;

describe('MoatChecklist', () => {
  beforeEach(() => {
    useAuthStore.setState({
      accessToken: 'token-123',
      user: { id: '1', email: 'a@b.c', displayName: null, createdAt: '' },
    });
    mockedFetch.mockReset();
    mockedUpsert.mockReset();
  });

  afterEach(() => {
    vi.resetAllMocks();
  });

  it('renders nothing when not authenticated', () => {
    useAuthStore.setState({ accessToken: null, user: null });
    const { container } = render(<MoatChecklist ticker="AAPL" />);
    expect(container.firstChild).toBeNull();
  });

  it('renders all four moat categories', async () => {
    mockedFetch.mockResolvedValueOnce({
      ticker: 'AAPL',
      entries: [
        { moatType: 'INTANGIBLE_ASSETS', status: null, note: null, updatedAt: null },
        { moatType: 'SWITCHING_COSTS', status: null, note: null, updatedAt: null },
        { moatType: 'NETWORK_EFFECT', status: null, note: null, updatedAt: null },
        { moatType: 'COST_ADVANTAGE', status: null, note: null, updatedAt: null },
      ],
    });

    render(<MoatChecklist ticker="AAPL" />);

    await waitFor(() => {
      expect(screen.getByTestId('moat-INTANGIBLE_ASSETS')).toBeInTheDocument();
    });
    expect(screen.getByTestId('moat-SWITCHING_COSTS')).toBeInTheDocument();
    expect(screen.getByTestId('moat-NETWORK_EFFECT')).toBeInTheDocument();
    expect(screen.getByTestId('moat-COST_ADVANTAGE')).toBeInTheDocument();
  });

  it('hydrates select with persisted status from BE', async () => {
    mockedFetch.mockResolvedValueOnce({
      ticker: 'AAPL',
      entries: [
        {
          moatType: 'NETWORK_EFFECT',
          status: 'PRESENT',
          note: 'platform dynamics',
          updatedAt: '2026-05-22T10:00:00Z',
        },
        { moatType: 'INTANGIBLE_ASSETS', status: null, note: null, updatedAt: null },
        { moatType: 'SWITCHING_COSTS', status: null, note: null, updatedAt: null },
        { moatType: 'COST_ADVANTAGE', status: null, note: null, updatedAt: null },
      ],
    });

    render(<MoatChecklist ticker="AAPL" />);

    await waitFor(() => {
      const select = screen.getByTestId('moat-status-NETWORK_EFFECT') as HTMLSelectElement;
      expect(select.value).toBe('PRESENT');
    });
    const note = screen.getByTestId('moat-note-NETWORK_EFFECT') as HTMLTextAreaElement;
    expect(note.value).toBe('platform dynamics');
  });

  it('calls upsert on blur after a status change', async () => {
    mockedFetch.mockResolvedValueOnce({
      ticker: 'AAPL',
      entries: [
        { moatType: 'INTANGIBLE_ASSETS', status: null, note: null, updatedAt: null },
        { moatType: 'SWITCHING_COSTS', status: null, note: null, updatedAt: null },
        { moatType: 'NETWORK_EFFECT', status: null, note: null, updatedAt: null },
        { moatType: 'COST_ADVANTAGE', status: null, note: null, updatedAt: null },
      ],
    });
    mockedUpsert.mockResolvedValueOnce({
      moatType: 'COST_ADVANTAGE',
      status: 'PRESENT',
      note: null,
      updatedAt: '2026-05-22T10:30:00Z',
    });

    render(<MoatChecklist ticker="AAPL" />);

    const select = await waitFor(() =>
      screen.getByTestId('moat-status-COST_ADVANTAGE'),
    );
    fireEvent.change(select, { target: { value: 'PRESENT' } });
    fireEvent.blur(select);

    await waitFor(() => {
      expect(mockedUpsert).toHaveBeenCalledWith('AAPL', {
        moatType: 'COST_ADVANTAGE',
        status: 'PRESENT',
        note: null,
      });
    });
  });
});
