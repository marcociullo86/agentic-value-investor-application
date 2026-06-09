'use client';

import { BookOpen } from 'lucide-react';
import { cn } from '@/lib/utils/cn';

/**
 * WikiCitationsFooter — TSK-335 (US-101, EP-024 Fase 1).
 *
 * Footer con citazioni alle pagine wiki TA principali (AC US-101 §Layout 8).
 *
 * SCELTA TECNICA — link "esterni" alla wiki:
 *   La wiki Markdown (`wiki/concepts/*.md`, `wiki/syntheses/*.md`) NON è
 *   ancora esposta come pagine HTML dall'app FE (nessuna rotta
 *   `/wiki/[slug]` nell'App Router). Pattern adottato:
 *
 *   - Mostriamo i titoli citati come `<span>` non cliccabili (text-only)
 *     con icona `<BookOpen>` + tooltip "Disponibile come pagina Markdown
 *     in `wiki/...`". Coerente con [[ta-vs-vi-decision-layer]] §"Trasparenza"
 *     che chiede trasparenza sulle fonti, non navigazione.
 *
 *   - Quando la `wikiCitations` proviene dal `entryTimingAdvisor.rationale`
 *     (TSK-332 / US-099), conserviamo l'ordine e il formato originale BE
 *     (es. "wiki/syntheses/ta-entry-timing-stock-detail.md" o
 *     "[[ta-entry-timing-stock-detail]]") senza ri-parsing semantico.
 *
 *   - Aggiungiamo SEMPRE come baseline 4 link canonici alle pagine wiki TA
 *     primarie elencate in US-101 §Layout 8, anche quando il BE non popola
 *     citazioni custom (es. INDETERMINATE).
 *
 *   GAP fe-wiki-html-rendering (aperto): l'evoluzione naturale è una rotta
 *   `/wiki/[domain]/[slug]` con renderer Markdown server-side; quando
 *   esisterà, il componente diventerà `<Link href={wikiUrl(citation)}>`
 *   senza altri cambi al consumer.
 *
 * Accessibility (WCAG 2.2 AA — EP-016):
 *  - `<footer role="contentinfo">` landmark coerente.
 *  - `aria-label` esplicito sul nav delle citazioni.
 */

const CANONICAL_TA_WIKI_PAGES = [
  {
    slug: 'ta-entry-timing-stock-detail',
    path: 'wiki/syntheses/ta-entry-timing-stock-detail.md',
    title: 'Entry timing — checklist sul dettaglio ticker',
  },
  {
    slug: 'ta-stop-placement-position-sizing',
    path: 'wiki/syntheses/ta-stop-placement-position-sizing.md',
    title: 'Stop placement e position sizing (2%/6% Rule)',
  },
  {
    slug: 'elder-triple-screen-impulse-system',
    path: 'wiki/concepts/elder-triple-screen-impulse-system.md',
    title: 'Elder — Triple Screen e Impulse System',
  },
  {
    slug: 'elder-risk-management-2pct-6pct',
    path: 'wiki/concepts/elder-risk-management-2pct-6pct.md',
    title: 'Elder — Risk Management 2% / 6% Rule',
  },
] as const;

/** Normalizza una citazione BE in slug confrontabile. */
function normalizeCitation(raw: string): string {
  return raw
    .trim()
    .toLowerCase()
    .replace(/^\[\[(.+?)\]\]$/, '$1')
    .replace(/^wiki\/(concepts|syntheses)\//, '')
    .replace(/\.md$/, '');
}

export interface WikiCitationsFooterProps {
  /**
   * Citazioni custom emesse dal BE in `entryTimingAdvisor.rationale.wikiCitations`.
   * Possono essere formate come "[[slug]]" o "wiki/.../slug.md". Vengono
   * usate per evidenziare le pagine PIÙ rilevanti del verdetto corrente
   * (chip primario) mentre le pagine canoniche TA restano come riferimento
   * baseline.
   */
  readonly citations: ReadonlyArray<string>;
}

export function WikiCitationsFooter(
  props: WikiCitationsFooterProps,
): React.ReactElement {
  const { citations } = props;
  const highlighted = new Set(citations.map(normalizeCitation));

  return (
    <footer
      role="contentinfo"
      aria-label="Citazioni wiki Technical Analysis"
      data-testid="ta-wiki-citations-footer"
      className="flex flex-col gap-2 rounded-md border border-outline-variant bg-surface-container-high p-4 text-sm"
    >
      <p className="text-xs font-semibold uppercase tracking-wide text-on-surface/60">
        Pagine wiki di riferimento
      </p>
      <ul className="flex flex-wrap gap-2">
        {CANONICAL_TA_WIKI_PAGES.map((page) => {
          const isHighlighted = highlighted.has(page.slug);
          return (
            <li key={page.slug}>
              <span
                data-testid={`ta-wiki-link-${page.slug}`}
                data-highlighted={isHighlighted || undefined}
                title={`Disponibile come pagina Markdown in ${page.path}`}
                className={cn(
                  'inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-medium',
                  isHighlighted
                    ? 'border-blue-300 bg-blue-50 text-blue-900 dark:border-blue-800 dark:bg-blue-950 dark:text-blue-200'
                    : 'border-outline-variant bg-surface text-on-surface/80',
                )}
              >
                <BookOpen aria-hidden="true" className="h-3.5 w-3.5" />
                {page.title}
              </span>
            </li>
          );
        })}
      </ul>
      {citations.length > 0 ? (
        <p
          data-testid="ta-wiki-citations-custom"
          className="text-xs text-on-surface/60"
        >
          Citazioni evidenziate dal verdetto corrente: {citations.join(', ')}
        </p>
      ) : null}
    </footer>
  );
}
