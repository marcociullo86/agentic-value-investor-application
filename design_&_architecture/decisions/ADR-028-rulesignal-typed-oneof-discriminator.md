---
id: ADR-028
title: RuleSignal typed payload — OpenAPI oneOf/discriminator + Kotlin sealed interface
status: accepted
created: 2026-06-03
deciders: [lead-architect]
supersedes_scope: "Stato as-is OpenAPI righe 1097-1148 — `RuleSignal` flat con metadati stringa `rationale`. Superseded da `oneOf`/`discriminator` su `ruleId`."
---
# ADR-028 — RuleSignal typed payload: OpenAPI oneOf/discriminator + Kotlin sealed interface

## Contesto

Lo schema OpenAPI 3.1 di `RuleSignal` (righe 1097-1148 di `api/openapi.yaml`) è oggi una `data class` flat:

```yaml
RuleSignal:
  required: [ruleId, signal, threshold]
  properties:
    ruleId:        # enum di 13 valori (7 Buffett + 6 Graham)
    signal:        # GREEN | YELLOW | RED | INDETERMINATE | NOT_CALCULABLE
    observedValue: # numero, nullable
    threshold:     # stringa human-readable
    rationale:     # stringa libera con i metadati strutturati embedded
```

Il commento alla riga 1101-1107 dichiara esplicitamente che la strutturazione tipata per `ruleId` "è rimandata a un futuro refactor del contratto: non scope di TSK-087/EP-010 per evitare breaking change sui client TS auto-generati".

Il debito è formalizzato dal code-reviewer @ CQRL Sprint 18 (TSK-289 iter-1) nel gap `rulesignal-typed-metadata-deferred` (2026-06-03): i 13 ruleId espongono internamente valori semantici (es. `revenueLatest`/`thresholdUsd` per `SIZE_LATEST`, `yearsPositive`/`yearsAvailable`/`lossYears` per `EARNINGS_STABILITY_10Y`) che vengono attualmente embeddati come stringa in `rationale`. I consumer (FE proprio + integrazioni future) devono parsare regex anziché leggere campi tipati.

EP-021 (3 storie US-093/094/095, `ready`) chiude il gap. Questo ADR è la **decisione contrattuale** che precede l'implementazione: tipo di union, strategia di backward-compat, persistenza JSONB, gestione client TS generato.

[^src: management/kanban/EP-021-rulesignal-payload-refactor/EP-021.md §Obiettivo]
[^src: management/kanban/EP-021-rulesignal-payload-refactor/US-093-rulesignal-schema-typed-be/US-093.md §Business Rules]
[^src: wiki/gaps.md §2026-06-03 rulesignal-typed-metadata-deferred]
[^src: design_&_architecture/api/openapi.yaml righe 1097-1148]
[^src: design_&_architecture/decisions/ADR-005-rule-engine-design.md §Strategy pattern]

## Decisione

`RuleSignal` diventa un **union type discriminato** in OpenAPI 3.1 (`oneOf` + `discriminator` su `ruleId`) e una **sealed interface Kotlin polimorfica** Jackson-aware. I campi legacy `observedValue` + `rationale` restano nel payload come `deprecated` per una finestra di **2 release** (R+1, R+2) e vengono rimossi a R+3. La migrazione è **breaking** per i client che già consumavano `rationale` come stringa parser-driven; coordinamento BE → contract regen → FE consumer migration in **unica wave** (sequenza US-093 → US-094 → US-095 obbligata).

### 1. Sealed interface Kotlin

File `src/backend/.../ruleengine/RuleSignal.kt` rifattorizzato:

```kotlin
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "ruleId",
    visible = true,
)
@JsonSubTypes(
    JsonSubTypes.Type(value = RuleSignal.Size::class,                   name = "SIZE_LATEST"),
    JsonSubTypes.Type(value = RuleSignal.EarningsStability10y::class,   name = "EARNINGS_STABILITY_10Y"),
    JsonSubTypes.Type(value = RuleSignal.EpsGrowth10y::class,           name = "EPS_GROWTH_10Y"),
    JsonSubTypes.Type(value = RuleSignal.Pe3yAvg::class,                name = "PE_3Y_AVG"),
    JsonSubTypes.Type(value = RuleSignal.PbLatest::class,               name = "PB_LATEST"),
    JsonSubTypes.Type(value = RuleSignal.DividendContinuity20y::class,  name = "DIVIDEND_CONTINUITY_20Y"),
    JsonSubTypes.Type(value = RuleSignal.Roe10yAvg::class,              name = "ROE_10Y_AVG"),
    JsonSubTypes.Type(value = RuleSignal.Roic10yAvg::class,             name = "ROIC_10Y_AVG"),
    JsonSubTypes.Type(value = RuleSignal.GrossMargin10yAvg::class,      name = "GROSS_MARGIN_10Y_AVG"),
    JsonSubTypes.Type(value = RuleSignal.NetMargin10yAvg::class,        name = "NET_MARGIN_10Y_AVG"),
    JsonSubTypes.Type(value = RuleSignal.CurrentRatioLatest::class,     name = "CURRENT_RATIO_LATEST"),
    JsonSubTypes.Type(value = RuleSignal.DebtToIncomeLatest::class,     name = "DEBT_TO_INCOME_LATEST"),
    JsonSubTypes.Type(value = RuleSignal.CapexIntensity10yAvg::class,   name = "CAPEX_INTENSITY_10Y_AVG"),
    // EP-023 (se rilasciata prima/insieme):
    JsonSubTypes.Type(value = RuleSignal.NcavLatest::class,             name = "NCAV_LATEST"),
    JsonSubTypes.Type(value = RuleSignal.NetNetRatio::class,            name = "NET_NET_RATIO"),
)
sealed interface RuleSignal {
    val ruleId: String
    val signal: Signal

    // Legacy compat (rimozione R+3). Marcati @Deprecated a livello Kotlin
    // per emettere warning sui consumer interni; @JsonProperty mantiene
    // il campo serializzato per i client legacy.
    @get:Deprecated("Use typed metadata fields. Removed in R+3.")
    val observedValue: Double?

    @get:Deprecated("Use typed metadata fields. Removed in R+3.")
    val rationale: String

    data class Size(
        override val signal: Signal,
        val revenueLatest: Double?,
        val thresholdUsd: Long,
        @Deprecated("R+3") override val observedValue: Double?,
        @Deprecated("R+3") override val rationale: String,
    ) : RuleSignal { override val ruleId: String = "SIZE_LATEST" }

    // ... 12 sotto-tipi (vedi §3 per il mapping completo)
}
```

**Note Jackson**:
- `include = EXISTING_PROPERTY` + `visible = true`: `ruleId` rimane visibile nel JSON come property normale (no `@type` ghost). Coerente con la convenzione attuale.
- `data class` per ogni sotto-tipo → equals/hashCode automatici, utili per snapshot test e idempotenza.
- I sotto-tipi referenziano direttamente l'enum `Signal` (file `Signal.kt` invariato).

### 2. OpenAPI 3.1 — oneOf/discriminator

```yaml
RuleSignal:
  oneOf:
    - $ref: '#/components/schemas/RuleSignalSize'
    - $ref: '#/components/schemas/RuleSignalEarningsStability10y'
    - $ref: '#/components/schemas/RuleSignalEpsGrowth10y'
    - $ref: '#/components/schemas/RuleSignalPe3yAvg'
    - $ref: '#/components/schemas/RuleSignalPbLatest'
    - $ref: '#/components/schemas/RuleSignalDividendContinuity20y'
    - $ref: '#/components/schemas/RuleSignalRoe10yAvg'
    - $ref: '#/components/schemas/RuleSignalRoic10yAvg'
    - $ref: '#/components/schemas/RuleSignalGrossMargin10yAvg'
    - $ref: '#/components/schemas/RuleSignalNetMargin10yAvg'
    - $ref: '#/components/schemas/RuleSignalCurrentRatioLatest'
    - $ref: '#/components/schemas/RuleSignalDebtToIncomeLatest'
    - $ref: '#/components/schemas/RuleSignalCapexIntensity10yAvg'
    # EP-023:
    - $ref: '#/components/schemas/RuleSignalNcavLatest'
    - $ref: '#/components/schemas/RuleSignalNetNetRatio'
  discriminator:
    propertyName: ruleId
    mapping:
      SIZE_LATEST:               '#/components/schemas/RuleSignalSize'
      EARNINGS_STABILITY_10Y:    '#/components/schemas/RuleSignalEarningsStability10y'
      EPS_GROWTH_10Y:            '#/components/schemas/RuleSignalEpsGrowth10y'
      PE_3Y_AVG:                 '#/components/schemas/RuleSignalPe3yAvg'
      PB_LATEST:                 '#/components/schemas/RuleSignalPbLatest'
      DIVIDEND_CONTINUITY_20Y:   '#/components/schemas/RuleSignalDividendContinuity20y'
      ROE_10Y_AVG:               '#/components/schemas/RuleSignalRoe10yAvg'
      ROIC_10Y_AVG:              '#/components/schemas/RuleSignalRoic10yAvg'
      GROSS_MARGIN_10Y_AVG:      '#/components/schemas/RuleSignalGrossMargin10yAvg'
      NET_MARGIN_10Y_AVG:        '#/components/schemas/RuleSignalNetMargin10yAvg'
      CURRENT_RATIO_LATEST:      '#/components/schemas/RuleSignalCurrentRatioLatest'
      DEBT_TO_INCOME_LATEST:     '#/components/schemas/RuleSignalDebtToIncomeLatest'
      CAPEX_INTENSITY_10Y_AVG:   '#/components/schemas/RuleSignalCapexIntensity10yAvg'
      NCAV_LATEST:               '#/components/schemas/RuleSignalNcavLatest'
      NET_NET_RATIO:             '#/components/schemas/RuleSignalNetNetRatio'

RuleSignalBase:
  type: object
  required: [ruleId, signal]
  properties:
    ruleId: { type: string }
    signal: { $ref: '#/components/schemas/Signal' }
    threshold:
      type: string
      deprecated: true
      description: "Legacy human-readable threshold label. Use typed fields per-ruleId."
    observedValue:
      type: number
      nullable: true
      deprecated: true
      description: "Legacy observed value. Use typed fields per-ruleId."
    rationale:
      type: string
      deprecated: true
      description: "Legacy human-readable rationale. Use typed fields per-ruleId."

RuleSignalSize:
  allOf:
    - $ref: '#/components/schemas/RuleSignalBase'
    - type: object
      required: [revenueLatest, thresholdUsd]
      properties:
        ruleId:        { type: string, enum: [SIZE_LATEST] }
        revenueLatest: { type: number, nullable: true, description: "Revenue ultimo esercizio in USD" }
        thresholdUsd:  { type: number, description: "Soglia Graham defensive: USD 100M" }
# ... 14 schemi totali (13 + 2 EP-023 se in flight; vedi §3)
```

### 3. Mapping completo 13 sotto-tipi (+ 2 EP-023)

| ruleId | Campi tipati nuovi | Note |
|---|---|---|
| `SIZE_LATEST` | `revenueLatest: Double?`, `thresholdUsd: Long` | Graham §Adequate Size (US-032, $100M) |
| `EARNINGS_STABILITY_10Y` | `yearsPositive: Int`, `yearsAvailable: Int`, `lossYears: List<Int>` | Graham §Earnings Stability |
| `EPS_GROWTH_10Y` | `cagrPercent: Double?`, `thresholdPercent: Double`, `epsStart: Double?`, `epsEnd: Double?`, `yearStart: Int?`, `yearEnd: Int?` | Graham §EPS Growth |
| `PE_3Y_AVG` | `pe3yAvg: Double?`, `threshold: Double` | Graham §P/E moderate (<15) |
| `PB_LATEST` | `pbLatest: Double?`, `threshold: Double` | Graham §P/B moderate (<1.5) |
| `DIVIDEND_CONTINUITY_20Y` | `consecutiveYears: Int?`, `thresholdYears: Int` | Graham §Dividend (20y) |
| `ROE_10Y_AVG` | `averagePercent: Double?`, `yearsAvailable: Int`, `thresholdGreenPercent: Double`, `thresholdYellowPercent: Double` | Buffett quality |
| `ROIC_10Y_AVG` | (identico a ROE) | Buffett quality |
| `GROSS_MARGIN_10Y_AVG` | `averagePercent: Double?`, `thresholdGreenPercent: Double`, `thresholdYellowPercent: Double` | Buffett pricing power |
| `NET_MARGIN_10Y_AVG` | `averagePercent: Double?`, `thresholdGreenPercent: Double` | Buffett pricing power |
| `CURRENT_RATIO_LATEST` | `ratioLatest: Double?`, `thresholdGreen: Double`, `thresholdYellow: Double` | Buffett financial strength |
| `DEBT_TO_INCOME_LATEST` | `ratioLatest: Double?`, `thresholdGreen: Double`, `thresholdYellow: Double`, `netIncomePositive: Boolean` | Buffett (INDETERMINATE se net income ≤ 0) |
| `CAPEX_INTENSITY_10Y_AVG` | `averagePercent: Double?`, `thresholdGreenPercent: Double`, `thresholdYellowPercent: Double` | Buffett capital-light |
| `NCAV_LATEST` (EP-023) | `ncavTotal: Double?`, `ncavPerShare: Double?` | Graham enterprising — vedi ADR-029 |
| `NET_NET_RATIO` (EP-023) | `priceLatest: Double?`, `ncavPerShare: Double?`, `ratio: Double?`, `thresholdRatio: 0.6667` | Graham enterprising — vedi ADR-029 |

**Note di scope mapping**: nomi e tipi proposti sono **vincolanti** sull'API contract. Eventuali piccoli aggiustamenti (es. `epsStart`/`epsEnd` come `Int` vs `Double`) restano nello scope del TSK BE ma devono essere riflessi simmetricamente in OpenAPI + sealed interface.

### 4. Persistenza JSONB — backward-compat strategia

Tabella `rule_engine_result.signals` (JSONB) memorizza la lista `List<RuleSignal>` serializzata da Jackson.

**Forward write**: tutti i nuovi record contengono i campi tipati + i campi legacy `observedValue`/`rationale` (deprecated ma compilati dalle 13 strategie durante la finestra di transizione).

**Backward read**: i record JSONB pre-EP-021 contengono `{ruleId, signal, observedValue, threshold, rationale}` senza campi tipati. Il deserializer polimorfico Jackson:
1. Legge `ruleId` (discriminator).
2. Determina il sotto-tipo (`RuleSignal.Size` ecc).
3. I campi tipati mancanti (`revenueLatest`, `thresholdUsd`) → vengono valorizzati `null` (sono nullable in §1).
4. I campi legacy presenti vengono letti normalmente.

**Conseguenza**: i record vecchi sono **letti** senza errore, ma il FE che usa formatter typed-driven (US-095) mostrerebbe valori "N/A" per i campi tipati. Mitigazione: il fallback su `rationale` (US-095 paranoid fallback) copre questi record. Nel tempo, i record vengono **naturalmente sovrascritti** dalla rivalutazione del Rule Engine (`/api/analysis/{ticker}` ricalcola continuamente). Nessuna migration batch necessaria.

**Decisione**: opzione **"natural overwrite"** (no batch rewrite, no migration). Documentato nel TSK BE (US-093) come parte della strategia di rollout. Se nel futuro emergesse necessità di backfill aggressivo (es. cache cold con record stale persistenti), può essere aggiunto un job batch in epica separata.

### 5. Client TS — gestione discriminator

`openapi-generator-cli` (toolchain US-094) genera per OpenAPI 3.1 `oneOf` + `discriminator` un union discriminated TypeScript:

```typescript
type RuleSignal =
  | RuleSignalSize
  | RuleSignalEarningsStability10y
  | RuleSignalEpsGrowth10y
  | /* ... 13/15 sotto-tipi */;

interface RuleSignalSize {
  ruleId: 'SIZE_LATEST';
  signal: Signal;
  revenueLatest: number | null;
  thresholdUsd: number;
  // legacy deprecated:
  observedValue?: number | null;
  rationale?: string;
  threshold?: string;
}
```

Il FE può fare **type narrowing** sicuro:

```typescript
function format(s: RuleSignal): string {
  switch (s.ruleId) {
    case 'SIZE_LATEST':
      // qui TS conosce: s.revenueLatest, s.thresholdUsd
      return `Revenue: $${formatMoney(s.revenueLatest)} (soglia $${formatMoney(s.thresholdUsd)})`;
    // ...
  }
}
```

**Rischio noto** (gap conoscenza): alcune versioni di `openapi-generator-cli` (e tooling come `orval`, `openapi-typescript`) hanno gestito storicamente `oneOf` con discriminator in modo subottimale, producendo a volte `any` o un'intersection invece di union discriminato. La verifica empirica è demandata a US-094.

**Strategia di mitigazione US-094**:
1. **Path preferito**: tentare la generazione con il config standard del generator usato.
2. **Path A — fix config**: se la generazione produce union sbagliato, applicare `useUnionTypes=true` (openapi-generator) o flag equivalente.
3. **Path B — post-processing**: se A fallisce, applicare uno script Node che riscriva il file TS generato per imporre union discriminato manuale.
4. **Path C — type guards manuali**: ultimo ricorso; definire `isRuleSignalSize(s): s is RuleSignalSize { return s.ruleId === 'SIZE_LATEST' }` nel codice FE e usarli come narrower.

La scelta tra A/B/C va **documentata in handoff US-094** (è già un AC). Il contract test (`OpenApiContractIT` esteso) gira in CI nel job `contract-check` e blocca la build se il payload reale diverge dallo schema tipato — questo è il guardiano contro regressioni indipendentemente dal path scelto.

### 6. FE — formatter typed-driven

US-095 introduce in `src/frontend/lib/rule-signals/formatters.ts`:

```typescript
type FormatterOutput = { title: string; subtitle: string; tooltip: string };

const formatters: Record<RuleSignal['ruleId'], (s: RuleSignal) => FormatterOutput> = {
  SIZE_LATEST: (s) => {
    const sized = s as RuleSignalSize; // narrowing
    return {
      title: 'Dimensione',
      subtitle: `Revenue: $${fmtMoney(sized.revenueLatest)} (soglia $${fmtMoney(sized.thresholdUsd)})`,
      tooltip: 'Graham Cap.14 — Adequate Size of Enterprise',
    };
  },
  // ... 13/15 formatter
};

export function formatRuleSignal(s: RuleSignal): FormatterOutput {
  const f = formatters[s.ruleId];
  if (!f) {
    // paranoid fallback: ruleId ignoto → mostra rationale legacy
    return { title: s.ruleId, subtitle: s.rationale ?? '', tooltip: '' };
  }
  return f(s);
}
```

**Fallback paranoid** (US-095 AC): se i campi tipati attesi sono `null` (es. record JSONB stale pre-EP-021) il formatter può decidere di degradare su `rationale` legacy per il subtitle. Il fallback va attivato solo nei rami specifici dei formatter (es. `if (sized.revenueLatest === null) return { ..., subtitle: s.rationale ?? '...' }`), non come prima linea.

**Componenti FE impattati**:
- `TrafficLightPanel` / `RuleSignalRow` / `RuleSignalBadge` → usano `formatRuleSignal(s)`.
- Pagina `/analysis` (deep analysis tab) → se forwarda `rationale`, sostituire con `formatRuleSignal(s).subtitle`.
- Test Vitest: snapshot per i 13/15 ruleId × 3 stati visivi (GREEN, RED, INDETERMINATE).

### 7. Rollout — sequenza obbligata

```
US-093 (BE + OpenAPI)       → US-094 (client TS + contract test)  → US-095 (FE consumer)
       └ unica wave deploy ─┘                                      └ deploy finale (rimuove uso rationale)
```

**Vincolo**: i 3 step vanno nel **medesimo deploy** (o dietro feature flag transitorio se serve gradualità). Non è ammesso rilasciare US-093 sul backend senza US-094 + US-095 a seguire — i client TS attuali continuerebbero a parsare `rationale` mentre il backend già emette campi tipati: il subtitle visibile non degrada (perché `rationale` rimane finché legacy non rimosso a R+3), ma il debito di tipo persiste e il gap non si chiude.

### 8. Finestra di transizione e rimozione legacy

| Release | Behavior `rationale` + `observedValue` |
|---|---|
| R+1 (rilascio EP-021) | Presenti, marcati `deprecated` in OpenAPI e `@Deprecated` in Kotlin. Compilati da tutte le strategie. |
| R+2 | Identico a R+1. Monitoraggio uso (lint check + grep CI: nessun consumer FE/BE legge i campi deprecati eccetto i formatter paranoid). |
| R+3 | **Rimossi**. Sotto-tipi Kotlin senza `observedValue`/`rationale`. OpenAPI: `RuleSignalBase` senza i due campi. Migration JSONB facoltativa (rewrite job una-tantum se i record stale danno fastidio). |

Decisione finale su R+3 va presa in nuovo ADR (`ADR-NNN supersedes ADR-028 §8`) o aggiornata via PR sul presente ADR se ancora `proposed` (attualmente `accepted` ⇒ nuovo ADR richiesto).

## Componenti

| Componente | Layer | Path | Modifica |
|---|---|---|---|
| `RuleSignal.kt` | BE | `src/backend/.../ruleengine/RuleSignal.kt` | sealed interface + 13/15 sotto-tipi |
| 13 ValuationRule (Buffett 7 + Graham 6) | BE | `src/backend/.../ruleengine/rules/*.kt` | emettono il sotto-tipo specifico anziché flat `RuleSignal` |
| `RuleEngineResultResponse.kt` | BE | `src/backend/.../api/model/RuleEngineResultResponse.kt` | `signals: List<RuleSignal>` (tipo invariato; deserializza polimorfico) |
| `api/openapi.yaml` | API | `design_&_architecture/api/openapi.yaml` | sostituire schema `RuleSignal` con `oneOf`/`discriminator` |
| Client TS generato | FE | `src/frontend/src/api-generated/*` | rigenerazione via openapi-generator-cli (US-094) |
| `formatters.ts` | FE | `src/frontend/lib/rule-signals/formatters.ts` (nuovo) | 13/15 formatter typed-driven |
| `TrafficLightPanel`, `RuleSignalRow`, `RuleSignalBadge` | FE | `src/frontend/components/*` | consumano `formatRuleSignal(s)` |

### Schema DB

Nessuna migration richiesta. `rule_engine_result.signals` (JSONB) accoglie il nuovo formato; i record vecchi vengono letti via deserializer polimorfico con campi tipati `null` (natural overwrite, §4).

### API Changes

| Endpoint | Change |
|---|---|
| `GET /api/analysis/{ticker}` | `signals[].rationale` + `signals[].observedValue` + `signals[].threshold` marcati `deprecated`. Nuovi campi tipati per ruleId aggiunti (additivo per gli schemi specifici, breaking solo per consumer che si appoggiavano su `RuleSignal` flat senza discriminator). |

## Motivazioni

1. **Chiude il debito formale** `rulesignal-typed-metadata-deferred` (gap 2026-06-03, code-reviewer CQRL Sprint 18).
2. **Type safety end-to-end**: type narrowing TypeScript automatico via union discriminato; il FE consuma `revenueLatest`, `ncavPerShare`, ecc. con autocomplete e check statico.
3. **Standard OpenAPI 3.1 verbatim**: `oneOf` + `discriminator` con `propertyName` + `mapping` è il pattern canonico documentato (raw/tech_stack.md §Standards verbatim). springdoc-openapi 2.x lo supporta.
4. **Backward compat persistenza JSONB**: zero migration richiesta; i record vecchi sono letti via Jackson polimorfico, sovrascritti naturalmente al ricalcolo.
5. **Backward compat client legacy (transition window)**: i campi `rationale`/`observedValue`/`threshold` restano nel payload deprecati per R+1/R+2, evitando rottura immediata di eventuali integrazioni esterne non ancora migrate. Rimozione finale R+3 con nuovo ADR.
6. **Sealed interface Kotlin**: pattern idiomatico per ADT (algebraic data type) in Kotlin; `when` exhaustive sulle 13/15 varianti dà compile-time guarantees lato BE.
7. **Coordinamento EP-023**: il pattern accoglie nativamente i 2 nuovi `ruleId` NCAV (vedi ADR-029) senza refactor aggiuntivo.

## Alternative considerate

| Alternativa | Esito |
|---|---|
| **Campo `metadata: Map<String, Any>` libero** in `RuleSignal` | Niente type safety, FE deve fare cast manuali. Scartato. |
| **13 endpoint separati `GET /api/analysis/{ticker}/rules/{ruleId}`** | Over-engineering, breakup del payload coeso, latenza N+1 sul FE. Scartato. |
| **Mantenere `RuleSignal` flat + 13 nuovi campi opzionali** (`revenueLatest?`, `pe3yAvg?`, ...) | Schema "wide table" con 30+ campi opzionali nullable, semantica confusa, no type narrowing. Scartato. |
| **Versionare il payload** (`signalsV2: [...]` accanto a `signals: [...]`) | Raddoppia il body, breaking pattern OpenAPI standard. Scartato. |
| **`anyOf` invece di `oneOf`** | `anyOf` permette il match parziale → ambiguità sul discriminator. `oneOf` impone match unico = corretto per union. Scartato. |
| **Rimozione immediata `rationale`/`observedValue` in R+1** (no transition window) | Più "pulito" ma breaking aggressivo sui consumer legacy. La finestra 2-release mitiga il rischio. Scartato. |
| **Migrazione JSONB batch in R+1** (rewrite record vecchi) | Costo + rischio operativo; il pattern "natural overwrite" via ricalcolo è equivalente nel medio termine. Scartato. |

## Conseguenze

- **US-093 (BE + OpenAPI)**: refactor `RuleSignal` Kotlin + adeguamento 13 strategie + nuovo schema OpenAPI. Test unitari per ognuno dei 13 ruleId verifica il sotto-tipo emesso. Persistenza JSONB letta retrocompat.
- **US-094 (client TS + contract test)**: rigenerazione client + contract test su 13 ruleId. Workaround documentato in handoff se discriminator generation problematica.
- **US-095 (FE consumer)**: formatter typed-driven + migrazione `TrafficLightPanel`/`RuleSignalRow`/`RuleSignalBadge` + test Vitest snapshot + Playwright smoke.
- **Coordinamento EP-023**: se EP-021 done prima di EP-023, US-096 emette nativamente sotto-tipi `RuleSignal.NcavLatest` / `RuleSignal.NetNetRatio`. Se parallelo, US-096 attende US-093 per definire i sotto-tipi.
- **Wave Sprint**: i 3 step EP-021 sono sequenziali in unica wave deploy. Tpm scheduling consigliato: US-093 → US-094 → US-095 in stessa sprint, no PR partial-merge.
- **Wiki**: gap `rulesignal-typed-metadata-deferred` chiudibile da wiki-keeper dopo merge US-095 (riferimento nell'handoff CQRL).
- **ADR esistenti**: ADR-005 (Rule Engine design) **non superseded** — la strategy pattern resta. Solo lo shape del payload `RuleSignal` cambia. ADR-007 (API contract) coerente — `oneOf`/`discriminator` è OpenAPI 3.1 standard. ADR-012 (ProblemDetail) non impattato.

## Tracciabilità US → AC → policy

| US | AC | Policy |
|---|---|---|
| US-093 | Schema OpenAPI `oneOf`/`discriminator` con 13/15 sotto-schemi | §2 |
| US-093 | 13 strategie emettono sotto-tipo tipato (test unitari) | §1 + §3 |
| US-093 | GREEN/YELLOW/RED/INDETERMINATE/NOT_CALCULABLE invariati | §1 (logica intatta, solo shape DTO) |
| US-093 | Payload contiene typed + legacy deprecated in transizione | §3 + §8 |
| US-093 | JSONB letto retrocompat | §4 |
| US-094 | Client TS espone union discriminato 13/15 tipi | §5 (Path preferito) |
| US-094 | Contract test passa per tutti i ruleId | `OpenApiContractIT` esteso |
| US-094 | Workaround documentato se discriminator generation buggy | §5 (Path A/B/C) |
| US-095 | Subtitle/tooltip derivato da typed fields per 13/15 ruleId | §6 (formatters.ts) |
| US-095 | Fallback paranoid su `rationale` | §6 (last resort, AC US-095) |
| US-095 | Smoke Playwright + Vitest snapshot 13/15 × 3 stati | test plan US-095 |

## Pagine collegate

- [ADR-005](ADR-005-rule-engine-design.md) — Rule Engine design (invariato; questo ADR cambia solo lo shape DTO)
- [ADR-007](ADR-007-api-contract.md) — API contract OpenAPI 3.1 (coerente)
- [ADR-012](ADR-012-problemdetail-rfc9457-flatten.md) — ProblemDetail (non impattato)
- [ADR-029](ADR-029-net-net-stocks-ncav.md) — NCAV rule (consuma il pattern di questo ADR)
- [api/openapi.yaml](../api/openapi.yaml) §RuleSignal
- EP-021 `management/kanban/EP-021-rulesignal-payload-refactor/EP-021.md`
- US-093 / US-094 / US-095
- [[value-investing-rule-engine]] §Aggiornamenti (v2026-05-21)
- [[analysis-api-pipeline]] §Tredici regole (`signals`)
- [[openapi-contract-check]]
