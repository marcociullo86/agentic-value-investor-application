import { ScreenerForm } from '@/components/screener/ScreenerForm';
import { ResultsListInline } from '@/components/screener/ResultsListInline';

/**
 * Pagina `/screener` — TSK-006 (US-002).
 *
 * Server Component (Next 16 RSC). Compone i Client Components
 * `ScreenerForm` + `ResultsListInline` che leggono dallo store Zustand
 * condiviso (`useScreenerStore`).
 *
 * Layout 2-colonne (sidebar form sinistra, results destra) su desktop,
 * stack verticale su mobile.
 *
 * Riferimento design: design_&_architecture/components/frontend-components.md
 *   §screener/page.tsx.
 * Riferimento contratto: design_&_architecture/api/openapi.yaml §/api/screener.
 */
export default function ScreenerPage() {
  return (
    <main className="mx-auto flex min-h-screen max-w-7xl flex-col gap-6 px-6 py-10">
      <header className="flex flex-col gap-2">
        <h1 className="text-3xl font-bold tracking-tight">
          Screener — Filtra titoli per market cap e settore
        </h1>
        <p className="max-w-3xl text-sm text-slate-600 dark:text-slate-300">
          Restringi l&apos;universo di mercato per capitalizzazione e settore
          GICS. I candidati possono poi essere analizzati singolarmente con
          il framework Value Investing.
        </p>
      </header>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,360px)_1fr]">
        <aside aria-label="Filtri screener">
          <ScreenerForm />
        </aside>
        <section aria-label="Risultati screener" className="min-w-0">
          <ResultsListInline />
        </section>
      </div>
    </main>
  );
}
