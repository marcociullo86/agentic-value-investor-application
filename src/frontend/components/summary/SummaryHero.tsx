'use client';

import {
  CheckCircle,
  Hourglass,
  XCircle,
  Info,
} from 'lucide-react';
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from '@/components/ui/Card';
import { cn } from '@/lib/utils/cn';
import type { SummaryVerdict, ViVerdict, DeepVerdict } from '@/lib/api/summary';
import type { ReentryCondition } from '@/lib/api/technical';

/**
 * SummaryHero — TSK-342 (US-104, EP-024 Fase 2).
 *
 * Hero verdetto del tab "Riepilogo" — primo tab + default di landing del
 * dettaglio ticker. Copre i 4 stati di `SummaryVerdict` (US-103):
 *
 *  - ENTER_NOW          → "ENTRA ORA"        verde + icona check
 *  - WAIT_FOR_SETUP     → "ASPETTA"          blu + icona timer + reentryCondition
 *  - AVOID              → "EVITA"            rosso + icona stop + gate bocciante
 *  - INSUFFICIENT_DATA  → "DATI INSUFFICIENTI" grigio + icona info
 *
 * Il verdetto arriva GIÀ deciso dal BE (gate VI primario hardcoded Kotlin,
 * ADR-030 §3+§5). Il FE NON ricalcola — solo renderizza.
 *
 * Accessibility (WCAG 2.2 AA — EP-016, AC US-104):
 *  - Colore NON è l'unico canale: testo + icona + tono di colore.
 *  - `role="status"` sul badge: stato non interattivo che cambia.
 *  - `aria-label` completo include verdetto + sub-headline esplicativa.
 *  - Icone `aria-hidden="true"` (testo già presente come label).
 *  - Tutte le palette superano il contrasto 4.5:1 (shade 100/300/900
 *    su sfondo light + dark mode con 950/200/800).
 *
 * Sorgenti contratto + spec:
 *  - OpenAPI §schemas/SummaryVerdictResponse + SummaryVerdict (US-103)
 *  - US-104 §"Layout" 1 (Hero verdetto)
 *  - ADR-030 §4 (presentazione verdetto)
 *  - [[ta-vs-vi-decision-layer]] §"Sintesi: la regola delle due domande"
 */

interface VerdictPresentation {
  readonly cardClassName: string;
  readonly badgeClassName: string;
  readonly icon: React.ReactNode;
  readonly label: string;
}

const PRESENTATIONS: Readonly<Record<SummaryVerdict, VerdictPresentation>> = {
  ENTER_NOW: {
    cardClassName: 'border-l-4 border-green-500',
    badgeClassName:
      'bg-green-100 text-green-900 border-green-300 ' +
      'dark:bg-green-950 dark:text-green-200 dark:border-green-800',
    icon: <CheckCircle aria-hidden="true" className="h-6 w-6" />,
    label: 'ENTRA ORA',
  },
  WAIT_FOR_SETUP: {
    cardClassName: 'border-l-4 border-blue-500',
    badgeClassName:
      'bg-blue-100 text-blue-900 border-blue-300 ' +
      'dark:bg-blue-950 dark:text-blue-200 dark:border-blue-800',
    icon: <Hourglass aria-hidden="true" className="h-6 w-6" />,
    label: 'ASPETTA',
  },
  AVOID: {
    cardClassName: 'border-l-4 border-red-500',
    badgeClassName:
      'bg-red-100 text-red-900 border-red-300 ' +
      'dark:bg-red-950 dark:text-red-200 dark:border-red-800',
    icon: <XCircle aria-hidden="true" className="h-6 w-6" />,
    label: 'EVITA',
  },
  INSUFFICIENT_DATA: {
    cardClassName: 'border-l-4 border-slate-300 dark:border-slate-700',
    badgeClassName:
      'bg-slate-100 text-slate-800 border-slate-300 ' +
      'dark:bg-slate-900 dark:text-slate-200 dark:border-slate-700',
    icon: <Info aria-hidden="true" className="h-6 w-6" />,
    label: 'DATI INSUFFICIENTI',
  },
};

/**
 * Sub-headline esplicativa per ciascuno dei 4 verdetti. Dipende dal verdetto
 * + (per AVOID) dal gate bocciante (VI o Munger RISCHIO_ESTREMO) +
 * (per WAIT_FOR_SETUP) dalla `reentryCondition.description`.
 *
 * NOTA: la sub-headline è generata FE-side (deterministica) — non viene
 * dal BE. Il `rationale.viSummary/deepSummary/taSummary` del BE è il
 * contenuto narrativo delle 3 card (TSK-343), NON dell'hero.
 */
function subHeadline(
  verdict: SummaryVerdict,
  viVerdict: ViVerdict,
  deepVerdict: DeepVerdict | null,
  reentryCondition: ReentryCondition | null,
): string {
  switch (verdict) {
    case 'ENTER_NOW':
      return 'Verdetto fondamentale e timing tecnico favorevoli. ' +
        'Il gate VI è passato e il setup tecnico non oppone resistenza.';
    case 'WAIT_FOR_SETUP': {
      const base =
        'Verdetto fondamentale positivo, ma il timing tecnico non è ancora ' +
        'pronto. Attendere il setup tecnico migliore per evitare uno stop ' +
        'loss prematuro su tesi VI corretta.';
      if (reentryCondition !== null) {
        return `${base} Re-valuta quando: ${reentryCondition.description}.`;
      }
      return base;
    }
    case 'AVOID': {
      if (deepVerdict === 'RISCHIO_ESTREMO') {
        return 'La Deep Analysis (Munger) ha rilevato RISCHIO ESTREMO: ' +
          'il gate Deep ha la priorità assoluta sul resto e blocca l\'entry.';
      }
      if (viVerdict === 'RED_DOMINANT') {
        return 'Il gate Value Investing è fallito: i fondamentali non ' +
          'supportano una tesi di acquisto. Nessuna conferma tecnica può ' +
          'ribaltare un verdetto VI negativo.';
      }
      return 'Il gate primario è stato bocciato. Vedi i fattori chiave ' +
        'sotto per il dettaglio.';
    }
    case 'INSUFFICIENT_DATA':
      return 'Dati fondamentali troppo lacunosi per un verdetto affidabile ' +
        '(troppi ruleId indeterminati o non calcolabili). Attendi che il ' +
        'BE indicizzi più filing o aggiorni il bilancio.';
  }
}

export interface SummaryHeroProps {
  readonly ticker: string;
  readonly verdict: SummaryVerdict;
  readonly viVerdict: ViVerdict;
  readonly deepVerdict: DeepVerdict | null;
  readonly reentryCondition: ReentryCondition | null;
  /** ISO-8601 — istante valutazione lato BE (per timestamp visibile). */
  readonly evaluatedAt: string;
}

export function SummaryHero(props: SummaryHeroProps): React.ReactElement {
  const {
    ticker,
    verdict,
    viVerdict,
    deepVerdict,
    reentryCondition,
    evaluatedAt,
  } = props;
  const presentation = PRESENTATIONS[verdict];
  const sub = subHeadline(verdict, viVerdict, deepVerdict, reentryCondition);
  const ariaLabel = `Verdetto Riepilogo per ${ticker}: ${presentation.label}. ${sub}`;

  return (
    <Card
      data-testid="summary-hero"
      data-verdict={verdict}
      className={cn(presentation.cardClassName)}
    >
      <CardHeader>
        <CardTitle as="h2">Verdetto Riepilogo</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <div className="flex flex-wrap items-center gap-3">
          <span
            data-testid="summary-hero-badge"
            role="status"
            aria-label={ariaLabel}
            className={cn(
              'inline-flex w-fit items-center gap-2 rounded-full border ' +
                'px-4 py-1.5 text-base font-bold tracking-wide',
              presentation.badgeClassName,
            )}
          >
            {presentation.icon}
            {presentation.label}
          </span>
          <span className="text-sm text-on-surface/60">
            Valutato il {new Date(evaluatedAt).toLocaleString('it-IT')}
          </span>
        </div>
        <p
          data-testid="summary-hero-subheadline"
          className="text-sm text-on-surface/80"
        >
          {sub}
        </p>
      </CardContent>
    </Card>
  );
}
