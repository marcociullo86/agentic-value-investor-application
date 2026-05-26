import { useAuthStore } from '@/lib/stores/useAuthStore';
import { refreshTokens } from '@/lib/api/auth';

/**
 * Token refresh mutex (TSK-213).
 *
 * Guarantees at most one refresh request in flight at any given time.
 * Concurrent callers await the same Promise and all resolve with the
 * new access token once the single refresh completes.
 *
 * On failure: clears the session (logout flow) and rejects all waiters.
 */

let refreshPromise: Promise<string> | null = null;

export function isRefreshing(): boolean {
  return refreshPromise !== null;
}

/**
 * Acquire a fresh access token. If a refresh is already in progress,
 * returns the pending Promise so all callers coalesce on a single
 * network round-trip.
 */
export async function acquireFreshToken(): Promise<string> {
  if (refreshPromise) {
    return refreshPromise;
  }

  refreshPromise = performRefresh();

  try {
    const token = await refreshPromise;
    return token;
  } finally {
    refreshPromise = null;
  }
}

async function performRefresh(): Promise<string> {
  try {
    const response = await refreshTokens();

    useAuthStore.setState({
      accessToken: response.accessToken,
      expiresAt: Date.now() + response.expiresInSeconds * 1000,
    });

    return response.accessToken;
  } catch (error) {
    useAuthStore.getState().clearSession();
    useAuthStore.getState().setSessionExpired(true);
    throw error;
  }
}
