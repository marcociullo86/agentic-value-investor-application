import axios, {
  AxiosError,
  AxiosHeaders,
  type AxiosInstance,
  type AxiosRequestConfig,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios';
import { useAuthStore } from '@/lib/stores/useAuthStore';
import { acquireFreshToken } from '@/lib/api/token-refresh-mutex';

/**
 * Frontend HTTP client (TSK-030).
 *
 * Riferimento: design_&_architecture/components/frontend-components.md §API client.
 *
 * Responsabilità:
 *  - baseURL da `NEXT_PUBLIC_API_BASE_URL` (default `http://localhost:8080`,
 *    CORS abilitato lato BE — TSK-031 CorsConfig).
 *  - Interceptor request: inietta `Authorization: Bearer ${accessToken}` se
 *    presente nello store auth (access token in memoria, mai persistito —
 *    ADR-006).
 *  - Interceptor response 401: tenta refresh una volta, fallback logout.
 *  - Estrazione headers `X-Data-Snapshot-At` / `X-Data-Stale` in wrapper
 *    `ApiResult<T>` (US-005/006 cross-cutting).
 *
 * NOTA: la business logic auth completa (refresh + logout) atterra in TSK-034.
 * Qui esponiamo lo scheletro affinché i task FE successivi consumino già il
 * wrapper.
 */

export interface ApiResult<T> {
  readonly data: T;
  /** Valore di `X-Data-Snapshot-At` (ISO-8601) quando presente. */
  readonly snapshotAt: string | null;
  /** Valore di `X-Data-Stale` (boolean) quando presente. */
  readonly isStale: boolean;
  readonly status: number;
}

const BASE_URL: string =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080';

export const apiClient: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: 30_000,
  withCredentials: true, // refresh token in httpOnly cookie (ADR-006).
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  },
});

apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig): InternalAxiosRequestConfig => {
    const token: string | null = useAuthStore.getState().accessToken;
    if (token) {
      const headers: AxiosHeaders =
        config.headers instanceof AxiosHeaders
          ? config.headers
          : new AxiosHeaders(config.headers);
      headers.set('Authorization', `Bearer ${token}`);
      config.headers = headers;
    }
    return config;
  },
);

interface RetriableRequest extends AxiosRequestConfig {
  _retry?: boolean;
}

apiClient.interceptors.response.use(
  (response: AxiosResponse): AxiosResponse => response,
  async (error: AxiosError): Promise<AxiosResponse> => {
    const originalRequest: RetriableRequest | undefined =
      error.config as RetriableRequest | undefined;
    if (
      error.response?.status === 401 &&
      originalRequest &&
      !originalRequest._retry
    ) {
      const url: string = originalRequest.url ?? '';
      if (url.includes('/api/auth/refresh')) {
        const store = useAuthStore.getState();
        store.clearSession();
        store.setSessionExpired(true);
        return Promise.reject(error);
      }
      originalRequest._retry = true;
      try {
        const newToken = await acquireFreshToken();
        const headers: AxiosHeaders =
          originalRequest.headers instanceof AxiosHeaders
            ? originalRequest.headers
            : new AxiosHeaders(originalRequest.headers as Record<string, string>);
        headers.set('Authorization', `Bearer ${newToken}`);
        originalRequest.headers = headers;
        return apiClient.request(originalRequest);
      } catch (refreshError) {
        return Promise.reject(refreshError);
      }
    }
    return Promise.reject(error);
  },
);

function extractMetadata<T>(response: AxiosResponse<T>): ApiResult<T> {
  const snapshotHeader: string | undefined =
    response.headers['x-data-snapshot-at'] ??
    response.headers['X-Data-Snapshot-At'];
  const staleHeader: string | undefined =
    response.headers['x-data-stale'] ?? response.headers['X-Data-Stale'];
  return {
    data: response.data,
    snapshotAt: snapshotHeader ?? null,
    isStale: staleHeader === 'true',
    status: response.status,
  };
}

export async function apiGet<T>(
  url: string,
  config?: AxiosRequestConfig,
): Promise<ApiResult<T>> {
  const response = await apiClient.get<T>(url, config);
  return extractMetadata(response);
}

export async function apiPost<T, B = unknown>(
  url: string,
  body?: B,
  config?: AxiosRequestConfig,
): Promise<ApiResult<T>> {
  const response = await apiClient.post<T>(url, body, config);
  return extractMetadata(response);
}

export async function apiPut<T, B = unknown>(
  url: string,
  body?: B,
  config?: AxiosRequestConfig,
): Promise<ApiResult<T>> {
  const response = await apiClient.put<T>(url, body, config);
  return extractMetadata(response);
}

export async function apiDelete<T>(
  url: string,
  config?: AxiosRequestConfig,
): Promise<ApiResult<T>> {
  const response = await apiClient.delete<T>(url, config);
  return extractMetadata(response);
}
