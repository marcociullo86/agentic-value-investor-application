import { describe, it, expect } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MungerReportCollapsible } from './MungerReportCollapsible';
import type { MungerReportBlock } from '@/lib/api/deep-analysis';

function makeReport(
  overrides: Partial<MungerReportBlock> = {},
): MungerReportBlock {
  return {
    livelloRischio: 'RISCHIO_MODERATO',
    sintesi: 'Sintesi Munger di prova sul perché del rischio.',
    rischiPrincipali: [{ testo: 'Rischio uno', chunkIndex: 1 }],
    puntiDiForza: [{ testo: 'Forza uno', chunkIndex: 2 }],
    segnaliRecenti10Q: [],
    filingComboHash: 'h',
    llmCallsCount: 11,
    ...overrides,
  };
}

describe('MungerReportCollapsible (US-090)', () => {
  it('shows the sintesi paragraph when expanded', () => {
    render(<MungerReportCollapsible report={makeReport()} />);
    fireEvent.click(screen.getByTestId('munger-toggle-button'));
    expect(screen.getByTestId('munger-synthesis')).toHaveTextContent(
      'Sintesi Munger di prova',
    );
  });

  it('omits the sintesi paragraph when sintesi is null', () => {
    render(<MungerReportCollapsible report={makeReport({ sintesi: null })} />);
    fireEvent.click(screen.getByTestId('munger-toggle-button'));
    expect(screen.queryByTestId('munger-synthesis')).toBeNull();
  });

  it('renders empty state when report is null', () => {
    render(<MungerReportCollapsible report={null} />);
    expect(screen.queryByTestId('munger-toggle-button')).toBeNull();
  });
});
