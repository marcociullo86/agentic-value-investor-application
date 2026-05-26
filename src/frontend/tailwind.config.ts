import type { Config } from 'tailwindcss';

/**
 * Tailwind config (TSK-030 + TSK-184).
 *
 * - `darkMode: 'class'` allinea a Radix UI primitives e consente toggle manuale.
 * - Tokens `signal.*` per Traffic Light (frontend-components.md §Design system,
 *   US-014 AC accessibilità: contrasto WCAG AA + alternative testuali/icona).
 * - Semantic tokens M3-aligned via CSS custom properties (ADR-023).
 */
const config: Config = {
  content: [
    './app/**/*.{ts,tsx}',
    './components/**/*.{ts,tsx}',
    './lib/**/*.{ts,tsx}',
    './styles/**/*.css',
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        signal: {
          green: '#16a34a',
          yellow: '#d97706',
          red: '#dc2626',
          neutral: '#64748b',
        },
        primary: 'var(--color-primary)',
        'on-primary': 'var(--color-on-primary)',
        'primary-container': 'var(--color-primary-container)',
        'on-primary-container': 'var(--color-on-primary-container)',
        secondary: 'var(--color-secondary)',
        'on-secondary': 'var(--color-on-secondary)',
        tertiary: 'var(--color-tertiary)',
        'on-tertiary': 'var(--color-on-tertiary)',
        surface: 'var(--color-surface)',
        'on-surface': 'var(--color-on-surface)',
        'surface-container': 'var(--color-surface-container)',
        'surface-container-high': 'var(--color-surface-container-high)',
        outline: 'var(--color-outline)',
        'outline-variant': 'var(--color-outline-variant)',
        error: 'var(--color-error)',
        'on-error': 'var(--color-on-error)',
        success: 'var(--color-success)',
        warning: 'var(--color-warning)',
        info: 'var(--color-info)',
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
      borderRadius: {
        sm: 'var(--shape-small)',
        md: 'var(--shape-medium)',
        lg: 'var(--shape-large)',
        full: 'var(--shape-full)',
      },
    },
  },
  plugins: [],
};

export default config;
