import axios, { AxiosError } from 'axios';
import type { ProblemDetail } from '@/lib/api/network-error-interceptor';

/**
 * User-safe auth form error mapping (TSK-257 / wave A6, da TSK-034).
 *
 * Catch della submit di login/register: prima si tentava `err.message` con
 * `includes('401')`, ma il body Axios non garantisce quella stringa
 * (puo' essere "Request failed with status code 401" oppure un
 * ProblemDetail). Si mappa quindi sullo `status` HTTP e sul `type`
 * ProblemDetail, fallback su messaggio generico IT — **mai** raw
 * `error.message`.
 *
 * Riferimento: code_quality/reports/TSK-034-iter-1.md finding #1-2,
 * design_&_architecture/api/openapi.yaml §components.responses.ProblemDetail.
 */

/** Contesto del form chiamante (alcuni codici hanno messaggi specifici per form). */
export type AuthFormContext = 'login' | 'register';

const GENERIC_FALLBACK = 'Si è verificato un errore imprevisto. Riprova.';
const NETWORK_FALLBACK =
  'Impossibile contattare il server. Verifica la connessione e riprova.';

const PROBLEM_TYPE_MESSAGES: Record<string, string> = {
  'urn:problem-type:invalid-credentials': 'Email o password non validi.',
  'urn:problem-type:unauthorized': 'Email o password non validi.',
  'urn:problem-type:forbidden': 'Non hai i permessi per questa operazione.',
  'urn:problem-type:email-already-registered': 'Email già registrata.',
  'urn:problem-type:validation-failed':
    'Dati non validi. Controlla i campi inseriti.',
  'urn:problem-type:rate-limited':
    'Troppe richieste. Riprova tra qualche istante.',
  'urn:problem-type:server-error':
    'Errore del server. Riprova tra qualche istante.',
};

function messageForStatus(status: number, context: AuthFormContext): string {
  if (status === 401 || status === 403) {
    return context === 'login'
      ? 'Email o password non validi.'
      : 'Sessione non valida. Effettua nuovamente l\'accesso.';
  }
  if (status === 404) {
    return context === 'login'
      ? 'Email o password non validi.'
      : 'Risorsa non trovata.';
  }
  if (status === 409) {
    return context === 'register'
      ? 'Email già registrata.'
      : 'Operazione non consentita.';
  }
  if (status === 422 || status === 400) {
    return 'Dati non validi. Controlla i campi inseriti.';
  }
  if (status === 429) {
    return 'Troppe richieste. Riprova tra qualche istante.';
  }
  if (status >= 500) {
    return 'Errore del server. Riprova tra qualche istante.';
  }
  return GENERIC_FALLBACK;
}

function extractProblemDetail(error: AxiosError): ProblemDetail | undefined {
  const data = error.response?.data;
  if (data && typeof data === 'object') {
    return data as ProblemDetail;
  }
  return undefined;
}

/**
 * Mappa l'errore (qualsiasi shape) a un messaggio IT user-safe per il
 * banner di login/register.
 *
 * Precedenza:
 *  1. ProblemDetail `type` → messaggio dedicato (RFC 7807).
 *  2. HTTP `status` → mapping contestuale.
 *  3. Errore senza response (network/timeout/CORS) → NETWORK_FALLBACK.
 *  4. Altro → GENERIC_FALLBACK.
 *
 * NB: non viene mai propagato `error.message` raw all'utente.
 */
export function getAuthFormErrorMessage(
  error: unknown,
  context: AuthFormContext,
): string {
  if (!axios.isAxiosError(error)) {
    return GENERIC_FALLBACK;
  }

  const axiosError = error;
  const problemDetail = extractProblemDetail(axiosError);
  const problemType = problemDetail?.type;
  if (problemType && PROBLEM_TYPE_MESSAGES[problemType]) {
    return PROBLEM_TYPE_MESSAGES[problemType];
  }

  const status = axiosError.response?.status;
  if (typeof status === 'number') {
    return messageForStatus(status, context);
  }

  return NETWORK_FALLBACK;
}
