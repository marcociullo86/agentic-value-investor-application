import { getRouteConfig } from './route-config';

/**
 * Client-side AuthGuard decision logic (TSK-266 / US-087 / ADR-026).
 *
 * Pure, framework-agnostic function: input -> decision. The hook
 * `useAuthGuard` (`@/hooks/use-auth-guard`) wires this evaluator to the
 * Zustand auth store and Next.js router, and the `ClientAuthGuard`
 * component applies it to a wrapped subtree.
 *
 * Matrix (from ADR-026 §Decisione + US-087 §AC):
 *   1. rehydration in flight                -> `loading`
 *   2. route public / unknown               -> `allow` (fail-open, BE enforces)
 *   3. session-expired marker on protected  -> `session-expired` (logout + login)
 *   4. protected + no access token          -> `unauthenticated` (login + returnUrl)
 *   5. protected + missing required role    -> `forbidden` (/403)
 *   6. otherwise                            -> `allow`
 *
 * Single source of truth for `requiresAuth` / `roles`: `route-config.ts`
 * (TSK-205, US-074).
 *
 * Compatibilità static export (ADR-026): nessuna dipendenza da middleware
 * runtime — il guard vive interamente client-side e funziona col bundle
 * statico servito dal backend.
 */
export type AuthGuardDecision =
  | { readonly type: 'loading' }
  | { readonly type: 'allow' }
  | { readonly type: 'unauthenticated'; readonly returnUrl: string }
  | { readonly type: 'forbidden' }
  | { readonly type: 'session-expired'; readonly returnUrl: string };

export interface AuthGuardInput {
  readonly pathname: string;
  /** Current path + search, used to build the `returnUrl` query param. */
  readonly currentUrl: string;
  readonly rehydrationStatus: 'pending' | 'rehydrating' | 'done';
  readonly accessToken: string | null;
  readonly userRole?: string | null;
  readonly sessionExpired: boolean;
}

function normalizeRole(role: string | null | undefined): string {
  return (role ?? '').toLowerCase();
}

/**
 * Case-insensitive role match.
 *
 * The route-config (TSK-205) declares roles in lowercase (`"admin"`) while
 * `UserProfile.role` exposes the backend casing (`"ADMIN" | "USER"`). The
 * guard normalises both sides so the route map can stay human-readable
 * without forcing the BE contract to change.
 */
function hasRequiredRole(
  userRole: string | null | undefined,
  required: readonly string[],
): boolean {
  if (required.length === 0) return true;
  const normalized = normalizeRole(userRole);
  if (!normalized) return false;
  return required.some((r) => normalizeRole(r) === normalized);
}

export function evaluateAuthGuard(input: AuthGuardInput): AuthGuardDecision {
  if (input.rehydrationStatus !== 'done') {
    return { type: 'loading' };
  }

  const config = getRouteConfig(input.pathname);
  if (!config?.requiresAuth) {
    return { type: 'allow' };
  }

  if (input.sessionExpired) {
    return { type: 'session-expired', returnUrl: input.currentUrl };
  }

  if (!input.accessToken) {
    return { type: 'unauthenticated', returnUrl: input.currentUrl };
  }

  const requiredRoles = config.roles ?? [];
  if (!hasRequiredRole(input.userRole, requiredRoles)) {
    return { type: 'forbidden' };
  }

  return { type: 'allow' };
}

/**
 * Build a `/login` URL with optional `returnUrl` and `expired=true` markers.
 * Matches the contract consumed by the login page (`?expired=true` banner)
 * and the legacy middleware (`returnUrl` for post-login redirect).
 */
export function buildLoginUrl(
  returnUrl: string,
  options?: { readonly expired?: boolean },
): string {
  const params = new URLSearchParams();
  if (options?.expired) {
    params.set('expired', 'true');
  }
  if (returnUrl && returnUrl !== '/login' && !returnUrl.startsWith('/login?')) {
    params.set('returnUrl', returnUrl);
  }
  const qs = params.toString();
  return qs ? `/login?${qs}` : '/login';
}
