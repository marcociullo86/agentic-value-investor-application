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

/**
 * ADR-025 §2 / TSK-222 — `script-src` without `'unsafe-inline'`;
 * inline Next.js / layout scripts use `'nonce-{nonce}'`.
 */
export function buildContentSecurityPolicy(nonce: string): string {
  return [
    "default-src 'self'",
    `script-src 'self' 'nonce-${nonce}'`,
    "style-src 'self' 'unsafe-inline'",
    "img-src 'self' data: https:",
    "connect-src 'self'",
    "font-src 'self'",
    "frame-src 'none'",
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
  ].join('; ');
}
