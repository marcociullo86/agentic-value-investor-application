import { type NextRequest, NextResponse } from "next/server";

import { getRouteConfig } from "./lib/auth/route-config";
import {
  buildContentSecurityPolicy,
  CSP_NONCE_HEADER,
  generateCspNonce,
} from "./lib/security/csp";

/**
 * Next.js proxy — **DEV-ONLY by design** (ADR-026 / TSK-268 / US-087).
 *
 * Convention `proxy` (ex `middleware`, rinominata in Next.js 16 — TSK-298):
 * stessa semantica runtime, firma e `config.matcher` invariati.
 *
 * Production runtime contract:
 *  - `next.config.js` impone `output: 'export'` (ADR-009), quindi il bundle
 *    produzione servito dal backend Spring Boot NON include questo
 *    proxy: nessuna Edge/Node runtime FE in prod.
 *  - L'AuthGuard di produzione vive interamente client-side: vedi
 *    `components/auth/ClientAuthGuard.tsx` + `hooks/use-auth-guard.ts`
 *    (TSK-266) — UX only, niente security boundary.
 *  - La security authoritativa resta sul backend (defense-in-depth,
 *    ADR-025): ogni endpoint protetto verifica auth/authz lato server.
 *  - In produzione la CSP è emessa dal backend via `SecurityHeadersConfig`
 *    (TSK-221), non da qui.
 *
 * Ruolo di questo file:
 *  - Convenienza per `next dev` (parità minima di comportamento auth/CSP
 *    con la produzione durante lo sviluppo locale).
 *  - Tutte le altre invocazioni eventuali (test, build SSR accidentale,
 *    runtime non-export) sono trattate come pass-through esplicito —
 *    vedi `isDevRuntime()` sotto. Questo è il guard-rail che impedisce a
 *    `proxy.ts` di diventare implicitamente "il" controllo auth di
 *    produzione se qualcuno in futuro rimuove `output: 'export'` senza
 *    ridisegnare il perimetro.
 *
 * Hardening dev-only (TSK-268):
 *  - `isDevRuntime()` controlla `process.env.NODE_ENV === 'development'`.
 *  - In ogni altro contesto la funzione esegue solo `NextResponse.next()`
 *    senza side-effect (no CSP nonce, no cookie inspection, no redirect).
 *  - Build/export statica non dipende da questo file: rimosso comporta
 *    solo perdita della convenienza dev.
 */

const AUTH_COOKIE = "isAuthenticated";
const ROLE_COOKIE = "userRole";
const SESSION_EXPIRED_COOKIE = "sessionExpired";

const LOGIN_PATH = "/login";
const REGISTER_PATH = "/register";
const FORBIDDEN_PATH = "/403";
const HOME_PATH = "/";

/**
 * True only in `next dev` (NODE_ENV === 'development').
 *
 * Wrapped in a helper so test setups can stub `process.env.NODE_ENV` via
 * `vi.stubEnv('NODE_ENV', 'development')` without forcing module reset,
 * and so future static analysis can grep the dev-only gate.
 */
function isDevRuntime(): boolean {
  return process.env.NODE_ENV === "development";
}

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
 * Attach per-request CSP + nonce request header (TSK-222 / US-080) — DEV ONLY.
 *
 * In produzione static export (`output: 'export'`, ADR-009) il proxy
 * non gira affatto: la CSP è emessa dal backend tramite
 * `SecurityHeadersConfig` (TSK-221) con policy statica equivalente.
 * Qui il nonce per-request serve solo a mantenere parità minima in
 * `next dev` (gap tracciato in
 * `wiki/gaps.md §fe-middleware-static-export-conflict`, accettato in
 * ADR-026).
 */
function withCspHeaders(
  request: NextRequest,
  response: NextResponse,
  nonce: string,
): NextResponse {
  const requestHeaders = new Headers(request.headers);
  requestHeaders.set(CSP_NONCE_HEADER, nonce);
  response.headers.set(
    "Content-Security-Policy",
    buildContentSecurityPolicy(nonce, { devMode: true }),
  );
  return response;
}

function nextWithCsp(request: NextRequest): NextResponse {
  const nonce = generateCspNonce();
  const requestHeaders = new Headers(request.headers);
  requestHeaders.set(CSP_NONCE_HEADER, nonce);

  const response = NextResponse.next({
    request: { headers: requestHeaders },
  });
  return withCspHeaders(request, response, nonce);
}

function redirectWithCsp(
  request: NextRequest,
  url: URL | string,
): NextResponse {
  const nonce = generateCspNonce();
  const response = NextResponse.redirect(url);
  return withCspHeaders(request, response, nonce);
}

/**
 * AuthGuard proxy (TSK-206 / US-073 / EP-017) — DEV-ONLY (ADR-026 / TSK-268).
 *
 * UX improvement — NOT a security boundary. Every protected backend endpoint
 * independently verifies auth & authz server-side (defense-in-depth).
 *
 * Production runtime:
 *  - Bundle servito dal backend con `output: 'export'`: questo proxy
 *    NON viene eseguito (nessun runtime FE in prod).
 *  - L'AuthGuard di produzione è implementato client-side: vedi
 *    `components/auth/ClientAuthGuard.tsx` + `hooks/use-auth-guard.ts`
 *    (TSK-266 / US-087).
 *  - CSP è emessa dal backend (`SecurityHeadersConfig`, TSK-221).
 *
 * Per qualunque runtime diverso da `next dev` (`NODE_ENV !== 'development'`),
 * la funzione esegue un pass-through esplicito senza side-effect: niente
 * lookup route, niente CSP, niente redirect. È un guard-rail di hardening
 * (TSK-268) che impedisce a `proxy.ts` di diventare implicitamente
 * il controllo auth di produzione se un futuro cambio di config rimuove
 * `output: 'export'` senza ridisegnare il perimetro.
 *
 * Decision table (eseguita SOLO in dev):
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
 */
export function proxy(request: NextRequest): NextResponse {
  if (!isDevRuntime()) {
    return NextResponse.next();
  }

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
    const response = redirectWithCsp(request, loginUrl);
    response.cookies.delete(SESSION_EXPIRED_COOKIE);
    response.cookies.delete(AUTH_COOKIE);
    return response;
  }

  // 2. Authenticated user visiting /login or /register → redirect home
  if (isAuthenticated && isAuthPage(pathname)) {
    return redirectWithCsp(request, new URL(HOME_PATH, request.url));
  }

  // 3–4. Lookup declarative route config (route-config.ts, TSK-205)
  const routeConfig = getRouteConfig(normalised);

  if (!routeConfig || !routeConfig.requiresAuth) {
    return nextWithCsp(request);
  }

  // 5. Protected route, not authenticated → redirect to login with returnUrl
  if (!isAuthenticated) {
    const loginUrl = new URL(LOGIN_PATH, request.url);
    const returnUrl = pathname + search;
    loginUrl.searchParams.set("returnUrl", returnUrl);
    return redirectWithCsp(request, loginUrl);
  }

  // 6. Role check (only when the route declares required roles)
  const requiredRoles = routeConfig.roles ?? [];
  if (requiredRoles.length > 0 && !requiredRoles.includes(userRole)) {
    return redirectWithCsp(request, new URL(FORBIDDEN_PATH, request.url));
  }

  // 7. All checks passed
  return nextWithCsp(request);
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
