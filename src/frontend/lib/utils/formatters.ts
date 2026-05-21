/**
 * Formatter UI (TSK-030).
 *
 * Tutti i numeri seguono locale `it-IT` per coerenza con frontend Italian-first;
 * le valute restano in formato nativo (USD/EUR/...) — il backend espone i codici
 * ISO 4217 nelle response.
 */

const NUMBER_FORMATTER_CACHE = new Map<string, Intl.NumberFormat>();

function getFormatter(key: string, options: Intl.NumberFormatOptions): Intl.NumberFormat {
  const cached = NUMBER_FORMATTER_CACHE.get(key);
  if (cached) return cached;
  const formatter = new Intl.NumberFormat('it-IT', options);
  NUMBER_FORMATTER_CACHE.set(key, formatter);
  return formatter;
}

export function formatCurrency(value: number, currency = 'USD'): string {
  if (!Number.isFinite(value)) return '—';
  return getFormatter(`currency:${currency}`, {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value);
}

export function formatPercent(value: number, decimals = 2): string {
  if (!Number.isFinite(value)) return '—';
  return getFormatter(`percent:${decimals}`, {
    style: 'percent',
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  }).format(value);
}

/**
 * Market cap "abbreviato" stile finanziario: 2_300_000_000 → "$2.30B".
 * Symbol valuta opzionale (default $).
 */
export function formatMarketCap(value: number, symbol = '$'): string {
  if (!Number.isFinite(value)) return '—';
  const abs = Math.abs(value);
  const sign = value < 0 ? '-' : '';
  if (abs >= 1e12) return `${sign}${symbol}${(abs / 1e12).toFixed(2)}T`;
  if (abs >= 1e9) return `${sign}${symbol}${(abs / 1e9).toFixed(2)}B`;
  if (abs >= 1e6) return `${sign}${symbol}${(abs / 1e6).toFixed(2)}M`;
  if (abs >= 1e3) return `${sign}${symbol}${(abs / 1e3).toFixed(2)}K`;
  return `${sign}${symbol}${abs.toFixed(2)}`;
}

export function formatDate(isoDate: string): string {
  if (!isoDate) return '—';
  const parsed = new Date(isoDate);
  if (Number.isNaN(parsed.getTime())) return '—';
  return getFormatter('date', {}).format(parsed.getTime() / 1000);
}

export function formatRatio(value: number, decimals = 2): string {
  if (!Number.isFinite(value)) return '—';
  return getFormatter(`ratio:${decimals}`, {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  }).format(value);
}
