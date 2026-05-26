'use client';

import { Suspense, useState } from 'react';
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
import { useAuthStore } from '@/lib/stores/useAuthStore';

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
 * Login page (TSK-034, TSK-201, TSK-207).
 * Posts to `POST /api/auth/login` via useAuthStore.
 * Shows a "session expired" banner when redirected with `?expired=true`.
 * Reference: design_&_architecture/components/frontend-components.md §app/(auth)/login.
 */
export default function LoginPage(): React.ReactElement {
  return (
    <Suspense>
      <LoginContent />
    </Suspense>
  );
}

function LoginContent(): React.ReactElement {
  const router = useRouter();
  const searchParams = useSearchParams();
  const login = useAuthStore((s) => s.login);
  const [serverError, setServerError] = useState<string | null>(null);

  const expired = searchParams.get('expired') === 'true';

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    mode: 'onSubmit',
  });

  async function onSubmit(data: LoginFormValues): Promise<void> {
    setServerError(null);
    try {
      await login(data.email, data.password);
      router.push('/');
    } catch (err) {
      const message =
        err instanceof Error ? err.message : 'Login failed, please try again';
      setServerError(
        message.includes('401') ? 'Email o password non validi' : message,
      );
    }
  }

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

          <Button
            type="submit"
            disabled={isSubmitting}
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
