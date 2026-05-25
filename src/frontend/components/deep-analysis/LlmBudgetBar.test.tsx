import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { LlmBudgetBar } from './LlmBudgetBar';

const mockUseAuthStore = vi.fn();
const mockUseLlmBudget = vi.fn();

vi.mock('@/lib/stores/useAuthStore', () => ({
  useAuthStore: (selector: (s: any) => any) => mockUseAuthStore(selector),
}));

vi.mock('@/lib/hooks/useLlmBudget', () => ({
  useLlmBudget: () => mockUseLlmBudget(),
}));

describe('LlmBudgetBar (TSK-157)', () => {
  it('renders nothing for USER role', () => {
    mockUseAuthStore.mockImplementation((selector: any) =>
      selector({ user: { role: 'USER' } }),
    );
    mockUseLlmBudget.mockReturnValue({
      data: { utilization: 50, monthlyCapUsd: 50, totalCostUsd: 25, frozen: false },
      isLoading: false,
      refresh: vi.fn(),
    });

    const { container } = render(<LlmBudgetBar />);
    expect(container).toBeEmptyDOMElement();
    expect(screen.queryByTestId('llm-budget-bar')).not.toBeInTheDocument();
  });

  it('renders budget bar for ADMIN role with correct data', () => {
    mockUseAuthStore.mockImplementation((selector: any) =>
      selector({ user: { role: 'ADMIN' } }),
    );
    mockUseLlmBudget.mockReturnValue({
      data: {
        utilization: 70,
        monthlyCapUsd: 50,
        totalCostUsd: 35,
        frozen: false,
      },
      isLoading: false,
      refresh: vi.fn(),
    });

    render(<LlmBudgetBar />);
    const bar = screen.getByTestId('llm-budget-bar');
    expect(bar).toBeInTheDocument();
    expect(bar).toHaveTextContent('Budget mensile usato 70% — $35.00/$50.00');
  });

  it('shows loading skeleton when data is loading', () => {
    mockUseAuthStore.mockImplementation((selector: any) =>
      selector({ user: { role: 'ADMIN' } }),
    );
    mockUseLlmBudget.mockReturnValue({
      data: undefined,
      isLoading: true,
      refresh: vi.fn(),
    });

    render(<LlmBudgetBar />);
    expect(screen.getByTestId('llm-budget-bar-loading')).toBeInTheDocument();
    expect(screen.queryByTestId('llm-budget-bar')).not.toBeInTheDocument();
  });

  it('renders red color when utilization >= 100%', () => {
    mockUseAuthStore.mockImplementation((selector: any) =>
      selector({ user: { role: 'ADMIN' } }),
    );
    mockUseLlmBudget.mockReturnValue({
      data: {
        utilization: 105,
        monthlyCapUsd: 50,
        totalCostUsd: 52.5,
        frozen: false,
      },
      isLoading: false,
      refresh: vi.fn(),
    });

    render(<LlmBudgetBar />);
    const bar = screen.getByTestId('llm-budget-bar');
    expect(bar).toHaveTextContent('Budget mensile usato 105%');
    const text = bar.querySelector('span');
    expect(text?.className).toContain('text-red-700');
  });

  it('renders amber color when utilization >= 80% and < 100%', () => {
    mockUseAuthStore.mockImplementation((selector: any) =>
      selector({ user: { role: 'ADMIN' } }),
    );
    mockUseLlmBudget.mockReturnValue({
      data: {
        utilization: 85,
        monthlyCapUsd: 50,
        totalCostUsd: 42.5,
        frozen: false,
      },
      isLoading: false,
      refresh: vi.fn(),
    });

    render(<LlmBudgetBar />);
    const bar = screen.getByTestId('llm-budget-bar');
    const text = bar.querySelector('span');
    expect(text?.className).toContain('text-amber-700');
  });

  it('renders nothing for null user', () => {
    mockUseAuthStore.mockImplementation((selector: any) =>
      selector({ user: null }),
    );
    mockUseLlmBudget.mockReturnValue({
      data: undefined,
      isLoading: false,
      refresh: vi.fn(),
    });

    const { container } = render(<LlmBudgetBar />);
    expect(container).toBeEmptyDOMElement();
  });
});
