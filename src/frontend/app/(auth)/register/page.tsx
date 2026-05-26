'use client';

import { useState } from 'react';
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
import { register as apiRegister } from '@/lib/api/auth';
import { useAuthStore } from '@/lib/stores/useAuthStore';

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
 * Register page (TSK-034, TSK-201). Posts to `POST /api/auth/register`, then
 * auto-logs the user in via the auth store.
 * Reference: design_&_architecture/components/frontend-components.md §app/(auth)/register.
 */
export default function RegisterPage(): React.ReactElement {
  const router = useRouter();
  const login = useAuthStore((s) => s.login);
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
    mode: 'onSubmit',
  });

  async function onSubmit(data: RegisterFormValues): Promise<void> {
    setServerError(null);
    try {
      await apiRegister({
        email: data.email,
        password: data.password,
        displayName: data.displayName?.trim() || null,
      });
      await login(data.email, data.password);
      router.push('/');
    } catch (err) {
      const message =
        err instanceof Error ? err.message : 'Registrazione fallita';
      if (message.includes('409')) {
        setServerError('Email già registrata');
      } else {
        setServerError(message);
      }
    }
  }

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

          <Button
            type="submit"
            disabled={isSubmitting}
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
