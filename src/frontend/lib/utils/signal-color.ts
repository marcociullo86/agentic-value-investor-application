/**
 * Mapping Signal → classi Tailwind + label accessibile (TSK-030).
 *
 * Riferimento: design_&_architecture/components/frontend-components.md
 *   §Design system / Codifica colore Traffic Light. WCAG AA verificato lato
 *   token (`tailwind.config.ts` → `colors.signal.*`).
 *
 * NOTA: il tipo `Signal` ricalca l'enum backend
 * (src/backend/.../ruleengine/Signal.kt). Dopo `npm run generate:api` si potrà
 * sostituire con `components['schemas']['Signal']` dallo schema generato.
 */

export type Signal = 'GREEN' | 'YELLOW' | 'RED' | 'INDETERMINATE' | 'NOT_CALCULABLE';

interface SignalPresentation {
  /** Combinazione di classi Tailwind per bg + testo. */
  readonly className: string;
  /** Label testuale (alternativa al solo colore, US-014 accessibilità). */
  readonly label: string;
  /** Glyph testuale fallback (assistive technology). */
  readonly icon: string;
}

const PRESENTATIONS: Readonly<Record<Signal, SignalPresentation>> = {
  GREEN: {
    className: 'bg-signal-green text-white',
    label: 'OK',
    icon: '✓',
  },
  YELLOW: {
    className: 'bg-signal-yellow text-black',
    label: 'Attenzione',
    icon: '!',
  },
  RED: {
    className: 'bg-signal-red text-white',
    label: 'Non soddisfatta',
    icon: '✕',
  },
  INDETERMINATE: {
    className: 'bg-signal-neutral text-white',
    label: 'Indeterminato',
    icon: '?',
  },
  NOT_CALCULABLE: {
    className: 'bg-signal-neutral text-white',
    label: 'Non calcolabile',
    icon: '?',
  },
};

export function signalClass(signal: Signal): string {
  return PRESENTATIONS[signal].className;
}

export function signalLabel(signal: Signal): string {
  return PRESENTATIONS[signal].label;
}

export function signalIcon(signal: Signal): string {
  return PRESENTATIONS[signal].icon;
}

export function signalPresentation(signal: Signal): SignalPresentation {
  return PRESENTATIONS[signal];
}
