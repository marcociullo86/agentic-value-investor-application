---
id: backend-components
title: Backend components — Kotlin + Spring Boot
status: accepted
created: 2026-05-20
deciders: [lead-architect, marco.ciullo]
---
# Backend components — Kotlin + Spring Boot

> Decomposizione modulare del backend. Stack definito in [ADR-002](../decisions/ADR-002-backend-stack.md); FMP adapter in [ADR-004](../decisions/ADR-004-fmp-integration.md); Rule Engine in [ADR-005](../decisions/ADR-005-rule-engine-design.md); persistenza in [ADR-003](../decisions/ADR-003-database-postgresql.md); auth in [ADR-006](../decisions/ADR-006-authentication.md); contratto API in [ADR-007](../decisions/ADR-007-api-contract.md).

## Diagramma componenti

```
+--------------------------------------------------------------+
|                         API LAYER                            |
|  @RestController                                             |
|  - SearchController         (US-001, US-002)                 |
|  - AnalysisController       (US-007..013, consumo US-014)    |
|  - FinancialsController     (US-004 diagnostic)              |
|  - HistoricalController     (US-015)                         |
|  - MoatChecklistController  (US-016)                         |
|  - WatchlistController      (US-017)                         |
|  - AuthController           (EP-006)                         |
|  - DcfOverrideController    (US-012)                         |
|                                                              |
|  + GlobalExceptionHandler   (mapper -> ProblemDetails)       |
|  + RequestIdFilter          (X-Request-Id MDC)               |
|  + JwtAuthenticationFilter  (ADR-006)                        |
+--------------------------------------------------------------+
                          |
                          v
+--------------------------------------------------------------+
|                       SERVICE LAYER                          |
|  @Service                                                    |
|  - SearchService            (orchestrazione US-001/002)      |
|  - AnalyzeTickerService     (orchestratore US-014 pipeline)  |
|  - FinancialDataService     (US-004 facade)                  |
|  - HistoricalSeriesService  (US-015)                         |
|  - MoatChecklistService     (US-016)                         |
|  - WatchlistService         (US-017)                         |
|  - AuthService              (EP-006)                         |
|  - DcfOverrideService       (US-012)                         |
+--------------------------------------------------------------+
            |                |                |
            v                v                v
+--------------------+  +--------------+  +------------------+
|  RULE ENGINE       |  | FMP MODULE   |  | PERSISTENCE      |
|  com.../ruleengine |  | com.../fmp   |  | com.../persistence
|                    |  |              |  |                  |
|  + RuleEngine      |  | + FmpAdapter |  | + JPA Repos:     |
|    Service         |  | + FmpCache   |  |   UserRepo,      |
|  + ValuationRule[] |  |   Service    |  |   StockRepo,     |
|    (13 strategies) |  | + Resilience |  |   FmpSnapRepo,   |
|  + GrahamCalc      |  |   Config     |  |   ProfileSnap,   |
|  + DcfCalculator   |  | + FmpDtos    |  |   RuleResultRepo,|
|  + GreenwaldMaint  |  | + FmpEvent   |  |   WatchlistRepo, |
|    CapexEstimator  |  |   Logger     |  |   ...            |
|  + FcfFallback     |  |              |  | + @Entity x 10   |
|  + MosEvaluator    |  +------+-------+  +--------+---------+
+--------+-----------+         |                   |
         |                     v                   |
         |              [FMP REST API]             |
         |                                         |
         |        +------------------+             |
         |        | SEC EDGAR MODULE |             |
         |        | com.../secedgar  |             |
         |        |                  |             |
         |        | + SecEdgarAdapter|             |
         |        | + SecEdgarRest   |             |
         |        |   Client         |             |
         |        | + ResilientSec   |             |
         |        |   EdgarAdapter   |             |
         |        | + CacheConfig    |             |
         |        | + ResilienceConf |             |
         |        | + dto/SecFiling  |             |
         |        |   Metadata       |             |
         |        +--------+---------+             |
         |                 |                       |
         |                 v                       |
         |          [SEC EDGAR API]                |
         |                                         |
         +----------------+------------------------+
                          |
                          v
                  +---------------+
                  |  PostgreSQL   |
                  +---------------+
```

## Package map (Kotlin)

```
com.valueinvesting.webapp
 ├── ValueInvestingWebappApplication.kt        # @SpringBootApplication
 ├── api
 │    ├── SearchController.kt
 │    ├── AnalysisController.kt
 │    ├── FinancialsController.kt
 │    ├── HistoricalController.kt
 │    ├── MoatChecklistController.kt
 │    ├── WatchlistController.kt
 │    ├── AuthController.kt
 │    ├── DcfOverrideController.kt
 │    ├── dto/ ... (request/response DTOs aderenti a openapi.yaml)
 │    └── error/
 │         ├── GlobalExceptionHandler.kt
 │         └── ProblemDetailsMapper.kt
 ├── service
 │    ├── SearchService.kt
 │    ├── AnalyzeTickerService.kt
 │    ├── FinancialDataService.kt
 │    ├── HistoricalSeriesService.kt
 │    ├── MoatChecklistService.kt
 │    ├── WatchlistService.kt
 │    ├── AuthService.kt
 │    └── DcfOverrideService.kt
 ├── ruleengine
 │    ├── RuleEngineService.kt
 │    ├── FinancialDataset.kt
 │    ├── RuleSignal.kt
 │    ├── Signal.kt                       # enum
 │    ├── rules
 │    │    ├── ValuationRule.kt           # interface
 │    │    ├── RoeRule.kt
 │    │    ├── RoicRule.kt
 │    │    ├── GrossMarginRule.kt
 │    │    ├── NetMarginRule.kt
 │    │    ├── CurrentRatioRule.kt
 │    │    ├── DebtToIncomeRule.kt
 │    │    ├── CapexIntensityRule.kt
 │    │    ├── SizeRule.kt                  # Graham 1 (EP-010)
 │    │    ├── EarningsStabilityRule.kt     # Graham 3 (EP-010)
 │    │    ├── EpsGrowthRule.kt             # Graham 5 (EP-010)
 │    │    ├── Pe3yAvgRule.kt               # Graham 6 (EP-010)
 │    │    ├── PbLatestRule.kt              # Graham 7 (EP-010)
 │    │    └── DividendContinuityRule.kt    # Graham 4 (EP-010)
 │    ├── valuation
 │    │    ├── GrahamNumberCalculator.kt
 │    │    └── dcf
 │    │         ├── DcfCalculator.kt
 │    │         ├── GreenwaldMaintenanceCapexEstimator.kt
 │    │         ├── FcfFallbackEstimator.kt
 │    │         └── DcfMethod.kt
 │    └── mos
 │         └── MarginOfSafetyEvaluator.kt
 ├── fmp
 │    ├── FmpAdapter.kt                   # interface
 │    ├── FmpAdapterRestClient.kt         # impl
 │    ├── FmpCacheService.kt
 │    ├── FmpResilienceConfig.kt          # Resilience4j @Bean
 │    ├── FmpEventLogger.kt
 │    ├── FmpHealthIndicator.kt
 │    └── dto
 │         ├── IncomeStatementDto.kt
 │         ├── BalanceSheetDto.kt
 │         ├── CashFlowDto.kt
 │         ├── KeyMetricsDto.kt
 │         ├── ProfileDto.kt
 │         ├── SearchDto.kt
 │         ├── ScreenerDto.kt
 │         ├── DividendRecord.kt
 │         ├── SecFilingFmpDto.kt
 │         ├── StockNewsItem.kt
 │         └── EodPriceRecord.kt
 ├── secedgar
 │    ├── SecEdgarAdapter.kt              # interface (resolveCik, listFilings, downloadHtml)
 │    ├── SecEdgarRestClient.kt           # impl (2 RestClient: data.sec.gov + www.sec.gov)
 │    ├── ResilientSecEdgarAdapter.kt     # @Primary decorator (CB/Retry/RateLimiter)
 │    ├── SecEdgarProperties.kt           # @ConfigurationProperties(prefix="sec.edgar")
 │    ├── SecEdgarCacheConfig.kt          # Caffeine ticker→CIK (TTL 30d)
 │    ├── SecEdgarResilienceConfig.kt     # RateLimiter 10 req/s (SEC fair-access)
 │    ├── SecEdgarExceptions.kt           # Service/RateLimit/AccessDenied
 │    └── dto
 │         └── SecFilingMetadata.kt
 ├── persistence
 │    ├── entity
 │    │    ├── User.kt
 │    │    ├── RefreshToken.kt
 │    │    ├── Stock.kt
 │    │    ├── FmpFinancialSnapshot.kt
 │    │    ├── FmpProfileSnapshot.kt
 │    │    ├── RuleEngineResultEntity.kt
 │    │    ├── Watchlist.kt
 │    │    ├── WatchlistItem.kt
 │    │    ├── MoatChecklistEntry.kt
 │    │    ├── DcfMethodOverride.kt
 │    │    └── FmpApiEvent.kt
 │    └── repository
 │         └── ... (Spring Data JPA Repository<T,ID>)
 ├── security
 │    ├── SecurityConfig.kt
 │    ├── JwtService.kt
 │    ├── JwtAuthenticationFilter.kt
 │    ├── UserDetailsServiceImpl.kt
 │    └── PasswordEncoderConfig.kt
 └── config
      ├── CorsConfig.kt
      ├── JacksonConfig.kt
      ├── RestClientConfig.kt
      └── OpenApiConfig.kt
```

## Flusso pipeline analisi (US-014 endpoint `/api/analysis/{ticker}`)

```
HTTP GET /api/analysis/AAPL
    |
    v
AnalysisController.getAnalysis("AAPL")
    |
    v
AnalyzeTickerService.analyze("AAPL")
    |--> FmpCacheService.getOrFetch("AAPL", "income-statement", ...)
    |--> FmpCacheService.getOrFetch("AAPL", "balance-sheet-statement", ...)
    |--> FmpCacheService.getOrFetch("AAPL", "cash-flow-statement", ...)
    |--> FmpCacheService.getOrFetch("AAPL", "key-metrics", ...)
    |--> FmpCacheService.getOrFetchProfile("AAPL", ...)   # prezzo corrente
    |--> assembla FinancialDataset
    |
    v
RuleEngineService.evaluateAll(financialDataset)
    |-- Buffett rules (7) ----------------------------------------
    |--> RoeRule.evaluate(dataset)            -> RuleSignal
    |--> RoicRule.evaluate(dataset)           -> RuleSignal
    |--> GrossMarginRule.evaluate(dataset)    -> RuleSignal
    |--> NetMarginRule.evaluate(dataset)      -> RuleSignal
    |--> CurrentRatioRule.evaluate(dataset)   -> RuleSignal
    |--> DebtToIncomeRule.evaluate(dataset)   -> RuleSignal
    |--> CapexIntensityRule.evaluate(dataset) -> RuleSignal
    |-- Graham defensive rules (6, EP-010) -----------------------
    |--> SizeRule.evaluate(dataset)               -> RuleSignal
    |--> EarningsStabilityRule.evaluate(dataset)  -> RuleSignal
    |--> EpsGrowthRule.evaluate(dataset)          -> RuleSignal
    |--> Pe3yAvgRule.evaluate(dataset)            -> RuleSignal
    |--> PbLatestRule.evaluate(dataset)           -> RuleSignal
    |--> DividendContinuityRule.evaluate(dataset) -> RuleSignal
    |--> GrahamNumberCalculator.calculate(eps, bvps)
    |--> DcfCalculator.calculate(dataset, override?)
    |        |--> GreenwaldMaintenanceCapexEstimator.estimate(...)  # primario
    |        |--> FcfFallbackEstimator.estimate(...)                # fallback
    |--> MarginOfSafetyEvaluator.evaluate(currentPrice, dcfResult)
    |--> persisti RuleEngineResultEntity in DB
    |
    v
Response RuleEngineResult JSON
    + header X-Data-Snapshot-At
    + header X-Data-Stale (se cache stale)
```

## Resilienza FMP (US-006, ADR-004)

```
FmpAdapter.fetch(ticker, endpoint)
    |
    v
Resilience4j chain:
    [RateLimiter (30/min)]
       |
    [Bulkhead (semaphore 10)]
       |
    [Retry (3 tentativi, exp backoff)]
       |
    [CircuitBreaker (sliding 20, 50% failure)]
       |
    [Timeout 10s]
       |
       v
   HTTP RestClient -> FMP
       |
    [success] -> persist snapshot is_stale=false -> return
    [fail]    -> FmpEventLogger.log(...) -> propagate exception
       |
    [exhausted] -> AnalyzeTickerService catch -> FmpCacheService.getStale(...)
                     [stale found]    -> mark is_stale=true, return
                     [no stale data]  -> throw FmpUnavailableException -> 503
```

## Decisioni di concorrenza

- Le 4 chiamate FMP in `AnalyzeTickerService` sono parallelizzate via `CompletableFuture.supplyAsync` su uno `Executors.newFixedThreadPool(8)` dedicato (custom `@Bean fmpExecutor`).
- Virtual threads (Java 21) ammessi se profilati come stabili; default conservativo: pool tradizionale.
- Le scritture su `rule_engine_result` sono in transazione `@Transactional` separata dal fetch (eventual consistency accettabile).

## Validazione null safety

- DTO Kotlin con `val ... : Double?` per ogni campo finanziario opzionale (es. `effectiveTaxRate`, `dividendsPaid`).
- Le regole filtrano `null` PRIMA del calcolo media (US-008 AC "anni con dati incompleti vengono esclusi dal calcolo della media senza causare errori").
- Mai `?: 0.0` su valori finanziari (US-004 AC "campi mancanti = assenti, mai 0").

## Testing strategy

| Tipo | Tooling | Scope |
|---|---|---|
| Unit | JUnit5 + AssertJ + Mockito-Kotlin | rule, calculator, service (puro) |
| Integration BE | SpringBootTest + Testcontainers PostgreSQL | repository, controller, security |
| Contract | springdoc-openapi vs `openapi.yaml` (validator) | drift schema |
| FMP fixtures | Spring `@MockBean` su `FmpAdapter` con JSON in `src/test/resources/fmp-fixtures/` | analyze pipeline deterministica |

## Pagine collegate

- [overview.md](../overview.md)
- [api/openapi.yaml](../api/openapi.yaml)
- [data/er-diagram.md](../data/er-diagram.md)
- [[webapp-architecture-vi]]
- [[value-investing-rule-engine]]
- [[value-investing-rule-engine-runbook]]
- [[fmp-api-quickstart]]
