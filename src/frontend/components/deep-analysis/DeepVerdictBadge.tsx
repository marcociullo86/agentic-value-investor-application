'use client';

import { CheckCircle, AlertTriangle, XCircle, ShieldAlert } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { LlmBudgetBar } from './LlmBudgetBar';
import type {
  DeepAnalysisResponse,
  VerdictClass,
} from '@/lib/api/deep-analysis';

export interface DeepVerdictBadgeProps {
  readonly data: DeepAnalysisResponse;
  readonly isValidating: boolean;
  readonly isFrozenByAdmin: boolean;
  readonly onInvokeLlm: () => Promise<DeepAnalysisResponse | undefined>;
}

interface VerdictPresentation {
  readonly colorClasses: string;
  readonly icon: React.ReactNode;
  readonly ariaLabel: string;
}

const VERDICT_MAP: Readonly<Record<VerdictClass, VerdictPresentation>> = {
  APPROVATO_PANIC_BUY: {
    colorClasses:
      'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200',
    icon: <CheckCircle className="h-4 w-4" aria-hidden="true" />,
    ariaLabel: 'Approvato Panic Buy',
  },
  APPROVATO: {
    colorClasses:
      'bg-green-50 text-green-700 dark:bg-green-950 dark:text-green-300',
    icon: <CheckCircle className="h-4 w-4" aria-hidden="true" />,
    ariaLabel: 'Approvato',
  },
  WATCHLIST: {
    colorClasses:
      'bg-amber-50 text-amber-700 dark:bg-amber-950 dark:text-amber-300',
    icon: <AlertTriangle className="h-4 w-4" aria-hidden="true" />,
    ariaLabel: 'Watchlist',
  },
  BOCCIATO_NUMERICO: {
    colorClasses: 'bg-red-50 text-red-700 dark:bg-red-950 dark:text-red-300',
    icon: <XCircle className="h-4 w-4" aria-hidden="true" />,
    ariaLabel: 'Bocciato Numerico',
  },
  BOCCIATO_QUALITATIVO: {
    colorClasses:
      'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200',
    icon: <ShieldAlert className="h-4 w-4" aria-hidden="true" />,
    ariaLabel: 'Bocciato Qualitativo',
  },
  BOCCIATO_VALUE_TRAP: {
    colorClasses:
      'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200',
    icon: <ShieldAlert className="h-4 w-4" aria-hidden="true" />,
    ariaLabel: 'Bocciato Value Trap',
  },
};

const LLM_TOOLTIP =
  'Lo step LLM analizza in profondità 10-K e 10-Q via Claude Opus 4.7. ' +
  'Costo a tuo carico (budget condiviso, gestito dall\u2019amministratore). ' +
  'Risultato salvato in cache per il mese corrente.';

export function DeepVerdictBadge({
  data,
  isValidating,
  isFrozenByAdmin,
  onInvokeLlm,
}: DeepVerdictBadgeProps): React.ReactElement {
  const llmAvailable = data.llmStatus === 'NOT_INVOKED';
  const llmCached = data.llmStatus === 'CACHE_HIT';
  const classe = data.verdict.verdettoClasse;
  const presentation = VERDICT_MAP[classe];
  const label = classe.replaceAll('_', ' ');

  const costEstimate = data.llmCostEstimateUsd;
  const costLabel =
    costEstimate != null ? ` \u2248 $${costEstimate.toFixed(2)}` : '';

  return (
    <Card data-testid="deep-verdict-section">
      <CardHeader>
        <CardTitle>Verdetto</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <div className="flex items-center gap-3">
          <span
            className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-sm font-semibold ${presentation.colorClasses}`}
            role="status"
            aria-label={`Verdetto: ${presentation.ariaLabel}${data.verdict.partialBasis ? ' (parziale)' : ''}`}
            data-testid="verdict-badge"
          >
            {presentation.icon}
            {label}
            {data.verdict.partialBasis ? (
              <span className="text-xs font-normal opacity-75">(parziale)</span>
            ) : null}
          </span>
          {data.positionSize !== null ? (
            <span
              className="text-sm text-slate-600 dark:text-slate-400"
              data-testid="position-size-label"
            >
              Position size: {data.positionSize.recommendedPct.toFixed(1)}% (
              {data.positionSize.rangeLow.toFixed(1)}–
              {data.positionSize.rangeHigh.toFixed(1)}%)
            </span>
          ) : null}
        </div>
        <p className="text-sm text-slate-700 dark:text-slate-300">
          {data.verdict.motivazioneAggregata}
        </p>
        {data.verdict.partialBasis ? (
          <p className="text-xs font-medium text-amber-700 dark:text-amber-400">
            VERDETTO PARZIALE — completa con analisi LLM
          </p>
        ) : null}

        {isFrozenByAdmin ? (
          <Button
            variant="primary"
            size="md"
            disabled
            data-testid="invoke-llm-button"
            aria-disabled="true"
          >
            Analisi LLM temporaneamente disabilitata dall&apos;admin
          </Button>
        ) : llmCached ? (
          <Button
            variant="primary"
            size="md"
            disabled={isValidating}
            onClick={() => void onInvokeLlm()}
            data-testid="invoke-llm-button"
          >
            {isValidating ? 'Caricamento…' : 'Mostra analisi precedente'}
          </Button>
        ) : llmAvailable ? (
          <div className="flex flex-col gap-2">
            <Button
              variant="primary"
              size="md"
              disabled={isValidating}
              onClick={() => void onInvokeLlm()}
              title={LLM_TOOLTIP}
              data-testid="invoke-llm-button"
            >
              {isValidating
                ? 'Analisi in corso…'
                : `Avvia analisi LLM${costLabel}`}
            </Button>
            <LlmBudgetBar />
          </div>
        ) : null}

        {data.llmStatus === 'INVOKED' && !isFrozenByAdmin ? (
          <span
            className="text-xs text-green-700 dark:text-green-400"
            data-testid="llm-invoked-signal"
          >
            Analisi LLM completata
          </span>
        ) : null}
      </CardContent>
    </Card>
  );
}
