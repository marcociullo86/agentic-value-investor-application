/**
 * Content-Security-Policy helpers (US-080, TSK-222, TSK-238).
 *
 * FE policy mirrors ADR-025 §2 with per-request `script-src` nonce;
 * BE static CSP lives in `SecurityHeadersConfig` (TSK-221).
 *
 * TSK-238 / ADR-025 §5: the policy explicitly whitelists Cloudflare
 * Turnstile origins so the per-IP CAPTCHA gate can mount its loader
 * script + iframe + siteverify-adjacent XHR. Without these, the
 * widget would silently fail under the strict default-`'self'`
 * policy and the brute-force gate would be effectively disabled
 * for the FE.
 */
export const CSP_NONCE_HEADER = 'x-nonce';

/**
 * Cloudflare Turnstile loader + challenge iframe + telemetry origin
 * (https://developers.cloudflare.com/turnstile/reference/content-security-policy/).
 * The widget loads its loader from `challenges.cloudflare.com`, then
 * mounts an iframe from the same origin which itself talks to
 * `*.cloudflare.com` for telemetry. Our CSP only needs to allow the
 * top-level `challenges.cloudflare.com` origin.
 */
const TURNSTILE_ORIGIN = 'https://challenges.cloudflare.com';

/** Per-request nonce for inline scripts (TSK-222). */
export function generateCspNonce(): string {
  return crypto.randomUUID();
}

export type CspOptions = {
  /** Relaxed policy for `next dev` (HMR needs inline scripts + ws). */
  devMode?: boolean;
};

/**
 * ADR-025 §2 / TSK-222 — `script-src` without `'unsafe-inline'` in production;
 * layout inline scripts use `'nonce-{nonce}'`. Dev mode relaxes script/connect for HMR.
 *
 * ADR-025 §5 / TSK-238 — Cloudflare Turnstile origin allow-listed on
 * `script-src`, `frame-src`, and `connect-src` so the per-IP CAPTCHA
 * widget can render and verify. Adding it to `connect-src` covers
 * the widget's own telemetry pings; siteverify itself is server-side
 * and never reaches the browser.
 */
export function buildContentSecurityPolicy(
  nonce: string,
  options: CspOptions = {},
): string {
  const devMode = options.devMode ?? false;
  const scriptSrc = devMode
    ? `script-src 'self' 'nonce-${nonce}' 'unsafe-eval' 'unsafe-inline' ${TURNSTILE_ORIGIN}`
    : `script-src 'self' 'nonce-${nonce}' ${TURNSTILE_ORIGIN}`;
  const connectSrc = devMode
    ? `connect-src 'self' ws: wss: ${TURNSTILE_ORIGIN}`
    : `connect-src 'self' ${TURNSTILE_ORIGIN}`;
  const frameSrc = `frame-src ${TURNSTILE_ORIGIN}`;

  return [
    "default-src 'self'",
    scriptSrc,
    "style-src 'self' 'unsafe-inline'",
    "img-src 'self' data: https:",
    connectSrc,
    "font-src 'self'",
    frameSrc,
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
  ].join('; ');
}
