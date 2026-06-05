'use client';

import { BadgeCheck } from 'lucide-react';
import type { RuleSignal } from '@/lib/api/analysis';
import { cn } from '@/lib/utils/cn';

/**
 * NetNetBadge — TSK-322 (US-097, EP-023, ADR-029 §6).
 *
 * Badge prominente che segnala l'opportunita' "net-net" Graham Cap.15:
 * prezzo corrente < 2/3 x NCAV per azione. Visibile **solo** quando il
 * rule signal `NET_NET_RATIO` ha `signal === 'GREEN'` (criterio Graham
 * soddisfatto). In tutti gli altri casi (RED, INDETERMINATE,
 * NOT_CALCULABLE, signal assente) ritorna `null` — niente rumore visivo.
 *
 * Sorgente contratto:
 *  - ADR-029 §6 (FE badge specs).
 *  - OpenAPI §schemas/RuleSignalNetNetRatio (ruleId enum).
 *  - US-097.md §Business Rules.
 *  - wiki/concepts/net-net-stocks.md §Definizione.
 *
 * UX governance (coerente con MrMarketSentimentBadge / LongTermTrendBadge
 * di EP-013):
 *  - Pattern visivo: pill rounded-full con border + icona lucide + testo.
 *  - Palette VERDE saturata (signal-green token, ADR-023 design system),
 *    coerente con la semantica GREEN del rule signal sottostante. A
 *    differenza dei badge advisory di EP-013 (palette neutra per evitare
 *    confusione coi 13 ruleSignals), qui il badge AMPLIFICA il segnale
 *    GREEN del rule signal NET_NET_RATIO, che e' un verdetto fondamentale
 *    (non advisory) — coerenza palette giustificata.
 *  - Icona: <BadgeCheck> (lucide-react gia' in package.json) — richiama
 *    visivamente "criterio soddisfatto".
 *
 * Accessibilita' (WCAG AA — AC US-097 + ADR-029 §6):
 *  - `aria-label="Criterio Graham Net-Net soddisfatto. <tooltip>"` —
 *    espone stato + spiegazione senza dipendere da hover tooltip (1.4.1
 *    "Use of Color": il colore NON e' l'unico canale informativo, testo
 *    "Net-Net" visibile e aria-label esplicito).
 *  - `role="img"`: il badge e' un'unita' informativa discreta (icona +
 *    testo), non interattiva — coerente con MrMarketSentimentBadge.
 *  - `title` HTML5 nativo per tooltip hover desktop + long-press mobile.
 *  - Icona <BadgeCheck> marcata `aria-hidden="true"` (testo gia' come
 *    label).
 *  - Contrasto: `bg-signal-green text-white` = WCAG AA verificato in
 *    tailwind.config.ts §colors.signal (TSK-030 design system).
 *
 * Render contract:
 *  - signals[] non contiene NET_NET_RATIO → null (forward-compat: cache
 *    response pre-EP-023 / BE non ancora deployato).
 *  - NET_NET_RATIO presente ma signal !== 'GREEN' → null (titolo non
 *    net-net per costruzione).
 *  - NET_NET_RATIO con signal === 'GREEN' → render pill verde.
 */

export interface NetNetBadgeProps {
  /**
   * L'intero array `signals` di `RuleEngineResult` (NO unpacking lato
   * parent). Il componente filtra internamente — caller non deve sapere
   * quale ruleId stiamo cercando.
   */
  readonly signals: ReadonlyArray<RuleSignal>;
}

const TOOLTIP_TEXT =
  'Prezzo inferiore ai 2/3 del Net Current Asset Value — criterio Graham Cap.15.';

const ARIA_LABEL = `Criterio Graham Net-Net soddisfatto. ${TOOLTIP_TEXT}`;

/**
 * Tailwind classes — pattern pill identico a MrMarketSentimentBadge /
 * LongTermTrendBadge per coerenza UX. Palette `bg-signal-green` da
 * design token system (ADR-023 / TSK-030).
 */
const BADGE_CLASSNAME =
  'bg-signal-green text-white border-signal-green';

export function NetNetBadge(props: NetNetBadgeProps): React.ReactElement | null {
  const { signals } = props;

  const netNetSignal = signals.find((s) => s.ruleId === 'NET_NET_RATIO');
  if (!netNetSignal || netNetSignal.signal !== 'GREEN') {
    return null;
  }

  return (
    <span
      data-testid="net-net-badge"
      data-signal={netNetSignal.signal}
      role="img"
      aria-label={ARIA_LABEL}
      title={TOOLTIP_TEXT}
      className={cn(
        'inline-flex w-fit items-center gap-2 rounded-full border px-3 py-1 text-sm font-semibold',
        BADGE_CLASSNAME,
      )}
    >
      <BadgeCheck aria-hidden="true" className="h-4 w-4" />
      <span data-testid="net-net-badge-text">Net-Net</span>
    </span>
  );
}
