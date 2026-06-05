/**
 * Formatter typed-driven per `RuleSignal` (US-095 / TSK-319).
 *
 * Consuma il client TS rigenerato da TSK-314 a partire dallo schema OpenAPI
 * `oneOf` + `discriminator` (ADR-028 §2/§5). Il tipo
 * `components["schemas"]["RuleSignal"]` e' una union discriminata su `ruleId`
 * con 15 sotto-tipi (13 EP-021 + 2 EP-023 NCAV). Ogni formatter narrowa via
 * `switch` esaustivo e legge i campi tipati del sotto-schema corrispondente.
 *
 * Output: `{ title, subtitle, tooltip }` — pronto per essere consumato dai
 * componenti `TrafficLightPanel` / `RuleSignalRow` / `RuleSignalBadge` nel
 * task successivo (TSK-320). QUESTO task NON modifica componenti React.
 *
 * Fallback paranoid (ADR-028 §6, §4 "natural overwrite"): per i record JSONB
 * pre-EP-021, i campi tipati attesi possono essere `null`. Ogni branch del
 * formatter rileva la condizione e degrada su `s.rationale` (legacy) per il
 * subtitle. Il fallback NON e' la prima linea, ma il ramo "last resort" di
 * ciascun formatter — coerente con la regola ADR-028 §6 ("Il fallback va
 * attivato solo nei rami specifici dei formatter, non come prima linea").
 *
 * Idiomi FE allineati a `lib/utils/formatters.ts` (TSK-030):
 *  - `formatMarketCap` per Revenue (M/B/T abbreviated);
 *  - `formatRatio` per ratio puri (P/E, P/B, current ratio, debt/income);
 *  - Percentuali ad uno o due decimali con simbolo `%` esplicito (le soglie
 *    in OpenAPI sono gia' in percento, quindi NON usiamo `Intl.NumberFormat`
 *    `style: 'percent'` che richiederebbe valori 0..1).
 *
 * Riferimenti:
 *  - ADR-028 §3 (mapping campi per ruleId), §6 (formatters.ts pattern).
 *  - schema.ts §components.schemas.RuleSignal{Size,EarningsStability10y,...}.
 *  - US-095 Business Rules (TSK kanban).
 */

import type { components } from '@/lib/api/generated/schema';
import { formatMarketCap, formatRatio } from '@/lib/utils/formatters';

// ---------------------------------------------------------------------------
// Public types
// ---------------------------------------------------------------------------

/**
 * Re-export tipato del payload `RuleSignal` (union discriminata) per gli
 * import consumer (componenti FE, test). Manteniamo il punto unico di
 * importazione qui per disaccoppiare i consumer dal path `generated/schema`.
 */
export type RuleSignal = components['schemas']['RuleSignal'];

/** Discriminator `ruleId` (15 valori = 13 EP-021 + 2 EP-023). */
export type RuleSignalId = RuleSignal['ruleId'];

export interface FormatterOutput {
  /** Titolo human-readable della regola (es. "Dimensione"). */
  readonly title: string;
  /**
   * Sottotitolo con valore osservato + soglia in formato leggibile (es.
   * "Revenue: $2.30B (soglia $100M)"). Quando i campi tipati sono `null`
   * (record JSONB stale), degrada su `rationale` legacy.
   */
  readonly subtitle: string;
  /** Tooltip esteso (citazione fonte Graham/Buffett). */
  readonly tooltip: string;
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

/**
 * Fallback "paranoid" su `rationale` legacy (deprecated, ADR-028 §8). Usato
 * dai branch del formatter quando i campi tipati attesi sono `null` — tipico
 * dei record JSONB pre-EP-021 non ancora rivalutati ("natural overwrite",
 * ADR-028 §4).
 */
function legacyFallback(s: RuleSignal): string {
  const rationale = s.rationale?.trim();
  return rationale && rationale.length > 0 ? rationale : 'N/A';
}

/**
 * Formato percentuale "X.X%" con 1 decimale di default. NON usa
 * `Intl.NumberFormat style: 'percent'` perche' i valori in OpenAPI sono gia'
 * in scala 0..100 (es. `averagePercent: 15.3` = 15.3%, non 0.153).
 */
function fmtPct(value: number | null | undefined, decimals = 1): string {
  if (value === null || value === undefined || !Number.isFinite(value)) {
    return 'N/A';
  }
  return `${value.toFixed(decimals)}%`;
}

/**
 * Formato monetario "abbreviato" (M/B/T) per importi USD. Wrapper su
 * `formatMarketCap` per consistenza con il resto del FE.
 */
function fmtUsd(value: number | null | undefined): string {
  if (value === null || value === undefined || !Number.isFinite(value)) {
    return 'N/A';
  }
  return formatMarketCap(value, '$');
}

/**
 * Formato ratio numerico (P/E, P/B, current ratio, ...) con 2 decimali via
 * `Intl.NumberFormat` it-IT (es. "15,23"). Wrapper su `formatRatio`.
 */
function fmtRatio(value: number | null | undefined, decimals = 2): string {
  if (value === null || value === undefined || !Number.isFinite(value)) {
    return 'N/A';
  }
  return formatRatio(value, decimals);
}

// ---------------------------------------------------------------------------
// Per-ruleId formatters (typed narrowing via `switch (s.ruleId)`)
// ---------------------------------------------------------------------------

function formatSize(s: components['schemas']['RuleSignalSize']): FormatterOutput {
  const { revenueLatest, thresholdUsd } = s;
  const subtitle =
    revenueLatest === null || revenueLatest === undefined
      ? legacyFallback(s)
      : `Revenue: ${fmtUsd(revenueLatest)} (soglia ${fmtUsd(thresholdUsd)})`;
  return {
    title: 'Dimensione',
    subtitle,
    tooltip: 'Graham Cap.14 — Adequate Size of the Enterprise (soglia $100M).',
  };
}

function formatEarningsStability(
  s: components['schemas']['RuleSignalEarningsStability10y'],
): FormatterOutput {
  const { yearsPositive, yearsAvailable } = s;
  // `lossYears` e' required nello schema typed, ma durante la finestra di
  // transizione R+1/R+2 (ADR-028 §8) un record stale o un cast dal tipo legacy
  // puo' consegnarlo `undefined`: normalizziamo difensivamente per non far
  // crashare la pagina analisi su `.length`.
  const lossYears = s.lossYears ?? [];
  // I campi tipati sono required: yearsPositive/yearsAvailable sono `number`,
  // non nullable. Il fallback rationale si attiva solo se la cardinalita' del
  // payload e' palesemente degenerata (yearsAvailable = 0 con lossYears vuoto).
  const subtitle =
    yearsAvailable === 0 && lossYears.length === 0
      ? legacyFallback(s)
      : `${yearsPositive}/${yearsAvailable} anni positivi` +
        (lossYears.length > 0 ? ` (${lossYears.length} loss years)` : '');
  return {
    title: 'Stabilita earnings',
    subtitle,
    tooltip: 'Graham Cap.14 — Earnings Stability (10y, no loss years).',
  };
}

function formatEpsGrowth(
  s: components['schemas']['RuleSignalEpsGrowth10y'],
): FormatterOutput {
  const { cagrPercent, thresholdPercent } = s;
  const subtitle =
    cagrPercent === null || cagrPercent === undefined
      ? legacyFallback(s)
      : `CAGR ${fmtPct(cagrPercent)} (soglia ${fmtPct(thresholdPercent)})`;
  return {
    title: 'Crescita EPS',
    subtitle,
    tooltip: 'Graham Cap.14 — EPS Growth >= 33% cumulativo (10y).',
  };
}

function formatPe3yAvg(
  s: components['schemas']['RuleSignalPe3yAvg'],
): FormatterOutput {
  const { pe3yAvg, thresholdGreen, thresholdYellow } = s;
  const subtitle =
    pe3yAvg === null || pe3yAvg === undefined
      ? legacyFallback(s)
      : `P/E 3y: ${fmtRatio(pe3yAvg)} ` +
        `(verde <=${fmtRatio(thresholdGreen)}, giallo <=${fmtRatio(thresholdYellow)})`;
  return {
    title: 'P/E moderato',
    subtitle,
    tooltip: 'Graham Cap.14 — Moderate Price/Earnings (media 3y, <=15 GREEN).',
  };
}

function formatPbLatest(
  s: components['schemas']['RuleSignalPbLatest'],
): FormatterOutput {
  const { pbLatest, thresholdGreen, thresholdYellow } = s;
  const subtitle =
    pbLatest === null || pbLatest === undefined
      ? legacyFallback(s)
      : `P/B: ${fmtRatio(pbLatest)} ` +
        `(verde <=${fmtRatio(thresholdGreen)}, giallo <=${fmtRatio(thresholdYellow)})`;
  return {
    title: 'P/B moderato',
    subtitle,
    tooltip: 'Graham Cap.14 — Moderate Price/Book (latest, <=1.5 GREEN).',
  };
}

function formatDividendContinuity(
  s: components['schemas']['RuleSignalDividendContinuity20y'],
): FormatterOutput {
  const { consecutiveYears, thresholdYears } = s;
  const subtitle =
    consecutiveYears === null || consecutiveYears === undefined
      ? legacyFallback(s)
      : `${consecutiveYears} anni consecutivi (soglia ${thresholdYears}y)`;
  return {
    title: 'Dividendo continuo',
    subtitle,
    tooltip: 'Graham Cap.14 — Dividend Record (20+ anni consecutivi).',
  };
}

function formatRoe10yAvg(
  s: components['schemas']['RuleSignalRoe10yAvg'],
): FormatterOutput {
  const { averagePercent, thresholdGreenPercent, thresholdYellowPercent } = s;
  const subtitle =
    averagePercent === null || averagePercent === undefined
      ? legacyFallback(s)
      : `Media ${fmtPct(averagePercent)} ` +
        `(verde >=${fmtPct(thresholdGreenPercent)}, giallo >=${fmtPct(thresholdYellowPercent)})`;
  return {
    title: 'ROE 10y',
    subtitle,
    tooltip: 'Buffett Quality — ROE medio 10y (>=15% GREEN, >=10% YELLOW).',
  };
}

function formatRoic10yAvg(
  s: components['schemas']['RuleSignalRoic10yAvg'],
): FormatterOutput {
  const { averagePercent, thresholdGreenPercent, thresholdYellowPercent } = s;
  const subtitle =
    averagePercent === null || averagePercent === undefined
      ? legacyFallback(s)
      : `Media ${fmtPct(averagePercent)} ` +
        `(verde >=${fmtPct(thresholdGreenPercent)}, giallo >=${fmtPct(thresholdYellowPercent)})`;
  return {
    title: 'ROIC 10y',
    subtitle,
    tooltip: 'Buffett Quality — ROIC medio 10y (>=15% GREEN, >=10% YELLOW).',
  };
}

function formatGrossMargin10yAvg(
  s: components['schemas']['RuleSignalGrossMargin10yAvg'],
): FormatterOutput {
  const { averagePercent, thresholdGreenPercent, thresholdYellowPercent } = s;
  const subtitle =
    averagePercent === null || averagePercent === undefined
      ? legacyFallback(s)
      : `Media ${fmtPct(averagePercent)} ` +
        `(verde >=${fmtPct(thresholdGreenPercent)}, giallo >=${fmtPct(thresholdYellowPercent)})`;
  return {
    title: 'Gross margin 10y',
    subtitle,
    tooltip: 'Buffett Pricing Power — Gross margin medio 10y (>=40% GREEN).',
  };
}

function formatNetMargin10yAvg(
  s: components['schemas']['RuleSignalNetMargin10yAvg'],
): FormatterOutput {
  const { averagePercent, thresholdGreenPercent } = s;
  const subtitle =
    averagePercent === null || averagePercent === undefined
      ? legacyFallback(s)
      : `Media ${fmtPct(averagePercent)} (verde >=${fmtPct(thresholdGreenPercent)})`;
  return {
    title: 'Net margin 10y',
    subtitle,
    tooltip: 'Buffett Pricing Power — Net margin medio 10y (>=20% GREEN).',
  };
}

function formatCurrentRatioLatest(
  s: components['schemas']['RuleSignalCurrentRatioLatest'],
): FormatterOutput {
  const { ratioLatest, thresholdGreen, thresholdYellow } = s;
  const subtitle =
    ratioLatest === null || ratioLatest === undefined
      ? legacyFallback(s)
      : `Ratio: ${fmtRatio(ratioLatest)} ` +
        `(verde >=${fmtRatio(thresholdGreen)}, giallo >=${fmtRatio(thresholdYellow)})`;
  return {
    title: 'Current ratio',
    subtitle,
    tooltip:
      'Buffett Financial Strength — currentAssets / currentLiabilities (>=2 GREEN).',
  };
}

function formatDebtToIncomeLatest(
  s: components['schemas']['RuleSignalDebtToIncomeLatest'],
): FormatterOutput {
  const {
    ratioLatest,
    thresholdGreen,
    thresholdYellow,
    netIncomePositive,
  } = s;
  // Edge case ADR-028 §3: net income <= 0 -> INDETERMINATE, ratio non
  // significativo. Subtitle riflette la condizione anziche' mostrare numeri
  // fuorvianti.
  if (!netIncomePositive) {
    return {
      title: 'Debt / income',
      subtitle: 'Net income <= 0 (non significativo)',
      tooltip:
        'Buffett Financial Strength — long-term debt / net income (<=4y GREEN). INDETERMINATE quando net income <= 0.',
    };
  }
  const subtitle =
    ratioLatest === null || ratioLatest === undefined
      ? legacyFallback(s)
      : `Ratio: ${fmtRatio(ratioLatest)} ` +
        `(verde <=${fmtRatio(thresholdGreen)}, giallo <=${fmtRatio(thresholdYellow)})`;
  return {
    title: 'Debt / income',
    subtitle,
    tooltip:
      'Buffett Financial Strength — long-term debt / net income (<=4y GREEN, <=8y YELLOW).',
  };
}

function formatCapexIntensity10yAvg(
  s: components['schemas']['RuleSignalCapexIntensity10yAvg'],
): FormatterOutput {
  const { averagePercent, thresholdGreenPercent, thresholdYellowPercent } = s;
  const subtitle =
    averagePercent === null || averagePercent === undefined
      ? legacyFallback(s)
      : `Media ${fmtPct(averagePercent)} ` +
        `(verde <=${fmtPct(thresholdGreenPercent)}, giallo <=${fmtPct(thresholdYellowPercent)})`;
  return {
    title: 'Capex intensity 10y',
    subtitle,
    tooltip:
      'Buffett Capital-Light — capex / operating cash flow medio 10y (<=25% GREEN).',
  };
}

// ---------------------------------------------------------------------------
// EP-023 NCAV (predisposti: scope EP-023 / US-096, non US-095, ma tipi gia'
// disponibili nello schema generato. Includerli rende lo `switch` esaustivo
// rispetto alla union del client TS, evitando il branch `default` morto e
// chiudendo il loop di type narrowing TS strict).
// ---------------------------------------------------------------------------

function formatNcavLatest(
  s: components['schemas']['RuleSignalNcavLatest'],
): FormatterOutput {
  const { ncavTotal, ncavPerShare } = s;
  const subtitle =
    ncavTotal === null || ncavTotal === undefined
      ? legacyFallback(s)
      : `NCAV: ${fmtUsd(ncavTotal)} ` +
        `(per azione: ${fmtUsd(ncavPerShare)})`;
  return {
    title: 'NCAV',
    subtitle,
    tooltip:
      'Graham Cap.15 — Net Current Asset Value = currentAssets - totalLiabilities (informativo).',
  };
}

function formatNetNetRatio(
  s: components['schemas']['RuleSignalNetNetRatio'],
): FormatterOutput {
  const { priceLatest, ncavPerShare, ratio, thresholdRatio } = s;
  if (ratio === null || ratio === undefined) {
    const subtitle =
      priceLatest === null || ncavPerShare === null
        ? legacyFallback(s)
        : `Prezzo: ${fmtUsd(priceLatest)}, NCAV/share: ${fmtUsd(ncavPerShare)}`;
    return {
      title: 'Net-Net ratio',
      subtitle,
      tooltip:
        'Graham Cap.15 — priceLatest / ncavPerShare (soglia 2/3 = 0.6667 GREEN).',
    };
  }
  return {
    title: 'Net-Net ratio',
    subtitle:
      `Ratio: ${fmtRatio(ratio, 4)} (soglia <${fmtRatio(thresholdRatio, 4)})`,
    tooltip:
      'Graham Cap.15 — priceLatest / ncavPerShare (soglia 2/3 = 0.6667 GREEN).',
  };
}

// ---------------------------------------------------------------------------
// Public API — exhaustive switch
// ---------------------------------------------------------------------------

/**
 * Marker di esaustivita': se la union `RuleSignal` cresce e qualcuno
 * dimentica di gestire un nuovo `ruleId`, il check `value: never` fa
 * fallire il typecheck (TS strict). Coerente con sealed interface Kotlin
 * lato BE (ADR-028 §1).
 */
function exhaustiveCheck(value: never): never {
  throw new Error(
    `formatRuleSignal: unhandled ruleId in union: ${JSON.stringify(value)}`,
  );
}

/**
 * Formatta un `RuleSignal` (union discriminata su `ruleId`) in
 * `{ title, subtitle, tooltip }`. Type narrowing automatico TS via `switch`
 * esaustivo (ADR-028 §5).
 *
 * Fallback paranoid (ADR-028 §6): per ogni branch, se i campi tipati attesi
 * sono `null` / `undefined` (record JSONB stale pre-EP-021 / natural
 * overwrite finestra di transizione, §4) degrada su `rationale` legacy.
 *
 * Robustezza runtime: se il backend emette un `ruleId` NON ancora previsto
 * dall'union (es. nuova regola lato BE prima del re-gen TS — drift
 * temporaneo coperto dal contract test OpenApiContractIT, TSK-315) NON va in
 * eccezione; degrada su `rationale` legacy e mostra il ruleId raw come
 * titolo. Questo e' un secondo livello di fallback paranoid esterno al
 * branch, complementare a quello interno per i campi `null`.
 */
export function formatRuleSignal(s: RuleSignal): FormatterOutput {
  switch (s.ruleId) {
    case 'SIZE_LATEST':
      return formatSize(s);
    case 'EARNINGS_STABILITY_10Y':
      return formatEarningsStability(s);
    case 'EPS_GROWTH_10Y':
      return formatEpsGrowth(s);
    case 'PE_3Y_AVG':
      return formatPe3yAvg(s);
    case 'PB_LATEST':
      return formatPbLatest(s);
    case 'DIVIDEND_CONTINUITY_20Y':
      return formatDividendContinuity(s);
    case 'ROE_10Y_AVG':
      return formatRoe10yAvg(s);
    case 'ROIC_10Y_AVG':
      return formatRoic10yAvg(s);
    case 'GROSS_MARGIN_10Y_AVG':
      return formatGrossMargin10yAvg(s);
    case 'NET_MARGIN_10Y_AVG':
      return formatNetMargin10yAvg(s);
    case 'CURRENT_RATIO_LATEST':
      return formatCurrentRatioLatest(s);
    case 'DEBT_TO_INCOME_LATEST':
      return formatDebtToIncomeLatest(s);
    case 'CAPEX_INTENSITY_10Y_AVG':
      return formatCapexIntensity10yAvg(s);
    case 'NCAV_LATEST':
      return formatNcavLatest(s);
    case 'NET_NET_RATIO':
      return formatNetNetRatio(s);
    default:
      // Drift safety net: ruleId presente nel JSON ma non noto al client TS.
      // Non e' un branch raggiungibile finche' schema.ts e' sincronizzato.
      // Lasciamo TS verificare l'esaustivita' via never, ma forniamo anche
      // un fallback runtime per non far esplodere la UI in caso di drift.
      return runtimeFallback(s);
  }
}

/**
 * Fallback runtime non-throwing per drift schema vs backend. Mai usato in
 * condizioni normali (schema sincronizzato), ma evita la propagazione di
 * un'eccezione fino alla UI. `s` qui ha tipo `never` (esaustivita' TS), ma
 * a runtime puo' essere un oggetto con `ruleId` sconosciuto.
 */
function runtimeFallback(s: never): FormatterOutput {
  // Cast localizzato al tipo Base solo per leggere campi comuni: il branch
  // non e' tipato a compile-time (e' il ramo "impossible" della union).
  const base = s as unknown as {
    readonly ruleId?: string;
    readonly rationale?: string;
  };
  const ruleId = base.ruleId ?? 'UNKNOWN';
  const rationale = base.rationale?.trim();
  return {
    title: ruleId,
    subtitle: rationale && rationale.length > 0 ? rationale : 'N/A',
    tooltip: `RuleId non riconosciuto dal client TS (drift schema): ${ruleId}.`,
  };
  // NOTA: exhaustiveCheck(s) provocherebbe un crash; preferiamo degradazione
  // soft. La validita' end-to-end e' garantita da OpenApiContractIT (TSK-315).
  // exhaustiveCheck import kept-alive intenzionale: vedi __ensureExhaustive.
}

/**
 * Keep-alive del simbolo `exhaustiveCheck` per non emettere "unused" warning
 * sotto TS strict / lint. `exhaustiveCheck` resta a disposizione di future
 * estensioni che vogliano switch-throw invece di soft-fallback.
 */
const __ensureExhaustive: typeof exhaustiveCheck = exhaustiveCheck;
void __ensureExhaustive;
