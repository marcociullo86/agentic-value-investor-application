'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { Button } from '@/components/ui/Button';
import { useAuthStore } from '@/lib/stores/useAuthStore';

/**
 * Navbar (TSK-034). Shows email + logout when authenticated, otherwise links
 * to login/register. Mounted in the root layout.
 */
export function Navbar(): React.ReactElement {
  const router = useRouter();
  const accessToken = useAuthStore((s) => s.accessToken);
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);

  async function handleLogout(): Promise<void> {
    await logout();
    router.push('/');
  }

  return (
    <header className="border-b border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-950">
      <nav className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-6 py-3">
        <Link href="/" className="text-sm font-semibold tracking-tight">
          Value Investing
        </Link>
        <div className="flex items-center gap-3 text-sm">
          <Link
            href="/screener"
            className="text-slate-600 hover:text-slate-900 dark:text-slate-300 dark:hover:text-white"
          >
            Screener
          </Link>
          <Link
            href="/top-picks"
            className="text-slate-600 hover:text-slate-900 dark:text-slate-300 dark:hover:text-white"
            data-testid="nav-top-picks"
          >
            Top Picks
          </Link>
          {accessToken ? (
            <>
              <Link
                href="/watchlist"
                className="text-slate-600 hover:text-slate-900 dark:text-slate-300 dark:hover:text-white"
                data-testid="nav-watchlist"
              >
                Watchlist
              </Link>
              <span
                className="text-slate-500"
                data-testid="nav-user-email"
                aria-label="utente autenticato"
              >
                {user?.displayName ?? user?.email ?? 'Account'}
              </span>
              <Button
                variant="ghost"
                size="sm"
                onClick={handleLogout}
                data-testid="nav-logout"
              >
                Logout
              </Button>
            </>
          ) : (
            <>
              <Link
                href="/login"
                className="text-slate-600 hover:text-slate-900 dark:text-slate-300 dark:hover:text-white"
                data-testid="nav-login"
              >
                Accedi
              </Link>
              <Button asChild variant="primary" size="sm">
                <Link href="/register">Registrati</Link>
              </Button>
            </>
          )}
        </div>
      </nav>
    </header>
  );
}
