'use client';

import { useRouter } from 'next/navigation';
import { useState, type FormEvent } from 'react';
import Link from 'next/link';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Card } from '@/components/ui/Card';
import { useAuthStore } from '@/lib/stores/useAuthStore';

/**
 * Login page (TSK-034). Posts to `POST /api/auth/login` via useAuthStore.
 * Reference: design_&_architecture/components/frontend-components.md §app/(auth)/login.
 */
export default function LoginPage(): React.ReactElement {
  const router = useRouter();
  const login = useAuthStore((s) => s.login);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(email, password);
      router.push('/');
    } catch (err) {
      const message =
        err instanceof Error ? err.message : 'Login failed, please try again';
      setError(message.includes('401') ? 'Invalid email or password' : message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="mx-auto flex min-h-screen max-w-md flex-col items-center justify-center px-6">
      <Card className="w-full p-6">
        <h1 className="mb-4 text-2xl font-bold">Accedi</h1>
        <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
          <label className="flex flex-col gap-1 text-sm">
            <span>Email</span>
            <Input
              type="email"
              required
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              data-testid="login-email"
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            <span>Password</span>
            <Input
              type="password"
              required
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              data-testid="login-password"
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
            data-testid="login-submit"
          >
            {submitting ? 'Accesso in corso…' : 'Accedi'}
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
