import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ResultsList, type ResultsListItem } from './ResultsList';

/**
 * Test ResultsList (TSK-007 DoD).
 *
 * Strategia mock Ag-Grid:
 *  - Ag-Grid usa un DOM virtuale interno complesso (cell renderers,
 *    row recycling, intersection observer per virtualizzazione) che in
 *    jsdom risulta inaffidabile e lento. Più importante: l'import dei
 *    CSS in `ResultsList.tsx` richiede `vitest.config.ts` con `css: true`
 *    (già impostato in TSK-030).
 *  - Mock parziale di `ag-grid-react`: sostituiamo `AgGridReact` con un
 *    componente leggero che renderizza `rowData` come righe semantiche
 *    e propaga `onRowClicked` ricostruendo il payload `{ data: row }`
 *    atteso dal componente reale. Questo testa il contratto del
 *    componente (props in → callback out) senza dipendere
 *    dall'implementazione interna del data grid.
 *  - Mock dei CSS: vitest+vite gestisce nativamente import CSS quando
 *    `test.css: true` è abilitato, quindi non serve mockare i due
 *    `ag-grid-community/styles/*.css`.
 *
 * Il rendering reale Ag-Grid è coperto in Playwright E2E (smoke test
 * visivo end-to-end, fuori scope unit Vitest).
 */

const pushMock = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: pushMock }),
}));

interface MockAgGridProps {
  readonly rowData: ReadonlyArray<ResultsListItem>;
  readonly onRowClicked?: (event: { data: ResultsListItem }) => void;
}

vi.mock('ag-grid-react', () => ({
  AgGridReact: (props: MockAgGridProps): React.ReactElement => (
    <div data-testid="ag-grid-mock" role="grid">
      {props.rowData.map((row) => (
        <div
          key={row.ticker}
          role="row"
          data-testid={`row-${row.ticker}`}
          onClick={() => props.onRowClicked?.({ data: row })}
        >
          <span data-testid={`cell-ticker-${row.ticker}`}>{row.ticker}</span>
          <span data-testid={`cell-name-${row.ticker}`}>{row.companyName}</span>
          <span data-testid={`cell-sector-${row.ticker}`}>
            {row.sector ?? '—'}
          </span>
          <span data-testid={`cell-marketcap-${row.ticker}`}>
            {row.marketCapUsd ?? ''}
          </span>
        </div>
      ))}
    </div>
  ),
}));

const SAMPLE_ITEMS: ReadonlyArray<ResultsListItem> = [
  {
    ticker: 'AAPL',
    companyName: 'Apple Inc.',
    sector: 'Information Technology',
    marketCapUsd: 3_000_000_000_000,
  },
  {
    ticker: 'MSFT',
    companyName: 'Microsoft Corp.',
    sector: 'Information Technology',
    marketCapUsd: 2_800_000_000_000,
  },
  {
    ticker: 'KO',
    companyName: 'The Coca-Cola Company',
    sector: 'Consumer Staples',
    marketCapUsd: 270_000_000_000,
  },
];

describe('ResultsList', () => {
  beforeEach(() => {
    pushMock.mockReset();
  });

  it('renderizza i 3 item con ticker, nome, settore, marketCap', () => {
    render(<ResultsList items={SAMPLE_ITEMS} />);

    // Grid mock presente (non empty state)
    expect(screen.getByTestId('ag-grid-mock')).toBeInTheDocument();
    expect(screen.queryByTestId('results-list-empty')).not.toBeInTheDocument();

    // Per ogni ticker verifico la presenza dei 4 campi
    for (const item of SAMPLE_ITEMS) {
      expect(screen.getByTestId(`row-${item.ticker}`)).toBeInTheDocument();
      expect(screen.getByTestId(`cell-ticker-${item.ticker}`)).toHaveTextContent(
        item.ticker,
      );
      expect(screen.getByTestId(`cell-name-${item.ticker}`)).toHaveTextContent(
        item.companyName,
      );
      expect(
        screen.getByTestId(`cell-sector-${item.ticker}`),
      ).toHaveTextContent(item.sector ?? '');
      expect(
        screen.getByTestId(`cell-marketcap-${item.ticker}`),
      ).toHaveTextContent(String(item.marketCapUsd));
    }
  });

  it('su lista vuota mostra messaggio dedicato (no grid)', () => {
    render(<ResultsList items={[]} />);

    const empty = screen.getByTestId('results-list-empty');
    expect(empty).toBeInTheDocument();
    expect(empty).toHaveTextContent(/nessun risultato disponibile/i);
    expect(empty).toHaveAttribute('role', 'status');
    // Grid NON renderizzata
    expect(screen.queryByTestId('ag-grid-mock')).not.toBeInTheDocument();
  });

  it('su lista vuota rispetta emptyMessage prop override', () => {
    render(
      <ResultsList items={[]} emptyMessage="Nessun titolo soddisfa i criteri" />,
    );
    expect(
      screen.getByText(/nessun titolo soddisfa i criteri/i),
    ).toBeInTheDocument();
  });

  it('su loading=true mostra skeleton (no grid, no empty)', () => {
    render(<ResultsList items={[]} loading />);

    expect(screen.getByTestId('results-list-loading')).toBeInTheDocument();
    expect(screen.queryByTestId('ag-grid-mock')).not.toBeInTheDocument();
    expect(screen.queryByTestId('results-list-empty')).not.toBeInTheDocument();
  });

  it('click su riga → router.push("/analysis/AAPL")', async () => {
    const user = userEvent.setup();
    render(<ResultsList items={SAMPLE_ITEMS} />);

    await user.click(screen.getByTestId('row-AAPL'));

    expect(pushMock).toHaveBeenCalledTimes(1);
    expect(pushMock).toHaveBeenCalledWith('/analysis?ticker=AAPL');
  });

  it('click su riga con ticker contenente caratteri speciali → encodeURIComponent', async () => {
    const user = userEvent.setup();
    render(
      <ResultsList
        items={[
          {
            ticker: 'BRK.B',
            companyName: 'Berkshire Hathaway Inc.',
            sector: 'Financials',
            marketCapUsd: 900_000_000_000,
          },
        ]}
      />,
    );

    await user.click(screen.getByTestId('row-BRK.B'));

    expect(pushMock).toHaveBeenCalledWith('/analysis?ticker=BRK.B');
  });
});
