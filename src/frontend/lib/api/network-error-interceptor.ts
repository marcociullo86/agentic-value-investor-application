import type { UseNotificationReturn } from '@/hooks/use-notification';
import { getErrorI18n } from '@/lib/errors/error-code-map';
import locale from '@/locales/it.json';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export type NetworkErrorCategory = 'offline' | 'timeout' | 'server' | 'validation';

export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  [key: string]: unknown;
}

export interface InterceptorOptions {
  notify: UseNotificationReturn['notify'];
  skipNotification?: boolean;
}

/**
 * Typed error thrown by the interceptor so callers can inspect the category,
 * HTTP status, Correlation ID, and parsed ProblemDetail body.
 */
export class NetworkError extends Error {
  override readonly name = 'NetworkError';
  readonly category: NetworkErrorCategory;
  readonly status: number | undefined;
  readonly correlationId: string | undefined;
  readonly problemDetail: ProblemDetail | undefined;

  constructor(
    category: NetworkErrorCategory,
    opts?: {
      status?: number;
      correlationId?: string;
      problemDetail?: ProblemDetail;
      cause?: unknown;
    },
  ) {
    super(`Network error: ${category}`);
    this.category = category;
    this.status = opts?.status;
    this.correlationId = opts?.correlationId;
    this.problemDetail = opts?.problemDetail;
    if (opts?.cause) this.cause = opts.cause;
  }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

type ErrorEntry = { title: string; message: string; cta?: string };

function getNetworkI18n(key: string): ErrorEntry {
  const section = (locale as { errors: Record<string, ErrorEntry> }).errors;
  return section[key] ?? { title: 'Errore', message: key };
}

async function tryParseProblemDetail(
  response: Response,
): Promise<ProblemDetail | undefined> {
  try {
    const ct = response.headers.get('Content-Type') ?? '';
    if (ct.includes('json')) {
      return (await response.clone().json()) as ProblemDetail;
    }
  } catch {
    /* body not parseable */
  }
  return undefined;
}

// ---------------------------------------------------------------------------
// Core interceptor
// ---------------------------------------------------------------------------

/**
 * Wraps a single `fetch` call with network-error categorisation and automatic
 * notification via the `NotificationService` (useNotification hook).
 *
 * Categories:
 *  1. **Offline** — `!navigator.onLine`
 *  2. **Timeout** — `AbortError` (from `AbortController`)
 *  3. **Server (5xx)** — `response.status >= 500`
 *  4. **Validation (4xx)** — `response.status >= 400 && < 500`
 *
 * Always throws a `NetworkError` for non-ok responses so callers can
 * react (retry, redirect, etc.).
 */
export async function interceptFetch(
  url: string,
  options: RequestInit | undefined,
  { notify, skipNotification }: InterceptorOptions,
): Promise<Response> {
  // --- 1. Offline ---------------------------------------------------------
  if (typeof navigator !== 'undefined' && !navigator.onLine) {
    const i18n = getNetworkI18n('offline');
    if (!skipNotification) {
      notify.error({ title: i18n.title, message: i18n.message });
    }
    throw new NetworkError('offline');
  }

  // --- Fetch --------------------------------------------------------------
  let response: Response;
  try {
    response = await fetch(url, options);
  } catch (error: unknown) {
    // --- 2. Timeout / AbortError ------------------------------------------
    if (error instanceof DOMException && error.name === 'AbortError') {
      const i18n = getNetworkI18n('timeout');
      if (!skipNotification) {
        notify.warning({ title: i18n.title, message: i18n.message });
      }
      throw new NetworkError('timeout', { cause: error });
    }

    // Other fetch failures (DNS, CORS, etc.) — surface as offline-like
    const i18n = getNetworkI18n('offline');
    if (!skipNotification) {
      notify.error({ title: i18n.title, message: i18n.message });
    }
    throw new NetworkError('offline', { cause: error });
  }

  // --- Success ------------------------------------------------------------
  if (response.ok) return response;

  // --- Shared metadata ----------------------------------------------------
  const correlationId =
    response.headers.get('X-Correlation-Id') ?? undefined;
  const problemDetail = await tryParseProblemDetail(response);
  const pdType = problemDetail?.type ?? '';

  // --- 3. Server error (5xx) ---------------------------------------------
  if (response.status >= 500) {
    const i18n = getErrorI18n(pdType, correlationId);

    if (!skipNotification) {
      const actions: Array<{ label: string; onClick: () => void }> = [];
      if (correlationId) {
        actions.push({
          label: 'Copia ID',
          onClick: () => {
            void navigator.clipboard.writeText(correlationId);
          },
        });
      }

      notify.error({
        title: i18n.title,
        message: i18n.message,
        correlationId,
        actions: actions.length > 0 ? actions : undefined,
        autoDismiss: false,
      });
    }

    throw new NetworkError('server', {
      status: response.status,
      correlationId,
      problemDetail,
    });
  }

  // --- 4. Validation / client error (4xx) ---------------------------------
  if (response.status >= 400) {
    const i18n = getErrorI18n(pdType, correlationId);

    if (!skipNotification) {
      notify.warning({
        title: i18n.title,
        message: i18n.message,
        correlationId,
      });
    }

    throw new NetworkError('validation', {
      status: response.status,
      correlationId,
      problemDetail,
    });
  }

  return response;
}

// ---------------------------------------------------------------------------
// SWR fetcher factory
// ---------------------------------------------------------------------------

/**
 * Creates a `fetch`-based SWR fetcher that automatically categorises network
 * errors and pushes user-facing notifications via the NotificationService.
 *
 * ```tsx
 * const { notify } = useNotification();
 * const fetcher = createFetcher(notify);
 * const { data } = useSWR('/api/endpoint', fetcher);
 * ```
 */
export function createFetcher(
  notify: UseNotificationReturn['notify'],
  opts?: { skipNotification?: boolean },
): (url: string) => Promise<unknown> {
  return async (url: string): Promise<unknown> => {
    const response = await interceptFetch(url, undefined, {
      notify,
      skipNotification: opts?.skipNotification,
    });
    return response.json();
  };
}
