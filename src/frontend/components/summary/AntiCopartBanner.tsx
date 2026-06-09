'use client';

import { AlertTriangle, ExternalLink } from 'lucide-react';
import { cn } from '@/lib/utils/cn';

/**
 * AntiCopartBanner — TSK-342 (US-104, EP-024 Fase 2).
 *
 * Banner di alert ALTAMENTE VISIBILE che spiega la "trappola COPART": titolo
 * VI-positivo + timing tecnico sfavorevole = rischio di stop loss prematuro
 * su una tesi VI corretta. Il banner appare SOLO quando
 *   viVerdict = GREEN_DOMINANT AND
 *   taVerdict ∈ {WAIT, ENTRY_UNFAVORABLE} AND
 *   summaryVerdict = WAIT_FOR_SETUP
 * (lato BE espresso come `warningAntiCopart` non vuoto — US-103 §AC).
 *
 * Lente di valore (memory/semantic/copart-timing-gap-ta-layer.md):
 * il caso CPRT ha motivato l'introduzione di EP-024. Questo banner è il
 * presidio operativo che traduce la lezione in UI prima dell'entry.
 *
 * SCELTA UX — colore di alert NON-rosso:
 *  - Rosso è già riservato al verdetto AVOID (hero). Usare rosso anche
 *    qui creerebbe ambiguità con "il titolo è da evitare". Il banner usa
 *    tono `amber/orange` (stesso del TechnicalAnalysisDisclaimer di US-101
 *    + ConfidenceReducedBanner) per segnalare "ATTENZIONE" senza confondere
 *    con "EVITA". Coerente con AC US-104 §"Banner anti-COPART": "colore di
 *    alert ma non rosso".
 *
 * Accessibility (WCAG 2.2 AA — EP-016, AC US-104):
 *  - `role="alert"` + `aria-live="assertive"`: lo screen reader annuncia in
 *    priorità il warning anti-COPART appena visibile.
 *  - Colore non unico canale: icona `<AlertTriangle>` + heading + testo
 *    + tono colore.
 *  - Link "Approfondisci →" raggiunge target con tastiera, focus visible.
 *
 * GAP fe-wiki-html-rendering (aperto, vedi WikiCitationsFooter di US-101):
 * la wiki Markdown non ha ancora una rotta `/wiki/[slug]` lato FE. Il link
 * "Approfondisci →" punta al percorso file Markdown nel repo
 * (`wiki/syntheses/ta-vs-vi-decision-layer.md#…`); quando esisterà il
 * renderer, basterà aggiornare l'href senza toccare i consumer.
 *
 * Sorgenti:
 *  - OpenAPI §schemas/SummaryVerdictResponse.warningAntiCopart (US-103)
 *  - US-104 §"Layout" 2 (Banner anti-COPART) + §AC
 *  - [[ta-vs-vi-decision-layer]] §"La domanda che ha motivato questa sintesi"
 *  - [[ta-stop-placement-position-sizing]] §"Il caso motivante: COPART"
 */

const WIKI_LINK =
  '/docs/wiki/syntheses/ta-vs-vi-decision-layer.md#la-domanda-che-ha-motivato-questa-sintesi';

export interface AntiCopartBannerProps {
  /**
   * Testo dell'avviso fornito dal BE (`warningAntiCopart`). Tipicamente:
   *   "Verdetto fondamentale positivo ma timing tecnico sfavorevole.
   *    Acquistare ora rischia uno stop loss prematuro su una tesi VI
   *    corretta — situazione COPART. Attendere il setup tecnico migliore."
   * Il componente non lo riformatta: lo mostra verbatim per preservare il
   * wording approvato lato BE/policy.
   */
  readonly warning: string;
}

export function AntiCopartBanner(
  props: AntiCopartBannerProps,
): React.ReactElement {
  const { warning } = props;
  return (
    <div
      data-testid="summary-anti-copart-banner"
      role="alert"
      aria-live="assertive"
      aria-labelledby="summary-anti-copart-heading"
      className={cn(
        'flex items-start gap-3 rounded-lg border-l-4 border-amber-500 ' +
          'bg-amber-50 p-4 dark:bg-amber-950/40 dark:border-amber-500',
      )}
    >
      <AlertTriangle
        aria-hidden="true"
        className="mt-0.5 h-6 w-6 shrink-0 text-amber-700 dark:text-amber-300"
      />
      <div className="flex-1">
        <h3
          id="summary-anti-copart-heading"
          className="text-sm font-bold uppercase tracking-wide text-amber-900 dark:text-amber-100"
        >
          Attenzione — trappola COPART
        </h3>
        <p
          data-testid="summary-anti-copart-text"
          className="mt-1 text-sm text-amber-900/90 dark:text-amber-100/90"
        >
          {warning}
        </p>
        <a
          href={WIKI_LINK}
          target="_blank"
          rel="noopener noreferrer"
          data-testid="summary-anti-copart-deeplink"
          className={cn(
            'mt-2 inline-flex items-center gap-1 text-sm font-medium ' +
              'text-amber-800 underline-offset-2 hover:underline ' +
              'focus-visible:outline-none focus-visible:ring-2 ' +
              'focus-visible:ring-amber-500 dark:text-amber-200',
          )}
        >
          Approfondisci
          <ExternalLink aria-hidden="true" className="h-3.5 w-3.5" />
        </a>
      </div>
    </div>
  );
}
