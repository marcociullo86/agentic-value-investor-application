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

const DATE_FORMATTER_CACHE = new Map<string, Intl.DateTimeFormat>();

function getDateFormatter(key: string, options: Intl.DateTimeFormatOptions): Intl.DateTimeFormat {
  const cached = DATE_FORMATTER_CACHE.get(key);
  if (cached) return cached;
  const formatter = new Intl.DateTimeFormat('it-IT', options);
  DATE_FORMATTER_CACHE.set(key, formatter);
  return formatter;
}

/**
 * Formatta un timestamp ricevuto dal backend come data leggibile.
 *
 * Bug originale (US-054): la versione precedente usava per errore
 * `Intl.NumberFormat` (cache condivisa) e produceva un numero in formato
 * italiano (es. "1.779.484.360,919") quando il backend serializzava
 * `dataSnapshotAt` come epoch in millisecondi. Risolto inserendo una cache
 * `Intl.DateTimeFormat` dedicata e accettando tre forme di input:
 *  - ISO-8601 string ("2026-05-22T11:12:40Z")
 *  - epoch in millisecondi (number o numeric string)
 *  - epoch in secondi (number, scalato se < 1e12)
 *
 * Output di default: "22 mag 2026, 11:12 UTC" (locale it-IT, UTC fisso per
 * coerenza con `dataSnapshotAt` che è UTC server-side).
 */
export function formatDate(value: string | number | null | undefined): string {
  if (value === null || value === undefined || value === '') return '—';

  let timestampMs: number;
  if (typeof value === 'number') {
    timestampMs = value < 1e12 ? value * 1000 : value;
  } else {
    const asNumber = Number(value);
    if (Number.isFinite(asNumber) && /^[0-9]+(\.[0-9]+)?$/.test(value.trim())) {
      timestampMs = asNumber < 1e12 ? asNumber * 1000 : asNumber;
    } else {
      const parsed = Date.parse(value);
      if (Number.isNaN(parsed)) return '—';
      timestampMs = parsed;
    }
  }

  if (!Number.isFinite(timestampMs)) return '—';

  return getDateFormatter('snapshot', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    timeZone: 'UTC',
    timeZoneName: 'short',
  }).format(new Date(timestampMs));
}

export function formatRatio(value: number, decimals = 2): string {
  if (!Number.isFinite(value)) return '—';
  return getFormatter(`ratio:${decimals}`, {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  }).format(value);
}
