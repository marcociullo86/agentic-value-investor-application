'use client';

import { FileText, ExternalLink } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import type { FilingRef } from '@/lib/api/deep-analysis';

export interface EdgarFilingLinksProps {
  readonly filings: readonly FilingRef[];
}

function buildSecUrl(filing: FilingRef): string {
  const formatted = filing.accessionNumber.replaceAll('-', '');
  return `https://www.sec.gov/Archives/edgar/data/${formatted}/${filing.accessionNumber}-index.htm`;
}

/**
 * Formatta la filing date in modo robusto. Il backend può inviare
 * "1970-01-01" (LocalDate.EPOCH) quando la data non è disponibile o è stata
 * scritta da un blob stale: in quel caso, e per date assenti/non valide,
 * mostriamo "n/d" invece di un fuorviante 01/01/1970.
 */
function formatFilingDate(raw: string | null | undefined): string {
  if (raw === null || raw === undefined || raw.length === 0) return 'n/d';
  const parsed = new Date(raw);
  if (Number.isNaN(parsed.getTime())) return 'n/d';
  // Epoch (o date pre-1971) = sentinella "data non disponibile".
  if (parsed.getUTCFullYear() <= 1970) return 'n/d';
  return parsed.toLocaleDateString('it-IT');
}

export function EdgarFilingLinks({
  filings,
}: EdgarFilingLinksProps): React.ReactElement {
  return (
    <Card data-testid="edgar-filing-section">
      <CardHeader>
        <CardTitle as="h2">Filing SEC analizzati</CardTitle>
      </CardHeader>
      <CardContent>
        {filings.length > 0 ? (
          <ul className="flex flex-col gap-2" data-testid="edgar-filing-list">
            {filings.map((filing) => (
              <li
                key={filing.accessionNumber}
                className="flex items-center gap-3 rounded-md border border-slate-100 px-3 py-2 transition hover:bg-slate-50 dark:border-slate-800 dark:hover:bg-slate-800/50"
              >
                <FileText
                  className="h-4 w-4 shrink-0 text-slate-400"
                  aria-hidden="true"
                />
                <div className="flex min-w-0 flex-1 flex-col gap-0.5 sm:flex-row sm:items-center sm:gap-3">
                  <span className="shrink-0 text-sm font-medium text-slate-900 dark:text-slate-100">
                    {filing.formType}
                  </span>
                  <span className="truncate text-xs text-slate-500 dark:text-slate-400">
                    {filing.accessionNumber}
                  </span>
                  <span className="text-xs text-slate-500 dark:text-slate-400">
                    {formatFilingDate(filing.filingDate)}
                  </span>
                </div>
                <a
                  href={buildSecUrl(filing)}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex shrink-0 items-center gap-1 text-sm font-medium text-blue-600 hover:text-blue-800 dark:text-blue-400 dark:hover:text-blue-300"
                  aria-label={`Apri filing ${filing.formType} del ${formatFilingDate(filing.filingDate)} su SEC.gov`}
                >
                  SEC.gov
                  <ExternalLink className="h-3 w-3" aria-hidden="true" />
                </a>
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-sm text-slate-500 dark:text-slate-400">
            Nessun filing utilizzato.
          </p>
        )}
      </CardContent>
    </Card>
  );
}
