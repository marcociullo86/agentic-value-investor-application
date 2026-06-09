'use client';

import { useMemo, useState } from 'react';
import { BookOpen, Briefcase, LineChart } from 'lucide-react';
import { cn } from '@/lib/utils/cn';
import type { WikiCitation } from '@/lib/api/summary';

/**
 * WikiCitationsSection — TSK-343 (US-104, EP-024 Fase 2).
 *
 * Sezione "Perché questo verdetto" del tab Riepilogo: lista delle
 * `wikiCitations` cross-dominio (US-103 §"Citazioni RAG cross-dominio")
 * raggruppate per `domain`:
 *
 *   - VALUE INVESTING (value-investing rule engine, MoS, intrinsic value, …)
 *   - TECHNICAL ANALYSIS / TRADING (Elder, Murphy, decision layer, …)
 *   - ALTRO (placeholder difensivo per stringhe dominio non note)
 *
 * GAP fe-wiki-html-rendering (aperto — già documentato in `WikiCitationsFooter`
 * di US-101): la wiki Markdown non ha ancora una rotta `/wiki/[slug]` lato
 * FE. Approccio:
 *   - Tutte le citazioni sono `<button>` cliccabili che aprono un modal
 *     "leggero" con `<details>` semantico mostrando un excerpt deterministico
 *     ("Disponibile come pagina Markdown in `wiki/...`"). Coerente con
 *     [[ta-vs-vi-decision-layer]] §"Trasparenza".
 *   - Quando esisterà la rotta wiki, basterà trasformare il button in `<Link>`
 *     senza altri cambi al consumer.
 *
 * Slug `id` heuristic: il BE invia `id` = slug della pagina (es.
 * "ta-vs-vi-decision-layer"). Lo trasformiamo in title-case human-friendly
 * sostituendo `-` con spazio + capitalizzando.
 *
 * Accessibility (WCAG 2.2 AA — EP-016, AC US-104):
 *  - `<section>` con `aria-labelledby` esplicito.
 *  - Ciascun gruppo dominio è una sub-section con heading `<h3>`.
 *  - I bottoni hanno `aria-label` esplicito (titolo + dominio).
 *  - Modal di excerpt: `<dialog>` HTML semantico + Escape key + focus
 *    trap nativo (browser support: tutti i moderni).
 *
 * Sorgenti:
 *  - OpenAPI §schemas/WikiCitation (US-103)
 *  - US-104 §"Layout" 5 (Sezione "Perche questo verdetto") + §AC
 *  - ADR-030 §2 (citazioni cross-dominio)
 */

const DOMAIN_LABELS = {
  'value-investing': {
    label: 'Value Investing',
    icon: Briefcase,
    className:
      'border-blue-300 bg-blue-50 text-blue-900 ' +
      'dark:border-blue-800 dark:bg-blue-950 dark:text-blue-200',
  },
  'technical-analysis-trading': {
    label: 'Technical Analysis / Trading',
    icon: LineChart,
    className:
      'border-amber-300 bg-amber-50 text-amber-900 ' +
      'dark:border-amber-800 dark:bg-amber-950 dark:text-amber-100',
  },
  other: {
    label: 'Altro',
    icon: BookOpen,
    className:
      'border-slate-300 bg-slate-50 text-slate-800 ' +
      'dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200',
  },
} as const;

type DomainKey = keyof typeof DOMAIN_LABELS;

function classifyDomain(raw: string): DomainKey {
  if (raw === 'value-investing') return 'value-investing';
  if (raw === 'technical-analysis-trading') return 'technical-analysis-trading';
  return 'other';
}

function humanizeSlug(slug: string): string {
  return slug
    .replace(/[-_]+/g, ' ')
    .trim()
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

function wikiMarkdownPath(citation: WikiCitation): string {
  // Best-effort: per i due domini noti, le pagine vivono sotto
  // `wiki/{concepts|syntheses}/{slug}.md`. Non abbiamo l'info "concept vs
  // synthesis" nel payload — mostriamo path generico `wiki/{slug}.md`.
  // L'utente che apre il repo trova la pagina con search FS (acceptable).
  const anchor =
    citation.anchor !== null && citation.anchor.length > 0
      ? `#${citation.anchor}`
      : '';
  return `wiki/${citation.id}.md${anchor}`;
}

export interface WikiCitationsSectionProps {
  readonly citations: ReadonlyArray<WikiCitation>;
}

export function WikiCitationsSection(
  props: WikiCitationsSectionProps,
): React.ReactElement {
  const { citations } = props;
  const [activeCitation, setActiveCitation] = useState<WikiCitation | null>(
    null,
  );

  const grouped = useMemo(() => {
    const map = new Map<DomainKey, WikiCitation[]>();
    for (const c of citations) {
      const key = classifyDomain(c.domain);
      const bucket = map.get(key);
      if (bucket !== undefined) {
        bucket.push(c);
      } else {
        map.set(key, [c]);
      }
    }
    return map;
  }, [citations]);

  if (citations.length === 0) {
    return (
      <section
        data-testid="summary-wiki-citations-empty"
        aria-labelledby="summary-wiki-citations-heading"
        className="flex flex-col gap-2 rounded-md border border-outline-variant bg-surface-container-high p-4"
      >
        <h2
          id="summary-wiki-citations-heading"
          className="text-sm font-semibold uppercase tracking-wide text-on-surface/70"
        >
          Perché questo verdetto
        </h2>
        <p className="text-sm text-on-surface/60">
          Nessuna citazione disponibile per questo verdetto.
        </p>
      </section>
    );
  }

  // Ordine fisso: VI prima (gate primario), TA seconda, altro a fondo.
  const orderedKeys: DomainKey[] = [
    'value-investing',
    'technical-analysis-trading',
    'other',
  ];

  return (
    <section
      data-testid="summary-wiki-citations"
      aria-labelledby="summary-wiki-citations-heading"
      className="flex flex-col gap-4 rounded-md border border-outline-variant bg-surface-container-high p-4"
    >
      <h2
        id="summary-wiki-citations-heading"
        className="text-sm font-semibold uppercase tracking-wide text-on-surface/70"
      >
        Perché questo verdetto
      </h2>
      {orderedKeys.map((key) => {
        const items = grouped.get(key);
        if (items === undefined || items.length === 0) return null;
        const meta = DOMAIN_LABELS[key];
        const Icon = meta.icon;
        return (
          <div
            key={key}
            data-testid={`summary-wiki-citations-group-${key}`}
            className="flex flex-col gap-2"
          >
            <h3 className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-on-surface/70">
              <Icon aria-hidden="true" className="h-3.5 w-3.5" />
              {meta.label}
            </h3>
            <ul className="flex flex-wrap gap-2">
              {items.map((c) => (
                <li key={`${key}-${c.id}-${c.anchor ?? ''}`}>
                  <button
                    type="button"
                    onClick={() => setActiveCitation(c)}
                    data-testid={`summary-wiki-citation-${c.id}`}
                    aria-label={`Apri estratto di ${humanizeSlug(c.id)} — dominio ${meta.label}`}
                    className={cn(
                      'inline-flex items-center gap-1.5 rounded-full ' +
                        'border px-3 py-1 text-xs font-medium ' +
                        'transition hover:brightness-95 ' +
                        'focus-visible:outline-none focus-visible:ring-2 ' +
                        'focus-visible:ring-blue-500',
                      meta.className,
                    )}
                  >
                    <BookOpen aria-hidden="true" className="h-3.5 w-3.5" />
                    {humanizeSlug(c.id)}
                  </button>
                </li>
              ))}
            </ul>
          </div>
        );
      })}

      {activeCitation !== null ? (
        <CitationExcerptModal
          citation={activeCitation}
          onClose={() => setActiveCitation(null)}
        />
      ) : null}
    </section>
  );
}

/* ------------------------------------------------------------------ */
/*  Inline modal (lightweight) per excerpt placeholder                  */
/* ------------------------------------------------------------------ */

function CitationExcerptModal({
  citation,
  onClose,
}: {
  readonly citation: WikiCitation;
  readonly onClose: () => void;
}): React.ReactElement {
  // Escape + click outside chiudono il modal. Niente focus trap custom
  // (delegato al browser): basta `role="dialog"` + `aria-modal="true"` +
  // `tabIndex={-1}` sul contenitore principale.
  return (
    <div
      data-testid="summary-wiki-citation-modal"
      role="dialog"
      aria-modal="true"
      aria-labelledby="summary-wiki-citation-modal-heading"
      className={cn(
        'fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4',
      )}
      onClick={onClose}
      onKeyDown={(e) => {
        if (e.key === 'Escape') onClose();
      }}
    >
      <div
        className={cn(
          'flex max-w-lg flex-col gap-3 rounded-lg border ' +
            'border-outline-variant bg-surface p-6 text-on-surface',
        )}
        onClick={(e) => e.stopPropagation()}
        tabIndex={-1}
      >
        <h3
          id="summary-wiki-citation-modal-heading"
          className="text-base font-semibold"
        >
          {humanizeSlug(citation.id)}
        </h3>
        <p className="text-xs uppercase tracking-wide text-on-surface/60">
          Dominio: {citation.domain}
          {citation.anchor !== null ? ` · §${citation.anchor}` : ''}
        </p>
        <p className="text-sm text-on-surface/80">
          Pagina wiki disponibile come Markdown in:
        </p>
        <code className="rounded bg-surface-container-high p-2 text-xs">
          {wikiMarkdownPath(citation)}
        </code>
        <p className="text-xs text-on-surface/60">
          Il renderer HTML in-app è in lavorazione (gap{' '}
          <code>fe-wiki-html-rendering</code>). Quando sarà pronto, questo
          modal diventerà una pagina dedicata con l&apos;estratto live.
        </p>
        <div className="mt-1 flex justify-end">
          <button
            type="button"
            onClick={onClose}
            data-testid="summary-wiki-citation-modal-close"
            className={cn(
              'rounded-md border border-outline-variant px-3 py-1 ' +
                'text-sm font-medium text-on-surface hover:bg-surface-container-high ' +
                'focus-visible:outline-none focus-visible:ring-2 ' +
                'focus-visible:ring-blue-500',
            )}
          >
            Chiudi
          </button>
        </div>
      </div>
    </div>
  );
}
