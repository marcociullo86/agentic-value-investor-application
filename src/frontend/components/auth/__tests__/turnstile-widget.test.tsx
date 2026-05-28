import { afterEach, beforeEach, describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { TurnstileWidget } from '../turnstile-widget';

/**
 * Behavioural surface (TSK-238):
 *  - Without `NEXT_PUBLIC_TURNSTILE_SITE_KEY` the widget renders a
 *    fail-closed banner so the surrounding form keeps the submit
 *    blocked.
 *  - With a site key set, the widget mounts the host container with
 *    the well-known `data-testid="turnstile-widget"` so end-to-end
 *    tests and parent forms can assert presence without touching
 *    Cloudflare's third-party DOM.
 *
 * The widget effect lazily injects the loader script, which we do
 * not need to exercise here — the unit boundary is "did the widget
 * mount the host container or the misconfig fallback?". Cross-origin
 * loader behaviour is covered by the e2e CSP smoke (`auth-csp-csrf.spec.ts`).
 */
describe('TurnstileWidget (TSK-238)', () => {
  const originalSiteKey = process.env.NEXT_PUBLIC_TURNSTILE_SITE_KEY;

  beforeEach(() => {
    vi.resetModules();
  });

  afterEach(() => {
    if (originalSiteKey === undefined) {
      delete process.env.NEXT_PUBLIC_TURNSTILE_SITE_KEY;
    } else {
      process.env.NEXT_PUBLIC_TURNSTILE_SITE_KEY = originalSiteKey;
    }
    vi.restoreAllMocks();
  });

  it('renders the fail-closed misconfig banner when the site key is missing', () => {
    delete process.env.NEXT_PUBLIC_TURNSTILE_SITE_KEY;

    render(<TurnstileWidget onToken={vi.fn()} />);

    const fallback = screen.getByTestId('turnstile-misconfigured');
    expect(fallback).toBeInTheDocument();
    expect(fallback).toHaveAttribute('role', 'alert');
    expect(screen.queryByTestId('turnstile-widget')).not.toBeInTheDocument();
  });

  it('renders the host container when a site key is configured', () => {
    process.env.NEXT_PUBLIC_TURNSTILE_SITE_KEY = '1x00000000000000000000AA';

    render(<TurnstileWidget onToken={vi.fn()} />);

    const host = screen.getByTestId('turnstile-widget');
    expect(host).toBeInTheDocument();
    expect(host).toHaveAttribute(
      'aria-label',
      'Verifica anti-bot Cloudflare Turnstile',
    );
    expect(
      screen.queryByTestId('turnstile-misconfigured'),
    ).not.toBeInTheDocument();
  });
});
