'use client';

import { useRouter } from 'next/navigation';
import { useEffect, type ReactNode } from 'react';
import { useAuthStore } from '@/lib/stores/useAuthStore';

/**
 * Client-side guard for protected pages (TSK-034/035).
 *
 * Renders `children` only when an access token is present; otherwise pushes
 * the user to `/login`. Server-side enforcement happens in `SecurityConfig`
 * (TSK-033) — this is a UX nicety.
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

  useEffect(() => {
    if (!accessToken) {
      router.replace('/login');
    }
  }, [accessToken, router]);

  if (!accessToken) return fallback;
  return children;
}
