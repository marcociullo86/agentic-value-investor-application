'use client';

import { useEffect, useState } from 'react';
import { AlertTriangle } from 'lucide-react';
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { cn } from '@/lib/utils/cn';
import { formatCurrency, formatPercent } from '@/lib/utils/formatters';
import { TA_EQUITY_DEFAULT, TA_EQUITY_MIN } from '@/lib/hooks/useEquityLocalStorage';
import { RewardRiskBadge } from './RewardRiskBadge';
import type { PositionSizing, RewardRiskRatio } from '@/lib/api/technical';

/**
 * PositionSizingPanel — TSK-335 (US-101, EP-024 Fase 1).
 *
 * Pannello sizing (AC US-101 §Layout 7):
 *  - Input `equity` (number, default 50000 USD, persistito in `localStorage`
 *    via `useEquityLocalStorage`). Coerente con US-100 §"Separazione di
 *    responsabilità": il BE NON persiste l'equity dell'utente, lo accetta
 *    come query param della GET /technical → ricalcolo automatico via la
 *    SWR key inclusiva di `equity` (TSK-333).
 *  - Output 2% Rule: shares raccomandate, valore posizione, % equity,
 *    warning `POSITION_EXCEEDS_EQUITY` se stop molto stretto.
 *  - Output 6% Rule: budget mensile aggregato + disclaimer (Iron Triangle
 *    Elder §51).
 *  - Badge Reward/Risk ratio vs DCF intrinsic value (delegato a
 *    `RewardRiskBadge`).
 *
 * UX dell'input:
 *  - Controlled input locale con debounce 350ms → commit a
 *    `onEquityChange` (evita refetch SWR a ogni tasto).
 *  - Pulsante "Reset" ripristina il default (50000 USD) sia in localStorage
 *    sia nell'input.
 *  - Validazione cliente-side: numeric, >= TA_EQUITY_MIN (0.01).
 *
 * Accessibility (WCAG 2.2 AA — EP-016):
 *  - `<label htmlFor>` esplicito sull'input.
 *  - Help text con `aria-describedby`.
 *  - Warning POSITION_EXCEEDS_EQUITY in `role="alert"` (cambio di stato).
 */

const DEBOUNCE_MS = 350;

export interface PositionSizingPanelProps {
  readonly sizing: PositionSizing;
  readonly rewardRisk: RewardRiskRatio | null;
  readonly equity: number;
  readonly equityHydrated: boolean;
  readonly onEquityChange: (value: number) => void;
  readonly onEquityReset: () => void;
}

export function PositionSizingPanel(
  props: PositionSizingPanelProps,
): React.ReactElement {
  const { sizing, rewardRisk, equity, equityHydrated, onEquityChange, onEquityReset } =
    props;
  const { twoPercentRule, sixPercentRule } = sizing;

  // Buffer locale dell'input per debounce — quando l'utente digita non
  // vogliamo scatenare un refetch SWR a ogni keystroke.
  const [draft, setDraft] = useState<string>(equity.toString());

  // Quando il prop equity cambia (es. reset) sincronizza il draft.
  useEffect(() => {
    setDraft(equity.toString());
  }, [equity]);

  // Debounce commit → onEquityChange.
  useEffect(() => {
    if (!equityHydrated) return;
    const parsed = Number(draft);
    if (!Number.isFinite(parsed) || parsed < TA_EQUITY_MIN) return;
    if (parsed === equity) return;
    const timer = setTimeout(() => onEquityChange(parsed), DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [draft, equity, equityHydrated, onEquityChange]);

  const draftNum = Number(draft);
  const draftInvalid =
    draft.trim() === '' || !Number.isFinite(draftNum) || draftNum < TA_EQUITY_MIN;

  return (
    <Card data-testid="ta-position-sizing-panel">
      <CardHeader>
        <CardTitle as="h2">Position sizing (2% / 6% Rule)</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <EquityInput
          draft={draft}
          onDraftChange={setDraft}
          onReset={onEquityReset}
          invalid={draftInvalid}
        />

        <div
          className="grid gap-3 sm:grid-cols-3"
          data-testid="ta-2pct-section"
        >
          <SizingRow
            label="Max rischio (2%)"
            value={formatCurrency(twoPercentRule.maxRiskAllowed, 'USD')}
            testId="ta-2pct-max-risk"
          />
          <SizingRow
            label="Shares raccomandate"
            value={
              twoPercentRule.sharesRecommended.toLocaleString('it-IT')
            }
            testId="ta-2pct-shares"
          />
          <SizingRow
            label="Valore posizione"
            value={formatCurrency(twoPercentRule.positionValueRecommended, 'USD')}
            sub={`${formatPercent(twoPercentRule.positionPctEquity, 2)} dell'equity`}
            testId="ta-2pct-value"
          />
        </div>

        {twoPercentRule.warning === 'POSITION_EXCEEDS_EQUITY' ? (
          <div
            data-testid="ta-2pct-warning"
            role="alert"
            className="flex items-start gap-3 rounded-md border-l-4 border-amber-500 bg-amber-50 p-3 text-sm dark:bg-amber-950/40"
          >
            <AlertTriangle
              aria-hidden="true"
              className="mt-0.5 h-4 w-4 shrink-0 text-amber-700 dark:text-amber-300"
            />
            <div>
              <p className="font-semibold text-amber-900 dark:text-amber-100">
                Stop molto stretto rispetto all&apos;equity
              </p>
              <p className="mt-1 text-amber-900/90 dark:text-amber-100/90">
                Le shares teoriche del 2% Rule superano il valore della tua
                equity: la size è stata cappata a floor(equity / prezzo).
                Considera di allargare lo stop (ATR-based) o ridurre la
                posizione.
              </p>
            </div>
          </div>
        ) : null}

        <div
          className="rounded-md border border-outline-variant bg-surface-container-high p-3"
          data-testid="ta-6pct-section"
        >
          <p className="text-xs font-semibold uppercase tracking-wide text-on-surface/60">
            6% Rule — heat di portafoglio (Elder §51)
          </p>
          <p className="mt-1 text-sm text-on-surface">
            Budget mensile aggregato:{' '}
            <strong className="tabular-nums">
              {formatCurrency(sixPercentRule.maxAggregateRiskPerMonth, 'USD')}
            </strong>
          </p>
          <p
            data-testid="ta-6pct-disclaimer"
            className="mt-1 text-xs text-on-surface/70"
          >
            {sixPercentRule.disclaimer}
          </p>
        </div>

        {rewardRisk !== null ? (
          <div className="flex flex-col gap-2" data-testid="ta-reward-risk-section">
            <RewardRiskBadge rewardRisk={rewardRisk} />
            <p className="text-xs text-on-surface/70">
              {rewardRisk.rationale}
            </p>
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}

/* ------------------------------------------------------------------ */
/*  Equity input                                                       */
/* ------------------------------------------------------------------ */

function EquityInput({
  draft,
  onDraftChange,
  onReset,
  invalid,
}: {
  readonly draft: string;
  readonly onDraftChange: (v: string) => void;
  readonly onReset: () => void;
  readonly invalid: boolean;
}): React.ReactElement {
  const helpId = 'ta-equity-help';
  return (
    <div className="flex flex-col gap-2 rounded-md border border-outline-variant bg-surface-container-high p-3">
      <label
        htmlFor="ta-equity-input"
        className="text-sm font-medium text-on-surface"
      >
        Equity di riferimento (USD)
      </label>
      <div className="flex flex-wrap items-center gap-2">
        <Input
          id="ta-equity-input"
          data-testid="ta-equity-input"
          type="number"
          inputMode="decimal"
          min={TA_EQUITY_MIN}
          step={100}
          value={draft}
          onChange={(e) => onDraftChange(e.target.value)}
          error={invalid}
          aria-describedby={helpId}
          aria-invalid={invalid || undefined}
          className="max-w-[12rem]"
        />
        <Button
          type="button"
          variant="ghost"
          size="sm"
          onClick={onReset}
          data-testid="ta-equity-reset"
          aria-label={`Ripristina equity al default (${TA_EQUITY_DEFAULT.toLocaleString('it-IT')} USD)`}
        >
          Reset
        </Button>
        {invalid ? (
          <span
            role="alert"
            className="text-xs font-medium text-red-700 dark:text-red-300"
          >
            Inserisci un valore numerico ≥ {TA_EQUITY_MIN}.
          </span>
        ) : null}
      </div>
      <p
        id={helpId}
        className={cn(
          'text-xs text-on-surface/60',
        )}
      >
        Valore persistito in <code>localStorage</code> del browser, mai
        inviato come dato salvato al backend (il BE lo accetta come query
        param e lo riflette nel calcolo, ma non lo conserva).
      </p>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/*  Row helper                                                         */
/* ------------------------------------------------------------------ */

function SizingRow({
  label,
  value,
  sub,
  testId,
}: {
  readonly label: string;
  readonly value: string;
  readonly sub?: string;
  readonly testId: string;
}): React.ReactElement {
  return (
    <div
      data-testid={testId}
      className="flex flex-col gap-1 rounded-md border border-outline-variant bg-surface-container-high p-3"
    >
      <span className="text-xs font-semibold uppercase tracking-wide text-on-surface/60">
        {label}
      </span>
      <span className="text-lg font-semibold tabular-nums text-on-surface">
        {value}
      </span>
      {sub !== undefined ? (
        <span className="text-xs text-on-surface/70">{sub}</span>
      ) : null}
    </div>
  );
}
