'use client';

import { CheckCircle, AlertTriangle, MinusCircle, Info } from 'lucide-react';
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from '@/components/ui/Card';
import { cn } from '@/lib/utils/cn';
import type {
  BacktestStrategyMetrics,
  BacktestTimingEdge,
  BacktestTimingEdgeLabel,
} from '@/lib/api/backtest';

/**
 * BacktestVerdictHero — TSK-351 (US-106, EP-024 Fase 3).
 *
 * Verdetto sintetico in alto al pannello "Verifica storica" — frase azionabile
 * derivata da `BacktestTimingEdge.label`:
 *
 *  - POSITIVE_EDGE → "Su {TICKER}, entrare nel momento giusto ha reso +{x}%
 *                     vs +{y}% comprando appena era a sconto." (verde)
 *  - NEUTRAL       → "Su {TICKER}, il timing non ha cambiato molto l'esito."
 *                     (grigio / neutro)
 *  - NEGATIVE_EDGE → "Attenzione: su {TICKER} il timing ha reso meno del
 *                     semplice comprare a sconto." (amber/arancione,
 *                     STESSO RISALTO del positivo — niente cherry-picking).
 *
 * Lente di valore (memory/semantic/value-investing-design-lens.md):
 * il caso negativo non è "nascosto". Stesso layout, stessa enfasi visiva del
 * positivo, semantica chiara ("Attenzione"): se il backtest dice che il
 * timing non sta dando valore, lo dichiariamo apertamente. È ciò che rende
 * il verdetto falsificabile invece che una promessa.
 *
 * Accessibility (WCAG 2.2 AA — EP-016):
 *  - Colore NON è l'unico canale: icona + heading testuale + tono.
 *  - `role="status"` sul badge label (non `alert` — non interrompe SR).
 *  - `aria-label` completo include verdetto + numeri letti dallo screen reader.
 *  - Palette: verde/amber/slate con contrasto verificato 4.5:1+ light + dark.
 */

const PRESENTATIONS: Readonly<
  Record<
    BacktestTimingEdgeLabel,
    {
      readonly cardClassName: string;
      readonly badgeClassName: string;
      readonly icon: React.ReactNode;
      readonly label: string;
    }
  >
> = {
  POSITIVE_EDGE: {
    cardClassName: 'border-l-4 border-green-500',
    badgeClassName:
      'bg-green-100 text-green-900 border-green-300 ' +
      'dark:bg-green-950 dark:text-green-200 dark:border-green-800',
    icon: <CheckCircle aria-hidden="true" className="h-5 w-5" />,
    label: 'Timing ha aggiunto valore',
  },
  NEUTRAL: {
    cardClassName: 'border-l-4 border-slate-300 dark:border-slate-700',
    badgeClassName:
      'bg-slate-100 text-slate-800 border-slate-300 ' +
      'dark:bg-slate-900 dark:text-slate-200 dark:border-slate-700',
    icon: <MinusCircle aria-hidden="true" className="h-5 w-5" />,
    label: 'Timing senza edge significativo',
  },
  NEGATIVE_EDGE: {
    cardClassName: 'border-l-4 border-amber-500',
    badgeClassName:
      'bg-amber-100 text-amber-900 border-amber-300 ' +
      'dark:bg-amber-950 dark:text-amber-100 dark:border-amber-800',
    icon: <AlertTriangle aria-hidden="true" className="h-5 w-5" />,
    label: 'Attenzione: timing in perdita vs solo sconto',
  },
};

export interface BacktestVerdictHeroProps {
  readonly ticker: string;
  readonly timingEdge: BacktestTimingEdge;
  readonly strategies: ReadonlyArray<BacktestStrategyMetrics>;
}

function formatSignedPct(value: number | null): string {
  if (value === null || !Number.isFinite(value)) return '—';
  // Backend espone returnPct/timingEdgePct in PUNTI PERCENTUALI (es. 12.4)
  // — non frazione. Mostriamo con segno per chiarezza.
  const sign = value > 0 ? '+' : value < 0 ? '' : '';
  return `${sign}${value.toFixed(2)}%`;
}

function buildVerdictSentence(
  ticker: string,
  edge: BacktestTimingEdge,
  ep024Avg: number | null,
  viOnlyAvg: number | null,
): string {
  if (edge.noSignalsInPeriod) {
    return `Nessun momento d'ingresso secondo EP-024 in questo periodo su ${ticker}: il confronto con le baseline (VI puro e buy & hold) è visibile sotto.`;
  }
  switch (edge.label) {
    case 'POSITIVE_EDGE':
      return `Su ${ticker}, entrare nel momento giusto (EP-024) ha reso in media ${formatSignedPct(
        ep024Avg,
      )} vs ${formatSignedPct(viOnlyAvg)} comprando appena era a sconto.`;
    case 'NEUTRAL':
      return `Su ${ticker}, il timing non ha cambiato molto l'esito (EP-024 ${formatSignedPct(
        ep024Avg,
      )} vs solo sconto ${formatSignedPct(viOnlyAvg)}).`;
    case 'NEGATIVE_EDGE':
      return `Attenzione: su ${ticker} il timing ha reso meno (${formatSignedPct(
        ep024Avg,
      )}) del semplice comprare a sconto (${formatSignedPct(viOnlyAvg)}).`;
  }
}

export function BacktestVerdictHero(
  props: BacktestVerdictHeroProps,
): React.ReactElement {
  const { ticker, timingEdge, strategies } = props;
  const presentation = PRESENTATIONS[timingEdge.label];

  const ep024 = strategies.find((s) => s.strategy === 'EP024_ENTER_NOW');
  const viOnly = strategies.find((s) => s.strategy === 'VI_ONLY');

  const sentence = buildVerdictSentence(
    ticker,
    timingEdge,
    ep024?.avgReturnPct ?? null,
    viOnly?.avgReturnPct ?? null,
  );

  const ariaLabel = `Verdetto backtest ${ticker}: ${presentation.label}. ${sentence}`;

  return (
    <Card
      data-testid="backtest-verdict-hero"
      data-edge={timingEdge.label}
      className={cn(presentation.cardClassName)}
    >
      <CardHeader>
        <CardTitle as="h3">Verdetto verifica storica</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <span
          data-testid="backtest-verdict-badge"
          role="status"
          aria-label={ariaLabel}
          className={cn(
            'inline-flex w-fit items-center gap-2 rounded-full border ' +
              'px-4 py-1.5 text-sm font-bold tracking-wide',
            presentation.badgeClassName,
          )}
        >
          {presentation.icon}
          {presentation.label}
        </span>
        <p
          data-testid="backtest-verdict-sentence"
          className="text-sm text-on-surface/90"
        >
          {sentence}
        </p>
        {timingEdge.timingEdgePct !== null ? (
          <p className="text-xs text-on-surface/60">
            Edge (EP024 − VI puro):{' '}
            <span
              data-testid="backtest-verdict-edge-pp"
              className="font-medium tabular-nums text-on-surface"
            >
              {formatSignedPct(timingEdge.timingEdgePct)}
            </span>
            <span className="ml-1">
              (soglia POSITIVE/NEGATIVE = ±2pp).
            </span>
          </p>
        ) : (
          <p className="text-xs text-on-surface/60 inline-flex items-center gap-1">
            <Info aria-hidden="true" className="h-3.5 w-3.5" />
            Edge non calcolabile: una delle strategie non ha trade nel periodo.
          </p>
        )}
      </CardContent>
    </Card>
  );
}
