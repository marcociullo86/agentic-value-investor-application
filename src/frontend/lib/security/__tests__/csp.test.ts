import { describe, expect, it, vi } from 'vitest';

import {
  buildContentSecurityPolicy,
  CSP_NONCE_HEADER,
  generateCspNonce,
} from '../csp';

describe('CSP helpers (TSK-222)', () => {
  it('exposes x-nonce header name for middleware → layout propagation', () => {
    expect(CSP_NONCE_HEADER).toBe('x-nonce');
  });

  it('generates a UUID nonce per call', () => {
    vi.stubGlobal('crypto', {
      randomUUID: () => 'test-nonce-uuid',
    });

    expect(generateCspNonce()).toBe('test-nonce-uuid');
    expect(generateCspNonce()).toBe('test-nonce-uuid');

    vi.unstubAllGlobals();
  });

  it('builds strict script-src with nonce (production / CI build)', () => {
    const policy = buildContentSecurityPolicy('abc-123');

    expect(policy).toContain("script-src 'self' 'nonce-abc-123'");
    expect(policy).not.toMatch(/script-src[^;]*unsafe-inline/);
    expect(policy).toContain("connect-src 'self'");
    expect(policy).toContain("style-src 'self' 'unsafe-inline'");
    expect(policy).toContain("frame-src 'none'");
    expect(policy).toContain("object-src 'none'");
  });

  it('relaxes script-src and connect-src in dev mode for Next.js HMR', () => {
    const policy = buildContentSecurityPolicy('abc-123', { devMode: true });

    expect(policy).toContain("script-src 'self' 'nonce-abc-123' 'unsafe-eval' 'unsafe-inline'");
    expect(policy).toContain("connect-src 'self' ws: wss:");
  });
});
