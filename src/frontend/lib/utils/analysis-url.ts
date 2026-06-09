/**
 * Canonical analysis page URL (ADR-013 / TSK-056).
 *
 * Static export uses query param, not `/analysis/[ticker]`.
 *
 * Semantica POST EP-024 Fase 2 (TSK-342 / US-104): la rotta canonica
 * `/analysis?ticker=…` è ora il **tab "Riepilogo"** (primo tab + default
 * di landing del dettaglio ticker). Le altre 3 viste vivono su rotte
 * esplicite — `/analysis/base`, `/analysis/deep`, `/analysis/technical` —
 * tutte query-param-based per coerenza con ADR-013.
 *
 * Deep-link compat:
 *  - vecchi link `/analysis?ticker=AAPL` ora aprono il Riepilogo (cambio
 *    di default voluto da US-104 — vedi §"Posizionamento del tab").
 *  - `/analysis/deep?ticker=AAPL` e `/analysis/technical?ticker=AAPL`
 *    restano invariati (US-101 §AC + US-104 §AC "Deep-link compatibility").
 *  - alias `/analysis/summary?ticker=AAPL` esposto per consistenza
 *    nomenclatura (TSK-342 §"Routing default-redirect").
 */
export function analysisUrl(ticker: string): string {
  const normalized = ticker.trim().toUpperCase();
  return `/analysis?ticker=${encodeURIComponent(normalized)}`;
}

/**
 * Summary tab URL (alias semantico di {@link analysisUrl}) — TSK-342.
 *
 * Il "Riepilogo" è il primo tab + landing su `/analysis?ticker=…`. Esponiamo
 * `summaryUrl()` come alias esplicito così i componenti che linkano AL tab
 * Riepilogo non dipendono dalla coincidenza "landing = Riepilogo". Se in
 * futuro Riepilogo migrasse su una rotta dedicata (`/analysis/summary`),
 * si aggiorna SOLO qui — nessun consumer da toccare.
 *
 * Convention: tutti i tab linkano a Riepilogo via `summaryUrl(ticker)`,
 * non via `analysisUrl(ticker)` — separazione di intent dal semplice
 * "default di landing".
 */
export function summaryUrl(ticker: string): string {
  return analysisUrl(ticker);
}

/**
 * Analisi Base page URL — TSK-342 (US-104, EP-024 Fase 2).
 *
 * Nuova rotta esplicita per il tab "Analisi Base" (Rule Engine + DCF + MoS +
 * Historical). Prima di EP-024 Fase 2 l'Analisi Base viveva direttamente su
 * `/analysis?ticker=…` (landing); il restyling di tab in US-104 sposta
 * il landing su Riepilogo e introduce un segment dedicato per Analisi Base.
 *
 * Query param — aligned with ADR-013 static export constraint.
 */
export function analysisBaseUrl(ticker: string): string {
  const normalized = ticker.trim().toUpperCase();
  return `/analysis/base?ticker=${encodeURIComponent(normalized)}`;
}

/**
 * Deep analysis page URL (TSK-122 / US-046).
 * Query param — aligned with ADR-013 static export constraint.
 */
export function deepAnalysisUrl(ticker: string): string {
  const normalized = ticker.trim().toUpperCase();
  return `/analysis/deep?ticker=${encodeURIComponent(normalized)}`;
}

/**
 * Technical Analysis page URL (TSK-334 / US-101, EP-024 Fase 1).
 * Terzo tab del dettaglio ticker. Query param — aligned with ADR-013
 * static export constraint, same pattern as deepAnalysisUrl().
 */
export function technicalAnalysisUrl(ticker: string): string {
  const normalized = ticker.trim().toUpperCase();
  return `/analysis/technical?ticker=${encodeURIComponent(normalized)}`;
}
