'use client';

import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from '@/components/ui/Card';
import { cn } from '@/lib/utils/cn';
import type { TaMomentumBlock } from '@/lib/api/technical';

/**
 * MomentumPanel — TSK-335 (US-101, EP-024 Fase 1).
 *
 * Pannello momentum (AC US-101 §Layout 5):
 *  - Riga RSI 14d (valore + zona Oversold/Neutral/Overbought).
 *    Soglie Wilder 30/70 (vedi [[oscillators-momentum-rsi]]).
 *  - Riga MACD daily (valore + direzione: BULLISH se > 0, BEARISH se < 0,
 *    NEUTRAL se ≈ 0).
 *  - Riga MACD weekly (Screen 1 Elder Triple Screen — vedi
 *    [[elder-triple-screen-impulse-system]]).
 *
 * Tutti i campi sono `nullable` nel payload (TaMomentumBlock): gestione `—`
 * graceful per indicatori non calcolabili (storico EOD insufficiente).
 *
 * UX governance — palette distinta dal TrafficLight VI:
 *  RSI: blu (oversold) / slate (neutro) / amber (overbought).
 *  MACD: green (>0 bullish) / slate (≈0) / red (<0 bearish).
 *
 * Accessibility (WCAG 2.2 AA — EP-016):
 *  - Lista descrittiva semantica `<dl>` per coppie label/value.
 *  - `aria-label` esteso include zona/direzione (non solo valore).
 *  - Colore non unico canale: testo + tono.
 *
 * Sorgenti:
 *  - OpenAPI §schemas/TaMomentumBlock (US-098 / TSK-326)
 *  - US-101 §Layout 5 (Pannello momentum)
 *  - [[oscillators-momentum-rsi]] (Murphy §Page 239)
 *  - [[elder-triple-screen-impulse-system]] (Elder §39)
 */

type RsiZone = 'OVERSOLD' | 'NEUTRAL' | 'OVERBOUGHT' | 'UNKNOWN';
type MacdDirection = 'BULLISH' | 'BEARISH' | 'NEUTRAL' | 'UNKNOWN';

const RSI_OVERSOLD_THRESHOLD = 30;
const RSI_OVERBOUGHT_THRESHOLD = 70;
const MACD_NEUTRAL_EPSILON = 1e-4;

function classifyRsi(rsi: number | null): RsiZone {
  if (rsi === null || !Number.isFinite(rsi)) return 'UNKNOWN';
  if (rsi <= RSI_OVERSOLD_THRESHOLD) return 'OVERSOLD';
  if (rsi >= RSI_OVERBOUGHT_THRESHOLD) return 'OVERBOUGHT';
  return 'NEUTRAL';
}

function classifyMacd(macd: number | null): MacdDirection {
  if (macd === null || !Number.isFinite(macd)) return 'UNKNOWN';
  if (macd > MACD_NEUTRAL_EPSILON) return 'BULLISH';
  if (macd < -MACD_NEUTRAL_EPSILON) return 'BEARISH';
  return 'NEUTRAL';
}

const RSI_ZONE_LABEL: Readonly<Record<RsiZone, string>> = {
  OVERSOLD: 'Oversold',
  NEUTRAL: 'Neutro',
  OVERBOUGHT: 'Overbought',
  UNKNOWN: 'n/d',
};

const RSI_ZONE_CLASS: Readonly<Record<RsiZone, string>> = {
  OVERSOLD:
    'bg-blue-100 text-blue-900 dark:bg-blue-950 dark:text-blue-200',
  NEUTRAL:
    'bg-slate-100 text-slate-700 dark:bg-slate-900 dark:text-slate-300',
  OVERBOUGHT:
    'bg-amber-100 text-amber-900 dark:bg-amber-950 dark:text-amber-100',
  UNKNOWN:
    'bg-slate-50 text-slate-400 dark:bg-slate-900 dark:text-slate-500',
};

const MACD_DIR_LABEL: Readonly<Record<MacdDirection, string>> = {
  BULLISH: 'Bullish (>0)',
  BEARISH: 'Bearish (<0)',
  NEUTRAL: 'Neutro (≈0)',
  UNKNOWN: 'n/d',
};

const MACD_DIR_CLASS: Readonly<Record<MacdDirection, string>> = {
  BULLISH:
    'bg-green-100 text-green-900 dark:bg-green-950 dark:text-green-200',
  BEARISH:
    'bg-red-100 text-red-900 dark:bg-red-950 dark:text-red-200',
  NEUTRAL:
    'bg-slate-100 text-slate-700 dark:bg-slate-900 dark:text-slate-300',
  UNKNOWN:
    'bg-slate-50 text-slate-400 dark:bg-slate-900 dark:text-slate-500',
};

function formatRsi(v: number | null): string {
  if (v === null || !Number.isFinite(v)) return '—';
  return v.toFixed(1);
}

function formatMacd(v: number | null): string {
  if (v === null || !Number.isFinite(v)) return '—';
  // MACD può essere molto piccolo (azioni a basso prezzo) o grande (azioni
  // ad alto prezzo). 3 decimali è un compromesso ragionevole.
  return v.toFixed(3);
}

export interface MomentumPanelProps {
  readonly momentum: TaMomentumBlock;
}

export function MomentumPanel(
  props: MomentumPanelProps,
): React.ReactElement {
  const { momentum } = props;
  const rsiZone = classifyRsi(momentum.rsi14);
  const macdDailyDir = classifyMacd(momentum.macdDaily);
  const macdWeeklyDir = classifyMacd(momentum.macdWeekly);

  return (
    <Card data-testid="ta-momentum-panel">
      <CardHeader>
        <CardTitle as="h2">Momentum</CardTitle>
      </CardHeader>
      <CardContent>
        <dl className="grid gap-3 sm:grid-cols-3">
          <MomentumRow
            label="RSI 14d"
            value={formatRsi(momentum.rsi14)}
            badge={RSI_ZONE_LABEL[rsiZone]}
            badgeClass={RSI_ZONE_CLASS[rsiZone]}
            ariaExtra={`zona ${RSI_ZONE_LABEL[rsiZone]}`}
            testId="ta-rsi14"
            dataZone={rsiZone}
          />
          <MomentumRow
            label="MACD daily"
            value={formatMacd(momentum.macdDaily)}
            badge={MACD_DIR_LABEL[macdDailyDir]}
            badgeClass={MACD_DIR_CLASS[macdDailyDir]}
            ariaExtra={`direzione ${MACD_DIR_LABEL[macdDailyDir]}`}
            testId="ta-macd-daily"
            dataZone={macdDailyDir}
          />
          <MomentumRow
            label="MACD weekly"
            value={formatMacd(momentum.macdWeekly)}
            badge={MACD_DIR_LABEL[macdWeeklyDir]}
            badgeClass={MACD_DIR_CLASS[macdWeeklyDir]}
            ariaExtra={`Screen 1 Triple Screen — direzione ${MACD_DIR_LABEL[macdWeeklyDir]}`}
            testId="ta-macd-weekly"
            dataZone={macdWeeklyDir}
          />
        </dl>
      </CardContent>
    </Card>
  );
}

function MomentumRow({
  label,
  value,
  badge,
  badgeClass,
  ariaExtra,
  testId,
  dataZone,
}: {
  readonly label: string;
  readonly value: string;
  readonly badge: string;
  readonly badgeClass: string;
  readonly ariaExtra: string;
  readonly testId: string;
  readonly dataZone: string;
}): React.ReactElement {
  const ariaLabel = `${label}: ${value}, ${ariaExtra}`;
  return (
    <div
      data-testid={testId}
      data-zone={dataZone}
      className="flex flex-col gap-1 rounded-md border border-outline-variant bg-surface-container-high p-3"
      aria-label={ariaLabel}
    >
      <dt className="text-xs font-semibold uppercase tracking-wide text-on-surface/60">
        {label}
      </dt>
      <dd className="flex items-center justify-between gap-2">
        <span className="text-lg font-semibold tabular-nums text-on-surface">
          {value}
        </span>
        <span
          className={cn(
            'inline-flex items-center rounded-full border border-transparent px-2 py-0.5 text-xs font-medium',
            badgeClass,
          )}
        >
          {badge}
        </span>
      </dd>
    </div>
  );
}
