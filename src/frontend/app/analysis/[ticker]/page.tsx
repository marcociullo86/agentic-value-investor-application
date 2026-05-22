import { AnalysisPageClient } from '@/components/analysis/AnalysisPageClient';

/**
 * Pagina `/analysis/{ticker}` — TSK-021 (US-014).
 *
 * Server Component (Next 16 RSC default). Estrae `ticker` da `params` e
 * delega il rendering interattivo al Client Component
 * `AnalysisPageClient`, che gestisce fetch via Zustand store +
 * orchestrazione `TrafficLightPanel` + `ValuationSummary` + `HistoricalChart`.
 *
 * Next 16 ha reso `params` un `Promise` (vedi
 * https://nextjs.org/docs/app/api-reference/file-conventions/page#params-optional)
 * quindi va `await`-ato prima dell'accesso.
 *
 * Riferimento design:
 *   design_&_architecture/components/frontend-components.md
 *   §app/analysis/[ticker]/page.tsx §Routing.
 * Riferimento contratto: design_&_architecture/api/openapi.yaml
 *   §/api/analysis/{ticker}.
 */

export interface AnalysisPageProps {
  readonly params: Promise<{ readonly ticker: string }>;
}

export default async function AnalysisPage(
  props: AnalysisPageProps,
): Promise<React.ReactElement> {
  const { ticker } = await props.params;
  return <AnalysisPageClient ticker={ticker} />;
}
