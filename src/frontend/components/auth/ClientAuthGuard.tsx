'use client';

import type { ReactNode } from 'react';
import { useAuthGuard } from '@/hooks/use-auth-guard';

export interface ClientAuthGuardProps {
  readonly children: ReactNode;
  /**
   * Rendered while the guard is still resolving (rehydration in flight) or
   * while a redirect is queued (`unauthenticated`, `forbidden`,
   * `session-expired`). Defaults to `null` so callers can opt into a
   * deterministic skeleton/spinner per page.
   */
  readonly fallback?: ReactNode;
}

/**
 * ClientAuthGuard (TSK-266 / US-087 / ADR-026).
 *
 * Client-side replacement for the legacy Next.js middleware guard, designed
 * to work with `output: 'export'` (no Edge runtime in production). Renders
 * `children` only when the unified decision matrix evaluates to `allow`;
 * otherwise renders the `fallback` while `useAuthGuard` queues a redirect.
 *
 * Reuse: dropped in front of any protected layout/page. Currently re-exported
 * via `AuthGuard` to keep existing call sites (watchlist, moat, profile/mfa)
 * working without manual migration.
 */
export function ClientAuthGuard({
  children,
  fallback = null,
}: ClientAuthGuardProps): ReactNode {
  const decision = useAuthGuard();
  return decision.type === 'allow' ? children : fallback;
}
