'use client';

import { useAuthStore } from '@/lib/stores/useAuthStore';
import { useLlmBudget } from '@/lib/hooks/useLlmBudget';

/**
 * LlmBudgetBar — ADMIN-only budget utilization indicator (TSK-157).
 *
 * Reads from GET /admin/llm-cost via useLlmBudget hook.
 * Rendered conditionally: only when session.user.role === 'ADMIN'.
 * Shows "Budget mensile usato X% — $Y/$Z".
 *
 * [^src: design_&_architecture/decisions/ADR-019-llm-cost-budget-telemetry.md §Appendice 2026-05-25]
 */

export interface LlmBudgetBarProps {
  readonly className?: string;
}

export function LlmBudgetBar({
  className = '',
}: LlmBudgetBarProps): React.ReactElement | null {
  const role = useAuthStore((s) => s.user?.role);
  const { data, isLoading } = useLlmBudget();

  if (role !== 'ADMIN') return null;

  if (isLoading || !data) {
    return (
      <div
        data-testid="llm-budget-bar-loading"
        className={`h-5 w-48 animate-pulse rounded bg-slate-100 dark:bg-slate-800 ${className}`}
      />
    );
  }

  const utilization = data.utilization;
  const barColor =
    utilization >= 100
      ? 'bg-red-500'
      : utilization >= 80
        ? 'bg-amber-500'
        : 'bg-green-500';

  const textColor =
    utilization >= 100
      ? 'text-red-700 dark:text-red-400'
      : utilization >= 80
        ? 'text-amber-700 dark:text-amber-400'
        : 'text-slate-600 dark:text-slate-400';

  return (
    <div
      data-testid="llm-budget-bar"
      className={`flex flex-col gap-1 ${className}`}
      aria-label={`Budget mensile usato ${utilization.toFixed(0)}%`}
    >
      <div className="h-2 w-48 overflow-hidden rounded-full bg-slate-200 dark:bg-slate-700">
        <div
          className={`h-full rounded-full transition-all ${barColor}`}
          style={{ width: `${Math.min(utilization, 100)}%` }}
        />
      </div>
      <span className={`text-xs font-medium ${textColor}`}>
        Budget mensile usato {utilization.toFixed(0)}% — $
        {data.totalCostUsd.toFixed(2)}/${data.monthlyCapUsd.toFixed(2)}
      </span>
    </div>
  );
}
