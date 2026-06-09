'use client';

import { TrendingUp, AlertTriangle, XCircle, Minus } from 'lucide-react';
import { cn } from '@/lib/utils/cn';
import type { RewardRiskLabel, RewardRiskRatio } from '@/lib/api/technical';

/**
 * RewardRiskBadge — TSK-335 (US-101, EP-024 Fase 1).
 *
 * Badge qualitativo per `RewardRiskRatio` (US-100 §"Reward/Risk vs DCF").
 * Calcolato BE come (upside = dcfIntrinsicValue − currentPrice) / (downside =
 * stopDistance). Etichette:
 *  - EXCELLENT (≥3:1)   verde
 *  - ACCEPTABLE (≥2:1)  verde tenue
 *  - MARGINAL (≥1:1)    giallo
 *  - UNFAVORABLE (<1)   rosso
 *  - NOT_APPLICABLE     grigio (DCF assente, DCF ≤ price, stop non calcolabile)
 *
 * Coerente con [[ta-stop-placement-position-sizing]] §"Principio 2: la
 * distanza dello stop determina la size (2% Rule)" — il reward/risk è il
 * metro che valida se lo sforzo vale (upside) il rischio (downside).
 *
 * Accessibility (WCAG 2.2 AA — EP-016): icona + testo + tono colore.
 */

const LABEL_PRESENTATION: Readonly<
  Record<
    RewardRiskLabel,
    {
      readonly text: string;
      readonly className: string;
      readonly icon: React.ReactNode;
    }
  >
> = {
  EXCELLENT: {
    text: 'Eccellente (≥3:1)',
    className:
      'bg-green-100 text-green-900 border-green-300 dark:bg-green-950 dark:text-green-200 dark:border-green-800',
    icon: <TrendingUp aria-hidden="true" className="h-4 w-4" />,
  },
  ACCEPTABLE: {
    text: 'Accettabile (≥2:1)',
    className:
      'bg-green-50 text-green-800 border-green-200 dark:bg-green-950 dark:text-green-300 dark:border-green-900',
    icon: <TrendingUp aria-hidden="true" className="h-4 w-4" />,
  },
  MARGINAL: {
    text: 'Marginale (≥1:1)',
    className:
      'bg-amber-100 text-amber-900 border-amber-300 dark:bg-amber-950 dark:text-amber-100 dark:border-amber-800',
    icon: <AlertTriangle aria-hidden="true" className="h-4 w-4" />,
  },
  UNFAVORABLE: {
    text: 'Sfavorevole (<1:1)',
    className:
      'bg-red-100 text-red-900 border-red-300 dark:bg-red-950 dark:text-red-200 dark:border-red-800',
    icon: <XCircle aria-hidden="true" className="h-4 w-4" />,
  },
  NOT_APPLICABLE: {
    text: 'Non applicabile',
    className:
      'bg-slate-100 text-slate-700 border-slate-300 dark:bg-slate-900 dark:text-slate-300 dark:border-slate-700',
    icon: <Minus aria-hidden="true" className="h-4 w-4" />,
  },
};

export interface RewardRiskBadgeProps {
  readonly rewardRisk: RewardRiskRatio;
}

export function RewardRiskBadge(
  props: RewardRiskBadgeProps,
): React.ReactElement {
  const { rewardRisk } = props;
  const presentation = LABEL_PRESENTATION[rewardRisk.label];

  const ratioText =
    rewardRisk.value !== null && Number.isFinite(rewardRisk.value)
      ? ` · ratio ${rewardRisk.value.toFixed(2)}`
      : '';

  return (
    <span
      data-testid="ta-reward-risk-badge"
      data-label={rewardRisk.label}
      role="status"
      aria-label={`Reward/Risk: ${presentation.text}${ratioText}. ${rewardRisk.rationale}`}
      title={rewardRisk.rationale}
      className={cn(
        'inline-flex w-fit items-center gap-1.5 rounded-full border px-3 py-1 text-sm font-semibold',
        presentation.className,
      )}
    >
      {presentation.icon}
      Reward/Risk: {presentation.text}
      {ratioText}
    </span>
  );
}
