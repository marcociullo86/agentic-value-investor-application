---
id: ADR-023
title: "Design Token System: shadcn/ui con semantic token M3-aligned (EP-016, risolve Q_004)"
status: proposed
created: 2026-05-26
deciders: [lead-architect]
resolves: [Q_004]
---
# ADR-023 — Design Token System: shadcn/ui + Semantic Token M3-Aligned

## Contesto

EP-016 (4 storie: US-069..072) richiede un sistema di design token per colori, tipografia e shape, switch light/dark, motion accessibile e conformità WCAG 2.2 AA. [^src: management/kanban/EP-016-refinement-ui-accessibilita/EP-016.md §Obiettivo]

Q_004 (soft) identifica il conflitto: REQ-03 prescrive il design token system Material Design 3 (M3), ma `raw/tech_stack.md` — che ha priorità assoluta (PATTERN §7 r.10) — prescrive "Tailwind CSS + Radix UI primitives" come stack di styling frontend. Il frontend attuale usa shadcn/ui (componenti Radix-based: Button, Input, Card, Modal, Toast, Table) con classi Tailwind. [^src: management/kanban/EP-016-refinement-ui-accessibilita/US-069-design-token-system/US-069.md §Business Rules]

I componenti UI esistenti (TrafficLightPanel, TopPicksTable, DeepVerdictBadge, MrMarketSentimentBadge, LongTermTrendBadge) usano classi Tailwind hardcoded per colori e spacing.

## Decisione

### Risoluzione Q_004: Opzione (b) — shadcn/ui + token M3-aligned

**Mantenere shadcn/ui (Radix + Tailwind)** come libreria di componenti e costruire un **layer di token semantici M3-aligned** sopra le CSS custom properties di Tailwind. Non migrare a MUI v6 o altra libreria M3-nativa.

Motivazione prioritaria: `raw/tech_stack.md` (status: `approved`, priorità assoluta PATTERN §7 r.10) prescrive esplicitamente "Tailwind CSS + Radix UI primitives". Cambiare libreria di componenti violerebbe il vincolo tecnologico più forte del progetto.

### 1. Token semantici colori

CSS custom properties definite in `styles/tokens/colors.css`, derivate da un seed color:

```css
:root {
  /* Seed color → schema M3-aligned */
  --color-primary: oklch(0.55 0.18 250);
  --color-on-primary: oklch(1.0 0 0);
  --color-primary-container: oklch(0.90 0.05 250);
  --color-on-primary-container: oklch(0.20 0.10 250);

  --color-secondary: oklch(0.55 0.08 250);
  --color-on-secondary: oklch(1.0 0 0);

  --color-tertiary: oklch(0.55 0.12 330);
  --color-on-tertiary: oklch(1.0 0 0);

  --color-surface: oklch(0.99 0.005 250);
  --color-on-surface: oklch(0.12 0.02 250);
  --color-surface-container: oklch(0.95 0.01 250);
  --color-surface-container-high: oklch(0.92 0.015 250);

  --color-outline: oklch(0.50 0.02 250);
  --color-outline-variant: oklch(0.80 0.015 250);

  --color-error: oklch(0.55 0.22 25);
  --color-on-error: oklch(1.0 0 0);

  --color-success: oklch(0.55 0.15 145);
  --color-warning: oklch(0.70 0.15 85);
  --color-info: oklch(0.55 0.12 240);
}
```

I colori usano OKLCH per derivazione coerente percettivamente. Il seed color è configurabile: cambiare il seed rigenera coerentemente tutti i ruoli semantici. [^src: management/kanban/EP-016-refinement-ui-accessibilita/US-069-design-token-system/US-069.md §Business Rules]

### 2. Token semantici tipografia

```css
:root {
  --font-family-base: var(--font-geist-sans, system-ui, sans-serif);
  --font-family-mono: var(--font-geist-mono, ui-monospace, monospace);

  /* Scala M3-aligned */
  --typography-display: 700 2.25rem/2.5rem var(--font-family-base);
  --typography-headline: 600 1.5rem/2rem var(--font-family-base);
  --typography-title: 600 1.25rem/1.75rem var(--font-family-base);
  --typography-body: 400 1rem/1.5rem var(--font-family-base);
  --typography-label: 500 0.875rem/1.25rem var(--font-family-base);
}
```

### 3. Token shape (corner radius)

```css
:root {
  --shape-none: 0;
  --shape-small: 0.5rem;   /* 8px — chip, badge */
  --shape-medium: 0.75rem; /* 12px — card, input */
  --shape-large: 1rem;     /* 16px — dialog, sheet */
  --shape-full: 9999px;    /* pill — avatar, FAB */
}
```

### 4. Integrazione Tailwind

`tailwind.config.ts` esteso con i token semantici:

```typescript
theme: {
  extend: {
    colors: {
      primary: 'var(--color-primary)',
      'on-primary': 'var(--color-on-primary)',
      'primary-container': 'var(--color-primary-container)',
      surface: 'var(--color-surface)',
      'on-surface': 'var(--color-on-surface)',
      'surface-container': 'var(--color-surface-container)',
      // ... tutti i ruoli semantici
    },
    borderRadius: {
      sm: 'var(--shape-small)',
      md: 'var(--shape-medium)',
      lg: 'var(--shape-large)',
      full: 'var(--shape-full)',
    },
  },
}
```

I componenti esistenti vengono migrati da classi hardcoded (`bg-blue-600`) a classi semantiche (`bg-primary`). [^src: management/kanban/EP-016-refinement-ui-accessibilita/US-069-design-token-system/US-069.md §Acceptance Criteria]

### 5. ThemeProvider (light/dark)

Componente React `ThemeProvider` nel root layout:

1. **Default iniziale**: rispetta `prefers-color-scheme` del sistema operativo.
2. **Override utente**: toggle light/dark, persistito in `localStorage('theme')`.
3. **Implementazione**: classe `dark` sul `<html>` element (pattern Tailwind standard).
4. **No FOUC**: script inline bloccante nel `<head>` che applica la classe prima del render (pattern Next.js `beforeInteractive`).
5. **Token dark**: file `styles/tokens/colors-dark.css` con override sotto `.dark { }`.

```css
.dark {
  --color-primary: oklch(0.80 0.12 250);
  --color-surface: oklch(0.15 0.01 250);
  --color-on-surface: oklch(0.92 0.01 250);
  /* ... tutti i ruoli semantici invertiti */
}
```

Contrasto WCAG 2.2 AA verificato in entrambi i temi: testo >= 4.5:1, UI components >= 3:1. [^src: management/kanban/EP-016-refinement-ui-accessibilita/US-070-light-dark-theme/US-070.md §Business Rules]

### 6. Motion tokens

```css
:root {
  --motion-easing-emphasized: cubic-bezier(0.2, 0, 0, 1);
  --motion-easing-standard: cubic-bezier(0.2, 0, 0, 1);
  --motion-duration-short: 150ms;
  --motion-duration-medium: 300ms;
  --motion-duration-long: 500ms;
}

@media (prefers-reduced-motion: reduce) {
  :root {
    --motion-duration-short: 0ms;
    --motion-duration-medium: 0ms;
    --motion-duration-long: 0ms;
  }
}
```

State layers (hover, focus, pressed) implementati come overlay con opacità tokenizzata (`--state-hover-opacity: 0.08`, `--state-focus-opacity: 0.12`), non colori diversi hardcoded. [^src: management/kanban/EP-016-refinement-ui-accessibilita/US-071-stati-interattivi-motion/US-071.md §Business Rules]

### 7. Audit WCAG 2.2 AA baseline

L'audit copre tutte le viste esistenti con i seguenti criteri:

| Criterio | Strumento | Target |
|---|---|---|
| Lighthouse Accessibility | CI (Playwright) | >= 95 su tutte le viste principali |
| axe-core | CI (vitest-axe) | Zero issue `serious` o `critical` |
| Tastiera | Manuale | Flussi critici completabili senza mouse |
| Screen reader | Manuale (VoiceOver/NVDA) | Annuncio corretto heading, form, risultati |
| Zoom 200% | Manuale | Nessun loss of content o scroll orizzontale |
| h1 unico per vista | CI (lint rule) | Un solo `<h1>` per pagina |
| Focus visibile | CI (axe-core SC 2.4.11/2.4.13) | Sempre presente su elementi interattivi |

[^src: management/kanban/EP-016-refinement-ui-accessibilita/US-072-audit-accessibilita-wcag/US-072.md §Acceptance Criteria]

## Componenti

| Componente | Layer | Path suggerito |
|---|---|---|
| Token colori | FE | `styles/tokens/colors.css` + `colors-dark.css` |
| Token tipografia | FE | `styles/tokens/typography.css` |
| Token shape | FE | `styles/tokens/shape.css` |
| Token motion | FE | `styles/tokens/motion.css` |
| `tailwind.config.ts` | FE | root |
| `ThemeProvider` | FE | `components/theme/theme-provider.tsx` |
| `useTheme` | FE | `hooks/use-theme.ts` |
| Lighthouse CI config | QA | `.lighthouserc.js` |

Nessun componente backend o DB.

## Motivazioni

1. **`raw/tech_stack.md` ha priorità assoluta**: prescrive "Tailwind CSS + Radix UI primitives". Migrare a MUI v6 violerebbe il vincolo tecnologico più forte.
2. **CSS custom properties come bridge**: i token semantici M3-aligned sono un layer di astrazione sopra Tailwind, senza lock-in su alcuna libreria M3.
3. **OKLCH per coerenza percettiva**: lo spazio colore OKLCH garantisce che le derivazioni light/dark mantengano uniformità di chroma e luminosità.
4. **Tailwind dark mode nativo**: il pattern classe `dark` + CSS custom properties è il meccanismo standard di Tailwind per il theming.
5. **Migrazione incrementale**: i componenti esistenti possono essere migrati uno alla volta da classi hardcoded a classi semantiche, senza big bang.

## Alternative considerate

| Alternativa | Esito |
|---|---|
| **(a) MUI v6** (implementa M3 nativamente) | Richiede sostituzione completa di shadcn/ui + Radix; viola `raw/tech_stack.md`; rework massiccio di tutti i componenti. Scartato. |
| **(c) Approccio ibrido** (MUI per nuovi componenti M3, shadcn per i vecchi) | Due sistemi di design nello stesso progetto; incoerenza visuale; aumento del bundle size. Scartato. |
| **CSS-in-JS token system** (vanilla-extract, Panda CSS) | Aggiunge build tooling e astrazione. CSS custom properties sono nativi, zero-config, performanti. Scartato. |
| **Tailwind v4 native CSS** | Tailwind v4 adotta CSS custom properties nativamente, ma il progetto usa Tailwind via Next.js 16 che pinna la versione. Rivalutabile quando Tailwind v4 sarà stabile nell'ecosistema Next.js. |

## Conseguenze

- **Q_004 risolta**: decisione formale "shadcn/ui + M3-aligned token layer" con motivazione architetturale documentata.
- **US-069..072**: tutte implementabili con i componenti descritti.
- **Gap `fintech-design-system-react`** in `wiki/gaps.md`: risolvibile dopo accettazione di questo ADR.
- **Componenti esistenti**: da migrare incrementalmente a classi semantiche (task da creare in EP-016).
- **Nessuna nuova dipendenza npm**: tutto basato su CSS native + Tailwind config.
- **EP-015** (notifiche): i token di colore garantiscono il contrasto WCAG nelle notifiche.

## Pagine collegate

- [ADR-022](ADR-022-frontend-error-notifications.md) — notifiche (dipendente per WCAG contrasto)
- [ADR-001-v2](ADR-001-v2-frontend-stack-versions-2026.md) — stack frontend versions
