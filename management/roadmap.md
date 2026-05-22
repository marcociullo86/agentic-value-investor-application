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

> Roadmap release-driven. R1.0 MVP chiuso 2026-05-22 (6 EP, 20 US, 49 TSK). R1.1 pianificata post-chiusura amministrativa.

## Convenzione

- **R1.0 MVP**: flusso end-to-end ricerca → dati → verdetto quantitativo + valutazione intrinseca + dashboard + watchlist/auth (chiuso).
- **R1.1 Consolidamento produzione**: hardening debito tecnico, deploy/ops, documentazione e throttling FMP.
- **R2 Espansione**: capacità avanzate non coperte da FSD MVP (SEC narrativo, SSO enterprise, alerting).

## Release 1.0 — MVP (chiusa 2026-05-22)

Obiettivo: l'utente cerca un ticker, il sistema scarica i dati di bilancio decennali, produce un verdetto quantitativo strutturato e calcola il valore intrinseco con Margin of Safety; dashboard, moat, watchlist e auth.

| Epica | Titolo | Priorità | Confidence | Stato |
|---|---|---|---|---|
| [EP-001](kanban/EP-001-ricerca-e-screening/EP-001.md) | Ricerca e Screening titoli | high | 80% | done |
| [EP-002](kanban/EP-002-integrazione-fmp-data-provider/EP-002.md) | Integrazione FMP Data Provider | high | 65% | done |
| [EP-003](kanban/EP-003-rule-engine-quantitativo/EP-003.md) | Value Investing Rule Engine quantitativo | high | 85% | done |
| [EP-004](kanban/EP-004-valore-intrinseco-margin-of-safety/EP-004.md) | Calcolo Valore Intrinseco e Margin of Safety | high | 80% | done |
| [EP-005](kanban/EP-005-dashboard-traffic-light-moat/EP-005.md) | Dashboard, Traffic Light e Moat qualitativo | medium | 75% | done |
| [EP-006](kanban/EP-006-watchlist-utente/EP-006.md) | Watchlist, autenticazione e profilo utente | medium | 70% | done |

Commit di riferimento chiusura: `940852a` su `master`.

## Release 1.1 — Consolidamento produzione

Obiettivo: chiudere debito tecnico post-MVP, abilitare cutover produzione e allineare throttling/documentazione FMP prima del go-live operativo.

| Epica | Titolo | Priorità | Confidence | US | Note |
|---|---|---|---|---|---|
| [EP-007](kanban/EP-007-hardening-produzione/EP-007.md) | Hardening produzione e conformità contratti | high | 85% | 5 | Gap `be-problemdetail-flatten`, `fe-swr-peer-r19`, `fe-static-export-tickers`, `tpm-profile-snapshot-ttl`, `arch-adr-version-sync` |
| [EP-008](kanban/EP-008-deploy-operativita-produzione/EP-008.md) | Deploy e operatività produzione | high | 78% | 3 | Gap `arch-deployment-target`; prerequisito cutover |
| [EP-009](kanban/EP-009-throttling-fmp-runbook/EP-009.md) | Throttling FMP e runbook operativo provider | medium | 72% | 2 | Gap `fmp-rate-limiting`, `fmp-endpoint-base-urls`, `fmp-error-codes`; US-030 dopo US-029 |

**Totale R1.1:** 3 epiche, 10 user story (US-021…US-030), tutte `ready`.

### Candidati R1.1+ (non taskizzati — confidence < 65%)

| Tema | Confidence | Gap / nota |
|---|---|---|
| SSO enterprise / provider OIDC esterno | 55% | `arch-auth-provider-choice` — evoluzione post-MVP, non bloccante R1.1 |
| Citation audit lint L4 deferred | 60% | memory episodica 2026-05-22; sanabile con US-025 + lint |
| Watchlist lazy vs eager creation | 65% | `tpm-watchlist-default-creation` — UX minore, non epica dedicata |

## Release 2 — Espansione

| Tema | Confidence | Fonte wiki |
|---|---|---|
| Analisi SEC narrativa (10-K Item 1/1A/7) | 45% | `vi-sec-narrative-gap` — richiede EDGAR o provider terzi |
| Notifiche / alerting watchlist | 40% | Non in FSD MVP |
| Integrazioni B2B SSO obbligatorio | 50% | `arch-auth-provider-choice` |

Nessuna epica EP-010+ allocata a R2 in questo run.

## Question aperte che impattano la roadmap

Nessuna question aperta al 2026-05-22. Vedi `management/questions.md` per lo storico [RISOLTE].

## Gap aperti che impattano la roadmap

| Gap | Epica R1.1 | Bloccante |
|---|---|---|
| be-problemdetail-flatten | EP-007 / US-021 | no |
| fe-swr-peer-r19 | EP-007 / US-022 | no |
| fe-static-export-tickers | EP-007 / US-023 | no |
| tpm-profile-snapshot-ttl | EP-007 / US-024 | no |
| arch-adr-version-sync | EP-007 / US-025 | no |
| arch-deployment-target | EP-008 | pre-cutover |
| fmp-rate-limiting, fmp-endpoint-base-urls, fmp-error-codes | EP-009 | no (US-030 attende US-029) |
| arch-auth-provider-choice | R2 / candidato | no |
| vi-sec-narrative-gap | R2 | no |

## Cronologia riconciliazioni

- **2026-05-20**: chiusura Q_001 / Q_002 / Q_003. EP-004 promossa R1.0; EP-005 promossa R1.1.
- **2026-05-22 (mattina)**: reconcile post Sprint 3–4; EP-006 esteso auth; US-020 sotto EP-004.
- **2026-05-22 (pomeriggio)**: chiusura amministrativa R1.0 MVP. Pianificazione R1.1: EP-007, EP-008, EP-009 con US-021…030.
