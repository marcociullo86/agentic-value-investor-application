import { type NextRequest, NextResponse } from "next/server";

import { getRouteConfig } from "./lib/auth/route-config";

const AUTH_COOKIE = "isAuthenticated";
const ROLE_COOKIE = "userRole";
const SESSION_EXPIRED_COOKIE = "sessionExpired";

const LOGIN_PATH = "/login";
const REGISTER_PATH = "/register";
const FORBIDDEN_PATH = "/403";
const HOME_PATH = "/";

/**
 * Normalise pathname by stripping a trailing slash (keeps "/" intact).
 * next.config.js has `trailingSlash: true`, so the request may arrive with
 * either "/login" or "/login/" depending on client behaviour.
 */
function normalisePath(pathname: string): string {
  return pathname.length > 1 && pathname.endsWith("/")
    ? pathname.slice(0, -1)
    : pathname;
}

function isAuthPage(pathname: string): boolean {
  const p = normalisePath(pathname);
  return p === LOGIN_PATH || p === REGISTER_PATH;
}

/**
 * AuthGuard middleware (TSK-206 / US-073 / EP-017).
 *
 * UX improvement — NOT a security boundary. Every protected backend endpoint
 * independently verifies auth & authz server-side (defense-in-depth).
 *
 * Decision table:
 *  1. Session-expired marker cookie  → clear cookie, redirect /login?expired=true
 *  2. Authenticated → auth page      → redirect /
 *  3. Route not in ROUTE_MAP          → pass through (fail-open)
 *  4. Public route                    → pass through
 *  5. Protected + not authenticated   → redirect /login?returnUrl=…
 *  6. Protected + role mismatch       → redirect /403
 *  7. Otherwise                       → pass through
 *
 * Cookies consumed (non-httpOnly hints set by backend / client):
 *  - isAuthenticated: "true" when a session exists
 *  - userRole: current user role string (e.g. "admin")
 *  - sessionExpired: "true" when the client-side 401 interceptor detects
 *    an unrecoverable session expiry (set by the Zustand clearSession flow)
 *
 * NOTE: requires a Next.js server runtime to execute. With the current
 * `output: 'export'` in next.config.js the middleware runs in `next dev` only.
 * See wiki/gaps.md (fe-middleware-static-export-conflict) for the tracking gap.
 */
export function middleware(request: NextRequest): NextResponse {
  const { pathname, search } = request.nextUrl;
  const normalised = normalisePath(pathname);

  const isAuthenticated =
    request.cookies.get(AUTH_COOKIE)?.value === "true";
  const sessionExpired =
    request.cookies.get(SESSION_EXPIRED_COOKIE)?.value === "true";
  const userRole = request.cookies.get(ROLE_COOKIE)?.value ?? "";

  // 1. Session expired → clear markers, redirect to login with expired flag
  if (sessionExpired) {
    const loginUrl = new URL(LOGIN_PATH, request.url);
    loginUrl.searchParams.set("expired", "true");
    const response = NextResponse.redirect(loginUrl);
    response.cookies.delete(SESSION_EXPIRED_COOKIE);
    response.cookies.delete(AUTH_COOKIE);
    return response;
  }

  // 2. Authenticated user visiting /login or /register → redirect home
  if (isAuthenticated && isAuthPage(pathname)) {
    return NextResponse.redirect(new URL(HOME_PATH, request.url));
  }

  // 3–4. Lookup declarative route config (route-config.ts, TSK-205)
  const routeConfig = getRouteConfig(normalised);

  if (!routeConfig || !routeConfig.requiresAuth) {
    return NextResponse.next();
  }

  // 5. Protected route, not authenticated → redirect to login with returnUrl
  if (!isAuthenticated) {
    const loginUrl = new URL(LOGIN_PATH, request.url);
    const returnUrl = pathname + search;
    loginUrl.searchParams.set("returnUrl", returnUrl);
    return NextResponse.redirect(loginUrl);
  }

  // 6. Role check (only when the route declares required roles)
  const requiredRoles = routeConfig.roles ?? [];
  if (requiredRoles.length > 0 && !requiredRoles.includes(userRole)) {
    return NextResponse.redirect(new URL(FORBIDDEN_PATH, request.url));
  }

  // 7. All checks passed
  return NextResponse.next();
}

/**
 * Matcher: run on all app routes except Next.js internals, API routes,
 * and static assets.
 */
export const config = {
  matcher: [
    "/((?!_next/static|_next/image|api|favicon\\.ico|.*\\.(?:svg|png|jpg|jpeg|gif|webp|ico|css|js|woff|woff2|ttf|eot)$).*)",
  ],
};
