import Link from 'next/link';
import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Accesso negato — Value Investing WebApp',
};

/**
 * Static 403 page — shown when the user lacks the required role/permissions.
 * No technical details exposed (TSK-207 spec).
 */
export default function ForbiddenPage(): React.ReactElement {
  return (
    <main className="mx-auto flex min-h-[60vh] max-w-md flex-col items-center justify-center px-6 text-center">
      <h1 className="text-3xl font-bold text-on-surface">Accesso negato</h1>

      <p className="mt-4 text-on-surface/70">
        Non hai i permessi per accedere a questa pagina.
      </p>

      <Link
        href="/"
        className="mt-8 inline-flex items-center justify-center rounded-md bg-primary px-6 py-2.5 text-sm font-medium text-on-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-surface"
        data-testid="forbidden-back-home"
      >
        Torna alla dashboard
      </Link>
    </main>
  );
}
