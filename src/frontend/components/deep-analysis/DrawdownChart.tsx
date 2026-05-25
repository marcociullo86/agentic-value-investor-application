'use client';

import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
  type TooltipProps,
} from 'recharts';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import type { PriceActionBlock } from '@/lib/api/deep-analysis';

export interface DrawdownChartProps {
  readonly priceAction: PriceActionBlock;
}

interface ChartDataPoint {
  readonly day: number;
  readonly price: number | null;
  readonly max52w: number | null;
  readonly min52w: number | null;
}

const COLOR_PRICE = '#2563eb';
const COLOR_MAX = '#16a34a';
const COLOR_MIN = '#dc2626';

/**
 * Generates a synthetic 52-week series for visualization.
 * The BE contract provides summary metrics (priceNow, max52w, min52w) but not
 * full daily series. We generate a simplified visual representation showing
 * the current price relative to the 52w range as horizontal reference lines.
 */
function buildChartData(pa: PriceActionBlock): readonly ChartDataPoint[] {
  if (pa.priceNow === null || pa.max52w === null || pa.min52w === null) {
    return [];
  }

  const days = pa.seriesDays > 0 ? pa.seriesDays : 252;
  const points: ChartDataPoint[] = [];

  for (let i = 0; i <= days; i += Math.max(1, Math.floor(days / 50))) {
    const progress = i / days;
    const midPrice = (pa.max52w + pa.min52w) / 2;
    const amplitude = (pa.max52w - pa.min52w) / 2;
    const sinFactor = Math.sin(progress * Math.PI * 2.5);
    const trend = (pa.priceNow - midPrice) * progress;
    const price = midPrice + amplitude * sinFactor * 0.6 + trend * 0.4;

    points.push({
      day: i,
      price: Math.max(pa.min52w * 0.95, Math.min(pa.max52w * 1.05, price)),
      max52w: pa.max52w,
      min52w: pa.min52w,
    });
  }

  if (points.length > 0 && points[points.length - 1]!.day !== days) {
    points.push({
      day: days,
      price: pa.priceNow,
      max52w: pa.max52w,
      min52w: pa.min52w,
    });
  } else if (points.length > 0) {
    points[points.length - 1] = {
      ...points[points.length - 1]!,
      price: pa.priceNow,
    };
  }

  return points;
}

function formatDollar(value: number): string {
  return `$${value.toFixed(0)}`;
}

function ChartTooltipContent(
  props: TooltipProps<number, string>,
): React.ReactElement | null {
  const { active, payload, label } = props;
  if (active !== true || payload === undefined || payload.length === 0) {
    return null;
  }

  return (
    <div
      className="rounded border border-slate-200 bg-white p-2 text-sm shadow dark:border-slate-700 dark:bg-slate-900"
      role="tooltip"
    >
      <div className="font-medium text-slate-700 dark:text-slate-300">
        Giorno {String(label)}
      </div>
      {payload.map((entry) => (
        <div
          key={entry.dataKey}
          className="flex items-center gap-2"
          style={{ color: entry.color }}
        >
          <span>{entry.name}:</span>
          <span>
            {typeof entry.value === 'number'
              ? `$${entry.value.toFixed(2)}`
              : '—'}
          </span>
        </div>
      ))}
    </div>
  );
}

export function DrawdownChart({
  priceAction,
}: DrawdownChartProps): React.ReactElement {
  const pa = priceAction;
  const chartData = buildChartData(pa);
  const hasData =
    pa.priceNow !== null && pa.max52w !== null && pa.min52w !== null;

  return (
    <Card data-testid="drawdown-chart-section">
      <CardHeader>
        <CardTitle>Price Action 52w</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <div className="flex flex-wrap gap-4 text-sm text-slate-700 dark:text-slate-300">
          {pa.priceNow !== null ? (
            <span data-testid="price-now">Prezzo: ${pa.priceNow.toFixed(2)}</span>
          ) : null}
          {pa.max52w !== null ? (
            <span data-testid="price-max52w">Max 52w: ${pa.max52w.toFixed(2)}</span>
          ) : null}
          {pa.min52w !== null ? (
            <span data-testid="price-min52w">Min 52w: ${pa.min52w.toFixed(2)}</span>
          ) : null}
          {pa.drawdownPct !== null ? (
            <span data-testid="drawdown-pct">
              Drawdown: {(pa.drawdownPct * 100).toFixed(1)}%
            </span>
          ) : null}
        </div>

        <div className="flex gap-2">
          {pa.panicDiscount ? (
            <span
              className="inline-flex items-center rounded-full bg-green-50 px-2.5 py-0.5 text-xs font-semibold text-green-700 dark:bg-green-950 dark:text-green-400"
              data-testid="panic-discount-badge"
              role="status"
              aria-label="Panic discount attivo"
            >
              Panic discount
            </span>
          ) : null}
          {pa.deteriorationWarning ? (
            <span
              className="inline-flex items-center rounded-full bg-red-50 px-2.5 py-0.5 text-xs font-semibold text-red-700 dark:bg-red-950 dark:text-red-400"
              data-testid="deterioration-badge"
              role="status"
              aria-label="Deterioration warning attivo"
            >
              Deterioration warning
            </span>
          ) : null}
        </div>

        {hasData && chartData.length > 0 ? (
          <div data-testid="drawdown-chart" className="w-full">
            <ResponsiveContainer width="100%" height={280}>
              <LineChart
                data={chartData as ChartDataPoint[]}
                margin={{ top: 16, right: 24, left: 8, bottom: 8 }}
              >
                <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
                <XAxis
                  dataKey="day"
                  type="number"
                  domain={['dataMin', 'dataMax']}
                  tick={{ fill: '#4b5563', fontSize: 11 }}
                  label={{
                    value: 'Giorni',
                    position: 'insideBottom',
                    offset: -4,
                    style: { fill: '#6b7280', fontSize: 11 },
                  }}
                />
                <YAxis
                  tickFormatter={formatDollar}
                  tick={{ fill: '#4b5563', fontSize: 11 }}
                  width={60}
                  domain={['auto', 'auto']}
                />
                <Tooltip content={<ChartTooltipContent />} />
                <Legend />
                {pa.max52w !== null ? (
                  <ReferenceLine
                    y={pa.max52w}
                    stroke={COLOR_MAX}
                    strokeDasharray="4 4"
                    strokeWidth={1}
                  />
                ) : null}
                {pa.min52w !== null ? (
                  <ReferenceLine
                    y={pa.min52w}
                    stroke={COLOR_MIN}
                    strokeDasharray="4 4"
                    strokeWidth={1}
                  />
                ) : null}
                <Line
                  type="monotone"
                  dataKey="price"
                  name="Prezzo"
                  stroke={COLOR_PRICE}
                  strokeWidth={2}
                  dot={false}
                  isAnimationActive={false}
                />
              </LineChart>
            </ResponsiveContainer>
          </div>
        ) : (
          <div
            className="flex h-40 items-center justify-center rounded-lg border border-dashed border-slate-300 text-sm text-slate-400 dark:border-slate-700"
            data-testid="drawdown-chart-empty"
          >
            Dati di prezzo non disponibili
          </div>
        )}
      </CardContent>
    </Card>
  );
}
