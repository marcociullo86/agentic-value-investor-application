'use client';

import Link from 'next/link';
import { Moon, Sun } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { useAuthStore } from '@/lib/stores/useAuthStore';
import { useTheme } from '@/hooks/use-theme';
import { useLogout } from '@/hooks/use-logout';

/**
 * Navbar (TSK-034 + TSK-187 + TSK-217). Shows email + logout when
 * authenticated, otherwise links to login/register. Includes theme toggle.
 */
export function Navbar(): React.ReactElement {
  const accessToken = useAuthStore((s) => s.accessToken);
  const user = useAuthStore((s) => s.user);
  const { logout } = useLogout();
  const { theme, toggleTheme } = useTheme();

  const resolvedDark =
    theme === 'dark' ||
    (theme === 'system' &&
      typeof window !== 'undefined' &&
      window.matchMedia('(prefers-color-scheme: dark)').matches);

  async function handleLogout(): Promise<void> {
    await logout();
  }

  return (
    <header className="border-b border-outline-variant bg-surface">
      <nav aria-label="Navigazione principale" className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-6 py-3">
        <Link href="/" className="text-sm font-semibold tracking-tight">
          Value Investing
        </Link>
        <div className="flex items-center gap-3 text-sm">
          <Link
            href="/screener"
            className="text-on-surface/70 hover:text-on-surface"
          >
            Screener
          </Link>
          <Link
            href="/top-picks"
            className="text-on-surface/70 hover:text-on-surface"
            data-testid="nav-top-picks"
          >
            Top Picks
          </Link>
          {accessToken ? (
            <>
              <Link
                href="/watchlist"
                className="text-on-surface/70 hover:text-on-surface"
                data-testid="nav-watchlist"
              >
                Watchlist
              </Link>
              <span
                className="text-on-surface/60"
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
                className="text-on-surface/70 hover:text-on-surface"
                data-testid="nav-login"
              >
                Accedi
              </Link>
              <Button asChild variant="primary" size="sm">
                <Link href="/register">Registrati</Link>
              </Button>
            </>
          )}
          <button
            type="button"
            onClick={toggleTheme}
            className="ml-1 rounded-md p-2 text-on-surface/70 hover:bg-surface-container hover:text-on-surface focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-surface"
            aria-label={resolvedDark ? 'Attiva tema chiaro' : 'Attiva tema scuro'}
            data-testid="theme-toggle"
          >
            {resolvedDark ? (
              <Sun className="h-4 w-4" aria-hidden="true" />
            ) : (
              <Moon className="h-4 w-4" aria-hidden="true" />
            )}
          </button>
        </div>
      </nav>
    </header>
  );
}
