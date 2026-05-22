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

/**
 * `output: 'export'` in next.config.js forces all dynamic segments to be
 * statically enumerated at build time via `generateStaticParams`. The actual
 * data fetch is client-side (`AnalysisPageClient` -> Zustand store -> /api),
 * so we don't actually pre-render per-ticker HTML; we only need to declare
 * which ticker URLs the static export will materialise as folder shells.
 *
 * Returning a representative US large-cap set covers the E2E suite (AAPL) and
 * the most common demo tickers. The list is intentionally short — real prod
 * deployments either drop `output: 'export'` for a server runtime or feed a
 * richer list from a build-time data source (gap `fe-static-export-tickers`).
 */
export async function generateStaticParams(): Promise<Array<{ ticker: string }>> {
  return [
    { ticker: 'AAPL' },
    { ticker: 'MSFT' },
    { ticker: 'GOOGL' },
    { ticker: 'AMZN' },
    { ticker: 'META' },
    { ticker: 'NVDA' },
    { ticker: 'TSLA' },
    { ticker: 'BRK.B' },
  ];
}

export default async function AnalysisPage(
  props: AnalysisPageProps,
): Promise<React.ReactElement> {
  const { ticker } = await props.params;
  return <AnalysisPageClient ticker={ticker} />;
}
