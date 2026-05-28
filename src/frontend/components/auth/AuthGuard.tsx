'use client';

import type { ReactNode } from 'react';
import { ClientAuthGuard } from './ClientAuthGuard';

/**
 * AuthGuard (TSK-034/035, TSK-211, TSK-266).
 *
 * Thin backward-compatible alias for `ClientAuthGuard`. Historical call
 * sites (`/watchlist`, `/moat`, `/profile/mfa`) import this name and get
 * the unified TSK-266 decision matrix (unauth → login+returnUrl,
 * forbidden → /403, sessionExpired → logout silente + login) without
 * any per-page migration.
 *
 * New protected layouts/pages SHOULD import `ClientAuthGuard` directly to
 * make the static-export-compatible client-side guard explicit at the
 * call site (ADR-026 §Decisione).
 */
export function AuthGuard({
  children,
  fallback = null,
}: {
  readonly children: ReactNode;
  readonly fallback?: ReactNode;
}): ReactNode {
  return <ClientAuthGuard fallback={fallback}>{children}</ClientAuthGuard>;
}
