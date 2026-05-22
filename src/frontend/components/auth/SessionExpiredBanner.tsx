'use client';

import Link from 'next/link';
import { useAuthStore } from '@/lib/stores/useAuthStore';

/**
 * Persistent banner shown when the silent-refresh flow surfaces a 401 that
 * the client cannot recover from — refresh-token sliding TTL expired, token
 * revoked, or absolute 30-day cap reached (ADR-010 §3).
 *
 * The store flag `sessionExpired` is flipped to `true` by the 401 interceptor
 * in `lib/api/client.ts`; clicking "Accedi" routes the user to /login which
 * is responsible for the eventual reset to `false` on the next successful
 * login. Dismissing the banner manually also resets the flag so it doesn't
 * resurrect on the next render.
 *
 * Renders nothing while the session is healthy. Mounted once in the root
 * layout so it sits above all routed content.
 *
 * Reference: TSK-043 — useAuthStore: 401 + session-expired banner.
 */
export function SessionExpiredBanner(): React.ReactElement | null {
  const sessionExpired = useAuthStore((s) => s.sessionExpired);
  const setSessionExpired = useAuthStore((s) => s.setSessionExpired);

  if (!sessionExpired) {
    return null;
  }

  return (
    <div
      role="alert"
      aria-live="assertive"
      data-testid="session-expired-banner"
      className="border-b border-amber-300 bg-amber-50 text-amber-900 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-100"
    >
      <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-6 py-2 text-sm">
        <span>Sessione scaduta, accedi di nuovo</span>
        <Link
          href="/login"
          onClick={() => setSessionExpired(false)}
          className="rounded border border-amber-400 px-3 py-1 font-medium hover:bg-amber-100 dark:hover:bg-amber-900"
          data-testid="session-expired-login"
        >
          Accedi
        </Link>
      </div>
    </div>
  );
}
