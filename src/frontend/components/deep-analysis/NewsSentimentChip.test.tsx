import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
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
  it('renders analyzed news items with title and text', () => {
    render(<NewsSentimentChip sentiment={makeSentiment()} />);
    expect(screen.getByTestId('news-item-0')).toHaveTextContent('Titolo A');
    expect(screen.getByTestId('news-item-0')).toHaveTextContent('Testo notizia A');
    expect(screen.getByTestId('news-item-1')).toHaveTextContent('Titolo B');
  });

  it('renders no items list when items is empty', () => {
    render(<NewsSentimentChip sentiment={makeSentiment({ items: [] })} />);
    expect(screen.queryByTestId('news-items-list')).toBeNull();
  });

  it('renders empty state when sentiment is null', () => {
    render(<NewsSentimentChip sentiment={null} />);
    expect(screen.queryByTestId('sentiment-bar')).toBeNull();
  });
});
