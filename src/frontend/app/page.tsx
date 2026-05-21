import Link from 'next/link';
import { Button } from '@/components/ui/Button';

/**
 * Landing minimale (TSK-030 placeholder).
 *
 * I componenti reali (SearchBar US-001, TrafficLightPanel US-014, etc.) arrivano
 * nei TSK FE successivi: TSK-003 (SearchBar), TSK-021 (TrafficLightPanel),
 * TSK-024 (HistoricalChart), TSK-027 (MoatChecklist), TSK-034 (auth).
 */
export default function HomePage() {
  return (
    <main className="mx-auto flex min-h-screen max-w-3xl flex-col items-center justify-center gap-6 px-6 py-16 text-center">
      <h1 className="text-4xl font-bold tracking-tight sm:text-5xl">
        Value Investing WebApp
      </h1>
      <p className="max-w-xl text-lg text-slate-600 dark:text-slate-300">
        Analisi quantitative Graham/Buffett su titoli quotati USA. Cerca un
        ticker per iniziare la valutazione fondamentale.
      </p>
      <div className="flex flex-wrap items-center justify-center gap-3">
        <Button asChild variant="primary" size="lg">
          <Link href="/search">Cerca un titolo</Link>
        </Button>
        <Button asChild variant="ghost" size="lg">
          <Link href="/screener">Apri screener</Link>
        </Button>
      </div>
      <p className="mt-8 text-xs text-slate-500">
        Versione {process.env.NEXT_PUBLIC_BUILD_VERSION ?? '0.1.0-dev'} —
        backend{' '}
        <code className="rounded bg-slate-100 px-1 py-0.5 dark:bg-slate-800">
          {process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080'}
        </code>
      </p>
    </main>
  );
}
