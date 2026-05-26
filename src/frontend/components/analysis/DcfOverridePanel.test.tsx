import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DcfOverridePanel } from './DcfOverridePanel';
import * as dcfApi from '@/lib/api/dcf-overrides';
import { useAuthStore } from '@/lib/stores/useAuthStore';
vi.mock('@/lib/api/dcf-overrides', () => ({
  getDcfOverride: vi.fn(),
  upsertDcfOverride: vi.fn(),
  deleteDcfOverride: vi.fn(),
  parseDcfFeasibilityProblem: vi.fn(),
}));

describe('DcfOverridePanel', () => {
  const onRefresh = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({ accessToken: 'token', user: null });
    vi.mocked(dcfApi.getDcfOverride).mockResolvedValue(null);
  });

  it('renders DEFAULT_POLICY badge', async () => {
    render(
      <DcfOverridePanel
        ticker="AAPL"
        dcfMethodSource="DEFAULT_POLICY"
        onAnalysisRefresh={onRefresh}
      />,
    );
    await waitFor(() => {
      expect(screen.getByTestId('dcf-method-source-badge')).toHaveTextContent(
        'Default policy',
      );
    });
  });

  it('renders USER_OVERRIDE badge', async () => {
    render(
      <DcfOverridePanel
        ticker="AAPL"
        dcfMethodSource="USER_OVERRIDE"
        onAnalysisRefresh={onRefresh}
      />,
    );
    expect(screen.getByTestId('dcf-method-source-badge')).toHaveTextContent(
      'Tuo override',
    );
  });

  it('pre-populates form when GET returns override', async () => {
    vi.mocked(dcfApi.getDcfOverride).mockResolvedValue({
      ticker: 'AAPL',
      forcedMethod: 'FCF_FALLBACK',
      createdAt: '2026-05-22T10:00:00Z',
    });

    render(
      <DcfOverridePanel
        ticker="AAPL"
        dcfMethodSource="USER_OVERRIDE"
        onAnalysisRefresh={onRefresh}
      />,
    );

    await waitFor(() => {
      expect(screen.getByTestId('dcf-override-method-select')).toHaveValue(
        'FCF_FALLBACK',
      );
    });
  });

  it('shows inline error on 422 from POST', async () => {
    const user = userEvent.setup();
    vi.mocked(dcfApi.upsertDcfOverride).mockRejectedValue(new Error('422'));
    vi.mocked(dcfApi.parseDcfFeasibilityProblem).mockReturnValue({
      detail: 'Greenwald requires ≥ 5 years',
      availableYears: 2,
      requiredYears: 5,
    });

    render(
      <DcfOverridePanel
        ticker="AAPL"
        dcfMethodSource="DEFAULT_POLICY"
        onAnalysisRefresh={onRefresh}
      />,
    );

    await waitFor(() => {
      expect(screen.getByTestId('dcf-override-method-select')).toBeInTheDocument();
    });
    await user.click(screen.getByTestId('dcf-override-apply'));

    await waitFor(() => {
      expect(screen.getByTestId('dcf-override-inline-error')).toHaveTextContent(
        /Greenwald requires/,
      );
      expect(screen.getByTestId('dcf-override-inline-error')).toHaveTextContent(
        /2\/5 anni/,
      );
    });
  });

  it('is hidden when user is not authenticated', () => {
    useAuthStore.setState({ accessToken: null, user: null });
    const { container } = render(
      <DcfOverridePanel
        ticker="AAPL"
        dcfMethodSource="DEFAULT_POLICY"
        onAnalysisRefresh={onRefresh}
      />,
    );
    expect(container).toBeEmptyDOMElement();
  });
});
