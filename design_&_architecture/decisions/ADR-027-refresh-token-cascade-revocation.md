---
id: ADR-027
title: Refresh token cascade revocation on reuse detection
status: accepted
created: 2026-06-03
deciders: [lead-architect]
---
# ADR-027 — Refresh token cascade revocation on reuse detection

## Contesto

US-075 AC §6 (EP-017, `done`) prescriveva esplicitamente: "Il riuso di un refresh token già ruotato causa revoca di tutti i refresh token dell'utente (simulato in test)". A valle di TSK-212 il qa-dev ha aperto il gap formale `auth-cascade-revocation-missing` (2026-05-27): l'`AuthService.refresh()` corrente identifica il riuso (un refresh token con `revoked_at != null` presentato di nuovo) e lo rifiuta con `InvalidRefreshTokenException(REASON_REVOKED)`, ma **non propaga la revoca** all'intero parco di refresh token attivi dell'utente. Un attaccante che ha esfiltrato un refresh token prima della rotation può continuare a tenere viva una catena parallela mentre la vittima continua a operare normalmente. [^src: management/kanban/EP-017-protezione-rotte-sessione/US-075-migrazione-storage-credenziali/US-075.md §Acceptance Criteria]
[^src: wiki/gaps.md §2026-05-27 auth-cascade-revocation-missing]
[^src: management/kanban/EP-017-protezione-rotte-sessione/US-092-cascade-revocation-refresh-token/US-092.md §Business Rules]

La US-092 chiude il debito (storia riaperta `in_progress`) senza riaprire US-075. La definizione di refresh token family esiste già implicitamente nello schema as-is: la colonna `refresh_tokens.first_issued_at` (ADR-010 §3, migration V009) preserva l'istante del login originale attraverso tutte le rotation della catena. Tutti i refresh token con lo stesso `(user_id, first_issued_at)` formano una **family**. [^src: design_&_architecture/decisions/ADR-010-auth-consolidation.md §3]
[^src: src/backend/src/main/resources/db/migration/V009__add_first_issued_at_to_refresh_tokens.sql]

Lo standard di riferimento per il pattern di reuse-detection con cascade revocation è **OAuth 2.0 Security Best Current Practice §4.13.2** (Refresh Token Rotation Replay Detection) e **RFC 6819 §5.2.2.3** (Threat: refresh token replay attack). Nessuno è citato verbatim nei raw (PATTERN §11 non vincola), ma sono best practice industry-standard per JWT + refresh rotation. [^web: OAuth 2.0 Security Best Current Practice §4.13.2]

## Decisione

Implementare **cascade revocation alla detection del riuso** in `AuthService.refresh()`. Tutti i refresh token attivi dell'utente vengono atomicamente marcati `revoked_at = now()` quando un token già ruotato viene presentato di nuovo. Nessuna nuova migration: lo schema as-is supporta il pattern.

### 1. Algoritmo refresh aggiornato

Sequenza di `AuthService.refresh(refreshTokenValue: String)`:

1. `findByTokenValue(refreshTokenValue)` → se `null` → throw `InvalidRefreshTokenException(REASON_NOT_FOUND)` (invariato).
2. **Detection riuso** (cambia rispetto allo stato as-is):
   - **Se `token.revokedAt != null`** → riuso detectato.
   - Invoca `refreshTokenRepository.revokeAllActiveByUserId(token.userId, now)` (bulk UPDATE atomico — vedi §2).
   - Emetti `SecurityEventLogger.refreshTokenReuseDetected(userId = token.userId, family = token.firstIssuedAt, revokedCount = <int returned>)`.
   - Throw `InvalidRefreshTokenException(REASON_REUSE_DETECTED)` (nuovo reason code interno; il client riceve `401 invalid-refresh` opaco — anti-enum, vedi §4).
3. Validazioni successive **invariate** (sliding expiry, absolute cap, rotation) — il path "happy" non cambia.

### 2. Repository — bulk revoke metodo nuovo

Aggiungere a `RefreshTokenRepository`:

```kotlin
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("UPDATE RefreshToken t SET t.revokedAt = :now " +
       "WHERE t.userId = :userId AND t.revokedAt IS NULL")
fun revokeAllActiveByUserId(userId: UUID, now: Instant): Int
```

**Indice esistente sfruttato**: `refresh_tokens_user_active_idx (user_id, revoked_at)` (V001) supporta direttamente il predicato `WHERE user_id = ? AND revoked_at IS NULL`. Nessuna nuova migration.

**Atomicità**: l'operazione gira nella stessa transazione `@Transactional` di `AuthService.refresh()` (propagation default `REQUIRED`). PostgreSQL garantisce isolamento `READ COMMITTED`: se due refresh concorrenti dello stesso utente arrivano in race, il bulk update vede un'unica vista consistente.

**Idempotenza**: il bulk update sui token già revocati ritorna `0` (`WHERE revoked_at IS NULL` non matcha nulla). Il secondo riuso del medesimo token rifiutato continua a:
- trovare `token.revokedAt != null` → invocare di nuovo `revokeAllActiveByUserId` → 0 righe modificate → log con `revokedCount = 0` → throw `REASON_REUSE_DETECTED`.

Nessun side-effect aggiuntivo, nessun errore 500.

### 3. Nuovo reason code

`InvalidRefreshTokenException`:

```kotlin
// stato as-is:  not_found | revoked | sliding_expired | absolute_cap | user_unknown
// aggiunto:
private const val REASON_REUSE_DETECTED: String = "reuse_detected"
```

Coerente con il pattern anti-enum-attack di TSK-041: il `reason` è strumentale solo al log server-side, mai esposto al client. Il `GlobalExceptionHandler` mappa l'eccezione su `401 ProblemDetail type=https://api/errors/invalid-refresh` (testo identico per tutti i reason — REQ-05 anti-enum).

### 4. Security event nuovo — `refresh_token_reuse_detected`

Aggiungere a `SecurityEventLogger` (file `service/SecurityEventLogger.kt`):

```kotlin
fun refreshTokenReuseDetected(userId: UUID, family: Instant, revokedCount: Int) {
    log.warn(
        SECURITY_EVENT,
        "Refresh token reuse detected — cascade revocation triggered",
        kv("event", "REFRESH_TOKEN_REUSE_DETECTED"),
        kv("userId", userId),
        kv("family", family.toString()),
        kv("revokedCount", revokedCount),
    )
}
```

**Severity**: `warn` (potenziale token theft, attenzione operatore).

**Marker**: `SECURITY_EVENT` (router retention 365 giorni, ADR-021 §7).

**MDC**: `correlationId` (CorrelationIdFilter) attaccato automaticamente.

**PII redaction**: `userId` UUID non è PII (no nome / email); `family` è un timestamp; `revokedCount` integer. Nessun encoder PII richiesto.

**Coerenza con EP-014 US-062**: il pattern di security event logging è già attivo. Il nuovo evento si inserisce nella stessa pipeline. [^src: design_&_architecture/decisions/ADR-021-structured-logging-pii-redaction.md §6]

### 5. Cosa NON cambia

- **Schema DB**: invariato. `refresh_tokens` mantiene la stessa struttura (V001 + V009). Nessuna migration richiesta.
- **TTL/sliding/absolute cap**: invariati (ADR-010 §3).
- **Cookie httpOnly transport**: invariato (ADR-024).
- **CSRF protection**: invariata (ADR-025).
- **ADR-006 / ADR-010 / ADR-024 / ADR-025**: nessuno superseded. ADR-027 **estende** la business logic di refresh; lo stack auth resta invariato.
- **Risposta al client**: continua a essere `401 invalid-refresh` opaco. Il FE (`useTokenRefresh` ADR-024 §4) continua a triggerare il logout flow → `/login` con messaggio "La sessione è scaduta". Comportamento FE invariato.

### 6. Modelli mentali: token family

Una **family** = insieme di refresh token che hanno lo stesso `first_issued_at` (= stesso login originale). La rotation lavora all'interno della family (vedi `issueTokenPair(user, firstIssuedAt)` in `AuthService`). Il reuse-detection scatta su un token revoked che viene ripresentato — significa che esiste un attaccante con copia di un token già ruotato → tutta la family è compromessa → tutti i token attivi (di tutte le family attive dell'utente) sono revocati per principio di precauzione.

**Decisione**: revochiamo **tutti i token attivi dell'utente**, non solo i token della family compromessa. Ragione: l'attaccante potrebbe aver ottenuto credenziali tramite altre vie (phishing, credential stuffing), e una "compromissione confermata" su una family giustifica la revoca di tutte le sessioni (industry standard "kill switch" su segnale forte). Lo stesso utente ri-autenticandosi otterrà una nuova family pulita.

**Alternativa scartata**: revocare solo i token della stessa family (`WHERE user_id = ? AND first_issued_at = ? AND revoked_at IS NULL`). Più conservativo ma lascia eventuali sessioni paralleme legittime — preferiamo l'over-revocation perché il signal "reuse detected" è già forte.

## Componenti

| Componente | Layer | Path | Modifica |
|---|---|---|---|
| `AuthService.refresh()` | BE | `src/backend/.../service/AuthService.kt` | Step 2 cascade |
| `RefreshTokenRepository` | BE | `src/backend/.../persistence/repository/RefreshTokenRepository.kt` | nuovo metodo `revokeAllActiveByUserId` |
| `InvalidRefreshTokenException` | BE | `src/backend/.../service/InvalidRefreshTokenException.kt` | nuovo reason `REASON_REUSE_DETECTED` |
| `SecurityEventLogger` | BE | `src/backend/.../service/SecurityEventLogger.kt` | nuovo metodo `refreshTokenReuseDetected` |
| `GlobalExceptionHandler` | BE | mapping eccezione → 401 invalid-refresh | invariato (riusa pattern esistente) |

## API Changes

Nessuno. Il body di risposta `POST /api/auth/refresh` in caso di errore resta `401 ProblemDetail type=https://api/errors/invalid-refresh` opaco. L'OpenAPI non cambia.

## Schema DB

Nessuna migration. Indice esistente `refresh_tokens_user_active_idx (user_id, revoked_at)` continua a supportare il bulk update.

## Motivazioni

1. **Chiude il debito di compliance** US-075 AC §6 e il gap formale `auth-cascade-revocation-missing` senza riaprire US-075 done.
2. **Pattern industry-standard** OAuth 2.0 Security BCP §4.13.2 / RFC 6819 §5.2.2.3 per JWT + refresh rotation.
3. **Zero schema migration**: sfrutta colonna `first_issued_at` già esistente (V009) come family identifier implicito e indice `refresh_tokens_user_active_idx` per il bulk update. Costo migration = 0.
4. **Atomicità transazionale**: bulk UPDATE in singola query JPA dentro la transaction di `AuthService.refresh()`; nessun race condition, nessun ordine parziale visibile a consumer concorrenti.
5. **Audit trail completo**: il nuovo security event (`REFRESH_TOKEN_REUSE_DETECTED`) entra nella pipeline ADR-021 con retention 365 giorni, conteggio token revocati e family identifier. SOC analyst può correlare.
6. **Idempotenza by-design**: il pattern "bulk update WHERE revoked_at IS NULL" è naturalmente idempotente. Tentativi successivi → 0 righe → no errore.
7. **Anti-enum**: il client continua a vedere il medesimo errore `401 invalid-refresh` per tutti i casi (not_found, revoked, expired, cap, reuse_detected, user_unknown). Nessuna informazione esposta a un attaccante.
8. **UX invariata**: l'utente legittimo vittima percepisce la revoca come "sessione scaduta" su tutti i device al primo refresh fallito. Comportamento già coperto da US-073/US-076 (`useTokenRefresh` redirect a `/login`).

## Alternative considerate

| Alternativa | Esito |
|---|---|
| **Revocare solo la family compromessa** (`WHERE user_id AND first_issued_at`) | Più conservativo, ma il signal "reuse detected" è già forte → preferiamo over-revoke. Scartato. |
| **Aggiungere colonna `family_id UUID` alla tabella** | Più "pulito" semanticamente ma richiede migration + backfill su record esistenti. `first_issued_at` (già presente) è funzionalmente equivalente come family identifier. Scartato per costo migration zero. |
| **Tabella `revoked_token_audit` separata** | Audit trail già coperto da `SecurityEventLogger` (log strutturato JSON + retention 365d). Tabella DB aggiuntiva ridondante. Scartato. |
| **Notifica email all'utente** ("sessione revocata per sicurezza") | Fuori scope US-092; richiederebbe transactional email service (out-of-scope MVP). Possibile follow-up. |
| **Rate limit aggiuntivo su `/refresh` post-reuse** | Già coperto da `bruteForceProtectionService` (ADR-025). Scartato come duplicazione. |
| **Mantenere `deleteAllByUserId` esistente vs nuovo `revokeAllActiveByUserId`** | `deleteAll` hard-elimina record (perdita audit); `revokeAll` setta `revoked_at = now()` (preserva audit + storia). Scelto `revokeAll`. |

## Conseguenze

- **TSK BE**: 1 task principale (modifica `AuthService.refresh()` + `RefreshTokenRepository` + `SecurityEventLogger` + reason code) — stima 1-2 punti. Test unitari + integrazione.
- **Test backend obbligatori (AC US-092)**:
  - Riuso → 401 + cascade su tutti i token attivi del medesimo userId.
  - Token altra device dello stesso utente → rifiutato dopo cascade (autenticazione fallita).
  - Isolamento per `userId`: altro user non impattato (test multi-user).
  - Idempotenza: secondo riuso dello stesso token → no errore 500, no revoche duplicate (`revokedCount = 0`).
  - Security event `REFRESH_TOKEN_REUSE_DETECTED` emesso con `userId`, `family`, `revokedCount`.
- **No regression** su US-075/US-076: il path "refresh token valido non ruotato → nuovo access + nuovo refresh" continua a funzionare invariato. Suite test esistente passa.
- **Wiki**: il gap `auth-cascade-revocation-missing` può essere chiuso dal wiki-keeper a valle del rilascio US-092.
- **ADR-010, ADR-024, ADR-025**: nessuna modifica/superseding.

## Tracciabilità US → AC → policy

| US-092 AC | Policy/Comportamento |
|---|---|
| Refresh token già ruotato presentato → 401 + cascade tutti i token attivi | Step 2 algoritmo §1 + bulk update §2 |
| Refresh token "legittimo" altra device → 401 dopo cascade | Conseguenza naturale del bulk update (tutti i `revoked_at IS NULL` → `revoked_at = now()`) |
| Isolamento per `userId` (no impatto altri utenti) | Predicato `WHERE user_id = :userId` nel bulk update |
| Idempotenza secondo riuso → no errori, no duplicazioni | Bulk update con `WHERE revoked_at IS NULL` naturalmente idempotente |
| Security event `refresh_token_reuse_detected` | Metodo `SecurityEventLogger.refreshTokenReuseDetected(userId, family, revokedCount)` §4 |
| Gap `auth-cascade-revocation-missing` richiamato | Handoff CQRL US-092 cita ADR-027 + gap (chiusura wiki-keeper) |
| No regression US-075/US-076 | Step 2 modifica solo branch `revokedAt != null`; happy-path invariato |

## Pagine collegate

- [ADR-006](ADR-006-authentication.md) — auth foundation (invariato)
- [ADR-010](ADR-010-auth-consolidation.md) — auth consolidation (invariato; questo ADR si estende sopra il pattern token family)
- [ADR-021](ADR-021-structured-logging-pii-redaction.md) — security event logging (nuovo evento usa stessa pipeline)
- [ADR-024](ADR-024-session-lifecycle-credential-storage.md) — session lifecycle / cookie httpOnly (invariato)
- [ADR-025](ADR-025-security-hardening-pci-dss.md) — security hardening (invariato)
- US-092 (`management/kanban/EP-017-protezione-rotte-sessione/US-092-cascade-revocation-refresh-token/US-092.md`)
- [[fintech-security-compliance]] §5.5, §5.6
- [[auth-guard-frontend]] §Storage dei token
