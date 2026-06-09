'use client';

import { useEffect, useState } from 'react';
import { Info, X } from 'lucide-react';
import { cn } from '@/lib/utils/cn';

/**
 * TechnicalAnalysisDisclaimer — TSK-334 (US-101, EP-024 Fase 1).
 *
 * Banner advisory prominente in testa al tab "Technical Analysis":
 *
 *   "Layer advisory di timing. Il verdetto fondamentale resta primario.
 *    [Approfondisci →]"
 *
 * Coerente con la lente di valore (memory/semantic/value-investing-design-lens.md):
 * la TA è giustificata in app SE E SOLO SE migliora l'esito del verdetto
 * fondamentale, mai come strategia autonoma.
 *
 * Comportamento (AC US-101 §Layout 1):
 *  - Dismissable per sessione: alla chiusura il flag vive in
 *    `sessionStorage` (NON `localStorage` — la dismissione non persiste
 *    cross-session, l'utente DEVE ri-vedere il disclaimer in ogni nuova
 *    sessione). Coerente con il principio di trasparenza dell'app.
 *  - Toggle "Approfondisci" → 3 bullet che riassumono
 *    [[ta-vs-vi-decision-layer]] (verdetto VI primario, TA solo timing,
 *    gate hardcoded BE).
 *
 * Accessibility (EP-016, WCAG 2.2 AA):
 *  - `role="region"` + `aria-labelledby` per landmark navigation.
 *  - Pulsante chiudi con `aria-label` esplicito (non solo icona).
 *  - Pulsante "Approfondisci" con `aria-expanded`/`aria-controls`.
 *  - Colore non è l'unico canale: icona `<Info>` + testo + tono giallo.
 *
 * Sorgenti:
 *  - US-101 §Layout 1 (disclaimer banner)
 *  - [[ta-vs-vi-decision-layer]] §"Architettura a due layer nell'app"
 */

const STORAGE_KEY = 'ta-disclaimer-dismissed:v1';

export function TechnicalAnalysisDisclaimer(): React.ReactElement | null {
  // Inizializziamo a `false` per evitare hydration mismatch (server non ha
  // sessionStorage). Al mount leggiamo il flag e ri-renderizziamo.
  const [dismissed, setDismissed] = useState<boolean>(false);
  const [hydrated, setHydrated] = useState<boolean>(false);
  const [expanded, setExpanded] = useState<boolean>(false);

  useEffect(() => {
    setHydrated(true);
    try {
      if (typeof window !== 'undefined') {
        setDismissed(window.sessionStorage.getItem(STORAGE_KEY) === '1');
      }
    } catch {
      // sessionStorage disabilitato (private browsing, policy): degradiamo
      // a "non dismissed" — meglio mostrare in più che nascondere il disclaimer.
      setDismissed(false);
    }
  }, []);

  const handleDismiss = (): void => {
    setDismissed(true);
    try {
      if (typeof window !== 'undefined') {
        window.sessionStorage.setItem(STORAGE_KEY, '1');
      }
    } catch {
      // best-effort
    }
  };

  // Render nulla fino a hydration completa per evitare flash.
  if (!hydrated) return null;
  if (dismissed) return null;

  return (
    <section
      role="region"
      aria-labelledby="ta-disclaimer-heading"
      data-testid="ta-disclaimer-banner"
      className={cn(
        'flex flex-col gap-2 rounded-lg border-l-4 border-amber-400 bg-amber-50 p-4',
        'dark:border-amber-500 dark:bg-amber-950/40',
      )}
    >
      <div className="flex items-start gap-3">
        <Info
          aria-hidden="true"
          className="mt-0.5 h-5 w-5 shrink-0 text-amber-700 dark:text-amber-400"
        />
        <div className="flex-1">
          <h2
            id="ta-disclaimer-heading"
            className="text-sm font-semibold text-amber-900 dark:text-amber-100"
          >
            Layer advisory di timing
          </h2>
          <p className="mt-1 text-sm text-amber-900/90 dark:text-amber-100/90">
            Il verdetto fondamentale (Rule Engine + DCF + Margin of Safety)
            resta primario. L&apos;analisi tecnica risponde solo alla domanda
            &quot;quando?&quot;, mai &quot;cosa?&quot;.
          </p>
          <button
            type="button"
            onClick={() => setExpanded((v) => !v)}
            aria-expanded={expanded}
            aria-controls="ta-disclaimer-details"
            data-testid="ta-disclaimer-toggle"
            className={cn(
              'mt-1 text-sm font-medium text-amber-800 underline-offset-2 hover:underline',
              'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-500',
              'dark:text-amber-200',
            )}
          >
            {expanded ? 'Nascondi dettagli' : 'Approfondisci →'}
          </button>
        </div>
        <button
          type="button"
          onClick={handleDismiss}
          aria-label="Chiudi disclaimer per questa sessione"
          data-testid="ta-disclaimer-dismiss"
          className={cn(
            'shrink-0 rounded p-1 text-amber-700 hover:bg-amber-100',
            'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-500',
            'dark:text-amber-300 dark:hover:bg-amber-900/40',
          )}
        >
          <X aria-hidden="true" className="h-4 w-4" />
        </button>
      </div>
      {expanded ? (
        <ul
          id="ta-disclaimer-details"
          data-testid="ta-disclaimer-details"
          className="ml-8 list-disc space-y-1 text-sm text-amber-900/90 dark:text-amber-100/90"
        >
          <li>
            Un titolo bocciato dai 13 ruleSignals fondamentali NON può diventare
            &quot;ENTRA ORA&quot; sulla base della TA — gate VI primario hardcoded.
          </li>
          <li>
            La TA serve a evitare la trappola COPART: comprare un titolo VI-positivo
            in downtrend primario o ipercomprato porta a chiusure da stop loss anche
            quando il verdetto fondamentale è corretto.
          </li>
          <li>
            Nessun ordine reale viene mai eseguito da questa app — niente broker,
            niente auto-trading. È un decision support tool, non una piattaforma di
            trading.
          </li>
        </ul>
      ) : null}
    </section>
  );
}
