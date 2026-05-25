import { DeepAnalysisPageClient } from './DeepAnalysisPageClient';

/**
 * Deep Analysis page — TSK-122 + TSK-123 (US-046, EP-011).
 *
 * Route: /analysis/{ticker}/deep (App Router, dynamic segment).
 *
 * `output: 'export'` requires generateStaticParams for dynamic segments.
 * Empty array → no pre-rendered pages; ticker resolved at runtime via
 * useParams (client-side SPA navigation). Aligned with ADR-013.
 *
 * [^src: design_&_architecture/api/openapi.yaml §/api/analysis/{ticker}/deep]
 */

export function generateStaticParams(): { ticker: string }[] {
  return [];
}

export default function DeepAnalysisPage(): React.ReactElement {
  return <DeepAnalysisPageClient />;
}
