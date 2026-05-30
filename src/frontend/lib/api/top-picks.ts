import { apiGet, apiPost, type ApiResult } from '@/lib/api/client';

/**
 * Top Picks domain API (TSK-141 — US-051, EP-012).
 *
 * Types verbatim da OpenAPI §components/schemas/TopPicksPageResponse e
 * TopPickItem (vedi backend `api/model/TopPicksPageResponse.kt`, TSK-138).
 *
 * Endpoint pubblico (no auth): GET /api/top-picks
 * Query params: date (YYYY-MM-DD), verdict, sector, min_mos (0..100),
 *               page (default 0), size (default 30, cap 1..100).
 *
 * Errori:
 *  - 400 problem+json su date malformata / data nel futuro / size fuori range
 *  - 503 fallback (servizio non disponibile)
 *
 * [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/api/model/TopPicksPageResponse.kt]
 * [^src: management/kanban/EP-012-batch-top-value-picks/US-051-frontend-top-picks/TSK-141.md]
 */

export type TopPickVerdict =
  | 'APPROVATO'
  | 'APPROVATO_PANIC_BUY'
  | 'WATCHLIST';

export interface TopPickItem {
  readonly ticker: string;
  readonly rankPosition: number;
  readonly verdettoClasse: string;
  readonly marginOfSafety: number | null;
  readonly sector: string | null;
  readonly marketCapUsd: number | null;
  readonly source: string;
  readonly companyName: string | null;
}

export interface TopPicksPageResponse {
  /** ISO yyyy-mm-dd; `null` se il DB non ha ancora alcuna run. */
  readonly runDate: string | null;
  readonly page: number;
  readonly size: number;
  readonly total: number;
  readonly items: readonly TopPickItem[];
}

export interface TopPicksQueryParams {
  readonly date?: string;
  readonly verdict?: string;
  readonly sector?: string;
  readonly minMos?: number;
  readonly page?: number;
  readonly size?: number;
}

/**
 * Costruisce l'URL `/api/top-picks?...` con i query param non-null/non-empty
 * mappati 1:1 al contratto BE (snake_case `min_mos`).
 *
 * Pure function — esportata per usare la stessa stringa come SWR key
 * (deduplication + cache hit cross-component).
 */
export function buildTopPicksUrl(params: TopPicksQueryParams): string {
  const qs = new URLSearchParams();
  if (params.date) qs.set('date', params.date);
  if (params.verdict) qs.set('verdict', params.verdict);
  if (params.sector) qs.set('sector', params.sector);
  if (params.minMos != null) qs.set('min_mos', String(params.minMos));
  if (params.page != null) qs.set('page', String(params.page));
  if (params.size != null) qs.set('size', String(params.size));
  const query = qs.toString();
  return query.length > 0 ? `/api/top-picks?${query}` : '/api/top-picks';
}

/**
 * GET /api/top-picks — restituisce `TopPicksPageResponse`.
 *
 * Usa `apiGet` (axios wrapper, TSK-030) per coerenza con tutti gli altri
 * domini (analysis, deep-analysis, watchlist). Endpoint pubblico: nessun
 * Authorization header richiesto, ma se presente non blocca (BE no-auth).
 */
export async function getTopPicks(
  params: TopPicksQueryParams,
): Promise<TopPicksPageResponse> {
  const url = buildTopPicksUrl(params);
  const result: ApiResult<TopPicksPageResponse> =
    await apiGet<TopPicksPageResponse>(url);
  return result.data;
}

/**
 * Risposta di POST /api/top-picks/run (manual trigger).
 *
 *  - 202 Accepted → `status: "started"`, job avviato in background.
 *  - 409 Conflict → `status: "already_running"`, un altro run è in flight.
 */
export interface TopPicksRunResponse {
  readonly status: 'started' | 'already_running';
  readonly startedAt: string | null;
  readonly message: string;
}

/**
 * POST /api/top-picks/run — trigger manuale on-demand del batch
 * TopValuePicksJob. Fire-and-forget (status 202): il job può durare 10-30
 * minuti, i risultati arrivano su `/top-picks` al termine.
 */
export async function triggerTopPicksRun(): Promise<{
  readonly httpStatus: number;
  readonly body: TopPicksRunResponse;
}> {
  // 409 va trattato come success-soft, non come errore di rete.
  try {
    const result = await apiPost<TopPicksRunResponse, undefined>(
      '/api/top-picks/run',
      undefined,
    );
    return { httpStatus: result.status, body: result.data };
  } catch (err) {
    const status =
      (err as { response?: { status?: number; data?: TopPicksRunResponse } })
        ?.response?.status ?? 500;
    const body =
      (err as { response?: { data?: TopPicksRunResponse } })?.response?.data;
    if (status === 409 && body) {
      return { httpStatus: 409, body };
    }
    throw err;
  }
}

/**
 * Risposta di POST /api/top-picks/run/cancel (blocco manuale).
 *
 *  - 202 Accepted → `status: "cancellation_requested"`, il batch si fermerà al
 *    prossimo ticker (cancellazione cooperativa, non preemptive).
 *  - 409 Conflict → `status: "not_running"`, nessun run da bloccare.
 */
export interface TopPicksCancelResponse {
  readonly status: 'cancellation_requested' | 'not_running';
  readonly startedAt: string | null;
  readonly message: string;
}

/**
 * POST /api/top-picks/run/cancel — richiede il blocco del run manuale in corso.
 * Il backend imposta un flag che il job controlla al confine di ogni ticker;
 * i top picks del giorno già presenti restano intatti (run log → ABORTED).
 */
export async function cancelTopPicksRun(): Promise<{
  readonly httpStatus: number;
  readonly body: TopPicksCancelResponse;
}> {
  // 409 (nessun run) è uno stato atteso, non un errore di rete.
  try {
    const result = await apiPost<TopPicksCancelResponse, undefined>(
      '/api/top-picks/run/cancel',
      undefined,
    );
    return { httpStatus: result.status, body: result.data };
  } catch (err) {
    const status =
      (err as { response?: { status?: number; data?: TopPicksCancelResponse } })
        ?.response?.status ?? 500;
    const body =
      (err as { response?: { data?: TopPicksCancelResponse } })?.response?.data;
    if (status === 409 && body) {
      return { httpStatus: 409, body };
    }
    throw err;
  }
}

/**
 * Stato del run manuale, da GET /api/top-picks/run/status. Liveness probe
 * leggera: permette alla UI di mostrare il controllo giusto (Lancia vs Blocca)
 * al mount e di accorgersi quando il run termina.
 */
export interface TopPicksRunStatus {
  readonly running: boolean;
  readonly startedAt: string | null;
}

/** GET /api/top-picks/run/status — true se un run manuale è in corso. */
export async function getTopPicksRunStatus(): Promise<TopPicksRunStatus> {
  const result = await apiGet<TopPicksRunStatus>('/api/top-picks/run/status');
  return result.data;
}
