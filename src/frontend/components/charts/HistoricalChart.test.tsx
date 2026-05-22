import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { HistoricalChart } from './HistoricalChart';
import type { HistoricalSeriesPoint } from '@/lib/api/historical';

/**
 * Test HistoricalChart — TSK-024 DoD (US-015).
 *
 * Strategia mock Recharts:
 *  - Recharts si appoggia a `ResponsiveContainer` che usa
 *    `ResizeObserver` + dimensioni computed del DOM padre; in jsdom le
 *    dimensioni risultano 0×0 e Recharts NON renderizza alcun <svg>
 *    (silenziosamente). Stesso problema noto agli Ag-Grid CSS-driven
 *    layout di TSK-007.
 *  - Adottiamo la stessa filosofia: MOCK PARZIALE del modulo `recharts`,
 *    sostituendo `ResponsiveContainer`/`LineChart`/`Line`/etc. con
 *    componenti leggeri che espongono SOLO il contratto props utile al
 *    test (dataKey, name, connectNulls, data). Verifichiamo che il
 *    componente SOTTO TEST passi i props corretti a Recharts (props in →
 *    contratto out), lasciando il rendering reale Recharts alla suite
 *    Playwright E2E (smoke visivo end-to-end, fuori scope unit).
 *  - `Tooltip` content={<CustomTooltip />} viene esposto come prop
 *    "content"; il mock lo renderizza forzando active=true + payload
 *    sintetico così possiamo validare il rendering del tooltip custom
 *    (formattazione "n/d" su isMissing).
 */

interface LineMockProps {
  readonly dataKey: 'revenue' | 'netIncome';
  readonly name?: string;
  readonly stroke?: string;
  readonly connectNulls?: boolean;
}

interface TooltipMockProps {
  readonly content?: React.ReactElement;
}

interface LineChartMockProps {
  readonly data: ReadonlyArray<HistoricalSeriesPoint>;
  readonly children?: React.ReactNode;
}

vi.mock('recharts', () => {
  const Passthrough = (props: { children?: React.ReactNode }): React.ReactElement => (
    <div>{props.children}</div>
  );
  return {
    ResponsiveContainer: ({ children }: { children?: React.ReactNode }): React.ReactElement => (
      <div data-testid="rc-responsive-container">{children}</div>
    ),
    LineChart: (props: LineChartMockProps): React.ReactElement => (
      <div data-testid="rc-line-chart" data-points-count={String(props.data.length)}>
        <pre data-testid="rc-line-chart-data">{JSON.stringify(props.data)}</pre>
        {props.children}
      </div>
    ),
    Line: (props: LineMockProps): React.ReactElement => (
      <div
        data-testid={`rc-line-${props.dataKey}`}
        data-name={props.name ?? ''}
        data-stroke={props.stroke ?? ''}
        data-connect-nulls={String(props.connectNulls ?? true)}
      />
    ),
    XAxis: (props: { dataKey?: string }): React.ReactElement => (
      <div data-testid="rc-xaxis" data-data-key={props.dataKey ?? ''} />
    ),
    YAxis: (): React.ReactElement => <div data-testid="rc-yaxis" />,
    CartesianGrid: (): React.ReactElement => <div data-testid="rc-grid" />,
    Legend: (): React.ReactElement => (
      <div data-testid="rc-legend">
        <span>Ricavi</span>
        <span>Utile Netto</span>
      </div>
    ),
    Tooltip: (props: TooltipMockProps): React.ReactElement => {
      // Renderizza il content custom forzando active=true + payload sintetico
      // così possiamo validare la formattazione del tooltip.
      const ContentCmp = props.content as React.ReactElement | undefined;
      if (ContentCmp === undefined) {
        return <div data-testid="rc-tooltip" />;
      }
      const ContentType = ContentCmp.type as React.ComponentType<Record<string, unknown>>;
      return (
        <div data-testid="rc-tooltip">
          <ContentType
            active={true}
            label={2024}
            payload={[
              {
                dataKey: 'revenue',
                name: 'Ricavi',
                value: null,
                color: '#2563eb',
                payload: {
                  fiscalYear: 2024,
                  revenue: null,
                  netIncome: null,
                  isMissing: true,
                },
              },
              {
                dataKey: 'netIncome',
                name: 'Utile Netto',
                value: null,
                color: '#16a34a',
                payload: {
                  fiscalYear: 2024,
                  revenue: null,
                  netIncome: null,
                  isMissing: true,
                },
              },
            ]}
          />
        </div>
      );
    },
    // Catch-all per eventuali altri import (Bar, Area, etc.) non usati.
    Bar: Passthrough,
    ComposedChart: Passthrough,
  };
});

function makeCompletePoints(): ReadonlyArray<HistoricalSeriesPoint> {
  const startYear = 2015;
  const points: HistoricalSeriesPoint[] = [];
  for (let i = 0; i < 10; i++) {
    points.push({
      fiscalYear: startYear + i,
      revenue: 100_000_000_000 + i * 5_000_000_000,
      netIncome: 20_000_000_000 + i * 1_000_000_000,
      isMissing: false,
    });
  }
  return points;
}

describe('HistoricalChart', () => {
  it('Test 1 — rende 10 punti completi con 2 serie (revenue + netIncome), connectNulls=false', () => {
    const points = makeCompletePoints();
    render(<HistoricalChart points={points} />);

    // Container + chart presenti
    expect(screen.getByTestId('historical-chart')).toBeInTheDocument();
    expect(screen.getByTestId('rc-responsive-container')).toBeInTheDocument();
    expect(screen.getByTestId('rc-line-chart')).toHaveAttribute(
      'data-points-count',
      '10',
    );

    // 2 serie con dataKey distinti
    const revenueLine = screen.getByTestId('rc-line-revenue');
    const netIncomeLine = screen.getByTestId('rc-line-netIncome');
    expect(revenueLine).toBeInTheDocument();
    expect(netIncomeLine).toBeInTheDocument();

    // Nomi serie umani (verifica legenda + contratto)
    expect(revenueLine).toHaveAttribute('data-name', 'Ricavi');
    expect(netIncomeLine).toHaveAttribute('data-name', 'Utile Netto');

    // Colori distinti tra le due serie
    const revenueStroke = revenueLine.getAttribute('data-stroke');
    const netIncomeStroke = netIncomeLine.getAttribute('data-stroke');
    expect(revenueStroke).toBeTruthy();
    expect(netIncomeStroke).toBeTruthy();
    expect(revenueStroke).not.toBe(netIncomeStroke);

    // connectNulls=false esplicito su entrambe → no interpolazione sui gap
    expect(revenueLine).toHaveAttribute('data-connect-nulls', 'false');
    expect(netIncomeLine).toHaveAttribute('data-connect-nulls', 'false');

    // Legenda render i due nomi
    const legend = screen.getByTestId('rc-legend');
    expect(legend).toHaveTextContent('Ricavi');
    expect(legend).toHaveTextContent('Utile Netto');

    // Asse X usa fiscalYear
    expect(screen.getByTestId('rc-xaxis')).toHaveAttribute(
      'data-data-key',
      'fiscalYear',
    );
  });

  it('Test 2 — punto isMissing=true preserva null (no interpolazione a 0)', () => {
    const points: ReadonlyArray<HistoricalSeriesPoint> = [
      { fiscalYear: 2021, revenue: 100_000_000_000, netIncome: 20_000_000_000, isMissing: false },
      { fiscalYear: 2022, revenue: null, netIncome: null, isMissing: true },
      { fiscalYear: 2023, revenue: 120_000_000_000, netIncome: 25_000_000_000, isMissing: false },
    ];
    render(<HistoricalChart points={points} />);

    // Verifica che i dati passati a LineChart contengano null e non 0
    const dataJson = screen.getByTestId('rc-line-chart-data').textContent ?? '';
    const parsed = JSON.parse(dataJson) as HistoricalSeriesPoint[];
    expect(parsed).toHaveLength(3);

    const missingPoint = parsed[1];
    expect(missingPoint).toBeDefined();
    expect(missingPoint?.fiscalYear).toBe(2022);
    expect(missingPoint?.revenue).toBeNull();
    expect(missingPoint?.netIncome).toBeNull();
    expect(missingPoint?.isMissing).toBe(true);

    // connectNulls=false: il "no data" deve generare un gap, non interpolazione.
    // Verifica il contratto Recharts (props in → Line.connectNulls=false out).
    expect(screen.getByTestId('rc-line-revenue')).toHaveAttribute(
      'data-connect-nulls',
      'false',
    );
    expect(screen.getByTestId('rc-line-netIncome')).toHaveAttribute(
      'data-connect-nulls',
      'false',
    );

    // Tooltip su un punto isMissing → formattazione "n/d"
    // (il mock Tooltip costruisce payload sintetico isMissing:true)
    const tooltipRevenue = screen.getByTestId('hc-tooltip-revenue');
    const tooltipNetIncome = screen.getByTestId('hc-tooltip-netIncome');
    expect(tooltipRevenue).toHaveTextContent('n/d');
    expect(tooltipNetIncome).toHaveTextContent('n/d');
  });

  it('Test 3 — lista vuota → empty state + nessun chart', () => {
    render(<HistoricalChart points={[]} />);

    const empty = screen.getByTestId('historical-chart-empty');
    expect(empty).toBeInTheDocument();
    expect(empty).toHaveTextContent(/nessun dato storico disponibile/i);
    expect(empty).toHaveAttribute('role', 'status');

    expect(screen.queryByTestId('rc-line-chart')).not.toBeInTheDocument();
    expect(screen.queryByTestId('rc-responsive-container')).not.toBeInTheDocument();
  });

  it('Test 3b — lista vuota rispetta emptyMessage prop override', () => {
    render(
      <HistoricalChart points={[]} emptyMessage="Storico non disponibile per AAPL" />,
    );
    expect(
      screen.getByText(/storico non disponibile per aapl/i),
    ).toBeInTheDocument();
  });

  it('Test 4 — loading=true mostra skeleton (no chart, no empty)', () => {
    render(<HistoricalChart points={[]} loading />);

    const skeleton = screen.getByTestId('historical-chart-loading');
    expect(skeleton).toBeInTheDocument();
    expect(skeleton).toHaveAttribute('aria-busy', 'true');
    expect(skeleton).toHaveAttribute('role', 'status');

    expect(screen.queryByTestId('rc-line-chart')).not.toBeInTheDocument();
    expect(screen.queryByTestId('historical-chart-empty')).not.toBeInTheDocument();
  });

  it('Test 5 — dataSnapshotAt valorizzato → footer "Dati aggiornati al ..." renderizzato', () => {
    render(
      <HistoricalChart
        points={makeCompletePoints()}
        dataSnapshotAt="2026-05-22T10:00:00Z"
      />,
    );
    const footer = screen.getByTestId('historical-chart-snapshot');
    expect(footer).toBeInTheDocument();
    expect(footer.textContent).toMatch(/dati aggiornati al/i);
  });
});
