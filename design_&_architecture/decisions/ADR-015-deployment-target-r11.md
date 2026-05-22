---
id: ADR-015
title: Target deploy produzione R1.1 — VM + Docker Compose + TLS
status: accepted
created: 2026-05-22
deciders: [lead-architect, marco.ciullo]
supersedes: []
related: ADR-009
---
# ADR-015 — Target deploy produzione R1.1

## Contesto

[ADR-009](ADR-009-deployment-target.md) fissa il **baseline** monorepo Docker (immagine unica BE+static FE) ma lascia aperto provider, sizing e operazioni (gap `arch-deployment-target`) [^src: management/kanban/EP-008-deploy-operativita-produzione/US-026-baseline-target-deploy/US-026.md §Descrizione] [^src: wiki/gaps.md §arch-deployment-target].

R1.1 richiede go-live verificabile: staging, secret management, risorse minime, coerenza con static export ([ADR-013](ADR-013-fe-analysis-routing-static-export.md)).

**Nota R2:** SSO enterprise (`arch-auth-provider-choice`) resta fuori scope — nessun ADR OIDC in questa run.

## Decisione

### Modello runtime R1.1 (accettato)

| Layer | Target |
|---|---|
| **Ambiente** | 1× VM Linux (Ubuntu 22.04+ LTS), **cloud-agnostica** (es. AWS EC2 `t3.small`, DigitalOcean Droplet 2 vCPU / 4 GiB, Hetzner CX22 — scelta operativa non vincolante) |
| **Orchestrazione** | **Docker Compose** in produzione (estensione `docker-compose.prod.yml` accanto al dev esistente) |
| **Servizi Compose** | `app` (monolite Spring Boot 3.5 + static FE), `postgres:17`, `nginx` (reverse proxy TLS termination) |
| **Database** | PostgreSQL 17 in container dedicato sullo stesso host (R1.1); migrazione a **RDS/managed Postgres** opzionale R2 senza cambiare app |
| **Frontend** | Static export in `/app/public` dentro immagine `app` — **nessun** container Node in prod |
| **TLS** | **nginx** + Let's Encrypt (certbot) o certificato gestito; HTTP/2, TLS 1.3 verso client (`raw/tech_stack.md` §Standards) |
| **Segreti** | File `.env` sul host (**non** in git) o secret manager del provider; variabili obbligatorie: `FMP_API_KEY`, `JWT_SIGNING_SECRET`, `DB_*` |
| **CI → staging** | Pipeline build immagine + `docker compose pull/up` su host staging (branch `main` tag `staging-latest`) |

### Sizing minimo (ordine di grandezza)

| Componente | CPU | RAM | Disco |
|---|---|---|---|
| `app` | 1 vCPU | 1.5 GiB | — |
| `postgres` | 1 vCPU | 1 GiB | 20 GiB SSD (dati + WAL) |
| `nginx` | 0.25 vCPU | 256 MiB | — |
| **Totale host** | **2 vCPU** | **4 GiB** | **40 GiB** root + volume DB |

Carico atteso: uso interno / demo investitori — non multi-tenant ad alto QPS.

### Diagramma logico

```
                    Internet
                        |
                   [nginx :443]
                   TLS terminate
                        |
              [app :8080 monolite]
                   /        \
            static FE      REST /api
                        |
                 [postgres:5432]
```

### Relazione con ADR-009

ADR-009 resta **accepted** per layout monorepo e Dockerfile multi-stage. ADR-015 **estende** con topologia prod (nginx + compose prod + sizing) senza supersede ADR-009.

Appendice non distruttiva: [ADR-009](ADR-009-deployment-target.md) §Appendice R1.1.

### Runbook

Procedura operativa dettagliata: [operations/deploy-runbook-r11.md](../operations/deploy-runbook-r11.md) (US-026, US-027, US-028).

## Alternative considerate

| Alternativa | Motivo scarto R1.1 |
|---|---|
| Kubernetes / Helm | Over-engineering per carico MVP |
| Split Vercel + API cloud | Due runtime; viola monolite ADR-009 |
| Serverless (Lambda) | Cold start + Postgres VPC complexity |
| Solo `docker run` senza compose | Ripetibilità staging/prod peggiore |

## Conseguenze

- US-026: baseline documentata; infra-dev può produrre `docker-compose.prod.yml` + nginx template.
- US-027/US-028: backup e checklist nel runbook.
- Gap `arch-deployment-target`: **parzialmente** chiuso (modello); provider cloud resta scelta operativa del team — non blocca task TPM.

## Pagine collegate

- [ADR-009](ADR-009-deployment-target.md)
- [ADR-013](ADR-013-fe-analysis-routing-static-export.md)
- [operations/deploy-runbook-r11.md](../operations/deploy-runbook-r11.md)
- [overview.md](../overview.md)
