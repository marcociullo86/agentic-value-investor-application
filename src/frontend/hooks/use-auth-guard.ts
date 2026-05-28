'use client';

import { useEffect, useMemo, useRef } from 'react';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { useAuthStore } from '@/lib/stores/useAuthStore';
import {
  buildLoginUrl,
  evaluateAuthGuard,
  type AuthGuardDecision,
} from '@/lib/auth/auth-guard-decision';

/**
 * useAuthGuard (TSK-266 / US-087 / ADR-026).
 *
 * Client-side AuthGuard hook compatible with `output: 'export'`. Reads the
 * declarative route-config (TSK-205) and the rehydration-aware auth store
 * (TSK-211) and applies the unified decision matrix:
 *
 *   - `unauth`           -> `/login?returnUrl=<current>`
 *   - `forbidden`        -> `/403`
 *   - `sessionExpired`   -> silent `clearSession` + `/login?expired=true&returnUrl=<current>`
 *
 * Returns the current decision so the wrapping component can render an
 * accessible loading/fallback while the redirect is in flight. The redirect
 * itself is fired from a `useEffect`, guarded by a ref so React's strict-mode
 * double-invocation and the transient state churn between
 * `clearSession()` and `router.replace()` never produce duplicate
 * navigations.
 */
export function useAuthGuard(): AuthGuardDecision {
  const router = useRouter();
  const pathname = usePathname() ?? '/';
  const searchParams = useSearchParams();

  const rehydrationStatus = useAuthStore((s) => s.rehydrationStatus);
  const accessToken = useAuthStore((s) => s.accessToken);
  const userRole = useAuthStore((s) => s.user?.role);
  const sessionExpired = useAuthStore((s) => s.sessionExpired);
  const setSessionExpired = useAuthStore((s) => s.setSessionExpired);
  const clearSession = useAuthStore((s) => s.clearSession);

  const currentUrl = useMemo(() => {
    const search = searchParams?.toString() ?? '';
    return search ? `${pathname}?${search}` : pathname;
  }, [pathname, searchParams]);

  const decision = useMemo(
    () =>
      evaluateAuthGuard({
        pathname,
        currentUrl,
        rehydrationStatus,
        accessToken,
        userRole: userRole ?? null,
        sessionExpired: Boolean(sessionExpired),
      }),
    [pathname, currentUrl, rehydrationStatus, accessToken, userRole, sessionExpired],
  );

  const redirectInFlightRef = useRef(false);

  useEffect(() => {
    if (decision.type === 'allow' || decision.type === 'loading') {
      redirectInFlightRef.current = false;
      return;
    }
    if (redirectInFlightRef.current) {
      return;
    }
    redirectInFlightRef.current = true;

    if (decision.type === 'session-expired') {
      clearSession?.();
      setSessionExpired?.(false);
      router.replace(buildLoginUrl(decision.returnUrl, { expired: true }));
      return;
    }

    if (decision.type === 'unauthenticated') {
      router.replace(buildLoginUrl(decision.returnUrl));
      return;
    }

    if (decision.type === 'forbidden') {
      router.replace('/403');
    }
  }, [decision, router, clearSession, setSessionExpired]);

  return decision;
}
