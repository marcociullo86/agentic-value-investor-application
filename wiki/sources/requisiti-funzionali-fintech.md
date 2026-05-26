---
type: source
sources: ["raw/requisiti-funzionali-fintech.md"]
status: draft
created: 2026-05-26
updated: 2026-05-26
tags: [fintech, hardening, logging, accessibility, security, auth-guard, material-design-3]
---
# Requisiti Funzionali — Iterazione Fintech

> Documento funzionale di hardening e refinement: 5 requisiti trasversali su osservabilita, UX errori, accessibilita, protezione rotte e sicurezza/compliance fintech.

## Contesto

Il documento descrive un'iterazione di consolidamento dell'applicazione fintech esistente. [^src: raw/requisiti-funzionali-fintech.md §Contesto] Non introduce nuovi domini di business: si tratta di miglioramenti trasversali su osservabilita backend, esperienza utente in caso di errore, qualita e accessibilita della UI, e sicurezza delle rotte frontend.

## Requisiti coperti

| ID | Titolo | Pagina wiki |
|---|---|---|
| REQ-01 | Logging backend strutturato e human-readable | [[structured-logging-backend]] |
| REQ-02 | Notifiche errori frontend accessibili | [[frontend-error-notifications]] |
| REQ-03 | Refinement UI: M3 + accessibilita | [[material-design-3-accessibility]] |
| REQ-04 | AuthGuard sulle sezioni protette | [[auth-guard-frontend]] |
| REQ-05 | Sicurezza, privacy e compliance fintech | [[fintech-security-compliance]] |

## Concetti trasversali

- [[correlation-id-tracing]] — Correlation ID propagato tra backend e frontend (REQ-01 + REQ-02).
- [[pii-redaction-checklist]] — Runbook operativo per la redazione PII nei log (REQ-01 + REQ-05 §5.1).

## Sintesi

[[fintech-hardening-requirements-map]] — Mappa cross-domain delle dipendenze tra i 5 REQ e integrazione con l'architettura esistente.

## Requisiti non funzionali trasversali

Il documento prescrive vincoli trasversali applicabili a tutti i REQ. [^src: raw/requisiti-funzionali-fintech.md §Requisiti non funzionali trasversali]

- **Internazionalizzazione:** tutti i messaggi utente passano dal layer i18n; log tecnici restano in inglese.
- **Testing:** coverage minima 80% sui moduli toccati; test E2E per flussi auth-guarded, notifiche errore e path PII-sensitive.
- **Documentazione:** aggiornamento README e ADR per le scelte rilevanti.
- **Stack-awareness:** specificare sempre lo stack reale nelle deleghe LLM per evitare risposte generiche.

## Out of scope

Esplicitamente esclusi: refactor del dominio di business, migrazione del framework frontend, introduzione di nuovi servizi backend, redesign visuale completo. [^src: raw/requisiti-funzionali-fintech.md §Out of scope]

## Concetti correlati

[[webapp-architecture-vi]]
[[analysis-api-pipeline]]
[[openapi-contract-check]]

## Pagine collegate

[[fintech-hardening-requirements-map]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
- EP-014 — Logging Strutturato e Observability Backend (US-058..US-063)
- EP-015 — Notifiche Errori Frontend Accessibili (US-064..US-068)
- EP-016 — Refinement UI Accessibilità e Design Token (US-069..US-072)
- EP-017 — Protezione Rotte e Ciclo di Vita Sessione (US-073..US-078)
- EP-018 — Hardening Sicurezza e Compliance Fintech (US-079..US-082)
