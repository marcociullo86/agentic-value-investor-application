'use client';

import { useMemo, useState } from 'react';
import {
  CartesianGrid,
  ComposedChart,
  Legend,
  Line,
  ResponsiveContainer,
  Scatter,
  Tooltip,
  XAxis,
  YAxis,
  type TooltipProps,
} from 'recharts';
import { cn } from '@/lib/utils/cn';
import { formatCurrency } from '@/lib/utils/formatters';
import type {
  BacktestExitReason,
  BacktestStrategy,
  BacktestTrade,
  BacktestWindow,
} from '@/lib/api/backtest';

/**
 * BacktestTradesChart — TSK-351 (US-106, EP-024 Fase 3).
 *
 * Timeline marker entry/exit dei trade sul price chart Recharts:
 *
 *  - Asse X: data (calendar days dal `window.fromDate` al `window.toDate`).
 *  - Asse Y: prezzo USD.
 *  - Linea prezzo: SCALETTA di entry/exit (vedi nota tecnica sotto).
 *  - Marker entry: cerchio neutro.
 *  - Marker exit: forma colorata per `exitReason` (VI_TARGET verde / STOP_HIT
 *    rosso / HORIZON grigio neutro) — AC US-106.
 *  - Default strategy: `EP024_ENTER_NOW`. Toggle per mostrare anche `VI_ONLY`
 *    (US-106 §"Timeline marker").
 *
 * NOTA TECNICA — assenza serie EOD daily nel payload `BacktestResponse`:
 *   Il payload BE non include la serie di prezzi OHLC della finestra di
 *   lookback (sarebbe un payload pesante e ridondante: il backtest opera in
 *   % di rendimento). Per la timeline costruiamo dunque una "spezzata" che
 *   connette i punti `entryPrice` e `exitPrice` di ogni trade: NON è la serie
 *   reale del mercato, ma è il prezzo realmente *operato* dalla strategia.
 *   Mostriamo questa scelta esplicitamente nel titolo della section e nella
 *   tabella dati alternativa accessibile — niente "grafico parziale
 *   fuorviante" (US-106 §AC).
 *
 *   Quando il BE esporrà una serie EOD sul payload backtest (o riusando il
 *   GAP fe-ta-daily-eod-endpoint già aperto per TSK-334), il componente
 *   accetterà la serie come prop `dailySeries` opzionale (drop-in).
 *
 * Accessibility (WCAG 2.2 AA — EP-016, AC US-106):
 *  - `role="img"` sul chart container con `aria-labelledby`.
 *  - Tabella dati alternativa accessibile (`<details>` con `<table>`):
 *    ogni trade è una riga (entryDate, entryPrice, exitDate, exitPrice,
 *    exitReason, returnPct, holdingDays).
 *  - Color contrast verificato (verde-600/rosso-600/slate-500).
 *  - Marker hanno tooltip Recharts attivabile via tastiera (focus su chart).
 *  - Toggle "Mostra anche VI_ONLY" è un `<input type="checkbox">` con label.
 */

const COLOR_PRICE_EP024 = '#0f172a'; // slate-900
const COLOR_PRICE_VI_ONLY = '#7c3aed'; // violet-600

// Marker per causale di uscita (US-106 §AC).
const COLOR_EXIT_VI_TARGET = '#16a34a'; // green-600
const COLOR_EXIT_STOP_HIT = '#dc2626'; // red-600
const COLOR_EXIT_HORIZON = '#64748b'; // slate-500 (neutro)
const COLOR_ENTRY = '#2563eb'; // blue-600 (neutro entry)

const EXIT_COLORS: Readonly<Record<BacktestExitReason, string>> = {
  VI_TARGET: COLOR_EXIT_VI_TARGET,
  STOP_HIT: COLOR_EXIT_STOP_HIT,
  HORIZON: COLOR_EXIT_HORIZON,
};

const EXIT_LABELS: Readonly<Record<BacktestExitReason, string>> = {
  VI_TARGET: 'Target VI raggiunto',
  STOP_HIT: 'Stop hit',
  HORIZON: 'Orizzonte raggiunto',
};

const STRATEGY_LABELS: Readonly<Record<BacktestStrategy, string>> = {
  EP024_ENTER_NOW: 'EP-024 (timing)',
  VI_ONLY: 'Solo sconto (VI)',
  BUY_AND_HOLD: 'Buy & hold',
};

/** Punto della "spezzata" prezzo per una strategia (alternato entry / exit). */
interface PriceLinePoint {
  readonly ts: number;
  readonly dateLabel: string;
  readonly ep024Price?: number;
  readonly viOnlyPrice?: number;
}

/** Scatter point (entry o exit marker). */
interface MarkerPoint {
  readonly ts: number;
  readonly dateLabel: string;
  readonly price: number;
  readonly kind: 'entry' | 'exit';
  readonly strategy: BacktestStrategy;
  readonly exitReason: BacktestExitReason | null;
  readonly returnPct: number | null;
  readonly holdingDays: number | null;
}

function toTs(isoDate: string): number {
  const t = Date.parse(isoDate);
  return Number.isFinite(t) ? t : 0;
}

function toDateLabel(isoDate: string): string {
  // YYYY-MM-DD → DD/MM/YYYY (it-IT)
  const parts = isoDate.split('-');
  if (parts.length !== 3) return isoDate;
  return `${parts[2]}/${parts[1]}/${parts[0]}`;
}

function buildSeries(
  trades: ReadonlyArray<BacktestTrade>,
  strategy: BacktestStrategy,
): {
  readonly priceLine: ReadonlyArray<PriceLinePoint>;
  readonly entries: ReadonlyArray<MarkerPoint>;
  readonly exits: ReadonlyArray<MarkerPoint>;
} {
  const filtered = trades
    .filter((t) => t.strategy === strategy)
    .slice()
    .sort((a, b) => toTs(a.entryDate) - toTs(b.entryDate));

  const priceLine: Array<PriceLinePoint> = [];
  const entries: Array<MarkerPoint> = [];
  const exits: Array<MarkerPoint> = [];

  for (const trade of filtered) {
    const entryTs = toTs(trade.entryDate);
    const exitTs = toTs(trade.exitDate);
    const entryLabel = toDateLabel(trade.entryDate);
    const exitLabel = toDateLabel(trade.exitDate);

    const entryPoint: PriceLinePoint =
      strategy === 'EP024_ENTER_NOW'
        ? {
            ts: entryTs,
            dateLabel: entryLabel,
            ep024Price: trade.entryPrice,
          }
        : {
            ts: entryTs,
            dateLabel: entryLabel,
            viOnlyPrice: trade.entryPrice,
          };
    const exitPoint: PriceLinePoint =
      strategy === 'EP024_ENTER_NOW'
        ? {
            ts: exitTs,
            dateLabel: exitLabel,
            ep024Price: trade.exitPrice,
          }
        : {
            ts: exitTs,
            dateLabel: exitLabel,
            viOnlyPrice: trade.exitPrice,
          };
    priceLine.push(entryPoint, exitPoint);

    entries.push({
      ts: entryTs,
      dateLabel: entryLabel,
      price: trade.entryPrice,
      kind: 'entry',
      strategy,
      exitReason: null,
      returnPct: null,
      holdingDays: null,
    });
    exits.push({
      ts: exitTs,
      dateLabel: exitLabel,
      price: trade.exitPrice,
      kind: 'exit',
      strategy,
      exitReason: trade.exitReason,
      returnPct: trade.returnPct,
      holdingDays: trade.holdingDays,
    });
  }

  return { priceLine, entries, exits };
}

function mergePriceLines(
  a: ReadonlyArray<PriceLinePoint>,
  b: ReadonlyArray<PriceLinePoint>,
): ReadonlyArray<PriceLinePoint> {
  const all = [...a, ...b];
  // sort by ts
  all.sort((p, q) => p.ts - q.ts);
  return all;
}

function CustomTooltip(
  props: TooltipProps<number, string>,
): React.ReactElement | null {
  const { active, payload } = props;
  if (active !== true || payload === undefined || payload.length === 0) {
    return null;
  }
  // Scatter point payload: payload[i].payload is a MarkerPoint with kind/exitReason.
  const items = payload
    .map((entry) => {
      const data = entry.payload as Partial<MarkerPoint>;
      return data;
    })
    .filter(
      (d): d is MarkerPoint =>
        d.dateLabel !== undefined && d.price !== undefined,
    );
  if (items.length === 0) return null;
  return (
    <div
      data-testid="backtest-chart-tooltip"
      role="tooltip"
      className="rounded border border-outline-variant bg-surface p-2 text-xs shadow"
    >
      {items.map((item, idx) => (
        <div key={`${item.ts}-${idx}`} className="flex flex-col gap-0.5">
          <div className="font-semibold text-on-surface">{item.dateLabel}</div>
          <div className="text-on-surface">
            {item.kind === 'entry' ? 'Entry' : 'Exit'} ·{' '}
            {STRATEGY_LABELS[item.strategy]}
          </div>
          <div className="tabular-nums text-on-surface">
            Prezzo: {formatCurrency(item.price, 'USD')}
          </div>
          {item.kind === 'exit' && item.exitReason !== null ? (
            <div
              className="font-medium"
              style={{ color: EXIT_COLORS[item.exitReason] }}
            >
              {EXIT_LABELS[item.exitReason]}
            </div>
          ) : null}
          {item.returnPct !== null ? (
            <div className="tabular-nums text-on-surface">
              Rendimento: {item.returnPct >= 0 ? '+' : ''}
              {item.returnPct.toFixed(2)}% · {item.holdingDays}g
            </div>
          ) : null}
        </div>
      ))}
    </div>
  );
}

export interface BacktestTradesChartProps {
  readonly ticker: string;
  readonly trades: ReadonlyArray<BacktestTrade>;
  readonly window: BacktestWindow;
}

export function BacktestTradesChart(
  props: BacktestTradesChartProps,
): React.ReactElement {
  const { ticker, trades, window: bwindow } = props;
  const [showViOnly, setShowViOnly] = useState<boolean>(false);

  const ep024 = useMemo(() => buildSeries(trades, 'EP024_ENTER_NOW'), [trades]);
  const viOnly = useMemo(() => buildSeries(trades, 'VI_ONLY'), [trades]);

  const priceLineData = useMemo(
    () =>
      showViOnly
        ? mergePriceLines(ep024.priceLine, viOnly.priceLine)
        : ep024.priceLine,
    [ep024.priceLine, viOnly.priceLine, showViOnly],
  );

  const entriesEp024 = ep024.entries;
  const exitsEp024 = ep024.exits;
  const entriesVi = showViOnly ? viOnly.entries : [];
  const exitsVi = showViOnly ? viOnly.exits : [];

  // Empty case (status=OK ma zero trade EP024) gestito a monte da BacktestPanel
  // via stato `empty`; questo componente è chiamato solo con almeno 1 trade.
  const hasAnyTrade =
    ep024.entries.length > 0 || viOnly.entries.length > 0;

  if (!hasAnyTrade) {
    return (
      <section
        data-testid="backtest-chart-empty"
        className="flex flex-col gap-2 rounded-md border border-dashed border-outline-variant bg-surface-container/60 p-6 text-sm text-on-surface/70"
      >
        <h3 className="text-base font-semibold text-on-surface">
          Timeline trade
        </h3>
        <p>
          Nessun trade nella finestra ({toDateLabel(bwindow.fromDate)} —{' '}
          {toDateLabel(bwindow.toDate)}). La timeline è omessa per evitare un
          grafico parziale fuorviante.
        </p>
      </section>
    );
  }

  return (
    <section
      data-testid="backtest-chart-section"
      className="flex flex-col gap-3"
      aria-labelledby="backtest-chart-heading"
    >
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h3
          id="backtest-chart-heading"
          className="text-base font-semibold text-on-surface"
        >
          Timeline entry/exit — {ticker}
        </h3>
        <label className="inline-flex items-center gap-2 text-sm text-on-surface">
          <input
            type="checkbox"
            data-testid="backtest-chart-toggle-vi-only"
            checked={showViOnly}
            onChange={(e) => setShowViOnly(e.target.checked)}
            className="h-4 w-4 rounded border-outline-variant text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          />
          Mostra anche &laquo;Solo sconto (VI)&raquo;
        </label>
      </div>
      <p className="text-xs text-on-surface/60">
        Spezzata che connette i punti entry → exit dei trade. NON è la serie
        OHLC di mercato (non esposta dal payload). I marker di uscita sono
        colorati per causale: verde = target VI raggiunto, rosso = stop hit,
        grigio = orizzonte raggiunto.
      </p>
      <div
        data-testid="backtest-chart"
        role="img"
        aria-labelledby="backtest-chart-heading"
        className="w-full"
      >
        <ResponsiveContainer width="100%" height={360}>
          <ComposedChart
            data={priceLineData as PriceLinePoint[]}
            margin={{ top: 16, right: 32, left: 8, bottom: 8 }}
          >
            <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
            <XAxis
              dataKey="ts"
              type="number"
              domain={[toTs(bwindow.fromDate), toTs(bwindow.toDate)]}
              tickFormatter={(v: number) => {
                if (!Number.isFinite(v)) return '';
                const d = new Date(v);
                return `${String(d.getMonth() + 1).padStart(2, '0')}/${d.getFullYear()}`;
              }}
              tick={{ fill: '#4b5563', fontSize: 11 }}
            />
            <YAxis
              tickFormatter={(v: number) =>
                Number.isFinite(v) ? `$${v.toFixed(0)}` : '—'
              }
              tick={{ fill: '#4b5563', fontSize: 11 }}
              width={64}
              domain={['auto', 'auto']}
            />
            <Tooltip content={<CustomTooltip />} />
            <Legend />
            <Line
              type="linear"
              dataKey="ep024Price"
              name={STRATEGY_LABELS.EP024_ENTER_NOW}
              stroke={COLOR_PRICE_EP024}
              strokeWidth={1.5}
              isAnimationActive={false}
              connectNulls
              dot={false}
            />
            {showViOnly ? (
              <Line
                type="linear"
                dataKey="viOnlyPrice"
                name={STRATEGY_LABELS.VI_ONLY}
                stroke={COLOR_PRICE_VI_ONLY}
                strokeWidth={1.5}
                strokeDasharray="4 2"
                isAnimationActive={false}
                connectNulls
                dot={false}
              />
            ) : null}
            <Scatter
              data={entriesEp024 as MarkerPoint[]}
              name="Entry EP-024"
              fill={COLOR_ENTRY}
              shape="circle"
              isAnimationActive={false}
            />
            {/* Un Scatter per ciascuna causale di uscita: serve per il colore
                marker (Recharts: `fill` è propriety statica dello Scatter,
                non per-point in modo nativo robusto cross-version). */}
            <Scatter
              data={
                exitsEp024.filter(
                  (e) => e.exitReason === 'VI_TARGET',
                ) as MarkerPoint[]
              }
              name="Exit · target VI (EP-024)"
              fill={COLOR_EXIT_VI_TARGET}
              shape="triangle"
              isAnimationActive={false}
            />
            <Scatter
              data={
                exitsEp024.filter(
                  (e) => e.exitReason === 'STOP_HIT',
                ) as MarkerPoint[]
              }
              name="Exit · stop hit (EP-024)"
              fill={COLOR_EXIT_STOP_HIT}
              shape="cross"
              isAnimationActive={false}
            />
            <Scatter
              data={
                exitsEp024.filter(
                  (e) => e.exitReason === 'HORIZON',
                ) as MarkerPoint[]
              }
              name="Exit · orizzonte (EP-024)"
              fill={COLOR_EXIT_HORIZON}
              shape="diamond"
              isAnimationActive={false}
            />
            {showViOnly ? (
              <>
                <Scatter
                  data={entriesVi as MarkerPoint[]}
                  name="Entry · VI puro"
                  fill={COLOR_PRICE_VI_ONLY}
                  shape="circle"
                  isAnimationActive={false}
                />
                <Scatter
                  data={
                    exitsVi.filter(
                      (e) => e.exitReason === 'VI_TARGET',
                    ) as MarkerPoint[]
                  }
                  name="Exit · target VI (VI puro)"
                  fill={COLOR_EXIT_VI_TARGET}
                  shape="triangle"
                  isAnimationActive={false}
                />
                <Scatter
                  data={
                    exitsVi.filter(
                      (e) => e.exitReason === 'STOP_HIT',
                    ) as MarkerPoint[]
                  }
                  name="Exit · stop hit (VI puro)"
                  fill={COLOR_EXIT_STOP_HIT}
                  shape="cross"
                  isAnimationActive={false}
                />
                <Scatter
                  data={
                    exitsVi.filter(
                      (e) => e.exitReason === 'HORIZON',
                    ) as MarkerPoint[]
                  }
                  name="Exit · orizzonte (VI puro)"
                  fill={COLOR_EXIT_HORIZON}
                  shape="diamond"
                  isAnimationActive={false}
                />
              </>
            ) : null}
          </ComposedChart>
        </ResponsiveContainer>
      </div>

      <BacktestTradesTable trades={trades} showViOnly={showViOnly} />
    </section>
  );
}

/**
 * Tabella dati alternativa accessibile (pattern HistoricalChart /
 * PriceChartWithOverlays). Lista verbatim dei trade renderizzati nel chart —
 * stesso filtro `showViOnly`.
 */
function BacktestTradesTable({
  trades,
  showViOnly,
}: {
  readonly trades: ReadonlyArray<BacktestTrade>;
  readonly showViOnly: boolean;
}): React.ReactElement {
  const filtered = trades.filter(
    (t) =>
      t.strategy === 'EP024_ENTER_NOW' ||
      (showViOnly && t.strategy === 'VI_ONLY'),
  );

  if (filtered.length === 0) {
    return (
      <details className="rounded border border-outline-variant bg-surface-container p-3 text-sm">
        <summary className="cursor-pointer font-medium text-on-surface focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary">
          Trade in formato tabella (accessibilità)
        </summary>
        <p className="mt-2 text-xs text-on-surface/70">Nessun trade selezionato.</p>
      </details>
    );
  }

  return (
    <details
      data-testid="backtest-trades-table"
      className={cn(
        'rounded border border-outline-variant bg-surface-container p-3 text-sm',
      )}
    >
      <summary className="cursor-pointer font-medium text-on-surface focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary">
        Trade in formato tabella (accessibilità) · {filtered.length} righe
      </summary>
      <div className="mt-2 overflow-x-auto">
        <table className="w-full table-auto">
          <caption className="sr-only">
            Lista dei trade simulati renderizzati nel chart timeline.
          </caption>
          <thead>
            <tr className="border-b border-outline-variant text-left text-xs uppercase tracking-wide text-on-surface/60">
              <th scope="col" className="px-2 py-1">
                Strategia
              </th>
              <th scope="col" className="px-2 py-1">
                Entry
              </th>
              <th scope="col" className="px-2 py-1 text-right">
                Prezzo entry
              </th>
              <th scope="col" className="px-2 py-1">
                Exit
              </th>
              <th scope="col" className="px-2 py-1 text-right">
                Prezzo exit
              </th>
              <th scope="col" className="px-2 py-1">
                Causale
              </th>
              <th scope="col" className="px-2 py-1 text-right">
                Rend.
              </th>
              <th scope="col" className="px-2 py-1 text-right">
                Holding
              </th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((t, idx) => (
              <tr
                key={`${t.strategy}-${t.entryDate}-${idx}`}
                className="border-b border-outline-variant/40"
              >
                <td className="px-2 py-1 text-on-surface">
                  {STRATEGY_LABELS[t.strategy]}
                </td>
                <td className="px-2 py-1 text-on-surface">
                  {toDateLabel(t.entryDate)}
                </td>
                <td className="px-2 py-1 text-right tabular-nums text-on-surface">
                  {formatCurrency(t.entryPrice, 'USD')}
                </td>
                <td className="px-2 py-1 text-on-surface">
                  {toDateLabel(t.exitDate)}
                </td>
                <td className="px-2 py-1 text-right tabular-nums text-on-surface">
                  {formatCurrency(t.exitPrice, 'USD')}
                </td>
                <td
                  className="px-2 py-1 font-medium"
                  style={{ color: EXIT_COLORS[t.exitReason] }}
                >
                  {EXIT_LABELS[t.exitReason]}
                </td>
                <td className="px-2 py-1 text-right tabular-nums text-on-surface">
                  {t.returnPct >= 0 ? '+' : ''}
                  {t.returnPct.toFixed(2)}%
                </td>
                <td className="px-2 py-1 text-right tabular-nums text-on-surface">
                  {t.holdingDays}g
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </details>
  );
}
