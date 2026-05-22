'use client';

import { useRouter } from 'next/navigation';
import { useState, type FormEvent } from 'react';
import Link from 'next/link';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Card } from '@/components/ui/Card';
import { register as apiRegister } from '@/lib/api/auth';
import { useAuthStore } from '@/lib/stores/useAuthStore';

/**
 * Register page (TSK-034). Posts to `POST /api/auth/register`, then auto-logs
 * the user in via the auth store.
 * Reference: design_&_architecture/components/frontend-components.md §app/(auth)/register.
 */
export default function RegisterPage(): React.ReactElement {
  const router = useRouter();
  const login = useAuthStore((s) => s.login);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    setError(null);
    if (password.length < 12) {
      setError('La password deve essere di almeno 12 caratteri');
      return;
    }
    setSubmitting(true);
    try {
      await apiRegister({
        email,
        password,
        displayName: displayName.trim() || null,
      });
      await login(email, password);
      router.push('/');
    } catch (err) {
      const message =
        err instanceof Error ? err.message : 'Registrazione fallita';
      if (message.includes('409')) {
        setError('Email già registrata');
      } else {
        setError(message);
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="mx-auto flex min-h-screen max-w-md flex-col items-center justify-center px-6">
      <Card className="w-full p-6">
        <h1 className="mb-4 text-2xl font-bold">Crea un account</h1>
        <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
          <label className="flex flex-col gap-1 text-sm">
            <span>Email</span>
            <Input
              type="email"
              required
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              data-testid="register-email"
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            <span>Password (min 12 caratteri)</span>
            <Input
              type="password"
              required
              minLength={12}
              autoComplete="new-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              data-testid="register-password"
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            <span>Nome (opzionale)</span>
            <Input
              type="text"
              maxLength={120}
              autoComplete="name"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              data-testid="register-displayname"
            />
          </label>
          {error && (
            <div
              role="alert"
              className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700"
            >
              {error}
            </div>
          )}
          <Button
            type="submit"
            disabled={submitting}
            data-testid="register-submit"
          >
            {submitting ? 'Registrazione…' : 'Registrati'}
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
