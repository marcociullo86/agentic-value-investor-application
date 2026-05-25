import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { DeepVerdictBadge } from './DeepVerdictBadge';
import type { DeepAnalysisResponse } from '@/lib/api/deep-analysis';

vi.mock('@/lib/stores/useAuthStore', () => ({
  useAuthStore: vi.fn((selector: (s: any) => any) =>
    selector({ user: { role: 'USER' } }),
  ),
}));

vi.mock('@/lib/hooks/useLlmBudget', () => ({
  useLlmBudget: vi.fn(() => ({
    data: undefined,
    isLoading: false,
    refresh: vi.fn(),
  })),
}));

function makeData(
  overrides: Partial<DeepAnalysisResponse> = {},
): DeepAnalysisResponse {
  return {
    ticker: 'AAPL',
    generatedAt: '2026-05-25T10:00:00Z',
    roe: {
      fiveYearAvg: 0.35,
      tenYearAvg: 0.32,
      fiveYearDataPoints: 5,
      tenYearDataPoints: 10,
    },
    priceAction: {
      priceNow: 180,
      max52w: 200,
      min52w: 130,
      drawdownPct: -10,
      trend3mPct: 5,
      ma50: 175,
      ma200: 165,
      panicDiscount: false,
      deteriorationWarning: false,
      seriesDays: 252,
    },
    ruleEngineResults: [],
    verdict: {
      verdettoClasse: 'WATCHLIST',
      positionSizePct: 3,
      partialBasis: true,
      motivazioneAggregata: 'Test motivazione',
      ruleCountGreen: 5,
      ruleCountYellow: 3,
      ruleCountRed: 2,
      livelloRischio: 'RISCHIO_MODERATO',
      newsSentimentDominante: 'NEUTRAL',
    },
    positionSize: null,
    filingsUsed: [],
    mungerReport: null,
    newsSentiment: null,
    llmStatus: 'NOT_INVOKED',
    llmCalls: 0,
    totalDurationMs: 1200,
    llmCostEstimateUsd: 0.49,
    ...overrides,
  };
}

describe('DeepVerdictBadge (TSK-157)', () => {
  const onInvokeLlm = vi.fn().mockResolvedValue(undefined);

  beforeEach(() => {
    onInvokeLlm.mockClear();
  });

  it('shows cost estimate on the invoke button label', () => {
    render(
      <DeepVerdictBadge
        data={makeData({ llmCostEstimateUsd: 0.49 })}
        isValidating={false}
        isFrozenByAdmin={false}
        onInvokeLlm={onInvokeLlm}
      />,
    );
    const button = screen.getByTestId('invoke-llm-button');
    expect(button).toHaveTextContent('Avvia analisi LLM ≈ $0.49');
  });

  it('shows cost estimate with null → no cost in label', () => {
    render(
      <DeepVerdictBadge
        data={makeData({ llmCostEstimateUsd: null })}
        isValidating={false}
        isFrozenByAdmin={false}
        onInvokeLlm={onInvokeLlm}
      />,
    );
    const button = screen.getByTestId('invoke-llm-button');
    expect(button).toHaveTextContent('Avvia analisi LLM');
    expect(button.textContent).not.toContain('$');
  });

  it('shows "Mostra analisi precedente" on CACHE_HIT', () => {
    render(
      <DeepVerdictBadge
        data={makeData({ llmStatus: 'CACHE_HIT' })}
        isValidating={false}
        isFrozenByAdmin={false}
        onInvokeLlm={onInvokeLlm}
      />,
    );
    const button = screen.getByTestId('invoke-llm-button');
    expect(button).toHaveTextContent('Mostra analisi precedente');
  });

  it('disables button with frozen message on 503 LLM_FROZEN_BY_ADMIN', () => {
    render(
      <DeepVerdictBadge
        data={makeData({ llmStatus: 'NOT_INVOKED' })}
        isValidating={false}
        isFrozenByAdmin={true}
        onInvokeLlm={onInvokeLlm}
      />,
    );
    const button = screen.getByTestId('invoke-llm-button');
    expect(button).toBeDisabled();
    expect(button).toHaveTextContent(
      "Analisi LLM temporaneamente disabilitata dall'admin",
    );
  });

  it('does not render budget bar for USER role', () => {
    render(
      <DeepVerdictBadge
        data={makeData()}
        isValidating={false}
        isFrozenByAdmin={false}
        onInvokeLlm={onInvokeLlm}
      />,
    );
    expect(screen.queryByTestId('llm-budget-bar')).not.toBeInTheDocument();
  });
});
