import { describe, it, expect } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { NewsSentimentChip } from './NewsSentimentChip';
import type { NewsSentimentBlock } from '@/lib/api/deep-analysis';

function makeSentiment(
  overrides: Partial<NewsSentimentBlock> = {},
): NewsSentimentBlock {
  return {
    total: 2,
    panicCount: 1,
    structuralCount: 0,
    neutralCount: 1,
    dominantClass: 'TEMPORARY_PANIC',
    items: [
      {
        headline: 'Titolo A',
        textExcerpt: 'Testo notizia A',
        sentimentClass: 'TEMPORARY_PANIC',
        motivazione: 'motivazione A',
        url: 'https://example.com/a',
      },
      {
        headline: 'Titolo B',
        textExcerpt: 'Testo notizia B',
        sentimentClass: 'NEUTRAL',
        motivazione: null,
        url: null,
      },
    ],
    ...overrides,
  };
}

describe('NewsSentimentChip (US-091)', () => {
  it('renders analyzed news items with title, text and per-class badge label when expanded', () => {
    render(<NewsSentimentChip sentiment={makeSentiment()} />);
    // Lista collassata di default (AC TSK-307 #2): occorre espandere prima di asserire gli items.
    fireEvent.click(screen.getByTestId('news-sentiment-toggle'));
    expect(screen.getByTestId('news-item-0')).toHaveTextContent('Titolo A');
    expect(screen.getByTestId('news-item-0')).toHaveTextContent('Testo notizia A');
    expect(screen.getByTestId('news-item-1')).toHaveTextContent('Titolo B');
    // AC TSK-307 #3: badge label coerente con la sentimentClass.
    expect(screen.getByTestId('news-item-0')).toHaveTextContent('Panic Temporaneo');
    expect(screen.getByTestId('news-item-1')).toHaveTextContent('Neutrale');
  });

  it('renders no items list when items is empty', () => {
    render(<NewsSentimentChip sentiment={makeSentiment({ items: [] })} />);
    // Senza items il toggle non è renderizzato e la lista non compare.
    expect(screen.queryByTestId('news-sentiment-toggle')).toBeNull();
    expect(screen.queryByTestId('news-items-list')).toBeNull();
  });

  it('renders empty state when sentiment is null', () => {
    render(<NewsSentimentChip sentiment={null} />);
    expect(screen.queryByTestId('sentiment-bar')).toBeNull();
  });
});
