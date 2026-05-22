import { SearchBar } from '@/components/search/SearchBar';

/**
 * Landing page `/` — TSK-003 (US-001).
 *
 * Server Component (Next 16 RSC default). Importa `SearchBar` come Client
 * Component (`"use client"` interno). Nessun fetch SSR — la ricerca è
 * 100% client-side, normalizza il ticker e fa GET /api/search.
 *
 * Riferimento design: design_&_architecture/components/frontend-components.md
 *   §app/layout.tsx.
 *
 * /screener (TSK-006) e ResultsList (TSK-007) sono link disabled / "coming
 * soon": vengono abilitati nei task successivi della stessa epica EP-001.
 */
export default function HomePage() {
  return (
    <main className="mx-auto flex min-h-screen max-w-3xl flex-col items-center justify-center gap-8 px-6 py-16">
      <header className="flex flex-col items-center gap-3 text-center">
        <h1 className="text-4xl font-bold tracking-tight sm:text-5xl">
          Value Investing WebApp
        </h1>
        <p className="max-w-xl text-lg text-slate-600 dark:text-slate-300">
          Analizza titoli con framework Graham/Buffett — cerca un ticker per
          avviare la valutazione fondamentale.
        </p>
      </header>

      <SearchBar />

      <div
        className="flex flex-wrap items-center justify-center gap-3 text-sm text-slate-500 dark:text-slate-400"
        aria-label="Strumenti aggiuntivi"
      >
        <span
          aria-disabled="true"
          title="Disponibile a breve (TSK-006)"
          className="cursor-not-allowed rounded-md border border-dashed border-slate-300 px-3 py-1 dark:border-slate-700"
        >
          Screener — coming soon
        </span>
      </div>

      <p className="mt-4 text-xs text-slate-500">
        Versione {process.env.NEXT_PUBLIC_BUILD_VERSION ?? '0.1.0-dev'} — backend{' '}
        <code className="rounded bg-slate-100 px-1 py-0.5 dark:bg-slate-800">
          {process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080'}
        </code>
      </p>
    </main>
  );
}
