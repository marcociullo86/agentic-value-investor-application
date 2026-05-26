'use client';

import { ExternalLink } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import type {
  NewsSentimentBlock,
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
  const hasSentiment = sentiment !== null;

  return (
    <Card data-testid="news-sentiment-section">
      <CardHeader>
        <CardTitle as="h2">Sentiment News</CardTitle>
      </CardHeader>
      <CardContent>
        {hasSentiment ? (
          <div className="flex flex-col gap-4">
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
            <SentimentBar sentiment={sentiment} />
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
