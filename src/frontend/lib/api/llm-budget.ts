import { apiGet, apiPost, apiPut } from './client';

export interface LlmCostStatus {
  utilization: number;
  monthlyCapUsd: number;
  totalCostUsd: number;
  frozen: boolean;
}

export interface UpdateBudgetRequest {
  monthlyCapUsd: number;
  reason?: string;
}

export async function getLlmCostStatus() {
  return apiGet<LlmCostStatus>('/admin/llm-cost');
}

export async function freezeLlm() {
  return apiPost<void>('/admin/llm-cost/freeze');
}

export async function unfreezeLlm() {
  return apiPost<void>('/admin/llm-cost/unfreeze');
}

export async function updateLlmBudget(request: UpdateBudgetRequest) {
  return apiPut<{ monthlyCapUsd: number }>('/admin/llm-cost/budget', request);
}
