'use client';

import { useCallback, useEffect, useState } from 'react';
import type { DcfMethod, DcfMethodSource } from '@/lib/api/analysis';
import {
  deleteDcfOverride,
  getDcfOverride,
  parseDcfFeasibilityProblem,
  upsertDcfOverride,
  type DcfOverride,
} from '@/lib/api/dcf-overrides';
import { useAuthStore } from '@/lib/stores/useAuthStore';
import { cn } from '@/lib/utils/cn';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';

export interface DcfOverridePanelProps {
  readonly ticker: string;
  readonly dcfMethodSource: DcfMethodSource;
  readonly onAnalysisRefresh: () => void;
}

const METHOD_OPTIONS: ReadonlyArray<{ value: DcfMethod; label: string }> = [
  { value: 'GREENWALD', label: 'Greenwald EPV' },
  { value: 'FCF_FALLBACK', label: 'FCF Fallback' },
];

function sourceBadgeLabel(source: DcfMethodSource): string {
  return source === 'USER_OVERRIDE' ? 'Tuo override' : 'Default policy';
}

export function DcfOverridePanel(props: DcfOverridePanelProps): React.ReactElement | null {
  const { ticker, dcfMethodSource, onAnalysisRefresh } = props;
  const normalized = ticker.trim().toUpperCase();
  const accessToken = useAuthStore((s) => s.accessToken);

  const [currentOverride, setCurrentOverride] = useState<DcfOverride | null>(null);
  const [selectedMethod, setSelectedMethod] = useState<DcfMethod>('GREENWALD');
  const [loadingOverride, setLoadingOverride] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [inlineError, setInlineError] = useState<string | null>(null);

  const loadOverride = useCallback(async (): Promise<void> => {
    if (!accessToken) {
      return;
    }
    setLoadingOverride(true);
    try {
      const existing = await getDcfOverride(normalized);
      setCurrentOverride(existing);
      if (existing?.forcedMethod === 'GREENWALD' || existing?.forcedMethod === 'FCF_FALLBACK') {
        setSelectedMethod(existing.forcedMethod);
      }
    } finally {
      setLoadingOverride(false);
    }
  }, [accessToken, normalized]);

  useEffect(() => {
    void loadOverride();
  }, [loadOverride]);

  if (!accessToken) {
    return null;
  }

  const handleApply = async (): Promise<void> => {
    setSubmitting(true);
    setInlineError(null);
    try {
      const saved = await upsertDcfOverride({
        ticker: normalized,
        forcedMethod: selectedMethod,
      });
      setCurrentOverride(saved);
      onAnalysisRefresh();
    } catch (err: unknown) {
      const problem = parseDcfFeasibilityProblem(err);
      if (problem) {
        const years =
          problem.availableYears != null && problem.requiredYears != null
            ? ` (${problem.availableYears}/${problem.requiredYears} anni)`
            : '';
        setInlineError(`${problem.detail ?? 'Metodo non applicabile'}${years}`);
      } else {
        setInlineError('Impossibile salvare l\'override. Riprova.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleRemove = async (): Promise<void> => {
    setSubmitting(true);
    setInlineError(null);
    try {
      await deleteDcfOverride(normalized);
      setCurrentOverride(null);
      onAnalysisRefresh();
    } catch {
      setInlineError('Impossibile rimuovere l\'override. Riprova.');
    } finally {
      setSubmitting(false);
    }
  };

  const accent = dcfMethodSource === 'USER_OVERRIDE';

  return (
    <Card data-testid="dcf-override-panel" className="w-full">
      <CardHeader className="flex flex-row flex-wrap items-center justify-between gap-2">
        <CardTitle as="h2">Override metodo DCF</CardTitle>
        <span
          data-testid="dcf-method-source-badge"
          className={cn(
            'inline-flex rounded px-2 py-0.5 text-xs font-medium',
            accent
              ? 'bg-indigo-100 text-indigo-800 dark:bg-indigo-900 dark:text-indigo-200'
              : 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300',
          )}
        >
          {sourceBadgeLabel(dcfMethodSource)}
        </span>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {loadingOverride ? (
          <p className="text-sm text-slate-500" data-testid="dcf-override-loading">
            Caricamento override…
          </p>
        ) : null}

        <div className="flex flex-wrap items-end gap-3">
          <label className="flex flex-col gap-1 text-sm">
            <span className="font-medium text-slate-700 dark:text-slate-300">Metodo</span>
            <select
              data-testid="dcf-override-method-select"
              className="rounded-md border border-slate-300 bg-white px-3 py-2 text-sm dark:border-slate-600 dark:bg-slate-900"
              value={selectedMethod}
              disabled={submitting}
              onChange={(e) => setSelectedMethod(e.target.value as DcfMethod)}
            >
              {METHOD_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
          </label>
          <Button
            type="button"
            data-testid="dcf-override-apply"
            disabled={submitting}
            onClick={() => void handleApply()}
          >
            Imposta
          </Button>
          {currentOverride !== null ? (
            <Button
              type="button"
              variant="secondary"
              data-testid="dcf-override-remove"
              disabled={submitting}
              onClick={() => void handleRemove()}
            >
              Rimuovi
            </Button>
          ) : null}
        </div>

        {inlineError !== null ? (
          <p
            data-testid="dcf-override-inline-error"
            role="alert"
            className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-100"
          >
            {inlineError}
          </p>
        ) : null}
      </CardContent>
    </Card>
  );
}
