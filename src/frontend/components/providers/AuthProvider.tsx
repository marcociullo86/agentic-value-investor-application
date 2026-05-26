'use client';

import { useEffect, type ReactNode } from 'react';
import { useAuthStore } from '@/lib/stores/useAuthStore';
import { useTokenRefresh } from '@/hooks/use-token-refresh';
import { IdleTimeoutProvider } from '@/components/auth/idle-timeout-provider';

/**
 * AuthProvider — bootstrap rehydration (TSK-211).
 *
 * On mount, if the in-memory accessToken is absent (e.g. after F5),
 * attempts `POST /api/auth/refresh`. The browser attaches the httpOnly
 * refresh-token cookie automatically. On success the access token is
 * restored to the Zustand store and the page renders without a flash.
 * On failure the user stays unauthenticated — downstream guards
 * (AuthGuard / middleware TSK-206) handle the redirect.
 *
 * While rehydration is in flight a minimal loading indicator is shown
 * so protected content is never briefly visible.
 */
export function AuthProvider({
  children,
}: {
  readonly children: ReactNode;
}): ReactNode {
  const rehydrate = useAuthStore((s) => s.rehydrate);
  const status = useAuthStore((s) => s.rehydrationStatus);

  useEffect(() => {
    void rehydrate();
  }, [rehydrate]);

  useTokenRefresh();

  if (status !== 'done') {
    return (
      <div
        className="flex min-h-screen items-center justify-center"
        aria-busy="true"
        aria-label="Caricamento sessione"
      >
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
      </div>
    );
  }

  return <IdleTimeoutProvider>{children}</IdleTimeoutProvider>;
}
