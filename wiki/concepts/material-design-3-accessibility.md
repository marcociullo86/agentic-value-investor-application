---
type: concept
sources: ["raw/requisiti-funzionali-fintech.md"]
status: draft
created: 2026-05-26
updated: 2026-05-26 (v2026-05-26 US-072 audit WCAG completata)
tags: [ui, material-design-3, accessibility, wcag, design-tokens, theme, frontend, fintech, platform-domain]
domain: platform
---
# Refinement UI: Material Design 3 e Accessibilita

> Allineare la UI ai nuovi standard Material Design 3 e ai principi WCAG 2.2 AA senza riscrivere l'applicazione.

## Contesto

REQ-03 del documento di iterazione fintech prescrive l'adozione del design token system Material Design 3 (M3) e il raggiungimento della conformita WCAG 2.2 AA come baseline di accessibilita. [^src: raw/requisiti-funzionali-fintech.md §REQ-03] Si tratta di un'iterazione di refinement, non di un redesign visuale completo.

## Dettaglio

### Design Token System M3

I colori vengono derivati da un seed color tramite schema dinamico M3: `primary`, `secondary`, `tertiary`, `surface`, `surface-container`, etc. La tipografia segue la scala M3: `display`, `headline`, `title`, `body`, `label`. [^src: raw/requisiti-funzionali-fintech.md §REQ-03]

### Light/Dark Theme

Supporto obbligatorio per switch utente persistente tra light e dark theme, con rispetto di `prefers-color-scheme` come default iniziale. [^src: raw/requisiti-funzionali-fintech.md §REQ-03]

### Componenti M3

I componenti aggiornati alle linee guida M3 includono: bottoni (filled, tonal, elevated, outlined, text), chips, navigation bar/rail, FAB, cards, dialogs. [^src: raw/requisiti-funzionali-fintech.md §REQ-03]

### Shape System

Corner radius coerenti tramite token: `shape.small`, `shape.medium`, `shape.large`. [^src: raw/requisiti-funzionali-fintech.md §REQ-03]

### Stati Interattivi e Motion

Gli stati hover, focus, pressed, dragged sono implementati tramite state layers M3 con overlay a opacita coerente (non colori hardcoded). Le transizioni usano easing M3 (`emphasized`, `standard`) con durate tokenizzate. Il rispetto di `prefers-reduced-motion` e obbligatorio: le animazioni decorative vengono disabilitate o ridotte. [^src: raw/requisiti-funzionali-fintech.md §REQ-03]

### Accessibilita WCAG 2.2 AA (baseline)

| Requisito | Specifica |
|---|---|
| Contrasto testo | >= 4.5:1 (normale), >= 3:1 (grande) |
| Contrasto UI components | >= 3:1 |
| Target touch | >= 24x24 CSS px (idealmente 48x48 su mobile) |
| Focus visibile | Sempre presente; no outline rimosso senza alternativa equivalente (SC 2.4.11/2.4.13) |
| Tastiera | Tutte le interazioni completabili da sola tastiera; ordine tab logico |
| Heading hierarchy | Un solo h1 per vista; no salti di livello |
| Form labels | `<label for>` o `aria-label` esplicite; error message via `aria-describedby` |
| Immagini | `alt` semantico; icone decorative con `aria-hidden="true"` |
| Zoom | Supporto fino a 200% senza loss of content o funzionalita |

[^src: raw/requisiti-funzionali-fintech.md §REQ-03]

## Acceptance criteria

- Punteggio Lighthouse Accessibility >= 95 su tutte le viste principali.
- Audit axe-core senza issue di severita serious o critical.
- Verifica manuale con tastiera e screen reader sui flussi critici (login, dashboard, transazione, profilo).

## Concetti correlati

[[frontend-error-notifications]]

## Pagine collegate

[[webapp-architecture-vi]]
[[fintech-hardening-requirements-map]]

## Aggiornamenti (v2026-05-26)

### EP-016 completata — Sprint 12 (10 TSK done, ADR-023 accettato)

L'epica EP-016 (Refinement UI e Accessibilità) è stata implementata e pushata su master. ADR-023 ha formalizzato l'approccio: estendere i componenti shadcn/ui esistenti con un sistema di design token M3-aligned (non sostituire con MUI). Di seguito il dettaglio implementativo per US.

### Design token system OKLCH (US-069)

Tre file di token CSS creati in `src/frontend/styles/tokens/`: [^src: src/frontend/styles/tokens/colors.css]

- **`colors.css`**: 19 variabili custom property OKLCH M3-aligned (`--color-primary`, `--color-on-primary`, `--color-primary-container`, `--color-secondary`, `--color-tertiary`, `--color-surface`, `--color-surface-container`, `--color-surface-container-high`, `--color-outline`, `--color-outline-variant`, `--color-error`, `--color-on-error`, `--color-success`, `--color-warning`, `--color-info`, etc.).
- **`typography.css`**: 5 livelli type scale M3 (`--type-display`, `--type-headline`, `--type-title`, `--type-body`, `--type-label`) in font shorthand con dimensioni monotonicamente decrescenti.
- **`shape.css`**: 5 border-radius token (`--shape-none` 0, `--shape-small` 4px, `--shape-medium` 8px, `--shape-large` 16px, `--shape-full` 9999px).

`tailwind.config.ts` esteso con mapping semantico: 19 colori via `var(--color-*)`, 4 `borderRadius` via `var(--shape-*)`. Signal colors (`bg-signal-*` per TrafficLightPanel) preservati come namespace separato.

### Migrazione 6 componenti a token (US-069, TSK-185)

6 componenti shadcn/ui migrati da classi Tailwind hardcoded (`bg-blue-600`, `bg-slate-200`, `bg-white`, `text-slate-600`) a classi semantiche CSS custom properties: [^src: src/frontend/components/ui/Button.tsx]

| Componente | Prima | Dopo |
|---|---|---|
| **Button** | `bg-blue-600`/`text-white` + 8 `dark:` override | `bg-primary`/`text-on-primary` (4 varianti) |
| **Card** | `bg-white`/`border-slate-200` + `dark:` | `bg-surface-container`/`border-outline-variant` |
| **Input** | `bg-white`/`border-slate-300` + `dark:` | `bg-surface`/`border-outline` |
| **Modal** | `bg-black/50`/`bg-white` + `dark:` | `bg-on-surface/50`/`bg-surface` |
| **Toast** | `bg-slate-100` + `dark:` | `bg-surface-container` |
| **Navbar** | `bg-white`/`border-slate-200` + `dark:` | `bg-surface`/`border-outline-variant` |

Dark mode gestito interamente dal CSS variable system: i token OKLCH si aggiornano via `:root`/`.dark` selector senza bisogno di `dark:` prefix Tailwind (eliminati tutti i `dark:` override ridondanti).

### ThemeProvider e dark mode (US-070)

`ThemeProvider` React Context (`src/frontend/components/theme/theme-provider.tsx`) con supporto `system`/`light`/`dark`: [^src: src/frontend/components/theme/theme-provider.tsx]

- `useTheme` hook espone `{ theme, setTheme, toggleTheme }`.
- Persistenza via `localStorage` con key `theme`.
- Listener `prefers-color-scheme` attivo solo in modalità `system`.
- **Anti-FOUC**: script inline blocking in `<head>` (in `app/layout.tsx`) che legge `localStorage` + `matchMedia` prima del primo render React, applicando la classe `.dark` immediatamente.
- **`colors-dark.css`**: 20 variabili OKLCH per `.dark` selector, M3-aligned.
- **Toggle accessibile** in Navbar: icone Sun/Moon (lucide-react), `aria-label`, `focus-visible`, keyboard navigable.
- `tailwind.config.ts` con `darkMode: 'class'` già configurato.

### Motion tokens e state layers (US-071)

`styles/tokens/motion.css` creato con token M3: [^src: src/frontend/styles/tokens/motion.css]

- 2 easing token: `--motion-easing-emphasized` e `--motion-easing-standard` (entrambi `cubic-bezier(0.2, 0, 0, 1)`).
- 3 durate: `--motion-duration-short` (150ms), `--motion-duration-medium` (300ms), `--motion-duration-long` (500ms).
- 3 state layer opacity: hover 0.08, focus 0.12, pressed 0.16.
- **`prefers-reduced-motion: reduce`**: media query che azzera tutte le durate a 0ms.

Classe utility `.state-layer` in `globals.css` con pseudo-elemento `::after` overlay a `currentColor` + opacity tokenizzata per hover/focus-visible/active. `Button.tsx` migrato a state layer (rimossi `hover:bg-*` Tailwind hardcoded). Focus-visible ring (WCAG 2.4.7) invariato.

### Audit accessibilità WCAG 2.2 AA (US-072, Sprint 12 residuo — completata)

US-072 completata con 3 TSK (TSK-191, TSK-192, TSK-193) che coprono audit manuale, CI automatizzato e test E2E.

**TSK-191 — Audit manuale WCAG 2.2 AA su 10 viste** (20 file toccati): heading hierarchy (singolo `h1` per vista, nessun salto di livello), focus rings (`focus-visible` globale verificato), form labels (`<label>` esplicite + `aria-describedby` per errori), table a11y (`<caption>` + header su WatchlistTable e TopPicksTable), skip-to-content link, navbar `aria-label`. Componenti corretti: Button, Card, Input, Navbar, TopPicksTable, WatchlistTable, ValuationSummary, DcfOverridePanel, DeepVerdictBadge, DrawdownChart, EdgarFilingLinks, MungerReportCollapsible, NewsSentimentChip, AnalysisPageClient, DeepAnalysisPageClient, layout, login, register, watchlist, moat.

**TSK-192 — Lighthouse CI + axe-core test suite**: 21 test (7 viste × 3 suite: axe-core zero serious/critical, single h1 lint, full audit). Configurazione Lighthouse CI (`.lighthouserc.js`) con target accessibility ≥ 95 su 7 URL. vitest-axe come devDependency.

**TSK-193 — E2E tastiera + zoom 200% + checklist screen reader** (Playwright): 4 flussi tastiera (login, search, top-picks, watchlist) + Escape/Shift+Tab. Zoom 200% via viewport 640×360 con `assertNoHorizontalOverflow` su 5 viste. Checklist screen reader manuale documentata (heading, form labels, aria-live toast, tabelle, focus management).

Le basi per la conformità WCAG 2.2 AA sono ora verificate: contrasto OKLCH, focus-visible globale, prefers-reduced-motion, aria-label, heading hierarchy, form labels, table a11y, skip-to-content, Lighthouse ≥ 95, axe-core zero serious/critical, tastiera navigabile, zoom 200% supportato.

## Aggiornamenti (v2026-05-27) — Run test E2E locale

Snapshot run manuale Playwright mocked su workstation (post-CI #131 verde): **30/30 test E2E a11y falliti per assenza browser Chromium** (`npx playwright install` non eseguito); **10/10 cutover-smoke skipped** (credenziali staging assenti). Vitest locale **434/434 pass**. Dettaglio tabellare e remediation in [[2026-05-27-local-fe-test-run]].

[^src: wiki/incidents/2026-05-27-local-fe-test-run.md §Playwright mocked — dettaglio per file]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
- EP-016 / [US-069](../../management/kanban/EP-016-refinement-ui-accessibilita/US-069-design-token-system/US-069.md) — Sistema design token colori, tipografia e shape
- EP-016 / [US-070](../../management/kanban/EP-016-refinement-ui-accessibilita/US-070-light-dark-theme/US-070.md) — Switch light/dark theme utente persistente
- EP-016 / [US-071](../../management/kanban/EP-016-refinement-ui-accessibilita/US-071-stati-interattivi-motion/US-071.md) — Stati interattivi e motion token
- EP-016 / [US-072](../../management/kanban/EP-016-refinement-ui-accessibilita/US-072-audit-accessibilita-wcag/US-072.md) — Audit e fix accessibilità WCAG 2.2 AA baseline
