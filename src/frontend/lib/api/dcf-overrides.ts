import { apiDelete, apiGet, apiPost } from '@/lib/api/client';
import type { DcfMethod } from '@/lib/api/analysis';
import { isAxiosError } from 'axios';

export interface DcfOverride {
  readonly ticker: string;
  readonly forcedMethod: DcfMethod;
  readonly createdAt: string;
}

export interface DcfOverrideRequest {
  readonly ticker: string;
  readonly forcedMethod: DcfMethod;
}

export interface DcfFeasibilityProblem {
  readonly detail?: string;
  readonly reason?: string;
  readonly availableYears?: number;
  readonly requiredYears?: number;
}

/**
 * GET /api/dcf-overrides/{ticker} — returns override or null if 404.
 */
export async function getDcfOverride(ticker: string): Promise<DcfOverride | null> {
  const normalized = ticker.trim().toUpperCase();
  try {
    const result = await apiGet<DcfOverride>(
      `/api/dcf-overrides/${encodeURIComponent(normalized)}`,
    );
    return result.data;
  } catch (err: unknown) {
    if (isAxiosError(err) && err.response?.status === 404) {
      return null;
    }
    throw err;
  }
}

export async function upsertDcfOverride(
  body: DcfOverrideRequest,
): Promise<DcfOverride> {
  const result = await apiPost<DcfOverride, DcfOverrideRequest>(
    '/api/dcf-overrides',
    body,
  );
  return result.data;
}

export async function deleteDcfOverride(ticker: string): Promise<void> {
  const normalized = ticker.trim().toUpperCase();
  await apiDelete(`/api/dcf-overrides/${encodeURIComponent(normalized)}`);
}

export function parseDcfFeasibilityProblem(err: unknown): DcfFeasibilityProblem | null {
  if (!isAxiosError(err) || err.response?.status !== 422) {
    return null;
  }
  const data = err.response.data as Record<string, unknown> | undefined;
  if (!data) {
    return null;
  }
  const props = (data.properties ?? data) as Record<string, unknown>;
  return {
    detail: typeof data.detail === 'string' ? data.detail : undefined,
    reason: typeof props.reason === 'string' ? props.reason : undefined,
    availableYears:
      typeof props.availableYears === 'number' ? props.availableYears : undefined,
    requiredYears:
      typeof props.requiredYears === 'number' ? props.requiredYears : undefined,
  };
}
