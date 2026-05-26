---
type: synthesis
sources: ["raw/requisiti-funzionali-fintech.md"]
status: draft
created: 2026-05-26
updated: 2026-05-26
tags: [fintech, hardening, cross-domain, requirements-map, architecture]
---
# Mappa dei Requisiti di Hardening Fintech

> Sintesi cross-domain: come i 5 requisiti dell'iterazione fintech interagiscono tra loro e si integrano con l'architettura esistente della WebApp Value Investing.

## Contesto

L'iterazione di hardening fintech introduce 5 requisiti trasversali che non aggiungono nuovi domini di business ma consolidano osservabilita, UX, accessibilita, protezione rotte e sicurezza/compliance. [^src: raw/requisiti-funzionali-fintech.md §Contesto] Questa synthesis mappa le dipendenze tra i requisiti e il loro impatto sull'architettura esistente ([[webapp-architecture-vi]]).

## Mappa delle dipendenze tra REQ

```
REQ-05 (Security/Compliance)
  |-- estende --> REQ-01 (Logging) via §5.1 PII redaction
  |-- estende --> REQ-04 (AuthGuard) via §5.2 Token storage
  |
REQ-01 (Logging) <-- Correlation ID --> REQ-02 (Notifications)
  |                                        |
  |-- PII redaction ---------------------> |-- WCAG 2.2 AA -----> REQ-03 (M3/A11y)
```

### Dipendenze dirette

| Da | A | Natura |
|---|---|---|
| REQ-05 | REQ-01 | §5.1 estende la policy di redazione PII nei log |
| REQ-05 | REQ-04 | §5.2 prescrive il default di storage dei token |
| REQ-01 | REQ-02 | Correlation ID propagato nei log e esposto nelle notifiche |
| REQ-02 | REQ-03 | Condividono la baseline WCAG 2.2 AA |
| REQ-05 | REQ-01 | §5.6 prescrive gli eventi di sicurezza da loggare |

### Regola di prevalenza

In caso di conflitto tra i requisiti, le indicazioni di REQ-05 prevalgono sugli altri. [^src: raw/requisiti-funzionali-fintech.md §REQ-05]

## Impatto sull'architettura esistente

### Backend (Spring Boot 3.5)

| Componente attuale | Impatto |
|---|---|
| `GlobalExceptionHandler` | Estendere con Correlation ID nelle risposte di errore RFC 9457 |
| Logger (Logback) | Configurazione JSON prod / pretty-print dev; MDC per Correlation ID |
| `SecurityFilterChain` (Spring Security) | Integrazione rate limiting login, session fixation protection, MFA TOTP |
| `FmpEventLogger`, `embeddings_api_event_log` | Allineare al pattern di redazione PII centralizzato |
| Nuovi componenti | `CorrelationIdFilter`, `PiiRedactionEncoder`, `SecurityEventLogger` |

### Frontend (React 19 + Next.js 16.x)

| Componente attuale | Impatto |
|---|---|
| Componenti shadcn/ui | Decisione architetturale necessaria: adottare M3 token system o mantenere Radix/shadcn con token M3-aligned |
| `lib/api/*.ts` (axios wrapper) | Integrazione Correlation ID nell'interceptor request/response |
| Routing Next.js | Implementazione AuthGuard centralizzato con metadata dichiarativi |
| Stato auth (JWT localStorage) | Migrazione a access token in memoria + refresh token httpOnly cookie |
| Nuovi componenti | `NotificationService`, `AuthGuard`, `ThemeProvider` M3 |

### Database (PostgreSQL 17)

Nessun impatto diretto sullo schema dati. Potenziale aggiunta tabella `security_events` per audit trail se richiesto dalla retention policy GDPR (complementare a `fmp_api_event_log` e `llm_call_log` esistenti).

## Requisiti non funzionali trasversali

I 5 REQ condividono vincoli trasversali: [^src: raw/requisiti-funzionali-fintech.md §Requisiti non funzionali trasversali]

- **i18n:** tutti i messaggi utente passano dal layer i18n; il progetto attualmente ha stringhe italiane hardcoded nei rationale del Rule Engine.
- **Testing:** coverage minima 80% sui moduli toccati.
- **Documentazione:** ADR richiesti per storage token, libreria logging, scope PCI-DSS, threat model.

## Gap identificati

- **fintech-design-system-react:** M3 richiesto da REQ-03 ma il frontend usa shadcn/ui (Radix-based). Decisione architetturale necessaria.
- **fintech-pci-dss-scope:** REQ-05 §5.4 richiede dichiarazione esplicita dello scope PCI-DSS. Per un'app di screening value investing, verosimilmente "non applicabile" ma serve ADR formale.

## Concetti correlati

[[structured-logging-backend]]
[[frontend-error-notifications]]
[[material-design-3-accessibility]]
[[auth-guard-frontend]]
[[fintech-security-compliance]]
[[correlation-id-tracing]]

## Pagine collegate

[[webapp-architecture-vi]]
[[analysis-api-pipeline]]
[[openapi-contract-check]]
[[pii-redaction-checklist]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
- EP-014 — Logging Strutturato e Observability Backend (REQ-01, REQ-05 §5.1/5.6)
- EP-015 — Notifiche Errori Frontend Accessibili (REQ-02)
- EP-016 — Refinement UI Accessibilità e Design Token (REQ-03)
- EP-017 — Protezione Rotte e Ciclo di Vita Sessione (REQ-04, REQ-05 §5.2)
- EP-018 — Hardening Sicurezza e Compliance Fintech (REQ-05 §5.3/5.4/5.5)
