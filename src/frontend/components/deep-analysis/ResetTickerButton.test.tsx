import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { ResetTickerButton } from './ResetTickerButton';

vi.mock('@/lib/api/deep-analysis', () => ({
  resetTicker: vi.fn().mockResolvedValue({
    ticker: 'AAPL',
    deletedByTable: {},
    totalDeleted: 0,
  }),
}));

describe('ResetTickerButton (EP-020 reset)', () => {
  it('renders the reset trigger button', () => {
    render(<ResetTickerButton ticker="AAPL" />);
    expect(screen.getByTestId('deep-analysis-reset-open')).toBeInTheDocument();
  });

  it('opens the master-password modal on click', () => {
    render(<ResetTickerButton ticker="AAPL" />);
    fireEvent.click(screen.getByTestId('deep-analysis-reset-open'));
    expect(screen.getByTestId('reset-master-password-input')).toBeInTheDocument();
    expect(screen.getByTestId('reset-confirm')).toBeInTheDocument();
  });
});
