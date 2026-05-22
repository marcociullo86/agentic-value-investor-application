import { apiGet, apiPost } from '@/lib/api/client';

/**
 * Moat checklist API wrapper (TSK-027). Schema reference:
 * design_&_architecture/api/openapi.yaml §components.schemas
 * (MoatChecklistEntry, MoatChecklistEntryRequest, MoatChecklist).
 */

export type MoatType =
  | 'INTANGIBLE_ASSETS'
  | 'SWITCHING_COSTS'
  | 'NETWORK_EFFECT'
  | 'COST_ADVANTAGE';

export type MoatStatus = 'PRESENT' | 'PARTIAL' | 'ABSENT';

export interface MoatChecklistEntry {
  readonly moatType: MoatType;
  readonly status: MoatStatus | null;
  readonly note: string | null;
  readonly updatedAt: string | null;
}

export interface MoatChecklist {
  readonly ticker: string;
  readonly entries: ReadonlyArray<MoatChecklistEntry>;
}

export interface MoatChecklistEntryRequest {
  readonly moatType: MoatType;
  readonly status: MoatStatus;
  readonly note?: string | null;
}

export async function fetchMoatChecklist(ticker: string): Promise<MoatChecklist> {
  const result = await apiGet<MoatChecklist>(
    `/api/moat-checklist/${encodeURIComponent(ticker)}`,
  );
  return result.data;
}

export async function upsertMoatEntry(
  ticker: string,
  body: MoatChecklistEntryRequest,
): Promise<MoatChecklistEntry> {
  const result = await apiPost<MoatChecklistEntry, MoatChecklistEntryRequest>(
    `/api/moat-checklist/${encodeURIComponent(ticker)}`,
    body,
  );
  return result.data;
}

export const MOAT_TYPE_LABELS: Record<MoatType, string> = {
  INTANGIBLE_ASSETS: 'Asset immateriali',
  SWITCHING_COSTS: 'Switching costs',
  NETWORK_EFFECT: 'Network effect',
  COST_ADVANTAGE: 'Vantaggio di costo',
};

export const MOAT_TYPE_DESCRIPTIONS: Record<MoatType, string> = {
  INTANGIBLE_ASSETS:
    'Brevetti, marchi, licenze e altre risorse immateriali difficilmente replicabili.',
  SWITCHING_COSTS:
    'Costi di passaggio elevati che disincentivano l’abbandono del prodotto/servizio.',
  NETWORK_EFFECT:
    'Il valore del prodotto cresce con il numero degli utenti che lo adottano.',
  COST_ADVANTAGE:
    'Costi di produzione strutturalmente inferiori alla concorrenza.',
};

export const MOAT_STATUS_LABELS: Record<MoatStatus, string> = {
  PRESENT: 'Presente',
  PARTIAL: 'Parziale',
  ABSENT: 'Assente',
};
