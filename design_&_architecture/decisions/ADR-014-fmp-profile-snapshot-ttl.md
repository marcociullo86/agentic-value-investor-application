---
id: ADR-014
title: TTL snapshot profilo FMP (prezzo e metadati)
status: accepted
created: 2026-05-22
deciders: [lead-architect, marco.ciullo]
---
# ADR-014 — TTL `fmp_profile_snapshot` (profilo / prezzo corrente)

## Contesto

[ADR-004](ADR-004-fmp-integration.md) formalizza TTL **24 ore** per `fmp_financial_snapshot` (bilanci, key metrics) [^src: management/kanban/EP-007-hardening-produzione/US-024-ttl-snapshot-profilo-formalizzato/US-024.md §Business Rules].

La tabella `fmp_profile_snapshot` (prezzo corrente, market cap, settore) è usata da US-001 e US-013; in L5 il `FmpCacheService` applica **1h** come default conservativo senza validazione Arch (gap `tpm-profile-snapshot-ttl`) [^src: wiki/gaps.md §tpm-profile-snapshot-ttl].

## Decisione

| Parametro | Valore | Note |
|---|---|---|
| Entità cache | `fmp_profile_snapshot` (endpoint logico `profile`) | Distinta da `fmp_financial_snapshot` |
| TTL produzione | **1 ora** (`Duration.ofHours(1)`) | Allineato al default L5 già in uso |
| TTL bilancio (invariato) | **24 ore** | [ADR-004](ADR-004-fmp-integration.md) §2 |
| Chiave cache | `(ticker, endpoint='profile')` | Come implementazione esistente |
| Header HTTP | `X-Data-Snapshot-At`, `X-Data-Stale` su endpoint che espongono prezzo corrente | Coerente con US-005/006 |
| Configurazione | Property `fmp.cache.profile-ttl-hours` (default `1`) | Override env senza rebuild |

### Regola di freschezza

- `now - fetched_at < 1h` → snapshot fresh, nessuna chiamata FMP.
- Scaduto → `getOrFetch` verso FMP `/api/v3/profile/{ticker}`.
- Fallback stale (circuit open / 429): stessa semantica [ADR-004](ADR-004-fmp-integration.md) §4 con `staleReason` esplicito.

### Test

Integration test: inserire snapshot con `fetched_at` > 1h fa → `getOrFetch` deve invocare fetch (mock WireMock) [^src: management/kanban/EP-007-hardening-produzione/US-024-ttl-snapshot-profilo-formalizzato/US-024.md §Acceptance Criteria].

## Motivazioni

1. **Prezzo corrente** richiede freschezza superiore ai bilanci decennali (24h).
2. **1h** è prudente rispetto a gap FMP rate limit non documentato; riduce chiamate vs TTL minuti.
3. Evita hotfix silenziosi: ogni modifica TTL passa da aggiornamento ADR-004 appendice o revisione ADR-014.

## Alternative considerate

| TTL | Pro | Contro |
|---|---|---|
| 15 min | Prezzo più fresco | +4× chiamate FMP profile/giorno per ticker attivo |
| 24h (uguale bilancio) | Minime chiamate FMP | MoS e traffic light su prezzo obsoleto |
| Nessuna cache profilo | Sempre fresh | Ignora US-005 pattern; rischio 429 |

## Conseguenze

- US-024: implementazione = allineare property + test; valore runtime già ~1h.
- Gap `tpm-profile-snapshot-ttl`: chiudibile post-verifica test.
- Appendice [ADR-004](ADR-004-fmp-integration.md) §2b (sotto).

## Pagine collegate

- [ADR-004](ADR-004-fmp-integration.md)
- [data/er-diagram.md](../data/er-diagram.md)
- [overview.md](../overview.md)
