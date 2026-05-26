'use client';

import { useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { mutate } from 'swr';
import { useAuthStore } from '@/lib/stores/useAuthStore';
import { logout as apiLogout } from '@/lib/api/auth';

const SESSION_START_KEY = '__idle_session_start';

/**
 * useLogout (TSK-217).
 *
 * Centralised logout sequence:
 * 1. POST /api/auth/logout — revokes refresh token server-side (best-effort)
 * 2. httpOnly cookie cleared by the server response (Set-Cookie max-age=0)
 * 3. isAuthenticated hint cookie cleared client-side
 * 4. Zustand auth store cleared (access token, user, expiry)
 * 5. SWR cache globally invalidated (no revalidation)
 * 6. sessionStorage absolute-timeout marker cleared
 * 7. history.replaceState + router.push('/login') — prevents back-button re-entry
 *
 * Resilience: local cleanup always proceeds even if the BE revocation fails.
 */
export function useLogout(): { readonly logout: () => Promise<void> } {
  const router = useRouter();
  const clearSession = useAuthStore((s) => s.clearSession);

  const logout = useCallback(async (): Promise<void> => {
    try {
      await apiLogout();
    } catch (e: unknown) {
      console.error('[useLogout] revocation failed, proceeding with local cleanup', e);
    }

    clearSession();

    await mutate(() => true, undefined, { revalidate: false });

    if (typeof window !== 'undefined') {
      sessionStorage.removeItem(SESSION_START_KEY);
      window.history.replaceState(null, '', '/login');
    }

    router.push('/login');
  }, [clearSession, router]);

  return { logout } as const;
}
