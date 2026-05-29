'use client';

import { useCallback, useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Card } from '@/components/ui/Card';
import { FormErrorSummary } from '@/components/forms/form-error-summary';
import { FormField } from '@/components/forms/form-field';
import { TurnstileWidget } from '@/components/auth/turnstile-widget';
import { register as apiRegister } from '@/lib/api/auth';
import { useAuthStore } from '@/lib/stores/useAuthStore';
import { isCaptchaRequiredError } from '@/lib/auth/captcha-error';
import { getAuthFormErrorMessage } from '../_lib/form-errors';

const registerSchema = z
  .object({
    email: z
      .string()
      .min(1, 'L\'email è obbligatoria')
      .email('Inserisci un indirizzo email valido'),
    password: z
      .string()
      .min(1, 'La password è obbligatoria')
      .min(12, 'La password deve essere di almeno 12 caratteri'),
    confirmPassword: z
      .string()
      .min(1, 'La conferma password è obbligatoria'),
    displayName: z.string().max(120).optional(),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: 'Le password non coincidono',
    path: ['confirmPassword'],
  });

type RegisterFormValues = z.infer<typeof registerSchema>;

const FIELD_LABELS: Record<string, string> = {
  email: 'Email',
  password: 'Password',
  confirmPassword: 'Conferma password',
  displayName: 'Nome',
};

/**
 * Register page (TSK-034, TSK-201, TSK-238). Posts to
 * `POST /api/auth/register`, then auto-logs the user in via
 * the auth store.
 *
 * TSK-238 (US-081 / ADR-025 §5): the BE applies a per-IP CAPTCHA
 * gate to /register too, so this page mirrors the login flow —
 * if the BE returns 401 with `captchaRequired: true`, a Cloudflare
 * Turnstile widget is mounted and the form blocks resubmission
 * until the user solves it. The token is forwarded as
 * `captchaToken` on the next /register attempt and verified
 * server-side by `BruteForceProtectionService` (TSK-230).
 *
 * Reference: design_&_architecture/components/frontend-components.md §app/(auth)/register.
 */
export default function RegisterPage(): React.ReactElement {
  const router = useRouter();
  const login = useAuthStore((s) => s.login);
  const [serverError, setServerError] = useState<string | null>(null);
  const [captchaRequired, setCaptchaRequired] = useState<boolean>(false);
  const [captchaToken, setCaptchaToken] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
    mode: 'onSubmit',
  });

  const handleCaptchaToken = useCallback((token: string): void => {
    setCaptchaToken(token);
  }, []);
  const handleCaptchaInvalidate = useCallback((): void => {
    setCaptchaToken(null);
  }, []);

  async function onSubmit(data: RegisterFormValues): Promise<void> {
    setServerError(null);
    try {
      await apiRegister({
        email: data.email,
        password: data.password,
        displayName: data.displayName?.trim() || null,
        captchaToken,
      });
      // Single-use: clear before any subsequent call so a
      // post-register auto-login never replays a stale token.
      setCaptchaToken(null);
      await login(data.email, data.password);
      router.push('/');
    } catch (err) {
      setCaptchaToken(null);
      if (isCaptchaRequiredError(err)) {
        setCaptchaRequired(true);
        setServerError(getAuthFormErrorMessage(err, 'register'));
        return;
      }
      setServerError(getAuthFormErrorMessage(err, 'register'));
    }
  }

  const submitDisabled =
    isSubmitting || (captchaRequired && captchaToken === null);

  return (
    <main className="mx-auto flex min-h-screen max-w-md flex-col items-center justify-center px-6">
      <Card className="w-full p-6">
        <h1 className="mb-4 text-2xl font-bold">Crea un account</h1>
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
              data-testid="register-email"
              {...register('email')}
            />
          </FormField>

          <FormField
            name="password"
            label="Password (min 12 caratteri)"
            error={errors.password?.message}
          >
            <Input
              id="password"
              type="password"
              autoComplete="new-password"
              error={!!errors.password}
              aria-describedby={errors.password ? 'password-error' : undefined}
              data-testid="register-password"
              {...register('password')}
            />
          </FormField>

          <FormField
            name="confirmPassword"
            label="Conferma password"
            error={errors.confirmPassword?.message}
          >
            <Input
              id="confirmPassword"
              type="password"
              autoComplete="new-password"
              error={!!errors.confirmPassword}
              aria-describedby={
                errors.confirmPassword ? 'confirmPassword-error' : undefined
              }
              data-testid="register-confirm-password"
              {...register('confirmPassword')}
            />
          </FormField>

          <FormField
            name="displayName"
            label="Nome (opzionale)"
            error={errors.displayName?.message}
          >
            <Input
              id="displayName"
              type="text"
              maxLength={120}
              autoComplete="name"
              error={!!errors.displayName}
              aria-describedby={
                errors.displayName ? 'displayName-error' : undefined
              }
              data-testid="register-displayname"
              {...register('displayName')}
            />
          </FormField>

          {captchaRequired && (
            <div data-testid="register-captcha" className="flex flex-col gap-2">
              <p id="register-captcha-hint" className="text-sm text-slate-600">
                Per motivi di sicurezza, completa la verifica anti-bot.
              </p>
              <TurnstileWidget
                onToken={handleCaptchaToken}
                onInvalidate={handleCaptchaInvalidate}
                action="register"
              />
            </div>
          )}

          <Button
            type="submit"
            disabled={submitDisabled}
            aria-describedby={captchaRequired ? 'register-captcha-hint' : undefined}
            data-testid="register-submit"
          >
            {isSubmitting ? 'Registrazione…' : 'Registrati'}
          </Button>
        </form>
        <p className="mt-4 text-center text-sm text-slate-600">
          Hai già un account?{' '}
          <Link href="/login" className="text-blue-600 hover:underline">
            Accedi
          </Link>
        </p>
      </Card>
    </main>
  );
}
