'use client';

import useSWR from 'swr';
import { getLlmCostStatus, type LlmCostStatus } from '@/lib/api/llm-budget';
import { useAuthStore } from '@/lib/stores/useAuthStore';

/**
 * SWR hook for GET /admin/llm-cost (TSK-157 — US-046).
 *
 * Only fetches when the current user has role ADMIN.
 * Exposes a `refresh` function for on-demand revalidation
 * (triggered after the user clicks "Avvia analisi LLM").
 */

export interface UseLlmBudgetResult {
  readonly data: LlmCostStatus | undefined;
  readonly isLoading: boolean;
  readonly refresh: () => Promise<LlmCostStatus | undefined>;
}

export function useLlmBudget(): UseLlmBudgetResult {
  const role = useAuthStore((s) => s.user?.role);
  const isAdmin = role === 'ADMIN';

  const { data, isLoading, mutate } = useSWR<LlmCostStatus>(
    isAdmin ? '/admin/llm-cost' : null,
    async () => {
      const res = await getLlmCostStatus();
      return res.data;
    },
    {
      revalidateOnFocus: false,
      revalidateOnReconnect: false,
      dedupingInterval: 30_000,
    },
  );

  async function refresh(): Promise<LlmCostStatus | undefined> {
    return mutate(undefined, { revalidate: true });
  }

  return { data, isLoading, refresh };
}
