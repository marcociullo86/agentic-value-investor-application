---
id: roadmap
type: roadmap
title: Roadmap
status: active
created: 2026-05-20
updated: 2026-06-08
tags: [planning]
---
# Roadmap — App Template Demo

> Roadmap release-driven. R1.0 MVP → R3.4 tutte chiuse (Sprint 1–20, **323/323 TSK `done`**). Prossimo: R4.0 EP-024 (Tab Riepilogo VI+TA + Tab Technical Analysis), in backlog `ready`, non ancora schedulato. Dettaglio esecutivo per sprint in [`kanban/sprint.md`](kanban/sprint.md).

## Convenzione

- **R1.0 MVP**: flusso end-to-end ricerca → dati → verdetto quantitativo + valutazione intrinseca + dashboard + watchlist/auth.
- **R1.1 / R1.1.x**: consolidamento produzione + hotfix rule engine.
- **R2.x**: deep analysis (10-K/10-Q + LLM Munger), batch Top Value Picks, Mr. Market context flags.
- **R3.x**: observability, UX/a11y, notifiche errori, protezione rotte/sessione, hardening sicurezza, bonifica CQRL, completeness Graham, trasparenza LLM, refactor contratto RuleSignal, NCAV Net-Net.
- **R4.0 Espansione**: decision layer VI+TA (Technical Analysis advisory + Riepilogo azionabile).

## Release chiuse

| Release | Sprint | Epiche | TSK | Chiusura | Sintesi |
|---|---|---|---|---|---|
| **R1.0 MVP** | 1–4 | EP-001…006 | 49 | 2026-05-22 | Ricerca → dati FMP decennali → verdetto quantitativo → valore intrinseco + MoS → dashboard, moat, watchlist, auth |
| **R1.1** | 5 | EP-007, EP-008, EP-009 | 23 | 2026-05-23 | Hardening produzione, deploy/ops, throttling FMP + runbook |
| **R1.1.x** | 5.5 | EP-007 fase 2 | 12 | 2026-05-26 | Hotfix rule engine (DCF per-share, ROE/ROIC deserializzazione, formato date) — US-052/053/054 |
| **R2.0** | 6–9 | EP-010, EP-011, EP-012 | 84 | 2026-05-26 | 6 criteri Graham, Deep Analysis 10-K/10-Q (pgvector + LLM Munger inversion), Top Value Picks batch |
| **R2.1** | 10 | EP-013 | 6 | 2026-05-26 | Mr. Market Context Flags (RSI + trend SMA200) |
| **R3.0** | 11–15, 17 | EP-014…018 | 75 | 2026-05-29 | Observability, UX/a11y, notifiche errori FE, protezione rotte/sessione, hardening sicurezza (MFA/CSP/rate-limit) |
| **R3.1** | 16 | EP-019 | 25 | 2026-05-29 | Bonifica CQRL generale — retro-review 224 TSK + ruleset canonical |
| **R3.2** | 18 | EP-002 US-031, EP-010 | 21 | 2026-06-03 | Migrazione FMP `/stable` + completeness 6 criteri Graham (13 ruleId totali) |
| **R3.3** | 19 | EP-020 | 10 | 2026-06-03 | Trasparenza analisi LLM (sintesi narrativa Munger, news analizzate, logging gated) |
| **R3.4** | 20 | EP-017 US-092, EP-021, EP-023 | 15 | 2026-06-06 | Cascade revocation refresh token, RuleSignal typed payload (13 sotto-tipi), NCAV Net-Net |

**Totale chiuso:** 23 epiche, 323 TSK `done`. Commit chiusura R3.4: `7c51c47` + remediation CI `3a7cbaa` su `master`.

ADR `accepted` storici fino a R3.4: ADR-021…029.

## Release 4.0 — Espansione decision layer VI+TA (prossima)

Obiettivo: aggiungere al dettaglio ticker un **tab Technical Analysis** (advisory sul *quando* entrare/uscire) e un **tab Riepilogo** che aggrega verdetto Value Investing (il *cosa*) e segnale TA (il *quando*) in una raccomandazione azionabile, con **gate VI primario hardcoded** in BE (un titolo VI-negativo non può mai diventare `ENTER_NOW`). Rationale empirico: caso COPART (titolo VI-positivo chiuso da stop loss per timing sbagliato).

| Epica | Titolo | Priorità | Confidence | Stato | Note |
|---|---|---|---|---|---|
| [EP-024](kanban/EP-024-riepilogo-e-technical-analysis-tab/EP-024.md) | Tab Riepilogo (verdetto azionabile VI+TA) + Tab Technical Analysis | high | 65% | `ready` (backlog) | 7 US definite (US-098…104), tutte `ready`; **non ancora taskizzate** → candidato Sprint 21 |

**Fase 1 (5 US):** US-098 (BE pipeline TA payload), US-099 (BE entry-timing advisor Triple-Screen-like), US-100 (BE stop-placement + position-sizing 2%/6% Rule), US-101 (FE tab Technical Analysis), US-102 (QA E2E + scenario stile-COPART).
**Fase 2 capstone (dipende da Fase 1):** US-103 (BE aggregatore `/summary` con gate VI hardcoded + citazioni RAG cross-dominio), US-104 (FE tab Riepilogo come primo tab + warning anti-COPART; `blocked_by` US-103, US-101).

**Precondizioni infra (tutte `done`):** EP-011 (pgvector + arctic-embed-l-v2), EP-013 (`FmpAdapter.getTechnicalIndicator`), EP-020 (trasparenza LLM), EP-007 (analyze service), EP-021/EP-023 (RuleSignal typed). Knowledge base TA già ingerita in R3.4: 3 syntheses + 11 concept (Elder/Murphy) nel dominio `technical-analysis-trading`.

**ADR atteso:** ADR-030 (delegato a lead-architect) — persistenza payload TA (cache-aside 24h vs tabella `technical_analysis_snapshot`) + governance corpus RAG cross-dominio (wiki concepts/syntheses come secondo corpus pgvector oltre ai filing).

### Candidati R4.0+ (non taskizzati — confidence < 65%)

| Tema | Confidence | Gap / nota |
|---|---|---|
| Analisi SEC narrativa (10-K Item 1/1A/7 estesa) | 45% | `vi-sec-narrative-gap` — oltre la pipeline RAG attuale |
| Notifiche / alerting watchlist | 40% | Non in FSD MVP |
| SSO enterprise / provider OIDC esterno | 50% | `arch-auth-provider-choice` — evoluzione post-MVP |

## Question aperte che impattano la roadmap

Nessuna question aperta al 2026-06-08. Vedi `management/questions.md` per lo storico [RISOLTE].

## Cronologia riconciliazioni

- **2026-05-20**: chiusura Q_001 / Q_002 / Q_003. EP-004 promossa R1.0; EP-005 promossa R1.1.
- **2026-05-22**: chiusura amministrativa R1.0 MVP; pianificazione R1.1 (EP-007/008/009).
- **2026-05-23 → 2026-06-03**: R1.1 → R3.3 chiuse in sequenza (vedi tabella release).
- **2026-06-06**: chiusura R3.4 Sprint 20 (EP-017 US-092 + EP-021 + EP-023), CI verde post-remediation.
- **2026-06-08**: riconciliazione roadmap ↔ `sprint.md` ↔ file EP. Allineate R1.1.x→R3.4 (la roadmap era ferma al 2026-05-22); EP-017/021/023 portate a `status: done`; board rigenerato. EP-024 confermata prossima (R4.0, backlog).
