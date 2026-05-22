'use client';

import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
  type TooltipProps,
} from 'recharts';
import type { HistoricalSeriesPoint } from '@/lib/api/historical';
import { formatCurrency, formatMarketCap } from '@/lib/utils/formatters';

/**
 * HistoricalChart — TSK-024 (US-015).
 *
 * Componente puro (props-only, no fetch interna) che renderizza la serie
 * temporale decennale di ricavi e utile netto su due linee sovrapposte.
 *
 * Riferimento ADR: design_&_architecture/decisions/ADR-001-frontend-stack.md
 *   §Decisione §Charts: Recharts.
 * Riferimento AC: management/kanban/EP-005-dashboard-traffic-light-moat/
 *   US-015-grafici-storici/US-015.md §AC.
 * Riferimento contract: design_&_architecture/api/openapi.yaml
 *   §HistoricalSeriesPoint (fiscalYear, revenue, netIncome, isMissing).
 *
 * Scelte design chiave (TSK-024):
 *  - `LineChart` (NON `ComposedChart`) — la US chiede "due serie temporali
 *    con due colori distinti", entrambe della stessa natura (importi USD
 *    annuali); due linee sovrapposte rispondono nativamente. `ComposedChart`
 *    sarebbe utile per metriche eterogenee (es. revenue=bar +
 *    margin%=line), non è il caso.
 *  - `connectNulls={false}` esplicito su entrambe le linee → Recharts NON
 *    interpola tra punti `null`, creando un gap visibile (AC US-015
 *    "anni mancanti non interpolati silenziosamente").
 *  - Custom `dot` renderer che disegna il punto vuoto (cerchio outlined,
 *    fill bianco) quando `payload.isMissing=true`, per evidenziare
 *    visivamente i punti "no data" anche quando una sola delle due serie è
 *    mancante in quell'anno.
 *  - Asse Y: `formatMarketCap` (`$B`/`$M`/`$T`) — gli importi annuali sono
 *    nell'ordine dei miliardi per le large cap, M/K per small/micro;
 *    abbreviazione finanziaria standard.
 *  - Tooltip custom: mostra `formatCurrency(v, 'USD')` con 2 decimali per
 *    leggibilità precisa al hover (AC US-015 "hover/tap → valore
 *    numerico"); "n/d" quando `isMissing=true`.
 *  - Colori: revenue `#2563eb` (blue-600 Tailwind), netIncome `#16a34a`
 *    (green-600). Contrast ratio WCAG AA su sfondo bianco (>4.5:1).
 *  - `ResponsiveContainer width="100%" height={400}` — l'altezza fissa di
 *    400px è scelta per leggibilità su desktop (≈viewport ratio 16:5)
 *    senza richiedere scroll; il container interno è responsivo.
 */

const COLOR_REVENUE = '#2563eb';
const COLOR_NET_INCOME = '#16a34a';

export interface HistoricalChartProps {
  /** Punti già ordinati per `fiscalYear` crescente (garantito BE TSK-023). */
  readonly points: ReadonlyArray<HistoricalSeriesPoint>;
  readonly loading?: boolean;
  /** Override del messaggio mostrato quando `points` è vuoto. */
  readonly emptyMessage?: string;
  /** ISO-8601 — se valorizzato, footer "Dati aggiornati al ...". */
  readonly dataSnapshotAt?: string;
}

interface TooltipPayloadItem {
  readonly dataKey: 'revenue' | 'netIncome';
  readonly name: string;
  readonly value: number | null;
  readonly color: string;
  readonly payload: HistoricalSeriesPoint;
}

function formatYValue(value: number): string {
  return formatMarketCap(value, '$');
}

function formatTooltipValue(value: number | null, isMissing: boolean): string {
  if (isMissing || value === null || !Number.isFinite(value)) {
    return 'n/d';
  }
  return formatCurrency(value, 'USD');
}

/**
 * Dot custom: cerchio "vuoto" quando il punto è `isMissing` (anche se solo
 * una delle due metriche manca quell'anno). Per i punti normali ritorna un
 * dot pieno standard.
 *
 * NB: Recharts passa props non tipizzate al dot; usiamo un narrow type
 * locale per accedere a `cx`, `cy`, `payload`, `stroke`.
 */
interface CustomDotProps {
  readonly cx?: number;
  readonly cy?: number;
  readonly stroke?: string;
  readonly payload?: HistoricalSeriesPoint;
}

function CustomDot(props: CustomDotProps): React.ReactElement | null {
  const { cx, cy, stroke, payload } = props;
  if (cx === undefined || cy === undefined || payload === undefined) {
    return null;
  }
  const isMissing = payload.isMissing === true;
  if (isMissing) {
    return (
      <circle
        cx={cx}
        cy={cy}
        r={5}
        stroke={stroke ?? '#9ca3af'}
        strokeWidth={2}
        strokeDasharray="2 2"
        fill="#ffffff"
        data-testid={`hc-dot-missing-${payload.fiscalYear}`}
      />
    );
  }
  return (
    <circle
      cx={cx}
      cy={cy}
      r={4}
      stroke={stroke ?? '#000000'}
      strokeWidth={1.5}
      fill={stroke ?? '#000000'}
      data-testid={`hc-dot-${payload.fiscalYear}`}
    />
  );
}

function CustomTooltip(
  props: TooltipProps<number, string>,
): React.ReactElement | null {
  const { active, payload, label } = props;
  if (active !== true || payload === undefined || payload.length === 0) {
    return null;
  }
  const items = payload as unknown as ReadonlyArray<TooltipPayloadItem>;
  const firstItem = items[0];
  if (firstItem === undefined) return null;
  const isMissing = firstItem.payload.isMissing === true;
  return (
    <div
      data-testid="hc-tooltip"
      className="rounded border border-gray-200 bg-white p-2 text-sm shadow"
      role="tooltip"
    >
      <div className="font-semibold text-gray-800">{String(label)}</div>
      {items.map((entry) => (
        <div
          key={entry.dataKey}
          className="flex items-center gap-2"
          style={{ color: entry.color }}
        >
          <span>{entry.name}:</span>
          <span data-testid={`hc-tooltip-${entry.dataKey}`}>
            {formatTooltipValue(entry.value, isMissing && entry.value === null)}
          </span>
        </div>
      ))}
    </div>
  );
}

export function HistoricalChart(props: HistoricalChartProps): React.ReactElement {
  const {
    points,
    loading = false,
    emptyMessage = 'Nessun dato storico disponibile',
    dataSnapshotAt,
  } = props;

  if (loading) {
    return (
      <div
        data-testid="historical-chart-loading"
        role="status"
        aria-busy="true"
        aria-live="polite"
        className="flex h-[400px] w-full animate-pulse items-center justify-center rounded bg-gray-100"
      >
        <span className="text-gray-500">Caricamento serie storica...</span>
      </div>
    );
  }

  if (points.length === 0) {
    return (
      <div
        data-testid="historical-chart-empty"
        role="status"
        className="flex h-[400px] w-full items-center justify-center rounded border border-dashed border-gray-300 text-gray-500"
      >
        {emptyMessage}
      </div>
    );
  }

  // Recharts richiede array mutabile; clone difensivo per rispettare ReadonlyArray.
  const chartData: ReadonlyArray<HistoricalSeriesPoint> = [...points];

  return (
    <div data-testid="historical-chart" className="w-full">
      <ResponsiveContainer width="100%" height={400}>
        <LineChart
          data={chartData as HistoricalSeriesPoint[]}
          margin={{ top: 16, right: 24, left: 8, bottom: 8 }}
        >
          <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
          <XAxis
            dataKey="fiscalYear"
            type="number"
            domain={['dataMin', 'dataMax']}
            allowDecimals={false}
            tick={{ fill: '#4b5563', fontSize: 12 }}
          />
          <YAxis
            tickFormatter={formatYValue}
            tick={{ fill: '#4b5563', fontSize: 12 }}
            width={64}
          />
          <Tooltip content={<CustomTooltip />} />
          <Legend />
          <Line
            type="monotone"
            dataKey="revenue"
            name="Ricavi"
            stroke={COLOR_REVENUE}
            strokeWidth={2}
            connectNulls={false}
            isAnimationActive={false}
            dot={<CustomDot />}
            activeDot={{ r: 6 }}
          />
          <Line
            type="monotone"
            dataKey="netIncome"
            name="Utile Netto"
            stroke={COLOR_NET_INCOME}
            strokeWidth={2}
            connectNulls={false}
            isAnimationActive={false}
            dot={<CustomDot />}
            activeDot={{ r: 6 }}
          />
        </LineChart>
      </ResponsiveContainer>
      {dataSnapshotAt !== undefined && dataSnapshotAt.length > 0 ? (
        <p
          data-testid="historical-chart-snapshot"
          className="mt-2 text-right text-xs text-gray-500"
        >
          Dati aggiornati al {new Date(dataSnapshotAt).toLocaleDateString('it-IT')}
        </p>
      ) : null}
    </div>
  );
}
