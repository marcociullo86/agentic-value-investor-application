'use client';

import { useEffect, useId, useRef } from 'react';

/**
 * Cloudflare Turnstile widget (TSK-238 — US-081 / ADR-025 §5).
 *
 * Renders the Cloudflare Turnstile challenge in an explicitly-rendered
 * container the moment the BE surfaces `captchaRequired: true` on a
 * /api/auth/login or /api/auth/register response. The widget produces
 * a one-time-use token that the next request must replay as
 * `captchaToken` so the BE can verify it via Cloudflare siteverify
 * (server-side check is owned by `BruteForceProtectionService`,
 * TSK-230).
 *
 * ## Lifecycle
 *
 * 1. The first mount lazy-loads the Cloudflare loader script
 *    (`https://challenges.cloudflare.com/turnstile/v0/api.js`) — a
 *    single `<script>` tag is injected at most once per session and
 *    kept on subsequent renders so reopens of the widget skip the
 *    network round-trip.
 * 2. Once `window.turnstile` is available, we call `turnstile.render`
 *    in *explicit* mode and remember the widget ID so we can
 *    `remove` it on unmount (mandatory — Turnstile leaks event
 *    listeners on the host iframe otherwise).
 * 3. The widget calls our `callback(token)` on success → bubbles up
 *    via `onToken`. Errors and expirations notify `onError`/
 *    `onExpire`; both reset the parent's stored token so the form
 *    cannot accidentally submit a stale value.
 *
 * ## Test posture
 *
 * Cloudflare publishes always-pass and always-block "test" sitekeys
 * (https://developers.cloudflare.com/turnstile/troubleshooting/testing/)
 * — `1x00000000000000000000AA` is the safest default for non-prod.
 * The site key is read from `NEXT_PUBLIC_TURNSTILE_SITE_KEY` so
 * staging / production / CI can each pin their own value without a
 * code change. When the env var is missing, the widget renders a
 * fail-closed fallback that disables the surrounding form so the
 * user is not silently allowed past the gate.
 *
 * Reference: ADR-025 §5 / TSK-230 (BE counterpart).
 */

const TURNSTILE_SCRIPT_SRC =
  'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit';
const TURNSTILE_SCRIPT_ID = 'cf-turnstile-loader';

type TurnstileTheme = 'auto' | 'light' | 'dark';

interface TurnstileRenderOptions {
  readonly sitekey: string;
  readonly callback: (token: string) => void;
  readonly 'error-callback'?: () => void;
  readonly 'expired-callback'?: () => void;
  readonly 'timeout-callback'?: () => void;
  readonly theme?: TurnstileTheme;
  readonly action?: string;
}

interface TurnstileApi {
  readonly render: (
    container: HTMLElement | string,
    options: TurnstileRenderOptions,
  ) => string;
  readonly reset: (widgetId?: string) => void;
  readonly remove: (widgetId: string) => void;
}

declare global {
  interface Window {
    turnstile?: TurnstileApi;
  }
}

export interface TurnstileWidgetProps {
  /**
   * Called with a fresh, single-use Turnstile token whenever the
   * widget completes the challenge. Tokens expire after ~5 minutes
   * (and on `onExpire`) — the parent must re-issue the protected
   * request before then or treat the token as invalid.
   */
  readonly onToken: (token: string) => void;
  /**
   * Called when the widget emits an `error-callback` or
   * `expired-callback`, signalling the previous token (if any)
   * is no longer usable. The parent should clear any stored
   * captcha token in response.
   */
  readonly onInvalidate?: () => void;
  /** Visual theme. `auto` mirrors the host page's color scheme. */
  readonly theme?: TurnstileTheme;
  /**
   * Optional `action` parameter — Cloudflare logs it alongside the
   * verification so analytics can distinguish "login" from "register"
   * attempts.
   */
  readonly action?: string;
}

function loadTurnstileScript(): Promise<void> {
  if (typeof window === 'undefined') {
    return Promise.resolve();
  }
  if (window.turnstile) {
    return Promise.resolve();
  }

  return new Promise((resolve, reject) => {
    const existing = document.getElementById(
      TURNSTILE_SCRIPT_ID,
    ) as HTMLScriptElement | null;
    if (existing) {
      if (window.turnstile) {
        resolve();
        return;
      }
      existing.addEventListener('load', () => resolve(), { once: true });
      existing.addEventListener(
        'error',
        () =>
          reject(new Error('Failed to load Cloudflare Turnstile script')),
        { once: true },
      );
      return;
    }

    const script = document.createElement('script');
    script.id = TURNSTILE_SCRIPT_ID;
    script.src = TURNSTILE_SCRIPT_SRC;
    script.async = true;
    script.defer = true;
    script.addEventListener('load', () => resolve(), { once: true });
    script.addEventListener(
      'error',
      () =>
        reject(new Error('Failed to load Cloudflare Turnstile script')),
      { once: true },
    );
    document.head.appendChild(script);
  });
}

export function TurnstileWidget({
  onToken,
  onInvalidate,
  theme = 'auto',
  action,
}: TurnstileWidgetProps): React.ReactElement {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const widgetIdRef = useRef<string | null>(null);
  const fallbackId = useId();

  // The site key MUST be provided by the deploy environment. Reading
  // here (component scope) keeps the constant inlined by Next.js
  // static export at build time and avoids leaking the secret-key
  // counterpart that lives only on the BE.
  const siteKey = process.env.NEXT_PUBLIC_TURNSTILE_SITE_KEY;

  useEffect(() => {
    if (!siteKey) {
      return undefined;
    }

    let cancelled = false;

    void loadTurnstileScript()
      .then(() => {
        if (cancelled || !containerRef.current || !window.turnstile) {
          return;
        }
        // Avoid double-render under React StrictMode by clearing any
        // previous widget tied to this container before re-rendering.
        if (widgetIdRef.current) {
          try {
            window.turnstile.remove(widgetIdRef.current);
          } catch {
            // already removed — ignore
          }
          widgetIdRef.current = null;
        }
        widgetIdRef.current = window.turnstile.render(containerRef.current, {
          sitekey: siteKey,
          callback: (token: string): void => onToken(token),
          'error-callback': (): void => onInvalidate?.(),
          'expired-callback': (): void => onInvalidate?.(),
          'timeout-callback': (): void => onInvalidate?.(),
          theme,
          ...(action !== undefined ? { action } : {}),
        });
      })
      .catch(() => {
        // Loader failure is surfaced to the parent so the form can
        // stay disabled with a recoverable error state.
        onInvalidate?.();
      });

    return (): void => {
      cancelled = true;
      if (widgetIdRef.current && window.turnstile) {
        try {
          window.turnstile.remove(widgetIdRef.current);
        } catch {
          // already gone — ignore
        }
        widgetIdRef.current = null;
      }
    };
  }, [siteKey, onToken, onInvalidate, theme, action]);

  if (!siteKey) {
    return (
      <div
        role="alert"
        id={fallbackId}
        data-testid="turnstile-misconfigured"
        className="rounded-md border border-error/30 bg-error/5 px-3 py-2 text-sm text-error"
      >
        Verifica anti-bot non disponibile (configurazione mancante).
        Contatta l&apos;assistenza.
      </div>
    );
  }

  return (
    <div
      ref={containerRef}
      data-testid="turnstile-widget"
      aria-label="Verifica anti-bot Cloudflare Turnstile"
      className="flex justify-center"
    />
  );
}
