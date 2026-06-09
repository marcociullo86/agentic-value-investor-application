'use client';

import {
  CheckCircle,
  AlertCircle,
  XCircle,
  Hourglass,
  HelpCircle,
} from 'lucide-react';
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from '@/components/ui/Card';
import { cn } from '@/lib/utils/cn';
import type {
  EntryTimingAdvisor,
  EntryTimingVerdict,
} from '@/lib/api/technical';

/**
 * EntryTimingVerdictCard — TSK-334 (US-101, EP-024 Fase 1).
 *
 * Card del verdetto entry-timing del Triple-Screen Elder (US-099):
 *  - Badge colorato + icona + testo (a11y: colore NON unico canale).
 *  - 5 stati: ENTRY_FAVORABLE / ENTRY_NEUTRAL / ENTRY_UNFAVORABLE / WAIT /
 *    INDETERMINATE.
 *  - Tre righe Screen 1/2/3 dal `rationale` strutturato (Elder §39):
 *    Screen 1 trend di lungo, Screen 2 oscillatore (RSI + MACD daily),
 *    Screen 3 livello d'entry (support/resistance).
 *  - Re-entry condition (verdict === WAIT) gestita in
 *    `ReentryConditionBanner` separato per chiarezza visiva.
 *
 * UX governance — coerente con MrMarketSentimentBadge / LongTermTrendBadge:
 *  - Palette DISTINTA dai 13 ruleSignals: NO bg-signal-green/red. Tonalità
 *    blu/giallo/verde-tenue/rosso-tenue → l'utente NON deve confondere il
 *    verdetto TA (advisory di timing) con il verdetto VI (fondamentale).
 *  - `viGate` non viene mostrato come testo — è disclaimer machine-readable,
 *    il banner globale `TechnicalAnalysisDisclaimer` copre la responsabilità
 *    di comunicare il principio "advisory" all'utente.
 *
 * Accessibility (WCAG 2.2 AA — EP-016, AC US-101):
 *  - `role="status"` sul badge: stato non interattivo che cambia.
 *  - `aria-label` completo include verdetto + nota Screen 1+2+3.
 *  - Icona `aria-hidden` (testo presente come label).
 *  - Contrasto verificato shade Tailwind 100/300/900 su sfondo bianco.
 *
 * Sorgenti contratto + spec:
 *  - OpenAPI §schemas/EntryTimingAdvisor + EntryTimingVerdict (US-099 / TSK-332)
 *  - US-101 §Layout 2 (Verdetto entry-timing)
 *  - [[elder-triple-screen-impulse-system]] §"Triple Screen" (Elder §39)
 */

interface VerdictPresentation {
  readonly cardClassName: string;
  readonly badgeClassName: string;
  readonly icon: React.ReactNode;
  readonly label: string;
  readonly summary: string;
}

const PRESENTATIONS: Readonly<Record<EntryTimingVerdict, VerdictPresentation>> = {
  ENTRY_FAVORABLE: {
    cardClassName: 'border-l-4 border-green-500',
    badgeClassName:
      'bg-green-100 text-green-900 border-green-300 dark:bg-green-950 dark:text-green-200 dark:border-green-800',
    icon: <CheckCircle aria-hidden="true" className="h-4 w-4" />,
    label: 'ENTRY FAVOREVOLE',
    summary:
      'Setup tecnico allineato al verdetto fondamentale. Timing d’ingresso ragionevole.',
  },
  ENTRY_NEUTRAL: {
    cardClassName: 'border-l-4 border-amber-400',
    badgeClassName:
      'bg-amber-100 text-amber-900 border-amber-300 dark:bg-amber-950 dark:text-amber-100 dark:border-amber-800',
    icon: <AlertCircle aria-hidden="true" className="h-4 w-4" />,
    label: 'ENTRY NEUTRO',
    summary:
      'Setup tecnico misto: nessun forte vento contrario, ma neppure conferma chiara.',
  },
  ENTRY_UNFAVORABLE: {
    cardClassName: 'border-l-4 border-red-500',
    badgeClassName:
      'bg-red-100 text-red-900 border-red-300 dark:bg-red-950 dark:text-red-200 dark:border-red-800',
    icon: <XCircle aria-hidden="true" className="h-4 w-4" />,
    label: 'ENTRY SFAVOREVOLE',
    summary:
      'Setup tecnico contrario: alto rischio di drawdown immediato anche su un titolo VI-positivo.',
  },
  WAIT: {
    cardClassName: 'border-l-4 border-blue-500',
    badgeClassName:
      'bg-blue-100 text-blue-900 border-blue-300 dark:bg-blue-950 dark:text-blue-200 dark:border-blue-800',
    icon: <Hourglass aria-hidden="true" className="h-4 w-4" />,
    label: 'ASPETTA',
    summary:
      'Verdetto VI positivo, ma il setup tecnico non è ancora pronto. Vedi la condizione di re-entry sotto.',
  },
  INDETERMINATE: {
    cardClassName: 'border-l-4 border-slate-300 dark:border-slate-700',
    badgeClassName:
      'bg-slate-100 text-slate-700 border-slate-300 dark:bg-slate-900 dark:text-slate-300 dark:border-slate-700',
    icon: <HelpCircle aria-hidden="true" className="h-4 w-4" />,
    label: 'INDETERMINATO',
    summary:
      'Dati insufficienti per esprimere un verdetto di timing (storico EOD troppo corto o indicatori mancanti).',
  },
};

export interface EntryTimingVerdictCardProps {
  readonly advisor: EntryTimingAdvisor;
}

export function EntryTimingVerdictCard(
  props: EntryTimingVerdictCardProps,
): React.ReactElement {
  const { advisor } = props;
  const presentation = PRESENTATIONS[advisor.verdict];
  const { rationale } = advisor;

  const ariaLabel =
    `Verdetto entry-timing: ${presentation.label}. ` +
    `Screen 1: ${rationale.screen1}. ` +
    `Screen 2: ${rationale.screen2}. ` +
    `Screen 3: ${rationale.screen3}.`;

  return (
    <Card
      data-testid="ta-entry-timing-card"
      data-verdict={advisor.verdict}
      className={cn(presentation.cardClassName)}
    >
      <CardHeader>
        <CardTitle as="h2">Verdetto entry-timing</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <div className="flex flex-wrap items-center gap-3">
          <span
            data-testid="ta-entry-timing-badge"
            role="status"
            aria-label={ariaLabel}
            className={cn(
              'inline-flex w-fit items-center gap-1.5 rounded-full border px-3 py-1 text-sm font-semibold',
              presentation.badgeClassName,
            )}
          >
            {presentation.icon}
            {presentation.label}
          </span>
        </div>
        <p className="text-sm text-on-surface/80">{presentation.summary}</p>
        <dl
          data-testid="ta-entry-timing-screens"
          className="grid gap-3 sm:grid-cols-3"
        >
          <ScreenRow
            label="Screen 1 — Trend di lungo"
            value={rationale.screen1}
            testId="ta-screen-1"
          />
          <ScreenRow
            label="Screen 2 — Oscillatore"
            value={rationale.screen2}
            testId="ta-screen-2"
          />
          <ScreenRow
            label="Screen 3 — Livello d'entry"
            value={rationale.screen3}
            testId="ta-screen-3"
          />
        </dl>
      </CardContent>
    </Card>
  );
}

function ScreenRow({
  label,
  value,
  testId,
}: {
  readonly label: string;
  readonly value: string;
  readonly testId: string;
}): React.ReactElement {
  return (
    <div className="rounded-md border border-outline-variant bg-surface-container-high p-3">
      <dt className="text-xs font-semibold uppercase tracking-wide text-on-surface/60">
        {label}
      </dt>
      <dd
        data-testid={testId}
        className="mt-1 text-sm text-on-surface"
      >
        {value}
      </dd>
    </div>
  );
}
