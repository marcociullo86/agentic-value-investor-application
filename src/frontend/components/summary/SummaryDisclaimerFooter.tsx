'use client';

import { Info } from 'lucide-react';
import { cn } from '@/lib/utils/cn';

/**
 * SummaryDisclaimerFooter — TSK-343 (US-104, EP-024 Fase 2).
 *
 * Footer disclaimer visibile in coda al tab Riepilogo (AC US-104 §"Footer"):
 *
 *   "Decision-support tool. Nessuna esecuzione automatica di ordini. Il
 *    verdetto VI resta gate primario; la TA è layer advisory di timing."
 *
 * Lente di valore: l'app non è una piattaforma di trading, è un decision
 * support tool. Il disclaimer è in coda (non in testa) perché in testa abbiamo
 * già `TechnicalAnalysisDisclaimer` (sul tab TA) e l'utente arriva al
 * Riepilogo per leggere il verdetto, non un avviso. Il footer chiude
 * l'esperienza con la nota di responsabilità (coerente con il pattern
 * di Deep Analysis e Technical Analysis che mettono i metadati a fondo).
 *
 * Accessibility:
 *  - `<footer role="contentinfo">` landmark.
 *  - Icona `aria-hidden="true"` (testo già auto-portante).
 */

export function SummaryDisclaimerFooter(): React.ReactElement {
  return (
    <footer
      role="contentinfo"
      aria-label="Disclaimer Riepilogo"
      data-testid="summary-disclaimer-footer"
      className={cn(
        'flex items-start gap-3 border-t border-outline-variant pt-4 ' +
          'text-xs text-on-surface/60',
      )}
    >
      <Info
        aria-hidden="true"
        className="mt-0.5 h-4 w-4 shrink-0 text-on-surface/50"
      />
      <p>
        <strong className="font-semibold text-on-surface/80">
          Decision-support tool.
        </strong>{' '}
        Nessuna esecuzione automatica di ordini. Il verdetto Value Investing
        resta il gate primario; la Technical Analysis è un layer advisory di
        timing, mai una strategia autonoma.
      </p>
    </footer>
  );
}
