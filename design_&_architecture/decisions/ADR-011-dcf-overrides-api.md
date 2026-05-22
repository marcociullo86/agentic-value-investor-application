---
id: ADR-011
title: DCF override API — contract con visibility, source flag e feasibility validation
status: accepted
created: 2026-05-22
deciders: [lead-architect, marco.ciullo]
---
# ADR-011 — DCF override API: US-020

## Contesto

Su `master` (Sprint 2, TSK-017) esistono già:

- Endpoint `POST /api/dcf-overrides` (upsert) e `DELETE /api/dcf-overrides/{ticker}` ([`DcfOverrideController.kt`](../../src/backend/src/main/kotlin/com/valueinvesting/webapp/api/DcfOverrideController.kt)).
- Tabella `dcf_method_override` con vincolo UNIQUE `(user_id, ticker)` e CHECK `forced_method IN ('GREENWALD','FCF_FALLBACK')` (Flyway V007).
- Auth reale via `@AuthenticationPrincipal UserPrincipal` (dopo TSK-033/034/035; lo stub `X-User-Id` è stato rimosso).

Il product-manager ha promosso (run 2026-05-22) **US-020** "Override DCF method per utente autenticato" — `management/kanban/EP-004-valore-intrinseco-margin-of-safety/US-020-override-dcf-method/US-020.md`. La US esplicita tre AC non interamente coperti dal contratto as-is:

- **AC#1**: "l'utente autenticato può visualizzare … quale metodo DCF è stato applicato e se proviene da default-policy o da override personale" → richiede sia (a) **lettura dell'override per ticker**, sia (b) un campo "source" nel payload `/api/analysis/{ticker}`.
- **AC#4**: "un override impostato da un utente non altera il risultato visto da altri utenti o da visualizzazioni anonime" → `/api/analysis/{ticker}` deve essere **auth-aware**: se autenticato applica l'override, se anonimo applica default-policy.
- **AC#5**: "la richiesta di un override su metodo per cui i dati storici sono insufficienti viene rifiutata con messaggio esplicito" → il `POST /api/dcf-overrides` non può limitarsi a `INSERT` cieco; deve eseguire un feasibility-check sui dati FMP del ticker.
- **AC#6**: "l'output dell'analisi rende esplicito il flag 'override applicato' e il metodo effettivamente in uso" → stesso campo del AC#1.

[^src: management/kanban/EP-004-valore-intrinseco-margin-of-safety/US-020-override-dcf-method/US-020.md §Business Rules]
[^src: wiki/runbooks/value-investing-rule-engine-runbook.md §3b. DCF (Discounted Cash Flow)]
[^src: wiki/runbooks/value-investing-rule-engine-runbook.md §Step 4 — Composizione Risultato]

## Decisione

Estendere il contratto DCF-override con: **(1) GET endpoint**, **(2) flag `dcfMethodSource` nel payload analysis**, **(3) feasibility validation con 422 Problem Details**, **(4) `/api/analysis/{ticker}` auth-opzionale**.

### 1. Endpoints (estensione di [ADR-007](ADR-007-api-contract.md))

| Method | Path | Auth | Note |
|---|---|---|---|
| GET | `/api/dcf-overrides/{ticker}` | **richiesta** | Ritorna l'override dell'utente corrente per il ticker. 200 con `DcfOverride` se presente, **404 Problem Details** `type=https://api/errors/dcf-override-not-found` se l'utente non ha override per quel ticker. |
| POST | `/api/dcf-overrides` | richiesta | Upsert. **Nuovo**: validazione feasibility sincrona contro i dati FMP del ticker. |
| DELETE | `/api/dcf-overrides/{ticker}` | richiesta | invariato. 204 sia se rimosso sia se non esisteva (idempotente). |

Le risposte `401 Unauthorized` su tutti e tre sono già garantite da `SecurityConfig.requestMatchers("/api/dcf-overrides/**").authenticated()`. [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/security/SecurityConfig.kt §securityFilterChain]

### 2. Feasibility validation (AC#5)

`POST /api/dcf-overrides` invoca, **prima del save**, `DcfFeasibilityCheck.canApply(ticker, method)` che usa lo stesso dataset FMP cached del Rule Engine. Esiti:

| Esito | HTTP | Body |
|---|---|---|
| Dati sufficienti | `201 Created` | `DcfOverride` |
| Greenwald non applicabile (es. < 5 anni di PPE/Revenue) | `422 Unprocessable Entity` | Problem Details `type=https://api/errors/dcf-method-unfeasible`, `detail="Greenwald requires ≥ 5 years of PPE_Ratio history; ticker has N years"`, `extensions: { method: "GREENWALD", reason: "PPE_RATIO_HISTORY_INSUFFICIENT", availableYears: N, requiredYears: 5 }` |
| FCF_FALLBACK non applicabile (FCF storici tutti nulli/negativi) | `422 Unprocessable Entity` | come sopra, `reason: "FCF_HISTORY_INSUFFICIENT"` |
| Ticker inesistente | `404 Not Found` | Problem Details `type=https://api/errors/ticker-not-found` |
| FMP non disponibile e cache vuota | `503 Service Unavailable` | come `/api/analysis/{ticker}` per coerenza |

La feasibility-check **non altera** la default-policy del Rule Engine (Greenwald primario, FCF fallback); è solo un gate **esplicito** per l'override. Lo strato di calcolo già implementa il fallback automatico per la default-policy ([`GreenwaldMaintenanceCapexEstimator` + `FcfFallbackEstimator`]) — la feasibility-check riusa gli stessi `Estimator` con metodo `.isFeasible(dataset): FeasibilityResult` (esposto come API interna del modulo `ruleengine`).

[^src: wiki/runbooks/value-investing-rule-engine-runbook.md §3b. DCF (Discounted Cash Flow)]
[^src: wiki/concepts/value-investing-rule-engine.md §Aggiornamenti (v2026-05-20)]

### 3. Flag `dcfMethodSource` nel `RuleEngineResult` (AC#1, AC#6)

Aggiungere al payload di `/api/analysis/{ticker}` un campo enum:

```yaml
DcfMethodSource:
  type: string
  enum: [DEFAULT_POLICY, USER_OVERRIDE]
  description: |
    Indica se il metodo DCF effettivamente applicato è stato selezionato dalla
    default-policy del Rule Engine o se proviene da un override personale
    dell'utente autenticato (US-020).
```

Schema aggiornato `RuleEngineResult`:

```yaml
RuleEngineResult:
  type: object
  required: [ticker, evaluatedAt, signals, mosSignal, dcfMethodSource]
  properties:
    ticker:              { type: string }
    evaluatedAt:         { type: string, format: date-time }
    signals:             { type: array, items: { $ref: '#/components/schemas/RuleSignal' } }
    grahamNumber:        { type: number, nullable: true }
    dcfIntrinsicValue:   { type: number, nullable: true }
    dcfMethod:           { $ref: '#/components/schemas/DcfMethod', nullable: true }
    dcfMethodSource:     { $ref: '#/components/schemas/DcfMethodSource' }   # NUOVO, default DEFAULT_POLICY per anonymous
    mosSignal:           { $ref: '#/components/schemas/Signal' }
    currentPriceAtEval:  { type: number, nullable: true }
    dataSnapshotAt:      { type: string, format: date-time }
    isStale:             { type: boolean }
```

Per anonymous request (no `Authorization` header valido) → `dcfMethodSource = DEFAULT_POLICY`, sempre.
Per autenticato con override su `(user, ticker)` valido → `dcfMethodSource = USER_OVERRIDE`, `dcfMethod = override.forced_method`.
Per autenticato senza override → `dcfMethodSource = DEFAULT_POLICY`, `dcfMethod = ruleengine.selectMethod(dataset)`.

### 4. `/api/analysis/{ticker}` auth-opzionale (AC#4)

`SecurityConfig` mantiene `/api/analysis/**` come **permitAll**. Il `JwtAuthenticationFilter` già popola opportunisticamente il `SecurityContext` quando il bearer è valido (vedi `JwtAuthenticationFilter.kt §doFilterInternal` "leaves the context empty and lets the SecurityFilterChain decide"). [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/security/JwtAuthenticationFilter.kt §doFilterInternal]

Modifica chiave in `AnalyzeTickerService`:

```kotlin
fun analyze(ticker: String): RuleEngineResultResponse {
    val dataset = financialDataService.fetchFor(ticker)
    val auth = SecurityContextHolder.getContext().authentication
    val userId = (auth?.principal as? UserPrincipal)?.userId

    val override = userId?.let { dcfOverrideRepository.findByUserIdAndTicker(it, ticker) }
    val (method, source) = when (override) {
        null -> ruleEngine.selectMethod(dataset) to DcfMethodSource.DEFAULT_POLICY
        else -> override.forcedMethod to DcfMethodSource.USER_OVERRIDE
    }
    // ... resto della pipeline invariato, usando `method` come input al DcfCalculator
}
```

Conseguenze:
- L'endpoint **resta cacheabile** a livello CDN solo per **anonymous** (no `Authorization` → `Vary: Authorization` deve essere settato in response). Per autenticati non cacheare CDN-side.
- Header `Vary: Authorization` aggiunto a `/api/analysis/{ticker}` (intercettore comune o annotation Spring `@CrossOrigin`/filter).

### 5. Vincoli invarianti (confermati da ADR-007 + ADR-006/010)

- Ticker normalizzato uppercase prima di qualsiasi lookup (`request.ticker.uppercase()`). [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/service/DcfOverrideService.kt §upsert]
- `application/problem+json` per tutti i 4xx/5xx (RFC 9457). [^src: design_&_architecture/decisions/ADR-007-api-contract.md §Error format]
- `forcedMethod` validato via Jakarta `@Pattern("GREENWALD|FCF_FALLBACK")` + CHECK constraint DB (defense-in-depth). [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/api/model/DcfOverrideRequest.kt §forcedMethod]
- Nessuna race possibile sull'UNIQUE `(user_id, ticker)`: l'`upsert` legge-poi-scrive in singola `@Transactional`; il vincolo DB è il fallback ultimo. [^src: src/backend/src/main/resources/db/migration/V007__create_dcf_overrides.sql §dcf_method_override_user_ticker_uidx]

## Motivazioni

1. **Auth-opzionale `/analysis`** mantiene l'asset di business "analisi pubblica per visitatori" senza rotture e abilita override personali con un'unica path → minor superficie API. Coerente con la policy "analisi anonima resta disponibile" di ADR-006 §Endpoint policy.
2. **Feasibility-check sincrono** è UX-superiore al "salva-comunque + errore-in-analisi": l'utente capisce subito che la sua scelta è inapplicabile, senza dover poi mandare `/analysis` e leggere `dcfIntrinsicValue=null` con `signals[].rationale` ambiguo.
3. **HTTP 422 vs 400**: la richiesta è sintatticamente valida (Jakarta validation ok), ma semanticamente non applicabile sul dominio → 422 Unprocessable Entity è il code corretto (RFC 9110 §15.5.21).
4. **GET dedicato vs body in POST**: REST semantics; consente al FE di mostrare "tuo override corrente = X" senza side-effect.
5. **Flag `dcfMethodSource` esplicito** è verificabile via contract-test e ben mappato sull'AC#6 ("rende esplicito il flag"). Alternativa booleana `isOverridden` scartata: enum è estensibile (es. R2 `MARKET_OVERRIDE` per scenari amministrativi).
6. **`Vary: Authorization`** è semantica HTTP corretta per response che variano in funzione del bearer (RFC 9110 §12.5.5).

## Alternative considerate

- **Endpoint separato `/api/analysis/{ticker}/personal` per il flusso autenticato**: duplica logica, rompe la mappa US→endpoint di `endpoints-overview.md`. Scartato.
- **Body POST con campo `dryRun: true` per la feasibility-check**: meno chiaro; mescola intent "verifica" e "salva". GET dedicato + 422 su POST reale è più REST-puro.
- **403 vs 422 su override non applicabile**: 403 implica "autorizzazione mancante"; qui l'utente è autorizzato ma il dato non lo permette. 422 è semanticamente corretto.
- **`isOverridden: boolean` invece di `dcfMethodSource: enum`**: enum è meglio per estensibilità futura (vedi punto 5 sopra).

## Conseguenze

- **Codice toccato**:
  - `DcfOverrideController`: aggiunta `GET /{ticker}` con 200/404. Modificato `POST` per delegare a `DcfOverrideService.upsertWithFeasibilityCheck(...)`.
  - `DcfOverrideService`: nuovo metodo `findByUserAndTicker(userId, ticker)`; metodo `upsert` rinominato `upsertWithFeasibilityCheck` con chiamata a `DcfFeasibilityCheck`.
  - **Nuovo** `DcfFeasibilityCheck` nel package `ruleengine/feasibility/`: riusa `GreenwaldMaintenanceCapexEstimator` + `FcfFallbackEstimator` con metodo `.isFeasible(dataset): FeasibilityResult`.
  - **Nuovo** `DcfMethodUnfeasibleException` mappato a 422 nel `GlobalExceptionHandler`.
  - `AnalyzeTickerService`: aggiunta lookup override + scelta `(method, source)` come sopra.
  - `RuleEngineResultResponse`: nuovo campo `dcfMethodSource: DcfMethodSource`.
  - **Nuovo** enum `DcfMethodSource { DEFAULT_POLICY, USER_OVERRIDE }`.
  - `AnalysisController`: aggiunta header `Vary: Authorization` (filter o `ResponseEntity.headers`).

- **OpenAPI**: aggiornamento `RuleEngineResult` con `dcfMethodSource`, nuovo `GET /api/dcf-overrides/{ticker}`, 422 documentato su `POST /api/dcf-overrides`, header `Vary: Authorization` su `/api/analysis/{ticker}`.

- **Schema DB**: invariato (il check è in-memory sui dati FMP cached, non persistito).

- **Frontend impatto**:
  - Pannello analysis legge `dcfMethodSource` per mostrare badge "Default policy" vs "Tuo override".
  - Form override mostra inline-error con `detail` del Problem Details su 422.
  - GET `/api/dcf-overrides/{ticker}` chiamato sulla stessa pagina per pre-popolare la UI.

- **Contract-test obbligatori**:
  - 422 con `extensions.reason` corretto per Greenwald insufficiente.
  - `dcfMethodSource = USER_OVERRIDE` quando bearer + override esistono.
  - `dcfMethodSource = DEFAULT_POLICY` per anonymous + per autenticato senza override.
  - `Vary: Authorization` presente nelle response di `/analysis`.

- **US sbloccate**: US-020 implementabile interamente. US-012 storica (pre-override-per-utente) resta coperta dallo stesso codice.

## Tracciabilità US → AC → policy

| AC US-020 | Policy |
|---|---|
| #1 visualizza metodo + source | `GET /api/dcf-overrides/{ticker}` + `dcfMethodSource` in `/api/analysis` |
| #2 set override fra Greenwald e FCF | `POST /api/dcf-overrides` con `forcedMethod` enum |
| #3 remove override → default-policy | `DELETE /api/dcf-overrides/{ticker}` (204 idempotente) |
| #4 isolamento per utente e per anonymous | `AnalyzeTickerService` legge `SecurityContext`; `Vary: Authorization` |
| #5 rifiuto insufficiente | `DcfFeasibilityCheck` → 422 Problem Details con `reason` |
| #6 output esplicita metodo + source | campo `dcfMethodSource` enum nel payload |
| #7 set/remove senza auth → rifiuto | `SecurityConfig` 401 su `/api/dcf-overrides/**` |

## Pagine collegate

- [ADR-006](ADR-006-authentication.md) — auth foundation
- [ADR-007](ADR-007-api-contract.md) — API contract REST + RFC 9457
- [ADR-010](ADR-010-auth-consolidation.md) — registrazione/login/inactivity (questo ADR dipende dall'auth reale)
- [ADR-005](ADR-005-rule-engine-design.md) — Rule Engine pipeline
- [api/openapi.yaml](../api/openapi.yaml) — `/api/dcf-overrides/*`, `/api/analysis/{ticker}`
- [api/endpoints-overview.md](../api/endpoints-overview.md) — mappa US → endpoint
- [[value-investing-rule-engine-runbook]] §3b
- [[vi-08-risoluzione-q001-owner-earnings]] — formula DCF
- US-020, US-012 (predecessore)
