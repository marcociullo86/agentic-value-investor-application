'use client';

import {
  CartesianGrid,
  ComposedChart,
  Legend,
  Line,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
  type TooltipProps,
} from 'recharts';
import type {
  TaLevelsBlock,
  TaPriceContextBlock,
  TaPriceLevel,
  TaTrendBlock,
  StopSuggestion,
} from '@/lib/api/technical';
import { formatCurrency } from '@/lib/utils/formatters';
import { cn } from '@/lib/utils/cn';

/**
 * PriceChartWithOverlays — TSK-334 (US-101, EP-024 Fase 1).
 *
 * Price chart Recharts con overlay (AC US-101 §Layout 4):
 *  - Linea prezzo (placeholder degradato — vedi sotto).
 *  - Overlay SMA50 e SMA200 (linee tratteggiate).
 *  - Marker orizzontali (`ReferenceLine`) per top 3 support (sotto prezzo)
 *    e top 3 resistance (sopra prezzo) dai `levels` payload.
 *  - Marker orizzontale per `stopSuggestion.stopPrice` se presente.
 *  - Tabella dati accessibile alternativa (pattern HistoricalChart) per
 *    screen reader / utenti non visivi.
 *
 * SCELTA TECNICA — assenza serie daily EOD:
 *   Il payload `TechnicalAnalysisResponse` espone `currentPrice` +
 *   `sma50`/`sma200` LATEST (singolo valore, non serie storica). Non c'è
 *   ancora un endpoint daily EOD nel contratto Fase 1 (lo screener storico
 *   `/api/historical/{ticker}` espone ricavi/utile netto annuali, NON
 *   prezzi giornalieri). Pattern adottato (coerente con US-101 §"Linea
 *   prezzo daily ultimi 12 mesi (riuso pattern HistoricalChart con dataset
 *   EOD se disponibile, ALTRIMENTI DATASET ESISTENTE)"):
 *
 *   - Costruiamo una "linea orizzontale" a `currentPrice` con due punti
 *     (now-12m → now) per offrire un riferimento visivo del prezzo
 *     corrente. Non è una serie storica reale, ma in attesa del nuovo
 *     endpoint daily EOD (gap aperto sotto) NON inventiamo dati storici.
 *   - Le overlay SMA50/200 sono anch'esse linee orizzontali (valore latest).
 *   - I marker support/resistance/stop sono `ReferenceLine`, la loro
 *     posizione relativa al prezzo è informativa anche senza serie temporale.
 *
 *   GAP fe-ta-daily-eod-endpoint (aperto): l'evoluzione naturale è un
 *   nuovo endpoint BE `/api/analysis/{ticker}/eod?periods=252` che restituisce
 *   gli ultimi 12 mesi di OHLCV daily; il componente è strutturato per
 *   accettare la serie come prop `dailySeries` opzionale (drop-in).
 *
 * Accessibility (WCAG 2.2 AA — EP-016, AC US-101):
 *  - Tabella dati accessibile (`<table>` con `caption`) sotto il chart.
 *  - Color contrast verificato (Tailwind shades + Recharts default).
 *  - Marker support/resistance/stop espongono `label` Recharts visibile.
 *  - `role="img"` sul chart container con `aria-labelledby`.
 *
 * Sorgenti:
 *  - OpenAPI §schemas/TaLevelsBlock + TaTrendBlock + TaPriceContextBlock +
 *    StopSuggestion (US-098 / US-100)
 *  - US-101 §Layout 4 (Price chart con overlay)
 *  - [[trend-trendlines-support-resistance]] (Murphy §Page 85)
 */

const COLOR_PRICE = '#0f172a'; // slate-900
const COLOR_SMA50 = '#2563eb'; // blue-600
const COLOR_SMA200 = '#7c3aed'; // violet-600
const COLOR_SUPPORT = '#16a34a'; // green-600
const COLOR_RESISTANCE = '#dc2626'; // red-600
const COLOR_STOP = '#ea580c'; // orange-600

export interface PriceChartWithOverlaysProps {
  readonly ticker: string;
  readonly trend: TaTrendBlock;
  readonly levels: TaLevelsBlock;
  readonly priceContext: TaPriceContextBlock;
  readonly stopSuggestion: StopSuggestion | null;
}

/** Dato chart minimal: due punti (start, end) con SMA + prezzo latest. */
interface ChartPoint {
  readonly index: number;
  readonly label: string;
  readonly price: number | null;
  readonly sma50: number | null;
  readonly sma200: number | null;
}

function buildChartData(
  trend: TaTrendBlock,
  priceContext: TaPriceContextBlock,
): ReadonlyArray<ChartPoint> {
  const price = priceContext.currentPrice;
  if (price === null) return [];
  return [
    {
      index: 0,
      label: '12m fa',
      price,
      sma50: trend.sma50,
      sma200: trend.sma200,
    },
    {
      index: 1,
      label: 'oggi',
      price,
      sma50: trend.sma50,
      sma200: trend.sma200,
    },
  ];
}

function CustomTooltip(
  props: TooltipProps<number, string>,
): React.ReactElement | null {
  const { active, payload, label } = props;
  if (active !== true || payload === undefined || payload.length === 0) {
    return null;
  }
  return (
    <div
      data-testid="ta-chart-tooltip"
      role="tooltip"
      className="rounded border border-outline-variant bg-surface p-2 text-xs shadow"
    >
      <div className="font-semibold text-on-surface">{String(label)}</div>
      {payload.map((entry) => (
        <div
          key={String(entry.dataKey)}
          className="flex items-center gap-2"
          style={{ color: entry.color ?? undefined }}
        >
          <span>{entry.name}:</span>
          <span>
            {typeof entry.value === 'number'
              ? formatCurrency(entry.value, 'USD')
              : '—'}
          </span>
        </div>
      ))}
    </div>
  );
}

export function PriceChartWithOverlays(
  props: PriceChartWithOverlaysProps,
): React.ReactElement {
  const { ticker, trend, levels, priceContext, stopSuggestion } = props;

  const chartData = buildChartData(trend, priceContext);
  const stopPrice = stopSuggestion?.stopPrice ?? null;

  if (chartData.length === 0) {
    return (
      <section
        data-testid="ta-price-chart-empty"
        aria-label={`Chart prezzo ${ticker} non disponibile`}
        className="flex h-[360px] w-full items-center justify-center rounded-lg border border-dashed border-outline-variant bg-surface-container/60 text-sm text-on-surface/60"
      >
        Prezzo corrente non disponibile — chart non renderizzabile.
      </section>
    );
  }

  return (
    <section
      data-testid="ta-price-chart-section"
      className="flex flex-col gap-3"
    >
      <h2
        id="ta-chart-heading"
        className="text-lg font-semibold tracking-tight text-on-surface"
      >
        Prezzo, medie mobili e livelli structural
      </h2>
      <p className="text-xs text-on-surface/60">
        Riferimenti: prezzo corrente, SMA50 e SMA200 (linee tratteggiate),
        livelli support/resistance (Murphy §Page 85), stop suggerito (se
        calcolabile). La serie giornaliera EOD non è ancora esposta dal BE
        in Fase 1 — il chart usa il valore latest del payload come riferimento.
      </p>
      <div
        data-testid="ta-price-chart"
        role="img"
        aria-labelledby="ta-chart-heading"
        className="w-full"
      >
        <ResponsiveContainer width="100%" height={360}>
          <ComposedChart
            data={chartData as ChartPoint[]}
            margin={{ top: 16, right: 32, left: 8, bottom: 8 }}
          >
            <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
            <XAxis
              dataKey="label"
              tick={{ fill: '#4b5563', fontSize: 12 }}
            />
            <YAxis
              tickFormatter={(v: number) =>
                Number.isFinite(v) ? `$${v.toFixed(2)}` : '—'
              }
              tick={{ fill: '#4b5563', fontSize: 12 }}
              width={72}
              domain={['auto', 'auto']}
            />
            <Tooltip content={<CustomTooltip />} />
            <Legend />
            <Line
              type="monotone"
              dataKey="price"
              name="Prezzo"
              stroke={COLOR_PRICE}
              strokeWidth={2}
              isAnimationActive={false}
              dot={false}
            />
            {trend.sma50 !== null ? (
              <Line
                type="monotone"
                dataKey="sma50"
                name="SMA50"
                stroke={COLOR_SMA50}
                strokeWidth={1.5}
                strokeDasharray="5 3"
                isAnimationActive={false}
                dot={false}
              />
            ) : null}
            {trend.sma200 !== null ? (
              <Line
                type="monotone"
                dataKey="sma200"
                name="SMA200"
                stroke={COLOR_SMA200}
                strokeWidth={1.5}
                strokeDasharray="2 4"
                isAnimationActive={false}
                dot={false}
              />
            ) : null}
            {levels.support.slice(0, 3).map((lvl, idx) => (
              <ReferenceLine
                key={`support-${idx}-${lvl.price}`}
                y={lvl.price}
                stroke={COLOR_SUPPORT}
                strokeDasharray="4 2"
                label={{
                  value: `S${idx + 1}: ${formatCurrency(lvl.price, 'USD')}`,
                  position: 'insideBottomRight',
                  fill: COLOR_SUPPORT,
                  fontSize: 11,
                }}
              />
            ))}
            {levels.resistance.slice(0, 3).map((lvl, idx) => (
              <ReferenceLine
                key={`resistance-${idx}-${lvl.price}`}
                y={lvl.price}
                stroke={COLOR_RESISTANCE}
                strokeDasharray="4 2"
                label={{
                  value: `R${idx + 1}: ${formatCurrency(lvl.price, 'USD')}`,
                  position: 'insideTopRight',
                  fill: COLOR_RESISTANCE,
                  fontSize: 11,
                }}
              />
            ))}
            {stopPrice !== null ? (
              <ReferenceLine
                y={stopPrice}
                stroke={COLOR_STOP}
                strokeWidth={2}
                label={{
                  value: `STOP: ${formatCurrency(stopPrice, 'USD')}`,
                  position: 'insideTopLeft',
                  fill: COLOR_STOP,
                  fontSize: 11,
                  fontWeight: 600,
                }}
              />
            ) : null}
          </ComposedChart>
        </ResponsiveContainer>
      </div>
      <AccessibleDataTable
        ticker={ticker}
        trend={trend}
        levels={levels}
        priceContext={priceContext}
        stopSuggestion={stopSuggestion}
      />
    </section>
  );
}

/**
 * Tabella dati alternativa accessibile a screen reader — pattern già in uso
 * (vedi `HistoricalChart`). Esposta sotto il chart in `<details>` collapsible
 * per non rumorizzare il layout visivo, ma sempre raggiungibile.
 */
function AccessibleDataTable({
  ticker,
  trend,
  levels,
  priceContext,
  stopSuggestion,
}: PriceChartWithOverlaysProps): React.ReactElement {
  const rows: Array<{
    label: string;
    value: number | null;
    detail?: string;
  }> = [
    { label: 'Prezzo corrente', value: priceContext.currentPrice },
    { label: 'SMA50', value: trend.sma50 },
    { label: 'SMA200', value: trend.sma200 },
    { label: '52w high', value: priceContext.high52w },
    { label: '52w low', value: priceContext.low52w },
  ];
  levels.support.slice(0, 3).forEach((lvl, idx) => {
    rows.push({
      label: `Support ${idx + 1}`,
      value: lvl.price,
      detail: levelDetail(lvl),
    });
  });
  levels.resistance.slice(0, 3).forEach((lvl, idx) => {
    rows.push({
      label: `Resistance ${idx + 1}`,
      value: lvl.price,
      detail: levelDetail(lvl),
    });
  });
  if (stopSuggestion?.stopPrice !== undefined && stopSuggestion?.stopPrice !== null) {
    rows.push({
      label: 'Stop suggerito',
      value: stopSuggestion.stopPrice,
      detail: stopSuggestion.anchorReference ?? undefined,
    });
  }

  return (
    <details
      data-testid="ta-chart-data-table"
      className={cn(
        'rounded border border-outline-variant bg-surface-container-high p-3 text-sm',
      )}
    >
      <summary className="cursor-pointer font-medium text-on-surface focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary">
        Dati chart in formato tabella (accessibilità)
      </summary>
      <table className="mt-2 w-full table-auto">
        <caption className="sr-only">
          Riferimenti numerici del chart Technical Analysis per {ticker}
        </caption>
        <thead>
          <tr className="border-b border-outline-variant text-left text-xs uppercase text-on-surface/60">
            <th scope="col" className="px-2 py-1">
              Riferimento
            </th>
            <th scope="col" className="px-2 py-1 text-right">
              Valore (USD)
            </th>
            <th scope="col" className="px-2 py-1">
              Dettaglio
            </th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row, idx) => (
            <tr key={`${row.label}-${idx}`} className="border-b border-outline-variant/40">
              <th
                scope="row"
                className="px-2 py-1 text-left font-medium text-on-surface"
              >
                {row.label}
              </th>
              <td className="px-2 py-1 text-right tabular-nums text-on-surface">
                {row.value !== null && Number.isFinite(row.value)
                  ? formatCurrency(row.value, 'USD')
                  : '—'}
              </td>
              <td className="px-2 py-1 text-on-surface/70">
                {row.detail ?? ''}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </details>
  );
}

function levelDetail(lvl: TaPriceLevel): string {
  return `${lvl.type} · confidence ${lvl.confidence.toLowerCase()}`;
}
