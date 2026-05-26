'use client';

import { TrendingDown, TrendingUp, Minus, HelpCircle } from 'lucide-react';
import type {
  LongTermTrendFlag,
  LongTermTrendSignal,
} from '@/lib/api/analysis';
import { cn } from '@/lib/utils/cn';

/**
 * LongTermTrendBadge — TSK-169 (US-057, EP-013).
 *
 * Advisory badge che renderizza il flag `contextFlags.longTermTrend` del
 * `RuleEngineResult`. Indica la posizione del prezzo corrente rispetto
 * alla SMA200 (Simple Moving Average 200-day) → contesto "deep value
 * pessimismo" vs "euforia".
 *
 * Important (UX governance):
 *  - **Palette neutra**: icone colorate (lucide TrendingDown/Up/Minus) +
 *    badge mostly white/slate. NESSUN conflitto con `TrafficLightPanel`
 *    (green/red) né con `MrMarketSentimentBadge` (blu/giallo per RSI):
 *    il segnale visivo primario è l'ICONA, non il fill del badge.
 *  - Soglie BE asimmetriche by design: `-0.05` / `+0.20` (US-057 §Codifica
 *    flag) — i mercati salgono over time, evita falsi positivi.
 *  - `INDETERMINATE` per IPO < 200gg / serie vuota / sma ≤ 0 → render
 *    tenue ("Trend lungo periodo non disponibile").
 *
 * Sorgente contratto:
 *  - OpenAPI §schemas/LongTermTrendFlag (BE TSK-166).
 *  - US-057.md §Business Rules.
 *  - EP-013.md §Obiettivo.
 *
 * Accessibilità (WCAG AA — AC US-057):
 *  - `aria-label` con stato + pct esplicito + disclaimer advisory.
 *  - Icone lucide `aria-hidden="true"` (testo già descrittivo); WCAG
 *    1.1.1 "Non-text Content" soddisfatto.
 *  - `role="img"` per coerenza con MrMarketSentimentBadge.
 *  - `title` = tooltip nativo HTML5.
 *
 * Format pct: `(priceVsSmaPct * 100).toFixed(1)` — es. `-0.20` → `-20.0`.
 * Il segno è già nel value (`-` negative, `+` per ABOVE_TREND aggiunto in
 * label-template per chiarezza, NEAR_TREND può oscillare e usa il segno
 * naturale del number).
 */

export interface LongTermTrendBadgeProps {
  /**
   * L'intera struttura `LongTermTrendFlag` (NO unpacking lato parent).
   * `null` ammesso per response BE pre-EP-013 / failure-tolerant.
   */
  readonly flag: LongTermTrendFlag | null;
}

interface SignalPresentation {
  /** Tailwind utility classes per badge container. */
  readonly className: string;
  /** Tailwind utility classes per icona (color). */
  readonly iconClassName: string;
  /** Lucide icon component. */
  readonly Icon: React.ComponentType<{
    readonly className?: string;
    readonly 'aria-hidden'?: boolean | 'true' | 'false';
  }>;
  /**
   * Label template — riceve la percentuale formattata 1-decimale
   * (preserva il segno naturale; per ABOVE_TREND prependiamo `+`).
   */
  readonly labelTemplate: (pctFormatted: string) => string;
  /** Tooltip specifico per il signal (può differire dal default). */
  readonly tooltip: string;
}

const TOOLTIP_DEFAULT =
  'SMA200 = media mobile a 200 giorni. Advisory tecnico, non sostituisce il giudizio fondamentale.';

const FALLBACK_TEXT = 'Trend lungo periodo non disponibile';
const FALLBACK_CLASSNAME =
  'bg-slate-50 text-slate-400 border-slate-200';

const PRESENTATIONS: Readonly<
  Record<Exclude<LongTermTrendSignal, 'INDETERMINATE'>, SignalPresentation>
> = {
  BELOW_TREND: {
    className: 'bg-white text-slate-800 border-slate-300',
    iconClassName: 'text-blue-600',
    Icon: TrendingDown,
    labelTemplate: (pct) => `Sotto SMA200 (${pct}%)`,
    tooltip: 'Mr. Market depresso — deep value potential. ' + TOOLTIP_DEFAULT,
  },
  NEAR_TREND: {
    className: 'bg-white text-slate-800 border-slate-300',
    iconClassName: 'text-slate-500',
    Icon: Minus,
    labelTemplate: (pct) => `In linea con SMA200 (${pct}%)`,
    tooltip: TOOLTIP_DEFAULT,
  },
  ABOVE_TREND: {
    className: 'bg-white text-slate-800 border-slate-300',
    iconClassName: 'text-amber-600',
    Icon: TrendingUp,
    labelTemplate: (pct) => `Sopra SMA200 (+${pct}%)`,
    tooltip: 'Cautela: rischio di acquisto in cima. ' + TOOLTIP_DEFAULT,
  },
};

/**
 * Format `priceVsSmaPct` (es. `-0.20` → `"-20.0"`).
 * - Moltiplica per 100 (BE memorizza ratio, UI mostra percentuale).
 * - 1 decimale (`toFixed(1)`).
 * - Per ABOVE_TREND, il template aggiunge `+` esplicito davanti (il
 *   number positivo non porta segno di default).
 * - NEAR_TREND può essere positivo o negativo entro `[-5%, +20%]`: il
 *   template lascia il segno naturale (negativo già con `-`).
 * - `null`/non-finite → `'—'`.
 */
function formatPct(value: number | null): string {
  if (value === null || !Number.isFinite(value)) return '—';
  return (value * 100).toFixed(1);
}

export function LongTermTrendBadge(
  props: LongTermTrendBadgeProps,
): React.ReactElement {
  const { flag } = props;

  if (flag === null || flag.flag === 'INDETERMINATE') {
    return (
      <span
        data-testid="long-term-trend-badge"
        data-signal={flag?.flag ?? 'NULL'}
        role="img"
        aria-label={`${FALLBACK_TEXT}. ${TOOLTIP_DEFAULT}`}
        title={TOOLTIP_DEFAULT}
        className={cn(
          'inline-flex w-fit items-center gap-2 rounded-full border px-3 py-1 text-sm font-medium',
          FALLBACK_CLASSNAME,
        )}
      >
        <HelpCircle aria-hidden="true" className="h-4 w-4" />
        <span data-testid="long-term-trend-badge-text">{FALLBACK_TEXT}</span>
      </span>
    );
  }

  const presentation = PRESENTATIONS[flag.flag];
  const pctFormatted = formatPct(flag.priceVsSmaPct);
  const label = presentation.labelTemplate(pctFormatted);
  const ariaLabel = `${label}. ${presentation.tooltip}`;
  const Icon = presentation.Icon;

  return (
    <span
      data-testid="long-term-trend-badge"
      data-signal={flag.flag}
      role="img"
      aria-label={ariaLabel}
      title={presentation.tooltip}
      className={cn(
        'inline-flex w-fit items-center gap-2 rounded-full border px-3 py-1 text-sm font-medium',
        presentation.className,
      )}
    >
      <Icon
        aria-hidden="true"
        className={cn('h-4 w-4', presentation.iconClassName)}
      />
      <span data-testid="long-term-trend-badge-text">{label}</span>
    </span>
  );
}
