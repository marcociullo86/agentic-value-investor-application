---
type: concept
sources: ["raw/requisiti-funzionali-fintech.md"]
status: draft
created: 2026-05-26
updated: 2026-05-26
tags: [ui, material-design-3, accessibility, wcag, design-tokens, theme, frontend, fintech]
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

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
- EP-016 / [US-069](../../management/kanban/EP-016-refinement-ui-accessibilita/US-069-design-token-system/US-069.md) — Sistema design token colori, tipografia e shape
- EP-016 / [US-070](../../management/kanban/EP-016-refinement-ui-accessibilita/US-070-light-dark-theme/US-070.md) — Switch light/dark theme utente persistente
- EP-016 / [US-071](../../management/kanban/EP-016-refinement-ui-accessibilita/US-071-stati-interattivi-motion/US-071.md) — Stati interattivi e motion token
- EP-016 / [US-072](../../management/kanban/EP-016-refinement-ui-accessibilita/US-072-audit-accessibilita-wcag/US-072.md) — Audit e fix accessibilità WCAG 2.2 AA baseline
