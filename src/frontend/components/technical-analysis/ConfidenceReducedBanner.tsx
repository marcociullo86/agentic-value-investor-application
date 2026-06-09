'use client';

import { AlertTriangle } from 'lucide-react';
import { cn } from '@/lib/utils/cn';

/**
 * ConfidenceReducedBanner — TSK-335 (US-101, EP-024 Fase 1).
 *
 * Banner giallo visibile quando ALMENO UNO dei sei blocchi indicatori
 * (`trend|momentum|volatility|volume|levels|priceContext`) ha
 * `confidenceReduced === true` (storico EOD < 200 sedute o serie troppo
 * corta per gli indicatori — cfr. OpenAPI §TaTrendBlock, US-098).
 *
 * UX: l'utente sa che gli indicatori esistono ma sono meno affidabili —
 * NON nascondiamo i numeri, segnaliamo solo il caveat.
 *
 * Accessibility (WCAG 2.2 AA — EP-016):
 *  - `role="status"` + `aria-live="polite"` (cambio di stato non bloccante).
 *  - Colore non unico canale: icona `<AlertTriangle>` + testo + tono giallo.
 */

export function ConfidenceReducedBanner(): React.ReactElement {
  return (
    <div
      data-testid="ta-confidence-reduced-banner"
      role="status"
      aria-live="polite"
      className={cn(
        'flex items-start gap-3 rounded-lg border-l-4 border-amber-400 bg-amber-50/80 p-4',
        'dark:bg-amber-950/30',
      )}
    >
      <AlertTriangle
        aria-hidden="true"
        className="mt-0.5 h-5 w-5 shrink-0 text-amber-700 dark:text-amber-300"
      />
      <div className="flex-1">
        <p className="text-sm font-semibold text-amber-900 dark:text-amber-100">
          Dati limitati: analisi tecnica con confidenza ridotta
        </p>
        <p className="mt-1 text-sm text-amber-900/90 dark:text-amber-100/90">
          Lo storico EOD disponibile per questo ticker è inferiore alla
          finestra canonica (≈200 sedute per SMA200/Triple Screen). Indicatori
          e advisor sono calcolati sulla serie disponibile, ma il verdetto
          tecnico è meno affidabile rispetto a un ticker con storico pieno.
        </p>
      </div>
    </div>
  );
}
