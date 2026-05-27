/**
 * Content-Security-Policy helpers (US-080, TSK-222).
 *
 * FE policy mirrors ADR-025 §2 with per-request `script-src` nonce;
 * BE static CSP lives in `SecurityHeadersConfig` (TSK-221).
 */
export const CSP_NONCE_HEADER = 'x-nonce';

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
 */
export function buildContentSecurityPolicy(
  nonce: string,
  options: CspOptions = {},
): string {
  const devMode = options.devMode ?? false;
  const scriptSrc = devMode
    ? `script-src 'self' 'nonce-${nonce}' 'unsafe-eval' 'unsafe-inline'`
    : `script-src 'self' 'nonce-${nonce}'`;
  const connectSrc = devMode ? "connect-src 'self' ws: wss:" : "connect-src 'self'";

  return [
    "default-src 'self'",
    scriptSrc,
    "style-src 'self' 'unsafe-inline'",
    "img-src 'self' data: https:",
    connectSrc,
    "font-src 'self'",
    "frame-src 'none'",
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
  ].join('; ');
}
