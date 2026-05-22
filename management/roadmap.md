---
id: roadmap
type: roadmap
title: Roadmap
status: draft
created: 2026-05-20
updated: 2026-05-22
tags: [planning]
---
# Roadmap — App Template Demo

> Roadmap release-driven. Compilata dal `product-manager` dopo il primo run; riconciliata il 2026-05-20 dopo chiusura Q_001/Q_002/Q_003.

## Convenzione

- **R1.0 MVP**: epiche ad alta confidence (>= 65%) che coprono il flusso end-to-end minimo (ricerca → dati → verdetto quantitativo + valutazione intrinseca).
- **R1.1 Consolidamento**: epiche con `confidence < 65%` o che richiedono ulteriore consolidamento UI / persistenza.
- **R2 Espansione**: capacità avanzate non più presenti dopo la riconciliazione del 2026-05-20.

## Release 1.0 — MVP

Obiettivo: l'utente cerca un ticker, il sistema scarica i dati di bilancio decennali, produce un verdetto quantitativo strutturato e calcola il valore intrinseco con Margin of Safety.

| Epica | Titolo | Priorità | Confidence |
|---|---|---|---|
| [EP-001](kanban/EP-001-ricerca-e-screening/EP-001.md) | Ricerca e Screening titoli | high | 80% |
| [EP-002](kanban/EP-002-integrazione-fmp-data-provider/EP-002.md) | Integrazione FMP Data Provider | high | 65% |
| [EP-003](kanban/EP-003-rule-engine-quantitativo/EP-003.md) | Value Investing Rule Engine quantitativo | high | 85% |
| [EP-004](kanban/EP-004-valore-intrinseco-margin-of-safety/EP-004.md) | Calcolo Valore Intrinseco e Margin of Safety | high | 80% |

Note:
- EP-001: Q_003 risolta → US-002 promossa a `todo` con criteri definiti (5 fasce cap + lista GICS). Confidence 75% → 80%.
- EP-004 promossa da R1.1 a R1.0: Q_001 risolta (Owner Earnings = Greenwald primario, FCF fallback). Confidence 55% → 80%.

## Release 1.1 — Dashboard, Moat e watchlist

Obiettivo: completare l'esperienza utente con visualizzazione semaforica integrata, grafici storici, valutazione qualitativa del Moat e persistenza della watchlist personale.

| Epica | Titolo | Priorità | Confidence | Note |
|---|---|---|---|---|
| [EP-005](kanban/EP-005-dashboard-traffic-light-moat/EP-005.md) | Dashboard, Traffic Light e Moat qualitativo | medium | 75% | Promossa da R2: Q_002 risolta (React + Next.js). |
| [EP-006](kanban/EP-006-watchlist-utente/EP-006.md) | Watchlist, autenticazione e profilo utente | medium | 70% | Scope esteso il 2026-05-22 con US-018 (registrazione) e US-019 (login/logout). |

## Release 2 — Espansione

Nessuna epica attualmente allocata a R2 dopo la riconciliazione del 2026-05-20. Riservata a evoluzioni post-MVP (es. notifiche, alerting, multi-utente).

## Question aperte che impattano la roadmap

Nessuna question aperta al 2026-05-20. Vedi `management/questions.md` per lo storico [RISOLTE].

## Gap aperti che impattano la roadmap

- `fmp-rate-limiting`, `fmp-endpoint-base-urls`, `fmp-error-codes` (EP-002): non bloccanti; implementazione conservativa.

## Cronologia riconciliazioni

- **2026-05-20**: chiusura Q_001 / Q_002 / Q_003 da raw 07 + 08. US-002/US-012/US-014/US-015/US-016 sbloccate. EP-004 promossa R1.1 → R1.0 (confidence 55% → 80%). EP-005 promossa R2 → R1.1 (confidence 50% → 75%).
- **2026-05-22**: full reconcile post Sprint 3 (auth + watchlist mergeed in master). EP-006 esteso a "Watchlist, autenticazione e profilo utente" con US-018 (registrazione) e US-019 (login/logout); confidence 60% → 70%. Aggiunta US-020 (override DCF method per utente autenticato) sotto EP-004, formalizzando TSK-017/018 già implementati su L5.
