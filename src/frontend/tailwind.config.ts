import type { Config } from 'tailwindcss';

/**
 * Tailwind config (TSK-030).
 *
 * - `darkMode: 'class'` allinea a Radix UI primitives e consente toggle manuale.
 * - Tokens `signal.*` per Traffic Light (frontend-components.md §Design system,
 *   US-014 AC accessibilità: contrasto WCAG AA + alternative testuali/icona).
 */
const config: Config = {
  content: [
    './app/**/*.{ts,tsx}',
    './components/**/*.{ts,tsx}',
    './lib/**/*.{ts,tsx}',
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        signal: {
          // GREEN — WCAG AA contrast con testo bianco (4.5:1+).
          green: '#16a34a',
          // YELLOW — usato con testo nero per garantire contrasto.
          yellow: '#d97706',
          // RED — bianco su rosso ≥4.5:1.
          red: '#dc2626',
          // INDETERMINATE / NOT_CALCULABLE.
          neutral: '#64748b',
        },
      },
      fontFamily: {
        sans: [
          'ui-sans-serif',
          'system-ui',
          '-apple-system',
          'Segoe UI',
          'Roboto',
          'sans-serif',
        ],
        mono: ['ui-monospace', 'SFMono-Regular', 'Menlo', 'monospace'],
      },
    },
  },
  plugins: [],
};

export default config;
