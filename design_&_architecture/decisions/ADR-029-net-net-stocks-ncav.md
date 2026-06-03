---
id: ADR-029
title: Net-Net Stocks (NCAV) — Graham enterprising criterion in Rule Engine
status: accepted
created: 2026-06-03
deciders: [lead-architect]
---
# ADR-029 — Net-Net Stocks (NCAV): Graham enterprising criterion nel Rule Engine

## Contesto

Il criterio "net-net" di Benjamin Graham (Cap.15 *The Intelligent Investor*) è documentato nel concept wiki [[net-net-stocks]] e nella checklist operativa [[enterprising-investor-checklist]] §Step 7, ma non è esposto come `ruleId` automatico del Rule Engine. Il gap `net-net-implementation-gap` (wiki-keeper 2026-05-22) lo formalizza. L'investitore intraprendente che vuole identificare titoli net-net via WebApp deve calcolarsi NCAV a mano.

Il Rule Engine oggi copre 13 `ruleId`:
- **7 Buffett-quality** (EP-003): ROE, ROIC, Gross/Net Margin, Current Ratio, Debt/Income, CapEx Intensity.
- **6 Graham-defensive** (EP-010, Cap.14): Size, Earnings Stability, EPS Growth, P/E 3y, P/B, Dividend Continuity.

Il criterio net-net appartiene al **profilo enterprising Graham (Cap.15)**, non al defensive. È storicamente raro nei mercati sviluppati moderni (2026), ma riemerge in crisi sistemiche, settori ciclici in fase bassa, mercati emergenti, small-cap non coperte.

EP-023 (2 storie US-096 BE / US-097 FE, `ready`) chiude il gap aggiungendo due `ruleId` (`NCAV_LATEST` informativo + `NET_NET_RATIO` decisionale) e un badge FE dedicato "Net-Net".

[^src: management/kanban/EP-023-net-net-stocks-ncav/EP-023.md §Obiettivo]
[^src: management/kanban/EP-023-net-net-stocks-ncav/US-096-regola-ncav-net-net-be/US-096.md §Business Rules]
[^src: management/kanban/EP-023-net-net-stocks-ncav/US-097-badge-net-net-fe/US-097.md §Business Rules]
[^src: wiki/concepts/net-net-stocks.md §Definizione]
[^src: wiki/runbooks/enterprising-investor-checklist.md §Step 7 — Criterio Net-Net]
[^src: wiki/gaps.md §2026-05-22 net-net-implementation-gap]

## Decisione

Aggiungere **due `ruleId` `NCAV_LATEST` (informativo) + `NET_NET_RATIO` (decisionale GREEN/RED su soglia Graham 2/3)** come strategie ValuationRule indipendenti seguendo il pattern EP-010, e un componente FE `NetNetBadge` visibile solo quando il criterio è soddisfatto. I due `ruleId` nascono **typed-native** (sotto-tipi della sealed interface di ADR-028) se EP-021 done; in caso EP-021 ancora in flight, sequenza obbligata US-093 → US-096.

### 1. Formula NCAV (Graham Cap.15)

```
NCAV (Net Current Asset Value)
   = totalCurrentAssets - totalLiabilities

  ↳ NOTA: passività TOTALI (correnti + non correnti), non solo correnti.
    Graham assume immobilizzazioni e intangibles a ZERO (criterio di
    liquidazione conservativa).

NCAV per share
   = NCAV / sharesOutstanding

Ratio Graham (decisionale)
   = priceLatest / NCAV_per_share

Soglia
   = 2/3  (= 0.6667 con precisione double)
```

Fonti dati FMP (già adapter-wrapped, EP-002):

| Campo | Fonte FMP primaria | Fallback |
|---|---|---|
| `totalCurrentAssets` | `balance-sheet-statement` (annual, latest) | — |
| `totalLiabilities` | `balance-sheet-statement` (annual, latest) | — |
| `sharesOutstanding` | `key-metrics` (annual, latest) | `balance-sheet-statement.weightedAverageShsOut` |
| `priceLatest` | `FmpProfileSnapshot` (cache 1h, ADR-014) | — |

Il be-dev sceglie in TSK il source primario di `sharesOutstanding` (preferire `KeyMetricsDto.sharesOutstanding` se non-null, altrimenti `BalanceSheetStatementDto.weightedAverageShsOut`).

### 2. `NCAV_LATEST` — ruleId informativo

**Scopo**: esporre il NCAV calcolato come **valore informativo**, indipendentemente dal prezzo. Non valuta opportunità di acquisto; serve a far emergere il calcolo nel pannello Traffic Light.

**Codifica**:

| Condizione | Signal | Note |
|---|---|---|
| `totalCurrentAssets == null` OR `totalLiabilities == null` OR `sharesOutstanding == null` OR `sharesOutstanding == 0` | `INDETERMINATE` | dati mancanti — coerente con regola "campi mancanti = assenti, mai 0" (US-004 / PATTERN §7 r.13) |
| `ncavTotal > 0` | `GREEN` | calcolo riuscito; net-net theoricamente possibile (decisione la prende `NET_NET_RATIO`) |
| `ncavTotal <= 0` | `RED` | passivo totale > attivo corrente; net-net impossibile a priori |

**Campi tipati** (sotto-tipo `RuleSignal.NcavLatest` in ADR-028):

```kotlin
data class NcavLatest(
    override val signal: Signal,
    val ncavTotal: Double?,        // USD
    val ncavPerShare: Double?,     // USD per share
    @Deprecated("R+3") override val observedValue: Double?,   // == ncavPerShare per compat
    @Deprecated("R+3") override val rationale: String,
) : RuleSignal { override val ruleId: String = "NCAV_LATEST" }
```

**OpenAPI schema** (sotto-tipo di ADR-028 §2):

```yaml
RuleSignalNcavLatest:
  allOf:
    - $ref: '#/components/schemas/RuleSignalBase'
    - type: object
      required: [ncavTotal, ncavPerShare]
      properties:
        ruleId:        { type: string, enum: [NCAV_LATEST] }
        ncavTotal:     { type: number, nullable: true, description: "Net Current Asset Value in USD" }
        ncavPerShare:  { type: number, nullable: true, description: "NCAV per azione in USD" }
```

### 3. `NET_NET_RATIO` — ruleId decisionale Graham

**Scopo**: segnale decisionale GREEN/RED secondo la soglia di acquisto Graham `prezzo < 2/3 × NCAV per share`.

**Codifica**:

| Condizione | Signal | Note |
|---|---|---|
| `ncavPerShare` non calcolabile (vedi `NCAV_LATEST` INDETERMINATE) OR `priceLatest == null` | `INDETERMINATE` | dati insufficienti |
| `ncavPerShare <= 0` | `NOT_CALCULABLE` | coerente con `NCAV_LATEST` RED; il ratio sarebbe negativo o non sensato |
| `ratio < 0.6667` (`priceLatest < (2/3) × ncavPerShare`) | `GREEN` | opportunità net-net Graham — MoS strutturale 33% sul valore di liquidazione |
| `ratio >= 0.6667` | `RED` | titolo non net-net |

**Campi tipati** (sotto-tipo `RuleSignal.NetNetRatio`):

```kotlin
data class NetNetRatio(
    override val signal: Signal,
    val priceLatest: Double?,         // USD per share
    val ncavPerShare: Double?,        // USD per share
    val ratio: Double?,               // price / ncavPerShare
    val thresholdRatio: Double = THRESHOLD_RATIO,  // 2/3
    @Deprecated("R+3") override val observedValue: Double?,   // == ratio per compat
    @Deprecated("R+3") override val rationale: String,
) : RuleSignal {
    override val ruleId: String = "NET_NET_RATIO"
    companion object { const val THRESHOLD_RATIO: Double = 2.0 / 3.0 }
}
```

**OpenAPI schema**:

```yaml
RuleSignalNetNetRatio:
  allOf:
    - $ref: '#/components/schemas/RuleSignalBase'
    - type: object
      required: [priceLatest, ncavPerShare, ratio, thresholdRatio]
      properties:
        ruleId:         { type: string, enum: [NET_NET_RATIO] }
        priceLatest:    { type: number, nullable: true, description: "Prezzo corrente in USD" }
        ncavPerShare:   { type: number, nullable: true, description: "NCAV per azione in USD" }
        ratio:          { type: number, nullable: true, description: "priceLatest / ncavPerShare" }
        thresholdRatio: { type: number, default: 0.6666666666666666, description: "Soglia Graham 2/3" }
```

### 4. BE — implementazione

**File nuovi**:

```
src/backend/.../ruleengine/rules/NcavLatestRule.kt
src/backend/.../ruleengine/rules/NetNetRatioRule.kt
src/backend/.../ruleengine/calculators/NcavCalculator.kt   (opzionale: condivide calcolo tra le 2 rule)
src/backend/src/test/.../ruleengine/rules/NcavLatestRuleTest.kt
src/backend/src/test/.../ruleengine/rules/NetNetRatioRuleTest.kt
```

**`NcavCalculator`** (utility condiviso, evita duplicazione):

```kotlin
object NcavCalculator {
    data class Result(
        val ncavTotal: Double?,
        val ncavPerShare: Double?,
        val reason: String,  // "missing_balance_sheet", "missing_shares", "negative", "ok"
    )
    fun compute(dataset: FinancialDataset): Result { /* ... */ }
}
```

**`NcavLatestRule`** e **`NetNetRatioRule`**:
- `@Component` Spring → autoinjected nel `RuleEngineService.rules: List<ValuationRule>` (pattern EP-003/EP-010 invariato).
- `evaluate(dataset)` chiama `NcavCalculator.compute(dataset)` e produce il sotto-tipo tipato del proprio `ruleId`.
- `NetNetRatioRule` legge anche `dataset.currentPrice` (already in `FinancialDataset` via `FmpProfileSnapshot`, vedi ADR-014/ADR-005 §MoS).

**Ordinamento lessicografico** (RuleEngineService §sortedBy ruleId):
- `NCAV_LATEST` → 'N' dopo `MOAT...` (non esiste) e prima/dopo gli altri 'N'...
- Stringhe ordinate: `..., MOAT_*, NCAV_LATEST, NET_MARGIN_10Y_AVG, NET_NET_RATIO, PB_LATEST, ...`
- (`NCAV_LATEST` < `NET_MARGIN_10Y_AVG` < `NET_NET_RATIO` confermato dall'ordinamento ASCII lexicographic: 'C' < 'T'). Determinismo preservato.

### 5. Coordinamento EP-021 (typed payload)

Tre scenari:

**Scenario A — EP-021 done prima di EP-023**:
- US-096 emette nativamente `RuleSignal.NcavLatest` / `RuleSignal.NetNetRatio` (sotto-tipi sealed interface).
- OpenAPI `RuleSignal.oneOf` include già `RuleSignalNcavLatest` + `RuleSignalNetNetRatio` (definiti in ADR-028 §3).
- Sequenza ideale.

**Scenario B — EP-021 in flight contemporaneamente**:
- US-096 attende US-093 done (sotto-tipi NCAV definiti nello stesso commit di refactor schema).
- Tpm scheduling: US-093 → (US-096 || US-094) → (US-097 || US-095). US-096 e US-094 parallelizzabili.

**Scenario C — EP-021 non ancora schedulata**:
- US-096 emette payload **legacy** flat (`RuleSignal(ruleId, signal, observedValue=ratio, threshold="<2/3 NCAV", rationale="Prezzo $X.XX vs 2/3 NCAV $Y.YY")`).
- Handoff US-096 documenta esplicitamente il debito di refactor a valle.
- A valle di EP-021 done, un piccolo TSK di refactor migra `NcavLatestRule` + `NetNetRatioRule` al pattern tipato.

**Decisione**: preferiamo Scenario A o B. Lo Scenario C è ammesso ma con annotazione esplicita di refactor pendente. Il tpm sceglie la sequenza Sprint in base allo stato di EP-021 al momento della pianificazione.

### 6. FE — Badge "Net-Net" + Traffic Light rows

**Nuovi componenti**:

```
src/frontend/components/analysis/NetNetBadge.tsx
src/frontend/lib/rule-signals/formatters.ts   (estensione US-095 con 2 nuovi formatter)
```

**`<NetNetBadge>`**:
- Posizionato nel header della pagina `/analysis`, accanto al nome del ticker.
- Visibile **solo se** `analysis.signals.find(s => s.ruleId === 'NET_NET_RATIO')?.signal === 'GREEN'`.
- Stile coerente con `<MrMarketBadge>` di EP-013 (riuso `<Badge>` di shadcn/ui — design token system ADR-023).
- Tooltip: "Prezzo inferiore ai 2/3 del Net Current Asset Value — criterio Graham Cap.15".
- Accessibilità WCAG AA: `aria-label="Criterio Graham Net-Net soddisfatto"`, testo "Net-Net" visibile (non solo colore).
- Test Vitest: 3 stati (GREEN visibile, RED nascosto, INDETERMINATE nascosto).

**`TrafficLightPanel`** — aggiunge 2 righe (riuso pattern delle 13 esistenti):
- `NCAV_LATEST`: subtitle "NCAV per azione: $X.XX (totale: $YYM)".
- `NET_NET_RATIO`: subtitle "Prezzo $X.XX vs 2/3 NCAV $Y.YY (ratio Z.ZZ — soglia <0.67)".
- Formatter typed-driven (US-095 pattern); se EP-021 non done, formatter legacy che legge `rationale`.

**Routing/page impatto**: nessuno (pagina `/analysis` esistente, ADR-013).

### 7. Persistenza

**Tabella `rule_engine_result.signals` (JSONB)**: accoglie 2 elementi in più nella lista. Nessuna migration. Lo schema rimane libero (JSONB) come da ADR-005 §Persistenza risultati.

**Idempotenza**: il rule engine ricalcola al refresh del ticker; i nuovi `ruleId` si materializzano alla prima `/api/analysis/{ticker}` post-deploy. Coerente con il pattern "natural overwrite" di ADR-028 §4.

### 8. Out of scope

- **Filtro/screener "trova net-net su tutto l'universo"** (EP-012 batch Top Value Picks): non toccato. Il segnale è ticker-by-ticker. Eventuale integrazione batch = follow-up epica.
- **Altri criteri checklist enterprising Graham Cap.15** (P/E ≤ 9 oltre il defensive, TNA, debito ≤ 110% NCAV): out-of-scope. Possibili epiche autonome.
- **Localizzazione del badge** oltre la lingua corrente del FE.
- **Sector carve-out per net-net** (es. financials/REIT dove NCAV non ha senso semantico): documentato come limite noto; il segnale resta calcolato ma l'investitore deve interpretarlo nel contesto. Un possibile follow-up può introdurre `INDETERMINATE` per settori specifici.

## Componenti

| Componente | Layer | Path | Tipo |
|---|---|---|---|
| `NcavLatestRule` | BE | `src/backend/.../ruleengine/rules/NcavLatestRule.kt` | nuovo `@Component : ValuationRule` |
| `NetNetRatioRule` | BE | `src/backend/.../ruleengine/rules/NetNetRatioRule.kt` | nuovo `@Component : ValuationRule` |
| `NcavCalculator` | BE | `src/backend/.../ruleengine/calculators/NcavCalculator.kt` | utility object |
| `RuleSignal.NcavLatest` + `RuleSignal.NetNetRatio` | BE | `RuleSignal.kt` (sealed interface da ADR-028) | sotto-tipi |
| OpenAPI sotto-schemi | API | `design_&_architecture/api/openapi.yaml` | `RuleSignalNcavLatest`, `RuleSignalNetNetRatio` + entry in `discriminator.mapping` + `enum ruleId` esteso |
| `NetNetBadge` | FE | `src/frontend/components/analysis/NetNetBadge.tsx` | nuovo componente |
| `formatters.ts` | FE | `src/frontend/lib/rule-signals/formatters.ts` | estensione con 2 nuovi formatter |
| `TrafficLightPanel` | FE | `src/frontend/components/.../TrafficLightPanel.tsx` | accoglie 2 nuove righe (no modifica codice; rendering data-driven) |

### Schema DB

Nessuna migration. `rule_engine_result.signals` (JSONB) accoglie i 2 nuovi elementi senza alterazione strutturale.

### API Changes

| Endpoint | Change |
|---|---|
| `GET /api/analysis/{ticker}` | `signals[]` contiene 2 elementi in più (`NCAV_LATEST`, `NET_NET_RATIO`). `RuleSignal.discriminator.mapping` (ADR-028) include i 2 nuovi `ruleId`. Additivo non-breaking sull'array; breaking solo per consumer che assumevano array di lunghezza fissa = 13. |

## Motivazioni

1. **Chiude il gap formale** `net-net-implementation-gap` (wiki-keeper 2026-05-22).
2. **Pattern Rule Engine consolidato**: estensione naturale via `@Component : ValuationRule` (EP-010 blueprint). Zero refactor core; il `RuleEngineService` auto-inietta le 2 nuove rule.
3. **Formula canonica Graham Cap.15 verbatim**: NCAV = currentAssets − totalLiabilities; soglia 2/3 verbatim. Documentazione completa in [[net-net-stocks]] + [[enterprising-investor-checklist]] §Step 7.
4. **Dati FMP già disponibili**: `balance-sheet-statement` (EP-002), `key-metrics`, `FmpProfileSnapshot` (cache 1h, ADR-014). Nessuna nuova fonte esterna.
5. **Type-native con EP-021**: sfrutta il pattern `oneOf`/`discriminator` di ADR-028. Niente debito di tipo per i nuovi ruleId.
6. **Edge case espliciti**: `NCAV ≤ 0` (passivo > attivo corrente) = RED determinato, **non** NOT_CALCULABLE. Coerente con la semantica Graham (titolo non net-net per costruzione).
7. **Campi mancanti = INDETERMINATE**: invariato pattern US-004 / PATTERN §7 r.13 (mai coerce a 0).
8. **Badge UX immediato**: il segnale net-net è raro nei mercati moderni; un badge prominente lo rende percepibile a colpo d'occhio. Coerente con pattern badge esistenti (Mr. Market context flags EP-013).
9. **Estende coverage da defensive a enterprising Graham**: il Rule Engine copre ora 15 ruleId (7 Buffett + 6 Graham defensive + 2 Graham enterprising net-net). Avvicina la WebApp al framework completo Graham.

## Alternative considerate

| Alternativa | Esito |
|---|---|
| **Un solo ruleId `NET_NET` con tutti i metadati in un solo signal** | Mescola valore informativo (NCAV) e decisione (ratio); peggior leggibilità del Traffic Light. Scartato. |
| **Calcolo NCAV come campo top-level di `RuleEngineResultResponse`** (analogo a `grahamNumber`) | Trade-off: i campi top-level sono per valori sintetici universali (Graham Number, DCF, MoS); i `signals[]` per regole GREEN/YELLOW/RED. NCAV è informativo + decisionale → riga in `signals[]` semanticamente più coerente. Scartato. |
| **Soglia 2/3 configurabile via application.yml** | Over-engineering MVP. La soglia è verbatim Graham Cap.15. Riapribile in epica futura se serve "modalità più conservativa" (es. 50%). Scartato. |
| **Aggiungere altri criteri Cap.15 (P/E, debito, TNA) nello stesso ADR** | Out-of-scope EP-023 esplicito; ogni criterio merita US dedicata (vedi [[enterprising-investor-checklist]]). Scartato. |
| **NCAV con immobilizzazioni a valore di book (non zero)** | Viola la definizione canonica Graham (liquidation value conservative). Scartato. |
| **NCAV usando balance-sheet TRIMESTRALE** anziché annuale | Annual = più stabile + cache 24h già wrapped EP-002. Trimestrale = più reattivo ma più rumoroso, e Graham usa annuals. Scartato. |
| **Filtro screener "find net-net" su tutto universo** (estensione EP-012) | Out-of-scope EP-023; possibile follow-up epica. Scartato per ora. |

## Conseguenze

- **US-096 (BE)**: 2 nuove `@Component : ValuationRule` + `NcavCalculator` shared utility + test unitari (GREEN, RED, INDETERMINATE, NOT_CALCULABLE, edge `NCAV ≤ 0`).
- **US-097 (FE)**: nuovo `<NetNetBadge>` + 2 righe nel Traffic Light + test Vitest + smoke Playwright.
- **Coordinamento EP-021** (vedi §5): preferito Scenario A/B (typed-native). Scenario C ammesso con nota refactor.
- **Wave Sprint**: US-096 prima di US-097 (sequenza obbligata da `blocked_by`). Se EP-021 in flight: US-093 → US-096; US-097 dopo US-094 (per il formatter typed-driven).
- **No regression**: i 13 ruleId esistenti restano invariati nella logica. Test suite Rule Engine deve passare integralmente.
- **Wiki**: gap `net-net-implementation-gap` chiudibile da wiki-keeper post-merge US-097 (handoff cita ADR-029).
- **Coverage Graham completa**: il Rule Engine apre la strada al profilo enterprising (Cap.15). Eventuali altri criteri (TNA, debito, P/E ≤ 9 più stretto) possono diventare epiche follow-up.
- **ADR esistenti**: ADR-005 (Rule Engine) intatto. ADR-007 (API) coerente. ADR-014 (FMP profile snapshot) consumato as-is. ADR-028 (RuleSignal typed) il consumer principale (i 2 nuovi sotto-tipi vivono nello stesso `oneOf`).

## Tracciabilità US → AC → policy

| US | AC | Policy |
|---|---|---|
| US-096 | Response include `NCAV_LATEST` + `NET_NET_RATIO` con campi tipati | §2 + §3 |
| US-096 | GREEN se ratio < 0.6667 | §3 |
| US-096 | RED se ratio ≥ 0.6667 | §3 |
| US-096 | INDETERMINATE su dati mancanti | §2 + §3 (campi null → INDETERMINATE) |
| US-096 | `NCAV ≤ 0` → `NCAV_LATEST` RED + `NET_NET_RATIO` NOT_CALCULABLE | §2 + §3 (definizione esplicita; be-dev può scegliere e documenta) |
| US-096 | Schema OpenAPI documenta i 2 nuovi ruleId | §2 + §3 (sotto-schemi `RuleSignalNcavLatest`/`RuleSignalNetNetRatio`) |
| US-096 | No regression sui 13 esistenti | §4 (NcavCalculator isolato, no impatto altri rule) |
| US-097 | Pannello Traffic Light mostra 2 nuovi segnali con subtitle | §6 |
| US-097 | Badge "Net-Net" visibile solo su NET_NET_RATIO GREEN | §6 |
| US-097 | Badge accessibile WCAG AA | §6 |
| US-097 | Test Vitest 3 stati + smoke Playwright | §6 |
| US-097 | Gap `net-net-implementation-gap` richiamato in handoff | conseguenza chiusura wiki-keeper |

## Pagine collegate

- [ADR-005](ADR-005-rule-engine-design.md) — Rule Engine design (pattern strategy invariato)
- [ADR-007](ADR-007-api-contract.md) — OpenAPI contract (coerente)
- [ADR-014](ADR-014-fmp-profile-snapshot-ttl.md) — FMP profile snapshot (priceLatest source)
- [ADR-023](ADR-023-design-token-system-shadcn.md) — Design token system (badge styling)
- [ADR-028](ADR-028-rulesignal-typed-oneof-discriminator.md) — RuleSignal typed payload (sotto-tipi NCAV)
- [api/openapi.yaml](../api/openapi.yaml) §RuleSignal
- EP-023 `management/kanban/EP-023-net-net-stocks-ncav/EP-023.md`
- US-096 / US-097
- [[net-net-stocks]] §Definizione + §Strategia Operativa
- [[enterprising-investor-checklist]] §Step 7 — Criterio Net-Net
- [[value-investing-rule-engine]] §Output del Rule Engine
