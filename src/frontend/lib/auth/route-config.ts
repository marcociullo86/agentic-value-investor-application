export interface RouteConfig {
  path: string;
  requiresAuth: boolean;
  roles?: string[];
  permissions?: string[];
}

/**
 * Declarative route map. To protect a new route, add an entry here —
 * the AuthGuard (TSK-206) will consume this config at runtime.
 */
export const ROUTE_MAP: readonly RouteConfig[] = [
  // Public routes
  { path: "/login", requiresAuth: false },
  { path: "/register", requiresAuth: false },
  { path: "/", requiresAuth: false },
  { path: "/analysis", requiresAuth: false },
  { path: "/analysis/deep", requiresAuth: false },
  { path: "/screener", requiresAuth: false },
  { path: "/top-picks", requiresAuth: false },

  // Authenticated routes
  { path: "/watchlist", requiresAuth: true },
  { path: "/moat", requiresAuth: true },
  { path: "/profile", requiresAuth: true },

  // Admin routes
  { path: "/admin", requiresAuth: true, roles: ["admin"] },
] as const;

/**
 * Lookup a route config by exact path match or longest-prefix match.
 * Returns undefined if the pathname does not match any configured route.
 */
export function getRouteConfig(pathname: string): RouteConfig | undefined {
  const exact = ROUTE_MAP.find((r) => r.path === pathname);
  if (exact) return exact;

  const sorted = [...ROUTE_MAP]
    .filter((r) => pathname.startsWith(r.path + "/") || pathname === r.path)
    .sort((a, b) => b.path.length - a.path.length);

  return sorted[0];
}

/**
 * Returns true if the given pathname requires authentication.
 * Unknown routes are treated as public (fail-open on frontend; backend enforces real authz).
 */
export function isProtectedRoute(pathname: string): boolean {
  const config = getRouteConfig(pathname);
  return config?.requiresAuth ?? false;
}

/**
 * Returns the roles required to access the given pathname.
 * Empty array means no role restriction (only auth check).
 */
export function getRequiredRoles(pathname: string): string[] {
  const config = getRouteConfig(pathname);
  return config?.roles ?? [];
}

/**
 * Returns the permissions required to access the given pathname.
 * Empty array means no specific permission beyond authentication.
 */
export function getRequiredPermissions(pathname: string): string[] {
  const config = getRouteConfig(pathname);
  return config?.permissions ?? [];
}
