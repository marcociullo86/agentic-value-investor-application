'use client';

import { useState, useCallback, useId } from 'react';
import { ChevronDown, ExternalLink } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { cn } from '@/lib/utils/cn';
import type {
  NewsSentimentBlock,
  NewsItem,
  SentimentClass,
} from '@/lib/api/deep-analysis';

export interface NewsSentimentChipProps {
  readonly sentiment: NewsSentimentBlock | null;
}

interface ClassPresentation {
  readonly label: string;
  readonly barColor: string;
  readonly chipColor: string;
}

const CLASS_MAP: Readonly<Record<SentimentClass, ClassPresentation>> = {
  TEMPORARY_PANIC: {
    label: 'Panic Temporaneo',
    barColor: 'bg-amber-500',
    chipColor:
      'bg-amber-50 text-amber-800 dark:bg-amber-950 dark:text-amber-300',
  },
  STRUCTURAL_DAMAGE: {
    label: 'Danno Strutturale',
    barColor: 'bg-red-500',
    chipColor: 'bg-red-50 text-red-800 dark:bg-red-950 dark:text-red-300',
  },
  NEUTRAL: {
    label: 'Neutrale',
    barColor: 'bg-slate-400',
    chipColor:
      'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300',
  },
};

function NewsItemList({
  items,
}: {
  readonly items: readonly NewsItem[] | undefined;
}): React.ReactElement | null {
  if (!items || items.length === 0) return null;

  return (
    <ul className="flex flex-col gap-3" data-testid="news-items-list">
      {items.map((item, idx) => {
        const cls = CLASS_MAP[item.sentimentClass];
        return (
          <li
            key={`${idx}-${item.url ?? item.headline ?? ''}`}
            className="flex flex-col gap-1 border-t border-slate-200 pt-3 first:border-t-0 first:pt-0 dark:border-slate-800"
            data-testid={`news-item-${idx}`}
          >
            <div className="flex items-start justify-between gap-2">
              {item.url ? (
                <a
                  href={item.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-start gap-1 text-sm font-medium text-blue-700 hover:underline dark:text-blue-400"
                >
                  {item.headline ?? '(senza titolo)'}
                  <ExternalLink className="mt-0.5 h-3 w-3 shrink-0" aria-hidden="true" />
                </a>
              ) : (
                <span className="text-sm font-medium text-slate-800 dark:text-slate-200">
                  {item.headline ?? '(senza titolo)'}
                </span>
              )}
              <span
                className={`shrink-0 rounded-full px-2 py-0.5 text-xs font-medium ${cls.chipColor}`}
              >
                {cls.label}
              </span>
            </div>
            {item.textExcerpt ? (
              <p className="text-xs text-slate-600 dark:text-slate-400">
                {item.textExcerpt}
              </p>
            ) : null}
            {item.motivazione ? (
              <p className="text-xs italic text-slate-500 dark:text-slate-500">
                {item.motivazione}
              </p>
            ) : null}
          </li>
        );
      })}
    </ul>
  );
}

function SentimentBar({
  sentiment,
}: {
  readonly sentiment: NewsSentimentBlock;
}): React.ReactElement {
  const total = sentiment.total || 1;
  const panicPct = (sentiment.panicCount / total) * 100;
  const structuralPct = (sentiment.structuralCount / total) * 100;
  const neutralPct = (sentiment.neutralCount / total) * 100;

  return (
    <div className="flex flex-col gap-2" data-testid="sentiment-bar">
      <div
        className="flex h-3 w-full overflow-hidden rounded-full"
        role="img"
        aria-label={`Distribuzione sentiment: ${sentiment.panicCount} panic, ${sentiment.structuralCount} structural, ${sentiment.neutralCount} neutral su ${sentiment.total} totali`}
      >
        {sentiment.panicCount > 0 ? (
          <div
            className="bg-amber-500 transition-all"
            style={{ width: `${panicPct}%` }}
          />
        ) : null}
        {sentiment.structuralCount > 0 ? (
          <div
            className="bg-red-500 transition-all"
            style={{ width: `${structuralPct}%` }}
          />
        ) : null}
        {sentiment.neutralCount > 0 ? (
          <div
            className="bg-slate-400 transition-all"
            style={{ width: `${neutralPct}%` }}
          />
        ) : null}
      </div>
      <div className="flex flex-wrap gap-3 text-xs">
        <span className="inline-flex items-center gap-1.5">
          <span className="h-2.5 w-2.5 rounded-full bg-amber-500" aria-hidden="true" />
          Panic ({sentiment.panicCount})
        </span>
        <span className="inline-flex items-center gap-1.5">
          <span className="h-2.5 w-2.5 rounded-full bg-red-500" aria-hidden="true" />
          Structural ({sentiment.structuralCount})
        </span>
        <span className="inline-flex items-center gap-1.5">
          <span className="h-2.5 w-2.5 rounded-full bg-slate-400" aria-hidden="true" />
          Neutral ({sentiment.neutralCount})
        </span>
      </div>
    </div>
  );
}

export function NewsSentimentChip({
  sentiment,
}: NewsSentimentChipProps): React.ReactElement {
  // Collassabile/espandibile coerente con MungerReportCollapsible (AC TSK-307 #2).
  // E2E: per espandere la lista notizie, click su data-testid="news-sentiment-toggle".
  const [expanded, setExpanded] = useState(false);
  const contentId = useId();

  const toggle = useCallback(() => {
    setExpanded((prev) => !prev);
  }, []);

  const hasSentiment = sentiment !== null;
  const hasItems = hasSentiment && (sentiment.items?.length ?? 0) > 0;

  return (
    <Card data-testid="news-sentiment-section">
      <CardHeader>
        <CardTitle as="h2">Sentiment News</CardTitle>
      </CardHeader>
      <CardContent>
        {hasSentiment ? (
          <div className="flex flex-col gap-4">
            <div className="flex items-center justify-between gap-3">
              <div className="flex items-center gap-3">
                <span
                  className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${CLASS_MAP[sentiment.dominantClass].chipColor}`}
                  data-testid="sentiment-dominant-chip"
                  role="status"
                  aria-label={`Classe dominante: ${CLASS_MAP[sentiment.dominantClass].label}`}
                >
                  {CLASS_MAP[sentiment.dominantClass].label}
                </span>
                <span className="text-sm text-slate-600 dark:text-slate-400">
                  {sentiment.total} news analizzate
                </span>
              </div>
              {hasItems ? (
                <button
                  type="button"
                  onClick={toggle}
                  aria-expanded={expanded}
                  aria-controls={contentId}
                  className="inline-flex items-center gap-1 rounded px-2 py-1 text-sm font-medium text-slate-600 transition hover:bg-slate-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 dark:text-slate-400 dark:hover:bg-slate-800"
                  data-testid="news-sentiment-toggle"
                >
                  {expanded ? 'Nascondi' : 'Espandi'}
                  <ChevronDown
                    className={cn(
                      'h-4 w-4 transition-transform',
                      expanded ? 'rotate-180' : 'rotate-0',
                    )}
                    aria-hidden="true"
                  />
                </button>
              ) : null}
            </div>
            <SentimentBar sentiment={sentiment} />
            {expanded ? (
              <div id={contentId}>
                <NewsItemList items={sentiment.items} />
              </div>
            ) : null}
          </div>
        ) : (
          <p className="text-sm text-slate-500 dark:text-slate-400">
            Premi &quot;Avvia analisi LLM&quot; per la classificazione news.
          </p>
        )}
      </CardContent>
    </Card>
  );
}
