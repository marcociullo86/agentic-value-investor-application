/**
 * Canonical analysis page URL (ADR-013 / TSK-056).
 * Static export uses query param, not `/analysis/[ticker]`.
 */
export function analysisUrl(ticker: string): string {
  const normalized = ticker.trim().toUpperCase();
  return `/analysis?ticker=${encodeURIComponent(normalized)}`;
}
