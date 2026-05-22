import { describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { WatchlistTable } from './WatchlistTable';
import type { WatchlistItem } from '@/lib/api/watchlist';

const fixture: WatchlistItem[] = [
  {
    ticker: 'AAPL',
    companyName: 'Apple Inc.',
    sector: 'Information Technology',
    marketCapUsd: 3_000_000_000_000,
    addedAt: '2026-05-22T10:00:00Z',
  },
  {
    ticker: 'MSFT',
    companyName: 'Microsoft Corporation',
    sector: 'Information Technology',
    marketCapUsd: null,
    addedAt: '2026-05-22T11:00:00Z',
  },
];

describe('WatchlistTable', () => {
  it('renders empty state when items is empty', () => {
    render(<WatchlistTable items={[]} onRemove={() => {}} />);
    expect(screen.getByTestId('watchlist-empty')).toBeInTheDocument();
  });

  it('renders one row per item and the ticker link', () => {
    render(<WatchlistTable items={fixture} onRemove={() => {}} />);
    expect(screen.getByTestId('watchlist-row-AAPL')).toBeInTheDocument();
    expect(screen.getByTestId('watchlist-row-MSFT')).toBeInTheDocument();
    const link = screen.getByTestId('watchlist-link-AAPL') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('/analysis/AAPL');
  });

  it('formats market cap with B/T suffix', () => {
    render(<WatchlistTable items={fixture} onRemove={() => {}} />);
    expect(screen.getByText('$3.00T')).toBeInTheDocument();
  });

  it('shows em dash for missing market cap', () => {
    render(<WatchlistTable items={fixture} onRemove={() => {}} />);
    const row = screen.getByTestId('watchlist-row-MSFT');
    expect(row.textContent).toContain('—');
  });

  it('invokes onRemove with ticker when remove button clicked', () => {
    const onRemove = vi.fn();
    render(<WatchlistTable items={fixture} onRemove={onRemove} />);
    fireEvent.click(screen.getByTestId('watchlist-remove-AAPL'));
    expect(onRemove).toHaveBeenCalledWith('AAPL');
  });
});
