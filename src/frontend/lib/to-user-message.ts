import axios, { type AxiosError } from 'axios';
import type { ProblemDetail } from '@/lib/api/network-error-interceptor';
import { getErrorI18n } from '@/lib/errors/error-code-map';

/**
 * User-safe error normalization (TSK-258, US-085 Fase B — wave A7 fix).
 *
 * Pattern condiviso dai catch-site FE (MoatChecklist, AddToWatchlistButton,
 * watchlist page, …) per rimpiazzare l'anti-pattern
 * `err instanceof Error ? err.message : 'generic'`, che esponeva il body
 * grezzo Axios ("Request failed with status code 500" o stack trace) al
 * dominio utente — flag `typescript.nextjs.errorhandling.user_safe_messages`
 * sollevato su TSK-027 (MoatChecklist) e TSK-035 (watchlist).
 *
 * Estende la logica già consolidata in `useScreenerStore.toUserMessage` /
 * `useAnalysisStore.toUserMessage` / `app/(auth)/_lib/form-errors.ts`
 * (EP-015) aggiungendo la mappa ProblemDetail → i18n del
 * `network-error-interceptor` (i18n IT in `locales/it.json`).
 *
 * Precedenza:
 *   1. `ProblemDetail.type` → messaggio i18n dedicato (RFC 7807 / RFC 9457).
 *   2. HTTP `status` → fallback contestuali per status comuni (404/429/5xx/…).
 *   3. Errore senza response (network/timeout/CORS) → fallback rete.
 *   4. Altro (TypeError, errore JS) → fallback generico.
 *
 * Mai propaga `err.message` né stack al banner utente.
 *
 * Riferimenti:
 *   - code_quality/reports/TSK-027-iter-1.md finding #1-2 (medium).
 *   - code_quality/reports/TSK-035-iter-1.md finding #1-2 (medium).
 *   - design_&_architecture/api/openapi.yaml §ProblemDetail.
 */

export interface ToUserMessageOptions {
  /**
   * Messaggio di fallback generico (default IT). Permette al call-site di
   * fornire un wording dominio-specifico ("Aggiunta fallita", "Salvataggio
   * fallito") quando lo status code non è informativo.
   */
  readonly fallback?: string;
  /**
   * Messaggio dedicato per errori di rete / offline / timeout / DNS
   * (default IT). Mantenuto separato dal `fallback` per consentire copy
   * più specifico ("Verifica la connessione …").
   */
  readonly networkFallback?: string;
  /**
   * Override puntuale per status HTTP (e.g. `{ 404: 'Ticker non trovato.' }`).
   * Ha priorità sui fallback ma cede ai mapping ProblemDetail.
   */
  readonly statusOverrides?: Readonly<Record<number, string>>;
}

const DEFAULT_FALLBACK = 'Si è verificato un errore imprevisto. Riprova.';
const DEFAULT_NETWORK_FALLBACK =
  'Errore di rete. Verifica la connessione e riprova.';

const STATUS_FALLBACKS: Readonly<Record<number, string>> = {
  400: 'Dati non validi. Controlla i campi inseriti.',
  401: 'Sessione scaduta. Effettua nuovamente l\'accesso.',
  403: 'Non hai i permessi per questa operazione.',
  404: 'Risorsa non trovata.',
  409: 'Operazione in conflitto con lo stato corrente.',
  422: 'Dati non validi. Controlla i campi inseriti.',
  429: 'Troppe richieste. Riprova tra qualche istante.',
  503: 'Servizio temporaneamente non disponibile. Riprova più tardi.',
};

function extractProblemDetail(error: AxiosError): ProblemDetail | undefined {
  const data = error.response?.data;
  if (data && typeof data === 'object') {
    return data as ProblemDetail;
  }
  return undefined;
}

function messageForStatus(
  status: number,
  overrides: Readonly<Record<number, string>> | undefined,
  fallback: string,
): string {
  if (overrides && overrides[status] !== undefined) {
    return overrides[status];
  }
  if (STATUS_FALLBACKS[status] !== undefined) {
    return STATUS_FALLBACKS[status];
  }
  if (status >= 500) {
    return 'Errore del server. Riprova tra qualche istante.';
  }
  return fallback;
}

/**
 * Normalizza un errore (qualsiasi shape) in un messaggio IT user-safe.
 *
 * @example
 *   try { await add(ticker); }
 *   catch (err) { setError(toUserMessage(err, { fallback: 'Aggiunta fallita' })); }
 */
export function toUserMessage(
  error: unknown,
  options: ToUserMessageOptions = {},
): string {
  const fallback = options.fallback ?? DEFAULT_FALLBACK;
  const networkFallback = options.networkFallback ?? DEFAULT_NETWORK_FALLBACK;

  if (!axios.isAxiosError(error)) {
    return fallback;
  }

  const axiosError = error;
  const problemDetail = extractProblemDetail(axiosError);
  const problemType = problemDetail?.type;
  if (typeof problemType === 'string' && problemType.length > 0) {
    const i18n = getErrorI18n(problemType);
    if (i18n.message && i18n.message !== problemType) {
      return i18n.message;
    }
  }

  const status = axiosError.response?.status;
  if (typeof status === 'number') {
    return messageForStatus(status, options.statusOverrides, fallback);
  }

  return networkFallback;
}
