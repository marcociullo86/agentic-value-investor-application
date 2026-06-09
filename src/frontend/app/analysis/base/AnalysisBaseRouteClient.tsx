'use client';

import { useSearchParams } from 'next/navigation';
import { AnalysisPageClient } from '@/components/analysis/AnalysisPageClient';

/**
 * AnalysisBaseRouteClient — TSK-342 (US-104, EP-024 Fase 2).
 *
 * Client island per la rotta `/analysis/base?ticker=…` (tab Analisi Base
 * esplicito). Estrae il ticker dalla query string e delega il rendering al
 * componente `AnalysisPageClient` esistente (Rule Engine + DCF + MoS +
 * Historical) — invariato in funzionalità rispetto a pre-EP-024 Fase 2,
 * cambia solo il percorso di accesso.
 *
 * NOTA: la nav tab interna a `AnalysisPageClient` non include ancora un
 * marker "Riepilogo" / la nuova nav `AnalysisTabNav`. Per evitare drift
 * di scope su US-104 (che si concentra sulla creazione del tab Riepilogo
 * + routing default), il refactor della nav interna di `AnalysisPageClient`
 * verso `AnalysisTabNav` è demandato a follow-up dedicato (gap
 * `analysis-base-tab-nav-refactor`). Lo stesso vale per
 * `DeepAnalysisPageClient` / `TechnicalAnalysisPageClient`: il marker tab
 * "Riepilogo" verrà introdotto in coda — non blocca il default landing
 * (che è il primo AC di US-104).
 *
 * Pattern coerente con `DeepAnalysisPageClient` / `TechnicalAnalysisPageClient`:
 * Server Component (`page.tsx`) → ClientAuthGuard → Suspense → island client.
 */
export function AnalysisBaseRouteClient(): React.ReactElement {
  const params = useSearchParams();
  const ticker = (params?.get('ticker') ?? '').trim().toUpperCase();

  if (!ticker) {
    return (
      <main className="mx-auto max-w-3xl px-6 py-12 text-center">
        <h1 className="sr-only">Analisi Base</h1>
        <p className="text-sm text-on-surface/60">
          Specifica un ticker (es. <code>/analysis/base?ticker=AAPL</code>).
        </p>
      </main>
    );
  }

  return <AnalysisPageClient ticker={ticker} />;
}
