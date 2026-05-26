'use client';

import { Activity } from 'lucide-react';
import type { MrMarketRsiFlag, MrMarketRsiSignal } from '@/lib/api/analysis';
import { cn } from '@/lib/utils/cn';

/**
 * MrMarketSentimentBadge — TSK-168 (US-056, EP-013).
 *
 * Advisory badge che renderizza il flag `contextFlags.mrMarketRsi` del
 * `RuleEngineResult`. Indica il sentiment di "Mr. Market" (Graham) sul
 * ticker via RSI 14-day Wilder (timeframe `1day`).
 *
 * Importante (UX governance):
 *  - **Palette DISTINTA dai 13 ruleSignals**: NO `bg-signal-green/red`.
 *    I 13 segnali Buffett+Graham + MoS sono il verdetto fondamentale; il
 *    badge RSI è solo advisory tecnico. Palette blu/grigio/giallo per
 *    evitare confusione visiva con il `TrafficLightPanel`.
 *  - `INDETERMINATE` e `flag === null` → render tenue ("Sentiment non
 *    disponibile"): l'utente DEVE sapere che il dato è assente, ma senza
 *    enfasi (no errore, no warning).
 *
 * Sorgente contratto:
 *  - OpenAPI §schemas/MrMarketRsiFlag (BE TSK-165).
 *  - US-056.md §Business Rules (soglie 30/70 Wilder).
 *  - EP-013.md §Obiettivo (advisory, non rule).
 *
 * Accessibilità (WCAG AA — AC US-056):
 *  - `aria-label` completo: stato + valore RSI + disclaimer advisory →
 *    screen reader annuncia tutto senza dipendere da tooltip hover.
 *  - `role="img"` semanticamente corretto: il badge è un'unità informativa
 *    discreta (icona + testo) non un widget interattivo.
 *  - `title` attribute = tooltip nativo HTML5 (hover desktop + long-press
 *    mobile); preferito a Radix Tooltip per evitare overhead — il badge
 *    è non-interattivo e non richiede focus management.
 *  - Icona `<Activity>` lucide marcata `aria-hidden="true"` (testo già
 *    presente come label).
 *  - Contrasto: tutte le combinazioni usano Tailwind shades 100/300/900
 *    su sfondo bianco → contrast ratio ≥ 4.5:1 verificato WCAG AA.
 *
 * Format RSI value: 1 decimale (`toFixed(1)`) — coerente con convenzione
 * trading platform (TradingView/MarketWatch). `null` o NaN → `—`.
 */

export interface MrMarketSentimentBadgeProps {
  /**
   * L'intera struttura `MrMarketRsiFlag` (NO unpacking lato parent) —
   * il componente è auto-contenuto, decisione visiva localizzata.
   * `null` ammesso per response BE pre-EP-013 / failure-tolerant.
   */
  readonly flag: MrMarketRsiFlag | null;
}

interface SignalPresentation {
  /** Tailwind utility classes per badge container (bg + border + text). */
  readonly className: string;
  /** Label localizzata IT (placeholder `{N}` sostituito con RSI value). */
  readonly labelTemplate: (rsiFormatted: string) => string;
}

const TOOLTIP_TEXT =
  'Indicatore tecnico RSI 14-day. Advisory — non sostituisce il giudizio fondamentale dei 13 ruleSignals.';

const FALLBACK_TEXT = 'Sentiment non disponibile';
const FALLBACK_CLASSNAME =
  'bg-slate-50 text-slate-400 border-slate-200';

const PRESENTATIONS: Readonly<
  Record<Exclude<MrMarketRsiSignal, 'INDETERMINATE'>, SignalPresentation>
> = {
  OVERSOLD: {
    className: 'bg-blue-100 text-blue-900 border-blue-300',
    labelTemplate: (n) => `Mr. Market: oversold (RSI ${n})`,
  },
  NEUTRAL: {
    className: 'bg-slate-100 text-slate-700 border-slate-300',
    labelTemplate: (n) => `Mr. Market: neutro (RSI ${n})`,
  },
  OVERBOUGHT: {
    className: 'bg-amber-100 text-amber-900 border-amber-300',
    labelTemplate: (n) => `Mr. Market: overbought (RSI ${n})`,
  },
};

function formatRsi(value: number | null): string {
  if (value === null || !Number.isFinite(value)) return '—';
  return value.toFixed(1);
}

export function MrMarketSentimentBadge(
  props: MrMarketSentimentBadgeProps,
): React.ReactElement {
  const { flag } = props;

  // Caso null o INDETERMINATE: stato tenue unificato.
  if (flag === null || flag.flag === 'INDETERMINATE') {
    return (
      <span
        data-testid="mr-market-sentiment-badge"
        data-signal={flag?.flag ?? 'NULL'}
        role="img"
        aria-label={`${FALLBACK_TEXT}. ${TOOLTIP_TEXT}`}
        title={TOOLTIP_TEXT}
        className={cn(
          'inline-flex w-fit items-center gap-2 rounded-full border px-3 py-1 text-sm font-medium',
          FALLBACK_CLASSNAME,
        )}
      >
        <Activity aria-hidden="true" className="h-4 w-4" />
        <span data-testid="mr-market-sentiment-badge-text">
          {FALLBACK_TEXT}
        </span>
      </span>
    );
  }

  const presentation = PRESENTATIONS[flag.flag];
  const rsiFormatted = formatRsi(flag.rsiLatest);
  const label = presentation.labelTemplate(rsiFormatted);
  const ariaLabel = `${label}. ${TOOLTIP_TEXT}`;

  return (
    <span
      data-testid="mr-market-sentiment-badge"
      data-signal={flag.flag}
      role="img"
      aria-label={ariaLabel}
      title={TOOLTIP_TEXT}
      className={cn(
        'inline-flex w-fit items-center gap-2 rounded-full border px-3 py-1 text-sm font-medium',
        presentation.className,
      )}
    >
      <Activity aria-hidden="true" className="h-4 w-4" />
      <span data-testid="mr-market-sentiment-badge-text">{label}</span>
    </span>
  );
}
