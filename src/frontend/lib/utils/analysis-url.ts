/**
 * Canonical analysis page URL (ADR-013 / TSK-056).
 * Static export uses query param, not `/analysis/[ticker]`.
 */
export function analysisUrl(ticker: string): string {
  const normalized = ticker.trim().toUpperCase();
  return `/analysis?ticker=${encodeURIComponent(normalized)}`;
}

/**
 * Deep analysis page URL (TSK-122 / US-046).
 * Dynamic segment — not static-exported.
 */
export function deepAnalysisUrl(ticker: string): string {
  const normalized = ticker.trim().toUpperCase();
  return `/analysis/${encodeURIComponent(normalized)}/deep`;
}
