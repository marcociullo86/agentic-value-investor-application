'use client';

import { useRouter } from 'next/navigation';
import { useEffect, type ReactNode } from 'react';
import { useAuthStore } from '@/lib/stores/useAuthStore';

/**
 * Client-side guard for protected pages (TSK-034/035, TSK-211).
 *
 * Waits for rehydration to complete before evaluating auth state.
 * This prevents a false redirect to /login when a valid httpOnly
 * refresh-token cookie exists but the in-memory store is empty
 * (e.g. after F5).
 *
 * Renders `children` only when an access token is present after
 * rehydration; otherwise pushes the user to `/login`. Server-side
 * enforcement happens in `SecurityConfig` (TSK-033) — this is a
 * UX nicety.
 */
export function AuthGuard({
  children,
  fallback = null,
}: {
  readonly children: ReactNode;
  readonly fallback?: ReactNode;
}): ReactNode {
  const router = useRouter();
  const accessToken = useAuthStore((s) => s.accessToken);
  const rehydrationStatus = useAuthStore((s) => s.rehydrationStatus);

  useEffect(() => {
    if (rehydrationStatus === 'done' && !accessToken) {
      router.replace('/login');
    }
  }, [accessToken, rehydrationStatus, router]);

  if (rehydrationStatus !== 'done') return fallback;
  if (!accessToken) return fallback;
  return children;
}
