'use client';

import { Suspense, useCallback, useMemo, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Card } from '@/components/ui/Card';
import { FormErrorSummary } from '@/components/forms/form-error-summary';
import { FormField } from '@/components/forms/form-field';
import { MfaChallengeForm } from '@/components/auth/mfa-challenge-form';
import { TurnstileWidget } from '@/components/auth/turnstile-widget';
import { useAuthStore } from '@/lib/stores/useAuthStore';
import { isCaptchaRequiredError } from '@/lib/auth/captcha-error';
import { getAuthFormErrorMessage } from '../_lib/form-errors';

const loginSchema = z.object({
  email: z
    .string()
    .min(1, 'L\'email è obbligatoria')
    .email('Inserisci un indirizzo email valido'),
  password: z
    .string()
    .min(1, 'La password è obbligatoria')
    .min(12, 'La password deve essere di almeno 12 caratteri'),
});

type LoginFormValues = z.infer<typeof loginSchema>;

const FIELD_LABELS: Record<string, string> = {
  email: 'Email',
  password: 'Password',
};

/**
 * Login page (TSK-034, TSK-201, TSK-207, TSK-238, TSK-267).
 *
 * Posts to `POST /api/auth/login` via useAuthStore.
 *
 * Shows a "session expired" banner when redirected with `?expired=true`
 * (TSK-267 / ADR-026 §session-expired branch).
 *
 * Consumes `?returnUrl=<path+query>` and redirects there after a
 * successful login (TSK-267 / US-087 AC §returnUrl post-login).
 * `returnUrl` is validated as a same-origin pathname to prevent
 * open-redirect attacks (any value not starting with a single `/` is
 * ignored and falls back to `/`).
 *
 * TSK-238 (US-081 / ADR-025 §5): when the BE flags the IP with
 * `captchaRequired: true` (after 10+ login failures from the same
 * IP in 5 min), the page mounts a Cloudflare Turnstile widget and
 * blocks resubmission until the user solves it. The Turnstile token
 * is then forwarded to /login as `captchaToken` so the BE can
 * verify it via siteverify (TSK-230).
 *
 * Reference: design_&_architecture/components/frontend-components.md §app/(auth)/login.
 * [^src: design_&_architecture/decisions/ADR-026-frontend-authguard-static-export-runtime.md §Decisione]
 */
export default function LoginPage(): React.ReactElement {
  return (
    <Suspense>
      <LoginContent />
    </Suspense>
  );
}

/**
 * Returns the requested `returnUrl` if it is a safe same-origin path
 * (starts with `/`, not `//`, not `/login*`), otherwise `/`.
 *
 * The guard mirrors the constraints imposed by
 * `buildLoginUrl(... )` (auth-guard-decision.ts) which never emits a
 * `returnUrl` pointing back at `/login`. Keeping the validation here
 * defends against tampered query strings (clipboard-pasted URLs,
 * cross-tab opens) trying to coerce a redirect to a third-party origin.
 */
export function resolveSafeReturnUrl(raw: string | null): string {
  if (!raw) return '/';
  if (!raw.startsWith('/')) return '/';
  if (raw.startsWith('//')) return '/';
  if (raw === '/login' || raw.startsWith('/login?') || raw.startsWith('/login/')) {
    return '/';
  }
  return raw;
}

/**
 * Tracks the MFA challenge state when /api/auth/login responded with
 * `mfaRequired: true`. The login page swaps the credentials form for an
 * `MfaChallengeForm` until the challenge succeeds (and the user is then
 * redirected to `/`).
 */
type MfaChallenge = { readonly mfaToken: string; readonly email: string };

function LoginContent(): React.ReactElement {
  const router = useRouter();
  const searchParams = useSearchParams();
  const login = useAuthStore((s) => s.login);
  const [serverError, setServerError] = useState<string | null>(null);
  const [mfaChallenge, setMfaChallenge] = useState<MfaChallenge | null>(null);
  // CAPTCHA gate: the BE never returns `captchaRequired` on the
  // success path — it can only be discovered by attempting the
  // request and catching the 401 ProblemDetail (see
  // `isCaptchaRequiredError`). Once true, it stays true for the
  // remainder of this page mount so the widget keeps gating
  // subsequent attempts even after a token has been consumed.
  const [captchaRequired, setCaptchaRequired] = useState<boolean>(false);
  const [captchaToken, setCaptchaToken] = useState<string | null>(null);

  const expired = searchParams.get('expired') === 'true';
  const returnUrl = useMemo(
    () => resolveSafeReturnUrl(searchParams.get('returnUrl')),
    [searchParams],
  );

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    mode: 'onSubmit',
  });

  // Stable callbacks so TurnstileWidget's effect dependency array
  // does not retrigger render() on each parent re-render.
  const handleCaptchaToken = useCallback((token: string): void => {
    setCaptchaToken(token);
  }, []);
  const handleCaptchaInvalidate = useCallback((): void => {
    setCaptchaToken(null);
  }, []);

  async function onSubmit(data: LoginFormValues): Promise<void> {
    setServerError(null);
    try {
      const result = await login(data.email, data.password, captchaToken);
      // The captcha token is single-use; whether the call succeeded,
      // failed for credentials, or pivoted into MFA, the BE has now
      // consumed (or rejected) it — clear locally so a stale value
      // never reaches a subsequent attempt.
      setCaptchaToken(null);
      if (result.type === 'mfa-required') {
        setMfaChallenge({ mfaToken: result.mfaToken, email: data.email });
        return;
      }
      router.push(returnUrl);
    } catch (err) {
      setCaptchaToken(null);
      if (isCaptchaRequiredError(err)) {
        // Generic credentials message keeps parity with the regular
        // 401 path — the only signal the user gets that the gate
        // tripped is the appearance of the widget itself, mirroring
        // ADR-025 §5 (no enumeration of brute-force counters).
        setCaptchaRequired(true);
        setServerError(getAuthFormErrorMessage(err, 'login'));
        return;
      }
      setServerError(getAuthFormErrorMessage(err, 'login'));
    }
  }

  if (mfaChallenge) {
    return (
      <main className="mx-auto flex min-h-screen max-w-md flex-col items-center justify-center px-6">
        <Card className="w-full p-6">
          <MfaChallengeForm
            mfaToken={mfaChallenge.mfaToken}
            email={mfaChallenge.email}
            onSuccess={() => router.push(returnUrl)}
          />
          <button
            type="button"
            className="mt-4 block w-full text-center text-sm text-slate-600 hover:underline"
            onClick={() => setMfaChallenge(null)}
            data-testid="mfa-cancel"
          >
            Torna al login
          </button>
        </Card>
      </main>
    );
  }

  // While the captcha gate is active, the submit button must wait
  // for a fresh token. If the user is mid-submission we keep the
  // disabled state from `isSubmitting` regardless.
  const submitDisabled =
    isSubmitting || (captchaRequired && captchaToken === null);

  return (
    <main className="mx-auto flex min-h-screen max-w-md flex-col items-center justify-center px-6">
      <Card className="w-full p-6">
        <h1 className="mb-4 text-2xl font-bold">Accedi</h1>

        {expired && (
          <div
            role="alert"
            data-testid="session-expired-alert"
            className="mb-4 rounded-md border border-warning/30 bg-warning/10 px-3 py-2 text-sm text-warning"
          >
            La tua sessione è scaduta. Effettua nuovamente l&apos;accesso.
          </div>
        )}

        <form
          className="flex flex-col gap-4"
          onSubmit={handleSubmit(onSubmit)}
          noValidate
        >
          <FormErrorSummary errors={errors} fieldLabels={FIELD_LABELS} />

          {serverError && (
            <div
              role="alert"
              className="rounded-md border border-error/30 bg-error/5 px-3 py-2 text-sm text-error"
            >
              {serverError}
            </div>
          )}

          <FormField
            name="email"
            label="Email"
            error={errors.email?.message}
          >
            <Input
              id="email"
              type="email"
              autoComplete="email"
              error={!!errors.email}
              aria-describedby={errors.email ? 'email-error' : undefined}
              data-testid="login-email"
              {...register('email')}
            />
          </FormField>

          <FormField
            name="password"
            label="Password"
            error={errors.password?.message}
          >
            <Input
              id="password"
              type="password"
              autoComplete="current-password"
              error={!!errors.password}
              aria-describedby={errors.password ? 'password-error' : undefined}
              data-testid="login-password"
              {...register('password')}
            />
          </FormField>

          {captchaRequired && (
            <div data-testid="login-captcha" className="flex flex-col gap-2">
              <p id="login-captcha-hint" className="text-sm text-slate-600">
                Per motivi di sicurezza, completa la verifica anti-bot.
              </p>
              <TurnstileWidget
                onToken={handleCaptchaToken}
                onInvalidate={handleCaptchaInvalidate}
                action="login"
              />
            </div>
          )}

          <Button
            type="submit"
            disabled={submitDisabled}
            aria-describedby={captchaRequired ? 'login-captcha-hint' : undefined}
            data-testid="login-submit"
          >
            {isSubmitting ? 'Accesso in corso…' : 'Accedi'}
          </Button>
        </form>
        <p className="mt-4 text-center text-sm text-slate-600">
          Non hai un account?{' '}
          <Link href="/register" className="text-blue-600 hover:underline">
            Registrati
          </Link>
        </p>
      </Card>
    </main>
  );
}
